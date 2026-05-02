package com.whatstat.reborn.data.repository

import com.whatstat.reborn.data.db.model.*
import com.whatstat.reborn.data.db.dao.MessageDao
import com.whatstat.reborn.data.db.dao.ChatDao
import com.whatstat.reborn.data.db.dao.ContactDao
import com.whatstat.reborn.data.cache.CacheManager
import com.whatstat.reborn.data.cache.WordFrequencyIndexData
import com.whatstat.reborn.data.cache.MonthlyWordIndexData
import com.whatstat.reborn.domain.model.ChatStats
import com.whatstat.reborn.domain.model.GlobalStats
import com.whatstat.reborn.domain.model.SlowChatStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.regex.Pattern
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.io.SuFile
import javax.inject.Inject
import javax.inject.Singleton

data class ChatContextInfo(
    val chatId: Long,
    val jid: String,
    val displayName: String,
    val isGroup: Boolean
)

/**
 * Repository per il calcolo delle statistiche.
 * Aggrega i dati dai vari DAO e calcola metriche derivate.
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val contactDao: ContactDao,
    private val cacheManager: CacheManager
) {

    /**
     * Calcola le statistiche globali aggregate su tutte le chat.
     * Supporta filtro temporale opzionale.
     */
    suspend fun getQuickGlobalStats(
        timeRange: TimeRange = TimeRange.CUSTOM,
        customRange: DateRange? = null,
        forceRefresh: Boolean = false,
        onProgress: (Int) -> Unit = {}
    ): GlobalStats = withContext(Dispatchers.IO) {
        // Cache solo per ALL_TIME (il caso più comune)
        if (!forceRefresh && timeRange == TimeRange.ALL_TIME) {
            val cached = cacheManager.getGlobalStats()
            if (cached != null) {
                android.util.Log.d("WhatStat", "Quick GlobalStats from cache")
                onProgress(100)
                return@withContext cached
            }
        }

        val (startTime, endTime) = if (timeRange == TimeRange.CUSTOM && customRange != null) {
            getDateRangeBounds(customRange)
        } else {
            getTimeRangeBounds(timeRange, customRange)
        }
        
        coroutineScope {
            val dTotalMessages = async { messageDao.getTotalMessageCount(startTime, endTime) }
            val dTotalChats    = async { chatDao.getChatCount() }
            val dHourly        = async { messageDao.getGlobalHourlyDistribution(startTime, endTime) }
            val dWeekly        = async { messageDao.getGlobalWeeklyDistribution(startTime, endTime) }
            val dDayOfMonth    = async { messageDao.getGlobalDayOfMonthDistribution(startTime, endTime) }
            val dMonthOfYear   = async { messageDao.getGlobalMonthOfYearDistribution(startTime, endTime) }
            val dYearTrend     = async { messageDao.getGlobalYearTrendDistribution(startTime, endTime) }
            val dStreak        = async { calculateLongestStreak(startTime) }
            val dOldestTs      = async { if (startTime != null) startTime else messageDao.getOldestTimestamp() }
            val dMediaCount    = async { messageDao.getMediaCount(startTime, endTime) }
            val dMostActiveChat = async { chatDao.getMostActiveChat(startTime, endTime) }

            onProgress(10)
            val totalMessages = dTotalMessages.await()
            onProgress(20)
            val totalChats = dTotalChats.await()
            onProgress(30)
            val hourly = dHourly.await()
            onProgress(40)
            val weekly = dWeekly.await()
            onProgress(50)
            val dayOfMonth = dDayOfMonth.await()
            onProgress(60)
            val monthOfYear = dMonthOfYear.await()
            onProgress(70)
            val yearTrend = dYearTrend.await()
            onProgress(80)
            val streak = dStreak.await()
            onProgress(90)
            val oldestTs = dOldestTs.await()
            onProgress(95)
            val mediaCount = dMediaCount.await()
            val mostActiveChatJid = dMostActiveChat.await()
            onProgress(100)
            
            val avgPerDay = if (startTime != null && endTime != null) {
                val daysInRange = ((endTime - startTime) / 86400000f).coerceAtLeast(1f)
                totalMessages / daysInRange
            } else if (oldestTs > 0) {
                val daysSinceFirst = ((System.currentTimeMillis() - oldestTs) / 86400000f)
                    .coerceAtLeast(1f)
                totalMessages / daysSinceFirst
            } else {
                0f
            }

val mostActiveChatName = contactDao.resolveContactName(mostActiveChatJid) ?: mostActiveChatJid.substringBefore("@")

            val stats = GlobalStats(
                totalMessages       = totalMessages,
                totalChats          = totalChats,
                totalContacts       = contactDao.getContactCount(),
                totalMediaFiles     = mediaCount,
                oldestMessageDate   = oldestTs,
                mostActiveChat      = mostActiveChatName,
                mostActiveHour      = hourly.maxByOrNull { it.count }?.hour ?: 0,
                mostActiveDayOfWeek = weekly.maxByOrNull { it.count }?.dayOfWeek ?: 0,
                averageMessagesPerDay = avgPerDay,
                longestStreak       = streak,
                hourlyDistribution  = hourly,
                weeklyDistribution  = weekly,
                dayOfMonthDistribution = dayOfMonth,
                monthOfYearDistribution = monthOfYear,
                yearTrendDistribution = yearTrend,
                topWords = emptyList(),
                topEmojis = emptyList()
            )
            
            // Salva in cache solo per ALL_TIME
            if (timeRange == TimeRange.ALL_TIME) {
                cacheManager.saveGlobalStats(stats)
            }
            stats
        }
    }

    /**
     * Carica le statistiche "lente" (parole + emoji) globali.
     *
     * ALL_TIME:
     *   - Usa il DataStore cache se disponibile (stessa sessione, DB invariato).
     *   - Altrimenti carica l'indice lessicale gzippato da disco e applica
     *     un delta incrementale: legge solo i messaggi nuovi dall'ultimo
     *     timestamp processato e unisce le frequenze. Full rebuild solo al
     *     primo avvio o se il DB risulta ridotto (cambio account).
     *
     * Filtri temporali (>= 28 giorni):
     *   - Usa l'indice mensile se disponibile e aggiornato.
     *   - Fallback: query SQL come in precedenza.
     */
    suspend fun getSlowGlobalStats(
        startTime: Long? = null,
        endTime: Long? = null,
        onProgress: (Int) -> Unit = {}
    ): GlobalStats = withContext(Dispatchers.IO) {

        if (startTime == null && endTime == null) {
            // ── ALL_TIME ─────────────────────────────────────────────────────

            // 1) DataStore (velocissimo se la sessione non è cambiata)
            val cached = cacheManager.getGlobalSlowStats()
            if (cached != null) {
                android.util.Log.d("WhatStat", "Slow GlobalStats from DataStore cache")
                onProgress(100)
                return@withContext GlobalStats(
                    totalMessages = 0, totalChats = 0, totalContacts = 0, totalMediaFiles = 0,
                    oldestMessageDate = 0, mostActiveChat = "", mostActiveHour = 0,
                    mostActiveDayOfWeek = 0, averageMessagesPerDay = 0f, longestStreak = 0,
                    topWords = cached.first, topEmojis = cached.second, topStickers = cached.third
                )
            }

            // 2) Indice lessicale (delta o full rebuild)
            onProgress(5)
            val currentMsgCount = messageDao.getGlobalTextMessageCount()
            onProgress(10)
            val (wordFreqs, emojiFreqs) = buildOrUpdateGlobalWordIndex(currentMsgCount)
            onProgress(80)

            val topWords  = wordFreqs.entries.sortedByDescending  { it.value }.take(50).map { WordFrequency(it.key, it.value) }
            val topEmojis = emojiFreqs.entries.sortedByDescending { it.value }.take(50).map { EmojiStat(it.key, it.value) }

            val topStickers = try {
                messageDao.getTopStickers(chatId = -1L, jid = null, startTime = null, endTime = null, limit = 50, globalQuery = true)
            } catch (e: Exception) {
                android.util.Log.w("WhatStat", "getGlobalTopStickers failed: ${e.message}")
                emptyList()
            }
            onProgress(100)

            // Salva in DataStore per accesso istantaneo nella stessa sessione
            cacheManager.saveGlobalSlowStats(topWords, topEmojis, topStickers)

            GlobalStats(
                totalMessages = 0, totalChats = 0, totalContacts = 0, totalMediaFiles = 0,
                oldestMessageDate = 0, mostActiveChat = "", mostActiveHour = 0,
                mostActiveDayOfWeek = 0, averageMessagesPerDay = 0f, longestStreak = 0,
                topWords = topWords, topEmojis = topEmojis, topStickers = topStickers
            )

        } else {
            // ── FILTRO TEMPORALE ─────────────────────────────────────────────
            onProgress(0)
            val resolvedStart = startTime ?: 0L
            val resolvedEnd   = endTime   ?: System.currentTimeMillis()
            val rangeSpanDays = (resolvedEnd - resolvedStart) / 86_400_000L

            val topWords: List<WordFrequency>
            val topEmojis: List<EmojiStat>

            // Usa l'indice mensile per range >= 28 giorni (approssimazione accettabile)
            val monthlyIndex = if (rangeSpanDays >= 28) cacheManager.loadMonthlyWordIndexFromDisk() else null
            if (monthlyIndex != null &&
                monthlyIndex.lastProcessedTimestamp >= resolvedEnd - 86_400_000L * 7
            ) {
                android.util.Log.d("WhatStat", "Word index: monthly for range=${rangeSpanDays}d")
                val (mw, me) = computeFromMonthlyIndex(monthlyIndex, resolvedStart, resolvedEnd)
                topWords  = mw
                topEmojis = me
            } else {
                // Fallback SQL
                val (tw, te) = try {
                    messageDao.getGlobalWordsAndEmojis(startTime, endTime, 50, 50)
                } catch (e: Exception) {
                    android.util.Log.w("WhatStat", "getGlobalWordsAndEmojis failed: ${e.message}")
                    Pair(emptyList(), emptyList())
                }
                topWords  = tw
                topEmojis = te
            }
            onProgress(80)

            val topStickers = try {
                messageDao.getTopStickers(chatId = -1L, jid = null, startTime = startTime, endTime = endTime, limit = 50, globalQuery = true)
            } catch (e: Exception) {
                android.util.Log.w("WhatStat", "getGlobalTopStickers failed: ${e.message}")
                emptyList()
            }
            onProgress(100)

            GlobalStats(
                totalMessages = 0, totalChats = 0, totalContacts = 0, totalMediaFiles = 0,
                oldestMessageDate = 0, mostActiveChat = "", mostActiveHour = 0,
                mostActiveDayOfWeek = 0, averageMessagesPerDay = 0f, longestStreak = 0,
                topWords = topWords, topEmojis = topEmojis, topStickers = topStickers
            )
        }
    }

    /**
     * Calcola le statistiche veloci per una singola chat (conteggi, date, distribuzioni).
     * Ritorna in <1s grazie agli indici.
     */
    suspend fun getQuickChatStats(
        chatId: Long,
        startTime: Long? = null,
        endTime: Long? = null,
        onProgress: (Int) -> Unit = {}
    ): ChatStats = withContext(Dispatchers.IO) {
        // Cache solo per ALL_TIME
        if (startTime == null && endTime == null) {
            val cached = cacheManager.getChatQuickStats(chatId)
            if (cached != null) {
                android.util.Log.d("WhatStat", "Quick ChatStats #$chatId from cache")
                return@withContext cached
            }
        }

        val basicInfo = chatDao.getChatBasicInfo(chatId)

        coroutineScope {
            val dMsgCount   = async { messageDao.getMessageCountForChat(chatId, startTime, endTime) }
            val dHourly     = async { messageDao.getHourlyDistribution(chatId, startTime, endTime) }
            val dWeekly     = async { messageDao.getDayOfWeekDistribution(chatId, startTime, endTime) }
            val dDayOfMonth = async { messageDao.getDayOfMonthDistribution(chatId, startTime, endTime) }
            val dMonthOfYear = async { messageDao.getMonthOfYearDistribution(chatId, startTime, endTime) }
            val dYearTrend  = async { messageDao.getYearTrendDistribution(chatId, startTime, endTime) }
            val dMonthly    = async { messageDao.getMonthlyTrend(chatId) }
            val dMediaStats = async { messageDao.getMediaStats(chatId, startTime, endTime) }
            val dFirstTs    = async { messageDao.getFirstMessageTimestamp(chatId) }
            val dLastTs     = async { messageDao.getLastMessageTimestamp(chatId) }
            val dAvgLen     = async { messageDao.getAverageMessageLength(chatId) }

            val displayName = if (basicInfo.isGroup) {
                basicInfo.subject ?: contactDao.resolveContactName(basicInfo.jid) ?: basicInfo.jid.substringBefore("@")
            } else {
                contactDao.resolveContactName(basicInfo.jid) ?: basicInfo.jid.substringBefore("@")
            }

            onProgress(15)
            val msgCount = dMsgCount.await()
            onProgress(40)
            val mediaStats = dMediaStats.await()
            onProgress(60)
            val hourly = dHourly.await()
            onProgress(75)
            val weekly = dWeekly.await()
            onProgress(90)
            
            val total = msgCount.total.coerceAtLeast(1)

            val result = ChatStats(
                chatId                   = chatId,
                contactName              = displayName,
                jid                      = basicInfo.jid,
                isGroup                  = basicInfo.isGroup,
                totalMessages            = msgCount.total,
                sentByMe                 = msgCount.sent,
                receivedFromThem         = msgCount.received,
                sentRatio                = msgCount.sent.toFloat() / total,
                firstMessageDate         = dFirstTs.await(),
                lastMessageDate          = dLastTs.await(),
                averageResponseTimeMs    = 0L,
                iStartedConversations    = 0,
                theyStartedConversations = 0,
                longestMessage           = "",
                averageMessageLength     = dAvgLen.await(),
                mediaCount               = mediaStats.sumOf { it.count },
                audioCount               = mediaStats.filter { it.mimeType.startsWith("audio") }.sumOf { it.count },
                videoCount               = mediaStats.filter { it.mimeType.startsWith("video") }.sumOf { it.count },
                hourlyDistribution       = hourly,
                weeklyDistribution       = weekly,
                dayOfMonthDistribution   = dDayOfMonth.await(),
                monthOfYearDistribution  = dMonthOfYear.await(),
                yearTrendDistribution    = dYearTrend.await(),
                monthlyTrend             = dMonthly.await(),
                topWords                 = emptyList(),
                senderBreakdown          = emptyList(),
                conversationStarterBreakdown = emptyList()
            )
            // Salva in cache solo per ALL_TIME
            if (startTime == null && endTime == null) {
                cacheManager.saveChatQuickStats(chatId, result)
            }
            result
        }
    }

    /**
     * Calcola le statistiche lente per una chat (top words, sender breakdown, starters, response time).
     *
     * ALL_TIME:
     *   - DataStore cache → istantaneo se DB invariato.
     *   - Altrimenti usa l'indice per-chat per parole/emoji (delta o full rebuild)
     *     e SQL per le statistiche che non si possono indicizzare incrementalmente
     *     (senderBreakdown, conversationStarters, avgResponseTime, longestMessage).
     *
     * Filtri temporali: SQL diretto (dataset ridotto, già veloce).
     */
    suspend fun getSlowChatStats(
        chatId: Long,
        startTime: Long? = null,
        endTime: Long? = null,
        onProgress: (Int) -> Unit = {}
    ): SlowChatStats = withContext(Dispatchers.IO) {
        // Cache solo se nessun filtro temporale
        if (startTime == null && endTime == null) {
            val cached = cacheManager.getChatSlowStats(chatId)
            if (cached != null) {
                android.util.Log.d("WhatStat", "Slow ChatStats #$chatId from DataStore cache")
                onProgress(100)
                return@withContext cached
            }
        }

        val basicInfo = chatDao.getChatBasicInfo(chatId)

        coroutineScope {
            // Parole/emoji: usa l'indice incrementale per ALL_TIME, SQL per filtri
            val dWordsAndEmojis: kotlinx.coroutines.Deferred<Pair<List<WordFrequency>, List<EmojiStat>>>
            if (startTime == null && endTime == null) {
                dWordsAndEmojis = async {
                    val count = messageDao.getChatTextMessageCount(chatId)
                    val (wf, ef) = buildOrUpdateChatWordIndex(chatId, count)
                    val words  = wf.entries.sortedByDescending { it.value }.take(50).map { WordFrequency(it.key, it.value) }
                    val emojis = ef.entries.sortedByDescending { it.value }.take(50).map { EmojiStat(it.key, it.value) }
                    words to emojis
                }
            } else {
                dWordsAndEmojis = async {
                    messageDao.getWordsAndEmojis(chatId = chatId, startTime = startTime, endTime = endTime, wordsLimit = 50, emojisLimit = 50)
                }
            }

            val dStickers   = async { messageDao.getTopStickers(chatId, null, startTime, endTime, 50) }
            val dStarters   = async { messageDao.getConversationStarters(chatId) }
            val dAvgResp    = async { messageDao.getAverageResponseTime(chatId) }
            val dSenders    = async { messageDao.getMessageCountBySender(chatId, startTime, endTime) }
            val dStartersBySender = async { messageDao.getConversationStartersBySender(chatId, startTime, endTime) }
            val dLongest    = async { messageDao.getLongestMessage(chatId) }

            onProgress(20)
            val starters = dStarters.await()
            onProgress(35)
            val rawSenders = dSenders.await()
            onProgress(50)
            val rawStarters = dStartersBySender.await()
            onProgress(65)
            val (topWords, topEmojis) = dWordsAndEmojis.await()
            val topStickers = dStickers.await()
            onProgress(95)
            val avgResp = dAvgResp.await()
            val longest = dLongest.await()
            onProgress(100)
            val senderJids = rawSenders.map { it.jid }
            val contactNames = contactDao.resolveContactNames(senderJids)
            val aggregatedSenders = rawSenders.map { stat ->
                val name = when {
                    stat.jid == "me@s.whatsapp.net" -> "Tu"
                    else -> contactNames[stat.jid]
                        ?: contactDao.resolveContactName(stat.jid)
                        ?: stat.displayName?.substringBefore("@")
                        ?: stat.jid.substringBefore("@")
                }
                stat.copy(displayName = name)
            }

            // Risoluzione nomi per conversation starters
            val starterJids = rawStarters.map { it.jid }
            val starterNames = contactDao.resolveContactNames(starterJids)
            val aggregatedStarters = rawStarters.map { stat ->
                val name = when {
                    stat.jid == "me@s.whatsapp.net" -> "Tu"
                    else -> starterNames[stat.jid]
                        ?: contactDao.resolveContactName(stat.jid)
                        ?: stat.displayName?.substringBefore("@")
                        ?: stat.jid.substringBefore("@")
                }
                stat.copy(displayName = name)
            }

            onProgress(100)
            val result = SlowChatStats(
                topWords                 = topWords,
                topEmojis                = topEmojis,
                topStickers              = topStickers,
                senderBreakdown          = aggregatedSenders,
                conversationStarterBreakdown = aggregatedStarters,
                averageResponseTimeMs    = avgResp,
                iStartedConversations    = starters.startedByMe,
                theyStartedConversations = starters.startedByThem,
                longestMessage           = longest
            )

            // Salva in DataStore solo per ALL_TIME
            if (startTime == null && endTime == null) {
                cacheManager.saveChatSlowStats(chatId, result)
            }

            result
        }
    }

    /**
     * Ritorna la distribuzione oraria globale (per la heatmap della dashboard).
     */
    suspend fun getGlobalHourlyDistribution(startTime: Long? = null, endTime: Long? = null): List<HourlyCount> = withContext(Dispatchers.IO) {
        messageDao.getGlobalHourlyDistribution(startTime, endTime)
    }

    /**
     * Ritorna la distribuzione settimanale globale (per il grafico della dashboard).
     */
    suspend fun getGlobalWeeklyDistribution(startTime: Long? = null, endTime: Long? = null): List<DayCount> = withContext(Dispatchers.IO) {
        messageDao.getGlobalWeeklyDistribution(startTime, endTime)
    }

    /**
     * Calcola il longest streak: numero massimo di giorni consecutivi
     * con almeno un messaggio inviato/ricevuto.
     */
    private fun calculateLongestStreak(startTime: Long? = null): Int {
        val dates = messageDao.getActiveDates(startTime)
        if (dates.isEmpty()) return 0

        var maxStreak = 1
        var currentStreak = 1

        for (i in 1 until dates.size) {
            if (dates[i] - dates[i - 1] == 1L) {
                currentStreak++
                if (currentStreak > maxStreak) maxStreak = currentStreak
            } else {
                currentStreak = 1
            }
        }

        return maxStreak
    }

    /**
     * Converte TimeRange in epoch milliseconds per filtri start/end.
     */
    fun getTimeRangeBounds(timeRange: TimeRange, customRange: DateRange? = null): Pair<Long?, Long?> {
        val now = System.currentTimeMillis()
        return when (timeRange) {
            TimeRange.ALL_TIME -> null to null // No time filter
            TimeRange.LAST_30_DAYS -> now - 2592000000L to now
            TimeRange.LAST_6_MONTHS -> now - 15552000000L to now
            TimeRange.LAST_YEAR -> now - 31536000000L to now
            TimeRange.CUSTOM -> customRange?.startTime to customRange?.endTime
        }
    }

    /**
     * Converte DateRange in epoch milliseconds ( Pair(start, end) ).
     */
    fun getDateRangeBounds(dateRange: DateRange): Pair<Long?, Long?> {
        return dateRange.startTime to dateRange.endTime
    }

    /**
     * Ricerca frequenza parola - versione diretta senza risoluzione nomi
     */
    suspend fun searchWordDirect(word: String, chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<WordSearchResult> = withContext(Dispatchers.IO) {
        val results = messageDao.searchWordFrequency(word, chatId, startTime, endTime)
        results
    }

    /**
     * Ricerca frequenza parola con filtro temporale e opzionalmente per chat.
     */
    suspend fun searchWord(word: String, chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<WordSearchResult> = withContext(Dispatchers.IO) {
        val results = messageDao.searchWordFrequency(word, chatId, startTime, endTime)
        if (results.isEmpty()) return@withContext emptyList()
        
        val jids = results.map { it.jid }
        val contactNames = contactDao.resolveContactNames(jids)
        
        // Resolve @lid → @s.whatsapp.net and aggregate counts
        val lidJids = jids.filter { it.endsWith("@lid") }
        val lidToReal = if (lidJids.isNotEmpty()) contactDao.resolveLidToRealJid(lidJids) else emptyMap()
        
        val aggregated = mutableMapOf<String, WordSearchResult>()
        results.forEach { result ->
            val canonicalJid = lidToReal[result.jid] ?: result.jid
            val displayName = if (canonicalJid == "me@s.whatsapp.net") "Tu"
                else contactNames[canonicalJid] ?: contactNames[result.jid] ?: canonicalJid.substringBefore("@")
            val existing = aggregated[canonicalJid]
            if (existing != null) {
                aggregated[canonicalJid] = existing.copy(count = existing.count + result.count)
            } else {
                aggregated[canonicalJid] = result.copy(jid = canonicalJid, displayName = displayName)
            }
        }
        aggregated.values.sortedByDescending { it.count }
    }

    /**
     * Recupera i messaggi contenenti la parola cercata per un mittente specifico.
     * Risolve il nome della chat di appartenenza tramite wa.db (chat 1-a-1)
     * oppure usa il subject del gruppo (gia' presente nel risultato DAO).
     */
    suspend fun getWordMessages(
        word: String,
        senderJid: String,
        chatId: Long? = null,
        startTime: Long? = null,
        endTime: Long? = null
    ): List<WordMessageResult> = withContext(Dispatchers.IO) {
        val raw = messageDao.searchWordMessages(word, senderJid, chatId, startTime, endTime)
        if (raw.isEmpty()) return@withContext emptyList()

        // Raccoglie i JID di chat 1-a-1 che hanno bisogno di risoluzione nome
        val jidsToResolve = raw
            .filter { !it.chatDisplayName.endsWith("@g.us") }
            .map { it.chatDisplayName }
            .distinct()
        
        val chatNames = if (jidsToResolve.isNotEmpty()) {
            contactDao.resolveContactNames(jidsToResolve)
        } else emptyMap()

        raw.map { msg ->
            val resolvedName = if (msg.chatDisplayName.endsWith("@g.us")) {
                // Per i gruppi, chatDisplayName contiene gia' il subject o il JID.
                // Se e' un JID (fallback nel DAO), proviamo a risolverlo.
                contactDao.resolveContactName(msg.chatDisplayName) ?: msg.chatDisplayName.substringBefore("@")
            } else {
                chatNames[msg.chatDisplayName] ?: msg.chatDisplayName.substringBefore("@")
            }
            msg.copy(chatDisplayName = resolvedName)
        }
    }

    /**
     * Statistiche chiamate globali o per chat con filtro temporale.
     */
    suspend fun getCallStats(chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<CallStat> = withContext(Dispatchers.IO) {
        val callStats = messageDao.getCallStats(chatId, startTime, endTime)
        if (callStats.isEmpty()) return@withContext emptyList()
        
        val jids = callStats.map { it.jid }
        val contactNames = contactDao.resolveContactNames(jids)
        
        callStats.map { stat ->
            val displayName = if (stat.jid == "me@s.whatsapp.net") "Tu"
                else contactNames[stat.jid] ?: stat.jid.substringBefore("@")
            stat.copy(displayName = displayName)
        }
    }

    /**
     * Statistiche link globali o per chat con filtro temporale.
     */
    suspend fun getLinkStats(chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<LinkStat> = withContext(Dispatchers.IO) {
        val linkStats = messageDao.getLinkStats(chatId, startTime, endTime)
        if (linkStats.isEmpty()) return@withContext emptyList()
        
        val jids = linkStats.map { it.jid }
        val contactNames = contactDao.resolveContactNames(jids)
        
        linkStats.map { stat ->
            val displayName = when (stat.jid) {
                "me@s.whatsapp.net" -> "Tu"
                "status@broadcast" -> "WhatsApp Stories"
                else -> contactNames[stat.jid] ?: stat.jid.substringBefore("@")
            }
            stat.copy(displayName = displayName)
        }
    }

    /**
     * Statistiche link per sender in una specifica chat.
     * Per chat singole: Tu vs Altro.
     * Per gruppi: tutti i partecipanti.
     */
    suspend fun getLinkStatsBySender(chatId: Long, startTime: Long? = null, endTime: Long? = null): List<LinkStat> = withContext(Dispatchers.IO) {
        val linkStats = messageDao.getLinkStatsBySender(chatId, startTime, endTime)
        if (linkStats.isEmpty()) return@withContext emptyList()
        
        val jids = linkStats.map { it.jid }
        val contactNames = contactDao.resolveContactNames(jids)
        
        // Resolve @lid → @s.whatsapp.net and aggregate counts
        val lidJids = jids.filter { it.endsWith("@lid") }
        val lidToReal = if (lidJids.isNotEmpty()) contactDao.resolveLidToRealJid(lidJids) else emptyMap()
        
        val aggregated = mutableMapOf<String, LinkStat>()
        linkStats.forEach { stat ->
            val canonicalJid = lidToReal[stat.jid] ?: stat.jid
            val displayName = if (canonicalJid == "me@s.whatsapp.net") "Tu"
                else contactNames[canonicalJid] ?: contactNames[stat.jid] ?: stat.displayName ?: canonicalJid.substringBefore("@")
            val existing = aggregated[canonicalJid]
            if (existing != null) {
                aggregated[canonicalJid] = existing.copy(
                    totalLinks = existing.totalLinks + stat.totalLinks,
                    mediaCount = existing.mediaCount + stat.mediaCount
                )
            } else {
                aggregated[canonicalJid] = stat.copy(jid = canonicalJid, displayName = displayName)
            }
        }
        aggregated.values.sortedByDescending { it.totalLinks + it.mediaCount }
    }

    suspend fun getChatContextInfo(chatId: Long): ChatContextInfo = withContext(Dispatchers.IO) {
        val basicInfo = chatDao.getChatBasicInfo(chatId)
        val resolvedName = if (basicInfo.isGroup) {
            basicInfo.subject ?: basicInfo.name ?: contactDao.resolveContactName(basicInfo.jid) ?: basicInfo.jid.substringBefore("@")
        } else {
            contactDao.resolveContactName(basicInfo.jid) ?: basicInfo.name ?: basicInfo.jid.substringBefore("@")
        }

        ChatContextInfo(
            chatId = basicInfo.chatId,
            jid = basicInfo.jid,
            displayName = resolvedName,
            isGroup = basicInfo.isGroup
        )
    }

    /**
     * Trova il chat_id dato il JID della chat.
     */
    suspend fun getChatIdByJid(jid: String): Long? = withContext(Dispatchers.IO) {
        messageDao.getChatIdByJid(jid)
    }

    /**
     * Statistiche link per sender dato un JID della chat.
     * Trova prima il chat_id dal JID, poi carica i dettagli per sender.
     */
    suspend fun getLinkStatsByJid(jid: String, startTime: Long? = null, endTime: Long? = null): List<LinkStat> = withContext(Dispatchers.IO) {
        val chatId = messageDao.getChatIdByJid(jid) ?: return@withContext emptyList()
        getLinkStatsBySender(chatId, startTime, endTime)
    }

    /**
     * Recupera i domini più frequenti dai link condivisi.
     */
    suspend fun getTopDomains(
        chatId: Long? = null,
        jid: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        onProgress: (Int) -> Unit = {}
    ): List<DomainStat> = withContext(Dispatchers.IO) {
        val texts = messageDao.getLinkTexts(chatId, jid, startTime, endTime)
        val domainCounts = mutableMapOf<String, Int>()
        val domainUrls = mutableMapOf<String, MutableMap<String, Int>>()
        val urlLastTimestamp = mutableMapOf<String, Long>()
        val total = texts.size.coerceAtLeast(1)
        
        // Regex per estrarre URL
        val urlPattern = Pattern.compile(
            "https?://[\\w-.]+(?:\\.[\\w-.]+)+[\\w-./?%&=]*",
            Pattern.CASE_INSENSITIVE
        )
        
        texts.forEachIndexed { index, (text, timestamp) ->
            if (index % 100 == 0) {
                onProgress((index.toFloat() / total * 100).toInt())
            }
            val matcher = urlPattern.matcher(text)
            while (matcher.find()) {
                val url = matcher.group()
                try {
                    val uri = URI(url)
                    val host = uri.host?.lowercase() ?: continue
                    val domain = if (host.startsWith("www.")) host.substring(4) else host
                    domainCounts[domain] = domainCounts.getOrDefault(domain, 0) + 1
                    val urlCounts = domainUrls.getOrPut(domain) { mutableMapOf() }
                    urlCounts[url] = urlCounts.getOrDefault(url, 0) + 1
                    // Track last timestamp for this URL
                    val currentLast = urlLastTimestamp[url] ?: 0L
                    if (timestamp > currentLast) {
                        urlLastTimestamp[url] = timestamp
                    }
                } catch (e: Exception) {
                    // Ignora URL malformati
                }
            }
        }
        
        domainCounts.map { (domain, count) ->
            val topUrls = domainUrls[domain]
                ?.map { (url, cnt) -> UrlFrequency(url, cnt, urlLastTimestamp[url]) }
                ?.sortedWith(compareByDescending<UrlFrequency> { it.count }.thenByDescending { it.lastTimestamp ?: 0 })
                ?: emptyList()
            DomainStat(domain, count, topUrls)
        }
            .sortedByDescending { it.count }
            .take(20)
    }

    /**
     * Ritorna le parole più usate per un mittente specifico.
     */
    suspend fun getTopWordsBySender(
        chatId: Long,
        jid: String?,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 5
    ): List<WordFrequency> = withContext(Dispatchers.IO) {
        messageDao.getTopWords(chatId, jid, startTime, endTime, limit)
    }

    suspend fun getTopEmojisBySender(
        chatId: Long,
        jid: String?,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 5
    ): List<EmojiStat> = withContext(Dispatchers.IO) {
        messageDao.getTopEmojis(chatId, jid, startTime, endTime, limit)
    }

    /**
     * Ritorna gli sticker più usati in una chat (aggregati per tutti i mittenti).
     */
    suspend fun getTopStickers(
        chatId: Long,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 10
    ): List<StickerStat> = withContext(Dispatchers.IO) {
        messageDao.getTopStickers(chatId, null, startTime, endTime, limit)
    }

    /**
     * Ritorna gli sticker più usati per un mittente specifico.
     */
    suspend fun getTopStickersBySender(
        chatId: Long,
        jid: String?,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 10
    ): List<StickerStat> = withContext(Dispatchers.IO) {
        messageDao.getTopStickers(chatId, jid, startTime, endTime, limit)
    }

    /**
     * Recupera lista di file media (foto/video) con risoluzione path assoluto.
     * Filtra automaticamente i file non esistenti nel filesystem.
     * Il paging è gestito in UI — qui viene restituita l'intera lista.
     */
    suspend fun getMediaFiles(
        chatId: Long? = null,
        jid: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        onProgress: ((Int) -> Unit)? = null
    ): List<MediaStat> = withContext(Dispatchers.IO) {
        onProgress?.invoke(0)

        // Query SQL e scansione filesystem in parallelo.
        // La scansione è lenta perché le directory WhatsApp contengono i file
        // di TUTTE le chat, ma viene eseguita una sola volta per sessione
        // (cachedMediaIndex): dalla seconda apertura è istantanea.
        val base = detectWhatsAppBase()
        lateinit var rawList: List<com.whatstat.reborn.data.db.model.MediaStat>
        lateinit var existingFiles: Set<String>
        coroutineScope {
            val rawDeferred = async { messageDao.getMediaFiles(chatId, jid, startTime, endTime) }
            val indexDeferred = async { getOrBuildMediaIndex(base) }
            rawList = rawDeferred.await()
            existingFiles = indexDeferred.await()
        }
        onProgress?.invoke(90)

        if (rawList.isEmpty()) {
            onProgress?.invoke(100)
            return@withContext emptyList()
        }

        val result = mutableListOf<com.whatstat.reborn.data.db.model.MediaStat>()
        for (stat in rawList) {
            val direct = "$base/${stat.filePath}"
            val sent = if (stat.filePath.contains("Media/") && !stat.filePath.contains("/Sent/")) {
                val relDir = stat.filePath.substringBeforeLast("/", "")
                val fileName = stat.filePath.substringAfterLast("/")
                "$base/$relDir/Sent/$fileName"
            } else null
            val found = when {
                direct in existingFiles -> direct
                sent != null && sent in existingFiles -> sent
                else -> null
            }
            if (found != null) result.add(stat.copy(filePath = found))
        }

        onProgress?.invoke(100)
        result
    }

    /**
     * Restituisce l'indice dei file presenti nella cartella Media di WhatsApp.
     * Priorità: 1) in-memory, 2) file gzippato su cacheDir, 3) find via root (lento).
     * Il risultato viene salvato sia in memoria che su disco per le sessioni successive.
     * La cache viene azzerata da CacheManager.clearCache() quando viene rilevato un
     * nuovo backup WhatsApp.
     */
    private fun getOrBuildMediaIndex(base: String): Set<String> {
        // 1) In-memory (stessa sessione)
        cacheManager.mediaFileIndex?.let { return it }
        // 2) Disco (sessioni precedenti)
        cacheManager.loadMediaIndexFromDisk()?.let { index ->
            cacheManager.mediaFileIndex = index
            return index
        }
        // 3) Scansione root — lenta, una tantum
        val result = Shell.cmd("find \"$base/Media\" -maxdepth 3 -type f 2>/dev/null").exec()
        val index: Set<String> = result.out.toHashSet()
        cacheManager.mediaFileIndex = index
        cacheManager.saveMediaIndexToDisk(index)
        return index
    }

    @Volatile private var cachedWhatsAppBase: String? = null

    private fun detectWhatsAppBase(): String {
        cachedWhatsAppBase?.let { return it }
        val scoped = "/sdcard/Android/media/com.whatsapp/WhatsApp"
        val base = try {
            if (SuFile("$scoped/Media").exists()) scoped else "/sdcard/WhatsApp"
        } catch (_: Exception) { "/sdcard/WhatsApp" }
        cachedWhatsAppBase = base
        return base
    }

    // =========================================================================
    // === Helper per l'indice lessicale incrementale ==========================
    // =========================================================================

    /** Unisce due mappe frequenza sommando i conteggi. */
    private fun mergeFrequencyMaps(base: Map<String, Int>, delta: Map<String, Int>): Map<String, Int> {
        if (delta.isEmpty()) return base
        val result = base.toMutableMap()
        delta.forEach { (key, count) -> result[key] = (result[key] ?: 0) + count }
        return result
    }

    /** Converte un timestamp Unix (ms) in stringa "YYYY-MM". */
    private fun timestampToYearMonth(timestamp: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        return "${cal.get(java.util.Calendar.YEAR)}-${String.format("%02d", cal.get(java.util.Calendar.MONTH) + 1)}"
    }

    /**
     * Costruisce o aggiorna l'indice mensile aggiungendo i testi con timestamp forniti.
     * Carica l'indice esistente dal disco, aggiunge i nuovi bucket mensili, salva.
     */
    private fun buildOrUpdateMonthlyIndex(textsWithTs: List<Pair<String, Long>>) {
        if (textsWithTs.isEmpty()) return
        val existing = cacheManager.loadMonthlyWordIndexFromDisk()
        val mWords  = existing?.monthlyWords
            ?.map { (k, v) -> k to v.toMutableMap() }
            ?.toMap(mutableMapOf()) ?: mutableMapOf()
        val mEmojis = existing?.monthlyEmojis
            ?.map { (k, v) -> k to v.toMutableMap() }
            ?.toMap(mutableMapOf()) ?: mutableMapOf()

        val byMonth = textsWithTs.groupBy { (_, ts) -> timestampToYearMonth(ts) }
        byMonth.forEach { (month, pairs) ->
            val texts = pairs.map { it.first }
            val wDelta = messageDao.buildWordFrequencyMap(texts)
            val eDelta = messageDao.buildEmojiFrequencyMap(texts)
            val mw = mWords.getOrPut(month)  { mutableMapOf() }
            val me = mEmojis.getOrPut(month) { mutableMapOf() }
            wDelta.forEach { (w, c) -> mw[w] = (mw[w] ?: 0) + c }
            eDelta.forEach { (e, c) -> me[e] = (me[e] ?: 0) + c }
        }

        val lastTs = textsWithTs.maxOf { it.second }
        cacheManager.saveMonthlyWordIndexToDisk(MonthlyWordIndexData(mWords, mEmojis, lastTs))
    }

    private var cachedMyJid: String? = null

    fun getMyJid(): String? {
        if (cachedMyJid == null) {
            cachedMyJid = messageDao.guessUserJid()
        }
        return cachedMyJid
    }

    /**
     * Recupera e consolida la storia del gruppo (partecipanti e nomi).
     */
    suspend fun getGroupHistory(chatId: Long): List<com.whatstat.reborn.domain.model.GroupEvent> = withContext(Dispatchers.IO) {
        val chatInfo = chatDao.getChatBasicInfo(chatId)
        if (!chatInfo.isGroup) return@withContext emptyList()
        
        val groupJid = chatInfo.jid

        val participantHistory = async { messageDao.getGroupParticipantHistory(chatId) }
        val nameHistory = async { messageDao.getGroupNameHistory(chatId) }

        val events = mutableListOf<com.whatstat.reborn.domain.model.GroupEvent>()

        participantHistory.await().forEach { entity ->
            val timestamp = if (entity.timestamp < 100000000000L) entity.timestamp * 1000L else entity.timestamp
            if (entity.action == 8) {
                events.add(com.whatstat.reborn.domain.model.GroupEvent.DescriptionChange(
                    timestamp = timestamp,
                    userJid = entity.jid,
                    newDescription = entity.textData ?: ""
                ))
            } else {
                events.add(com.whatstat.reborn.domain.model.GroupEvent.ParticipantAction(
                    timestamp = timestamp,
                    userJid = entity.jid,
                    actionType = entity.action,
                    adminJid = null
                ))
            }
        }

        nameHistory.await().forEach { entity ->
            events.add(com.whatstat.reborn.domain.model.GroupEvent.NameChange(
                timestamp = if (entity.timestamp < 100000000000L) entity.timestamp * 1000L else entity.timestamp,
                oldName = entity.oldData,
                newName = entity.newNameFallback ?: "",
                userJid = entity.senderJid
            ))
        }

        events.sortedByDescending { it.timestamp }
    }

    /**
     * Risolve i nomi dei contatti per una lista di JID.
     */
    suspend fun resolveContactNames(jids: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        contactDao.resolveContactNames(jids)
    }

    /**
     * Calcola top-N parole ed emoji dall'indice mensile per un intervallo di date.
     * Strategia "bucket": include tutti i mesi che si sovrappongono all'intervallo.
     * Usato solo per range >= 28 giorni dove l'approssimazione mensile è accettabile.
     */
    private fun computeFromMonthlyIndex(
        index: MonthlyWordIndexData,
        startTime: Long,
        endTime: Long,
        limit: Int = 50
    ): Pair<List<WordFrequency>, List<EmojiStat>> {
        val startMonth = timestampToYearMonth(startTime)
        val endMonth   = timestampToYearMonth(endTime)
        val wordTotals  = mutableMapOf<String, Int>()
        val emojiTotals = mutableMapOf<String, Int>()
        index.monthlyWords.entries
            .filter { (m, _) -> m >= startMonth && m <= endMonth }
            .forEach { (_, freqs) -> freqs.forEach { (w, c) -> wordTotals[w]  = (wordTotals[w]  ?: 0) + c } }
        index.monthlyEmojis.entries
            .filter { (m, _) -> m >= startMonth && m <= endMonth }
            .forEach { (_, freqs) -> freqs.forEach { (e, c) -> emojiTotals[e] = (emojiTotals[e] ?: 0) + c } }
        val words  = wordTotals.entries.sortedByDescending  { it.value }.take(limit).map { WordFrequency(it.key, it.value) }
        val emojis = emojiTotals.entries.sortedByDescending { it.value }.take(limit).map { EmojiStat(it.key, it.value) }
        return words to emojis
    }

    /**
     * Costruisce o aggiorna l'indice globale lessicale con approccio delta.
     * Ritorna la coppia (wordFreqMap, emojiFreqMap) aggiornata.
     * Se [existingIndex] è null o obsoleto, esegue full rebuild.
     */
    private fun buildOrUpdateGlobalWordIndex(currentMsgCount: Int): Pair<Map<String, Int>, Map<String, Int>> {
        val existing = cacheManager.loadGlobalWordIndexFromDisk()

        return if (existing != null && currentMsgCount >= existing.totalMessageCount) {
            // Delta: solo messaggi nuovi
            android.util.Log.d("WhatStat", "Word index global: delta from ts=${existing.lastProcessedTimestamp}")
            val newTexts = messageDao.getTextsWithTimestamps(sinceTimestamp = existing.lastProcessedTimestamp)
            if (newTexts.isEmpty()) {
                android.util.Log.d("WhatStat", "Word index global: no new messages")
                existing.wordFrequencies to existing.emojiFrequencies
            } else {
                val deltaWords  = messageDao.buildWordFrequencyMap(newTexts.map { it.first })
                val deltaEmojis = messageDao.buildEmojiFrequencyMap(newTexts.map { it.first })
                val merged = mergeFrequencyMaps(existing.wordFrequencies, deltaWords) to
                             mergeFrequencyMaps(existing.emojiFrequencies, deltaEmojis)
                buildOrUpdateMonthlyIndex(newTexts)
                val maxTs = newTexts.maxOf { it.second }
                cacheManager.saveGlobalWordIndexToDisk(
                    WordFrequencyIndexData(merged.first, merged.second, maxTs, currentMsgCount)
                )
                merged
            }
        } else {
            // Full rebuild
            android.util.Log.d("WhatStat", "Word index global: full rebuild (count=$currentMsgCount)")
            val textsWithTs = messageDao.getTextsWithTimestamps()
            val wordFreqs   = messageDao.buildWordFrequencyMap(textsWithTs.map { it.first })
            val emojiFreqs  = messageDao.buildEmojiFrequencyMap(textsWithTs.map { it.first })
            if (textsWithTs.isNotEmpty()) {
                buildOrUpdateMonthlyIndex(textsWithTs)
                val maxTs = textsWithTs.maxOf { it.second }
                cacheManager.saveGlobalWordIndexToDisk(
                    WordFrequencyIndexData(wordFreqs, emojiFreqs, maxTs, currentMsgCount)
                )
            }
            wordFreqs to emojiFreqs
        }
    }

    /**
     * Costruisce o aggiorna l'indice lessicale per una singola chat.
     * Ritorna la coppia (wordFreqMap, emojiFreqMap) aggiornata.
     */
    private fun buildOrUpdateChatWordIndex(chatId: Long, currentMsgCount: Int): Pair<Map<String, Int>, Map<String, Int>> {
        val existing = cacheManager.loadChatWordIndexFromDisk(chatId)

        return if (existing != null && currentMsgCount >= existing.totalMessageCount) {
            android.util.Log.d("WhatStat", "Word index chat #$chatId: delta from ts=${existing.lastProcessedTimestamp}")
            val newTexts = messageDao.getTextsWithTimestamps(chatId = chatId, sinceTimestamp = existing.lastProcessedTimestamp)
            if (newTexts.isEmpty()) {
                existing.wordFrequencies to existing.emojiFrequencies
            } else {
                val deltaWords  = messageDao.buildWordFrequencyMap(newTexts.map { it.first })
                val deltaEmojis = messageDao.buildEmojiFrequencyMap(newTexts.map { it.first })
                val merged = mergeFrequencyMaps(existing.wordFrequencies, deltaWords) to
                             mergeFrequencyMaps(existing.emojiFrequencies, deltaEmojis)
                val maxTs = newTexts.maxOf { it.second }
                cacheManager.saveChatWordIndexToDisk(chatId,
                    WordFrequencyIndexData(merged.first, merged.second, maxTs, currentMsgCount)
                )
                merged
            }
        } else {
            android.util.Log.d("WhatStat", "Word index chat #$chatId: full rebuild (count=$currentMsgCount)")
            val textsWithTs = messageDao.getTextsWithTimestamps(chatId = chatId)
            val wordFreqs   = messageDao.buildWordFrequencyMap(textsWithTs.map { it.first })
            val emojiFreqs  = messageDao.buildEmojiFrequencyMap(textsWithTs.map { it.first })
            if (textsWithTs.isNotEmpty()) {
                val maxTs = textsWithTs.maxOf { it.second }
                cacheManager.saveChatWordIndexToDisk(chatId,
                    WordFrequencyIndexData(wordFreqs, emojiFreqs, maxTs, currentMsgCount)
                )
            }
            wordFreqs to emojiFreqs
        }
    }

    /**
     * Ritorna suggerimenti di parole basati sull'indice lessicale esistente.
     */
    suspend fun getWordSuggestions(query: String, chatId: Long? = null): List<String> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.lowercase().trim()
        if (normalizedQuery.length < 3) return@withContext emptyList()

        val index = if (chatId != null) {
            cacheManager.loadChatWordIndexFromDisk(chatId)
        } else {
            cacheManager.loadGlobalWordIndexFromDisk()
        }

        if (index == null) return@withContext emptyList()

        index.wordFrequencies.entries
            .filter { it.key.startsWith(normalizedQuery) }
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }
}

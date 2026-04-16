package com.whatstat.reborn.data.repository

import com.whatstat.reborn.data.db.model.*
import com.whatstat.reborn.data.db.dao.MessageDao
import com.whatstat.reborn.data.db.dao.ChatDao
import com.whatstat.reborn.data.db.dao.ContactDao
import com.whatstat.reborn.domain.model.ChatStats
import com.whatstat.reborn.domain.model.GlobalStats
import com.whatstat.reborn.domain.model.SlowChatStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository per il calcolo delle statistiche.
 * Aggrega i dati dai vari DAO e calcola metriche derivate.
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val messageDao: MessageDao,
    private val chatDao: ChatDao,
    private val contactDao: ContactDao
) {

    /**
     * Calcola le statistiche globali aggregate su tutte le chat.
     * Supporta filtro temporale opzionale.
     */
    suspend fun getQuickGlobalStats(
        timeRange: TimeRange = TimeRange.CUSTOM,
        customRange: DateRange? = null,
        onProgress: (Int) -> Unit = {}
    ): GlobalStats = withContext(Dispatchers.IO) {
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
            
            val avgPerDay = if (oldestTs > 0) {
                val daysSinceFirst = ((System.currentTimeMillis() - oldestTs) / 86400000f)
                    .coerceAtLeast(1f)
                totalMessages / daysSinceFirst
            } else {
                0f
            }

            val mostActiveChatName = contactDao.resolveContactName(mostActiveChatJid) ?: mostActiveChatJid.substringBefore("@")

            GlobalStats(
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
                topEmojis          = emptyList()
            )
}
    }

    /**
     * Carica solo le statistiche "lente" (emoji).
     */
    suspend fun getSlowGlobalStats(
        startTime: Long? = null,
        endTime: Long? = null,
        onProgress: (Int) -> Unit = {}
    ): GlobalStats = withContext(Dispatchers.IO) {
        onProgress(0)
        val topEmojis = try {
            messageDao.getGlobalTopEmojis(startTime, endTime, 30)
        } catch (e: Exception) {
            android.util.Log.w("WhatStat", "getGlobalTopEmojis failed: ${e.message}")
            emptyList()
        }
        onProgress(100)
        GlobalStats(
            totalMessages = 0, totalChats = 0, totalContacts = 0, totalMediaFiles = 0,
            oldestMessageDate = 0, mostActiveChat = "", mostActiveHour = 0, mostActiveDayOfWeek = 0,
            averageMessagesPerDay = 0f, longestStreak = 0, topEmojis = topEmojis
        )
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

            ChatStats(
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
        }
    }

    /**
     * Calcola le statistiche lente (top words, sender breakdown, starters, response time).
     * Ritorna un partial update da mergiare con i dati quick.
     */
    suspend fun getSlowChatStats(
        chatId: Long,
        startTime: Long? = null,
        endTime: Long? = null,
        onProgress: (Int) -> Unit = {}
    ): SlowChatStats = withContext(Dispatchers.IO) {
        val basicInfo = chatDao.getChatBasicInfo(chatId)

        coroutineScope {
            val dTopWords   = async { messageDao.getTopWords(chatId = chatId, startTime = startTime, endTime = endTime) }
            val dTopEmojis  = async { messageDao.getTopEmojis(chatId = chatId, startTime = startTime, endTime = endTime) }
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
            val topWords = dTopWords.await()
            val topEmojis = dTopEmojis.await()
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
                        ?: stat.displayName
                }
                stat.copy(displayName = name)
            }.groupBy { it.displayName }.map { (name, group) ->
                com.whatstat.reborn.data.db.model.SenderStat(
                    jid = group.first().jid,
                    displayName = name,
                    count = group.sumOf { it.count }
                )
            }.sortedByDescending { it.count }

            // Risoluzione nomi per conversation starters
            val starterJids = rawStarters.map { it.jid }
            val starterNames = contactDao.resolveContactNames(starterJids)
            val aggregatedStarters = rawStarters.map { stat ->
                val name = when {
                    stat.jid == "me@s.whatsapp.net" -> "Tu"
                    else -> starterNames[stat.jid]
                        ?: contactDao.resolveContactName(stat.jid)
                        ?: stat.jid.substringBefore("@")
                }
                stat.copy(displayName = name)
            }.groupBy { it.displayName }.map { (name, group) ->
                com.whatstat.reborn.data.db.model.SenderStat(
                    jid = group.first().jid,
                    displayName = name,
                    count = group.sumOf { it.count }
                )
            }.sortedByDescending { it.count }

            onProgress(100)
            SlowChatStats(
                topWords                 = topWords,
                topEmojis                = topEmojis,
                senderBreakdown          = aggregatedSenders,
                conversationStarterBreakdown = aggregatedStarters,
                averageResponseTimeMs    = avgResp,
                iStartedConversations    = starters.startedByMe,
                theyStartedConversations = starters.startedByThem,
                longestMessage           = longest
            )
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
            TimeRange.TODAY -> now - 86400000L to now
            TimeRange.YESTERDAY -> now - 172800000L to now - 86400000L
            TimeRange.LAST_7_DAYS -> now - 604800000L to now
            TimeRange.LAST_30_DAYS -> now - 2592000000L to now
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
            val displayName = if (stat.jid == "me@s.whatsapp.net") "Tu"
                else contactNames[stat.jid] ?: stat.jid.substringBefore("@")
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
        val total = texts.size.coerceAtLeast(1)
        
        // Regex per estrarre URL
        val urlPattern = Pattern.compile(
            "https?://[\\w-.]+(?:\\.[\\w-.]+)+[\\w-./?%&=]*",
            Pattern.CASE_INSENSITIVE
        )
        
        texts.forEachIndexed { index, text ->
            if (index % 100 == 0) { // Aggiorna ogni 100 messaggi per performance
                onProgress((index.toFloat() / total * 100).toInt())
            }
            val matcher = urlPattern.matcher(text)
            while (matcher.find()) {
                val url = matcher.group()
                try {
                    val uri = URI(url)
                    val host = uri.host?.lowercase() ?: continue
                    // Normalizzazione: rimuove www.
                    val domain = if (host.startsWith("www.")) host.substring(4) else host
                    domainCounts[domain] = domainCounts.getOrDefault(domain, 0) + 1
                } catch (e: Exception) {
                    // Ignora URL malformati
                }
            }
        }
        
        domainCounts.map { DomainStat(it.key, it.value) }
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
}

package com.whatstat.reborn.data.repository

import com.whatstat.reborn.data.db.dao.ChatDao
import com.whatstat.reborn.data.db.dao.ContactDao
import com.whatstat.reborn.data.db.dao.MessageDao
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.DayCount
import com.whatstat.reborn.data.db.model.HourlyCount
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.domain.model.ChatStats
import com.whatstat.reborn.domain.model.GlobalStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    suspend fun getGlobalStats(timeRange: TimeRange = TimeRange.CUSTOM, customRange: DateRange? = null): GlobalStats = withContext(Dispatchers.IO) {
        val (startTime, endTime) = if (timeRange == TimeRange.CUSTOM && customRange != null) {
            getDateRangeBounds(customRange)
        } else {
            getTimeRangeBounds(timeRange, customRange)
        }
        
        val totalMessages = messageDao.getTotalMessageCount(startTime, endTime)
        val totalChats    = chatDao.getChatCount()
        val hourly        = messageDao.getGlobalHourlyDistribution(startTime, endTime)
        val weekly        = messageDao.getGlobalWeeklyDistribution(startTime, endTime)
        val streak        = calculateLongestStreak(startTime)
        val oldestTs      = if (startTime != null) startTime else messageDao.getOldestTimestamp()

        val avgPerDay = if (oldestTs > 0) {
            val daysSinceFirst = ((System.currentTimeMillis() - oldestTs) / 86400000f)
                .coerceAtLeast(1f)
            totalMessages / daysSinceFirst
        } else {
            0f
        }

        val mostActiveChatJid = chatDao.getMostActiveChat()
        val mostActiveChatName = if (mostActiveChatJid.contains("@g.us")) {
            // Per gruppi, il subject è già nel JID? No, è un JID tipo 123@g.us
            // Risolvi il nome via wa_contacts o chatDao
            contactDao.resolveContactName(mostActiveChatJid) ?: mostActiveChatJid.substringBefore("@")
        } else {
            contactDao.resolveContactName(mostActiveChatJid) ?: mostActiveChatJid.substringBefore("@")
        }

        GlobalStats(
            totalMessages       = totalMessages,
            totalChats          = totalChats,
            totalContacts       = contactDao.getContactCount(),
            totalMediaFiles     = messageDao.getMediaCount(),
            oldestMessageDate   = oldestTs,
            mostActiveChat      = mostActiveChatName,
            mostActiveHour      = hourly.maxByOrNull { it.count }?.hour ?: 0,
            mostActiveDayOfWeek = weekly.maxByOrNull { it.count }?.dayOfWeek ?: 0,
            averageMessagesPerDay = avgPerDay,
            longestStreak       = streak
        )
    }

    /**
     * Calcola le statistiche complete per una singola chat.
     * Supporta filtro temporale opzionale.
     */
    suspend fun getChatStats(chatId: Long, startTime: Long? = null, endTime: Long? = null): ChatStats = withContext(Dispatchers.IO) {
        val basicInfo  = chatDao.getChatBasicInfo(chatId)
        val msgCount   = messageDao.getMessageCountForChat(chatId, startTime, endTime)
        val hourly     = messageDao.getHourlyDistribution(chatId, startTime, endTime)
        val weekly     = messageDao.getDayOfWeekDistribution(chatId, startTime, endTime)
        val monthly    = messageDao.getMonthlyTrend(chatId)
        val topWords   = messageDao.getTopWords(chatId, startTime, endTime)
        val mediaStats = messageDao.getMediaStats(chatId)
        val starters   = messageDao.getConversationStarters(chatId)
        val avgResp    = messageDao.getAverageResponseTime(chatId)

        // Risolvi il nome dal database contatti (wa.db) o usa subject per gruppi
        val displayName = if (basicInfo.isGroup) {
            basicInfo.subject ?: contactDao.resolveContactName(basicInfo.jid) ?: basicInfo.jid.substringBefore("@")
        } else {
            contactDao.resolveContactName(basicInfo.jid) ?: basicInfo.jid.substringBefore("@")
        }

        val total = msgCount.total.coerceAtLeast(1)

        // Breakdown messaggi per mittente (pie chart)
        val rawSenders = messageDao.getMessageCountBySender(chatId, startTime, endTime)
        val senderJids = rawSenders.map { it.jid }
        val contactNames = contactDao.resolveContactNames(senderJids)
        val resolvedSenders = rawSenders.map { stat ->
            val name = when {
                stat.jid == "me@s.whatsapp.net" -> "Tu"
                else -> contactNames[stat.jid]
                    ?: contactDao.resolveContactName(stat.jid)
                    ?: stat.displayName
            }
            stat.copy(displayName = name)
        }
        // Aggrega eventuali @lid duplicati
        val aggregatedSenders = resolvedSenders
            .groupBy { it.displayName }
            .map { (name, group) ->
                com.whatstat.reborn.data.db.model.SenderStat(
                    jid = group.first().jid,
                    displayName = name,
                    count = group.sumOf { it.count }
                )
            }
            .sortedByDescending { it.count }

        // Breakdown conversation starters per mittente
        val rawStarters = messageDao.getConversationStartersBySender(chatId, startTime, endTime)
        val starterJids = rawStarters.map { it.jid }
        val starterNames = contactDao.resolveContactNames(starterJids)
        val resolvedStarters = rawStarters.map { stat ->
            val name = when {
                stat.jid == "me@s.whatsapp.net" -> "Tu"
                else -> starterNames[stat.jid]
                    ?: contactDao.resolveContactName(stat.jid)
                    ?: stat.jid.substringBefore("@")
            }
            stat.copy(displayName = name)
        }
        val aggregatedStarters = resolvedStarters
            .groupBy { it.displayName }
            .map { (name, group) ->
                com.whatstat.reborn.data.db.model.SenderStat(
                    jid = group.first().jid,
                    displayName = name,
                    count = group.sumOf { it.count }
                )
            }
            .sortedByDescending { it.count }

        ChatStats(
            chatId                   = chatId,
            contactName              = displayName,
            jid                      = basicInfo.jid,
            isGroup                  = basicInfo.isGroup,
            totalMessages            = msgCount.total,
            sentByMe                 = msgCount.sent,
            receivedFromThem         = msgCount.received,
            sentRatio                = msgCount.sent.toFloat() / total,
            firstMessageDate         = messageDao.getFirstMessageTimestamp(chatId),
            lastMessageDate          = messageDao.getLastMessageTimestamp(chatId),
            averageResponseTimeMs    = avgResp,
            iStartedConversations    = starters.startedByMe,
            theyStartedConversations = starters.startedByThem,
            longestMessage           = messageDao.getLongestMessage(chatId),
            averageMessageLength     = messageDao.getAverageMessageLength(chatId),
            mediaCount               = mediaStats.sumOf { it.count },
            audioCount               = mediaStats.filter { it.mimeType.startsWith("audio") }.sumOf { it.count },
            videoCount               = mediaStats.filter { it.mimeType.startsWith("video") }.sumOf { it.count },
            hourlyDistribution       = hourly,
            weeklyDistribution       = weekly,
            monthlyTrend             = monthly,
            topWords                 = topWords,
            senderBreakdown          = aggregatedSenders,
            conversationStarterBreakdown = aggregatedStarters
        )
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
    suspend fun searchWordDirect(word: String, chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.WordSearchResult> = withContext(Dispatchers.IO) {
        val results = messageDao.searchWordFrequency(word, chatId, startTime, endTime)
        results
    }

    /**
     * Ricerca frequenza parola con filtro temporale e opzionalmente per chat.
     */
    suspend fun searchWord(word: String, chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.WordSearchResult> = withContext(Dispatchers.IO) {
        val results = messageDao.searchWordFrequency(word, chatId, startTime, endTime)
        if (results.isEmpty()) return@withContext emptyList()
        
        val jids = results.map { it.jid }
        val contactNames = contactDao.resolveContactNames(jids)
        
        // Resolve @lid → @s.whatsapp.net and aggregate counts
        val lidJids = jids.filter { it.endsWith("@lid") }
        val lidToReal = if (lidJids.isNotEmpty()) contactDao.resolveLidToRealJid(lidJids) else emptyMap()
        
        val aggregated = mutableMapOf<String, com.whatstat.reborn.data.db.model.WordSearchResult>()
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
    suspend fun getCallStats(chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.CallStat> = withContext(Dispatchers.IO) {
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
    suspend fun getLinkStats(chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.LinkStat> = withContext(Dispatchers.IO) {
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
    suspend fun getLinkStatsBySender(chatId: Long, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.LinkStat> = withContext(Dispatchers.IO) {
        val linkStats = messageDao.getLinkStatsBySender(chatId, startTime, endTime)
        if (linkStats.isEmpty()) return@withContext emptyList()
        
        val jids = linkStats.map { it.jid }
        val contactNames = contactDao.resolveContactNames(jids)
        
        // Resolve @lid → @s.whatsapp.net and aggregate counts
        val lidJids = jids.filter { it.endsWith("@lid") }
        val lidToReal = if (lidJids.isNotEmpty()) contactDao.resolveLidToRealJid(lidJids) else emptyMap()
        
        val aggregated = mutableMapOf<String, com.whatstat.reborn.data.db.model.LinkStat>()
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
    suspend fun getLinkStatsByJid(jid: String, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.LinkStat> = withContext(Dispatchers.IO) {
        val chatId = messageDao.getChatIdByJid(jid) ?: return@withContext emptyList()
        getLinkStatsBySender(chatId, startTime, endTime)
    }
}

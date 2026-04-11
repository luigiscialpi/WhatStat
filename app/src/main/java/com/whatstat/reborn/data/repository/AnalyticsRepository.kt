package com.whatstat.reborn.data.repository

import com.whatstat.reborn.data.db.dao.ChatDao
import com.whatstat.reborn.data.db.dao.ContactDao
import com.whatstat.reborn.data.db.dao.MessageDao
import com.whatstat.reborn.data.db.model.HourlyCount
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
     */
    suspend fun getGlobalStats(): GlobalStats = withContext(Dispatchers.IO) {
        val totalMessages = messageDao.getTotalMessageCount()
        val totalChats    = chatDao.getChatCount()
        val hourly        = messageDao.getGlobalHourlyDistribution()
        val weekly        = messageDao.getGlobalWeeklyDistribution()
        val streak        = calculateLongestStreak()
        val oldestTs      = messageDao.getOldestTimestamp()

        // Calcola media messaggi/giorno basata sul range reale
        val avgPerDay = if (oldestTs > 0) {
            val daysSinceFirst = ((System.currentTimeMillis() - oldestTs) / 86400000f)
                .coerceAtLeast(1f)
            totalMessages / daysSinceFirst
        } else {
            0f
        }

        GlobalStats(
            totalMessages       = totalMessages,
            totalChats          = totalChats,
            totalContacts       = contactDao.getContactCount(),
            totalMediaFiles     = messageDao.getMediaCount(),
            oldestMessageDate   = oldestTs,
            mostActiveChat      = chatDao.getMostActiveChat(),
            mostActiveHour      = hourly.maxByOrNull { it.count }?.hour ?: 0,
            mostActiveDayOfWeek = weekly.maxByOrNull { it.count }?.dayOfWeek ?: 0,
            averageMessagesPerDay = avgPerDay,
            longestStreak       = streak
        )
    }

    /**
     * Calcola le statistiche complete per una singola chat.
     */
    suspend fun getChatStats(chatId: Long): ChatStats = withContext(Dispatchers.IO) {
        val basicInfo  = chatDao.getChatBasicInfo(chatId)
        val msgCount   = messageDao.getMessageCountForChat(chatId)
        val hourly     = messageDao.getHourlyDistribution(chatId)
        val weekly     = messageDao.getDayOfWeekDistribution(chatId)
        val monthly    = messageDao.getMonthlyTrend(chatId)
        val topWords   = messageDao.getTopWords(chatId)
        val mediaStats = messageDao.getMediaStats(chatId)
        val starters   = messageDao.getConversationStarters(chatId)
        val avgResp    = messageDao.getAverageResponseTime(chatId)

        // Risolvi il nome dal database contatti se mancante
        val displayName = basicInfo.name
            ?: basicInfo.subject
            ?: contactDao.resolveContactName(basicInfo.jid)
            ?: basicInfo.jid.substringBefore("@")

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
            topWords                 = topWords
        )
    }

    /**
     * Ritorna la distribuzione oraria globale (per la heatmap della dashboard).
     */
    suspend fun getGlobalHourlyDistribution(): List<HourlyCount> = withContext(Dispatchers.IO) {
        messageDao.getGlobalHourlyDistribution()
    }

    /**
     * Calcola il longest streak: numero massimo di giorni consecutivi
     * con almeno un messaggio inviato/ricevuto.
     */
    private fun calculateLongestStreak(): Int {
        val dates = messageDao.getActiveDates()
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
}

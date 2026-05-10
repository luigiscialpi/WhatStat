package com.whatstat.reborn.domain.model

import kotlinx.serialization.Serializable

import com.whatstat.reborn.data.db.model.DayCount
import com.whatstat.reborn.data.db.model.DayOfMonthCount
import com.whatstat.reborn.data.db.model.EmojiStat
import com.whatstat.reborn.data.db.model.HourlyCount
import com.whatstat.reborn.data.db.model.MonthOfYearCount
import com.whatstat.reborn.data.db.model.MonthlyCount
import com.whatstat.reborn.data.db.model.YearTrendCount
import com.whatstat.reborn.data.db.model.SenderStat
import com.whatstat.reborn.data.db.model.StickerStat
import com.whatstat.reborn.data.db.model.WordFrequency

/**
 * Statistiche globali aggregate su tutte le chat.
 */
@Serializable
enum class PredictionConfidence {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}

@Serializable
data class MilestonePrediction(
    val targetCount: Long,
    val estimatedDate: Long?,
    val daysRemaining: Long?,
    val progress: Float,
    val dailyRate: Float,
    val confidence: PredictionConfidence
)

@Serializable
data class MilestonePredictionState(
    val currentCount: Long = 0L,
    val dailyRate: Float = 0f,
    val confidence: PredictionConfidence = PredictionConfidence.NONE,
    val suggestedPredictions: List<MilestonePrediction> = emptyList()
)

@Serializable
data class GlobalStats(
    val totalMessages: Int,
    val totalChats: Int,
    val totalContacts: Int,
    val totalMediaFiles: Int,
    val oldestMessageDate: Long,       // Unix timestamp ms
    val mostActiveChat: String,
    val mostActiveHour: Int,           // 0–23
    val mostActiveDayOfWeek: Int,      // 0=domenica
    val averageMessagesPerDay: Float,
    val milestonePredictionState: MilestonePredictionState = MilestonePredictionState(),
    val longestStreak: Int,             // giorni consecutivi con almeno un messaggio
    val hourlyDistribution: List<HourlyCount> = emptyList(),
    val weeklyDistribution: List<DayCount> = emptyList(),
    val dayOfMonthDistribution: List<DayOfMonthCount> = emptyList(),
    val monthOfYearDistribution: List<MonthOfYearCount> = emptyList(),
    val yearTrendDistribution: List<YearTrendCount> = emptyList(),
    val topWords: List<WordFrequency> = emptyList(),
    val topEmojis: List<EmojiStat> = emptyList(),
    val topStickers: List<StickerStat> = emptyList(),
    val isLiteMode: Boolean = false
)

/**
 * Statistiche complete per una singola chat.
 */
data class ChatStats(
    val chatId: Long,
    val contactName: String,
    val jid: String,
    val isGroup: Boolean,
    val totalMessages: Int,
    val milestonePredictionState: MilestonePredictionState = MilestonePredictionState(),
    val sentByMe: Int,
    val receivedFromThem: Int,
    val sentWords: Int = 0,
    val receivedWords: Int = 0,
    val sentEmojis: Int = 0,
    val receivedEmojis: Int = 0,
    val sentRatio: Float,              // percentuale messaggi inviati (0.0–1.0)
    val firstMessageDate: Long,        // Unix timestamp ms
    val lastMessageDate: Long,
    val averageResponseTimeMs: Long,
    val iStartedConversations: Int,
    val theyStartedConversations: Int,
    val longestMessage: String,
    val averageMessageLength: Float,
    val mediaCount: Int,
    val audioCount: Int,
    val videoCount: Int,
    val hourlyDistribution: List<HourlyCount>,   // 24 elementi
    val weeklyDistribution: List<DayCount>,       // 7 elementi
    val dayOfMonthDistribution: List<DayOfMonthCount> = emptyList(),
    val monthOfYearDistribution: List<MonthOfYearCount> = emptyList(),
    val yearTrendDistribution: List<YearTrendCount> = emptyList(),
    val monthlyTrend: List<MonthlyCount>,
    val topWords: List<WordFrequency>,
    val topEmojis: List<EmojiStat> = emptyList(),
    val senderBreakdown: List<SenderStat> = emptyList(),
    val conversationStarterBreakdown: List<SenderStat> = emptyList(),
    val avatarPath: String? = null
)

/**
 * Risultati delle query lente, da mergiare con ChatStats.
 */
data class SlowChatStats(
    val topWords: List<WordFrequency>,
    val topEmojis: List<EmojiStat> = emptyList(),
    val topStickers: List<StickerStat> = emptyList(),
    val senderBreakdown: List<SenderStat>,
    val conversationStarterBreakdown: List<SenderStat>,
    val averageResponseTimeMs: Long,
    val iStartedConversations: Int,
    val theyStartedConversations: Int,
    val longestMessage: String,
    val sentEmojis: Int = 0,
    val receivedEmojis: Int = 0
)

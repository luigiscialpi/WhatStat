package com.whatstats.domain.model

import com.whatstats.data.db.model.DayCount
import com.whatstats.data.db.model.DayOfMonthCount
import com.whatstats.data.db.model.EmojiStat
import com.whatstats.data.db.model.HourlyCount
import com.whatstats.data.db.model.MonthOfYearCount
import com.whatstats.data.db.model.MonthlyCount
import com.whatstats.data.db.model.SenderStat
import com.whatstats.data.db.model.StickerStat
import com.whatstats.data.db.model.WordFrequency
import com.whatstats.data.db.model.YearTrendCount
import kotlinx.serialization.Serializable

/**
 * Statistiche globali aggregate su tutte le chat.
 */
@Serializable
enum class PredictionConfidence {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
}

@Serializable
data class MilestonePrediction(
    val targetCount: Long,
    val estimatedDate: Long?,
    val daysRemaining: Long?,
    val progress: Float,
    val dailyRate: Float,
    val confidence: PredictionConfidence,
)

@Serializable
data class MilestonePredictionState(
    val currentCount: Long = 0L,
    val dailyRate: Float = 0f,
    val confidence: PredictionConfidence = PredictionConfidence.NONE,
    val suggestedPredictions: List<MilestonePrediction> = emptyList(),
)

@Serializable
data class GlobalStats(
    val totalMessages: Int,
    val totalChats: Int,
    val totalContacts: Int,
    val totalMediaFiles: Int,
    // Unix timestamp ms
    val oldestMessageDate: Long,
    val mostActiveChat: String,
    val mostActiveChatId: Long? = null,
    // 0–23
    val mostActiveHour: Int,
    // 0=domenica
    val mostActiveDayOfWeek: Int,
    val averageMessagesPerDay: Float,
    val milestonePredictionState: MilestonePredictionState = MilestonePredictionState(),
    // giorni consecutivi con almeno un messaggio
    val longestStreak: Int,
    val hourlyDistribution: List<HourlyCount> = emptyList(),
    val weeklyDistribution: List<DayCount> = emptyList(),
    val dayOfMonthDistribution: List<DayOfMonthCount> = emptyList(),
    val monthOfYearDistribution: List<MonthOfYearCount> = emptyList(),
    val yearTrendDistribution: List<YearTrendCount> = emptyList(),
    val topWords: List<WordFrequency> = emptyList(),
    val topEmojis: List<EmojiStat> = emptyList(),
    val topStickers: List<StickerStat> = emptyList(),
    val isLiteMode: Boolean = false,
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
    // percentuale messaggi inviati (0.0–1.0)
    val sentRatio: Float,
    // Unix timestamp ms
    val firstMessageDate: Long,
    val lastMessageDate: Long,
    val averageResponseTimeMs: Long,
    val iStartedConversations: Int,
    val theyStartedConversations: Int,
    val longestMessage: String,
    val averageMessageLength: Float,
    val mediaCount: Int,
    val audioCount: Int,
    val videoCount: Int,
    // 24 elementi
    val hourlyDistribution: List<HourlyCount>,
    // 7 elementi
    val weeklyDistribution: List<DayCount>,
    val dayOfMonthDistribution: List<DayOfMonthCount> = emptyList(),
    val monthOfYearDistribution: List<MonthOfYearCount> = emptyList(),
    val yearTrendDistribution: List<YearTrendCount> = emptyList(),
    val monthlyTrend: List<MonthlyCount>,
    val topWords: List<WordFrequency>,
    val topEmojis: List<EmojiStat> = emptyList(),
    val senderBreakdown: List<SenderStat> = emptyList(),
    val conversationStarterBreakdown: List<SenderStat> = emptyList(),
    val avatarPath: String? = null,
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
    val receivedEmojis: Int = 0,
)

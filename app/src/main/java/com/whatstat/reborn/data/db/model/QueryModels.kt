package com.whatstat.reborn.data.db.model

import kotlinx.serialization.Serializable

/**
 * Modelli dati per i risultati delle query SQL.
 * Questi sono i DTO (Data Transfer Objects) usati dai DAO
 * per restituire i risultati delle query al cursor.
 */

/** Range temporale per i filtri */
enum class TimeRange(val millisBack: Long?) {
    ALL_TIME(null),
    LAST_30_DAYS(2592000000L),
    LAST_6_MONTHS(15552000000L),
    LAST_YEAR(31536000000L),
    CUSTOM(null)
}

data class DateRange(
    val startTime: Long?,
    val endTime: Long?
) {
    companion object {
        val ALL = DateRange(null, null)
        val TODAY = DateRange(System.currentTimeMillis() - 86400000L, System.currentTimeMillis())
    }
}

/** Conteggio messaggi per singola chat */
@Serializable
data class ChatMessageCount(
    val chatId: Long,
    val jid: String,
    val name: String?,
    val total: Int,
    val sent: Int,
    val received: Int,
    val sentWords: Int = 0,
    val receivedWords: Int = 0
)

@Serializable
data class HourlyCount(
    val hour: Int,
    val count: Int
)

@Serializable
data class DayCount(
    val dayOfWeek: Int,
    val count: Int
)

@Serializable
data class MonthlyCount(
    val month: String,
    val count: Int
)

@Serializable
data class DayOfMonthCount(
    val day: Int,
    val count: Int
)

@Serializable
data class MonthOfYearCount(
    val month: Int,
    val count: Int
)

@Serializable
data class YearTrendCount(
    val year: Int,
    val count: Int
)

@Serializable
data class WordFrequency(
    val word: String,
    val frequency: Int
)

/** Statistiche per tipo di media */
data class MediaTypeStat(
    val mimeType: String,
    val count: Int,
    val totalSize: Long
)

/** Chi ha iniziato le conversazioni */
data class ConversationStarterStats(
    val startedByMe: Int,
    val startedByThem: Int
)

/** Info base di una chat */
data class ChatBasicInfo(
    val chatId: Long,
    val jid: String,
    val name: String?,
    val isGroup: Boolean,
    val subject: String?
)

/** Elemento della lista chat per il ranking */
data class ChatListItem(
    val chatId: Long,
    val jid: String,
    val displayName: String,
    val isGroup: Boolean,
    val totalMessages: Int,
    val lastMessageTimestamp: Long,
    val importTimestamp: Long? = null,
    val avatarPath: String? = null
)

/** Statistiche chiamate per contatto */
data class CallStat(
    val jid: String,
    val displayName: String?,
    val totalCalls: Int,
    val videoCalls: Int,
    val missedCalls: Int,
    val totalDurationSec: Long,
    val audioDurationSec: Long = 0L,
    val videoDurationSec: Long = 0L,
    val avatarPath: String? = null
)

/** Statistiche link e media per contatto */
data class LinkStat(
    val jid: String,
    val displayName: String?,
    val totalLinks: Int,
    val mediaCount: Int,
    val avatarPath: String? = null
)

/** Risultato ricerca parole */
data class WordSearchResult(
    val jid: String,
    val displayName: String?,
    val count: Int,
    val avatarPath: String? = null
)

/** Conteggio messaggi per singolo mittente (pie chart) */
data class SenderStat(
    val jid: String,
    val displayName: String,
    val count: Int
)

/** Frequenza di un singolo URL */
@Serializable
data class UrlFrequency(
    val url: String,
    val count: Int,
    val lastTimestamp: Long? = null
)

/** Statistiche frequenza domini link */
@Serializable
data class DomainStat(
    val domain: String,
    val count: Int,
    val topUrls: List<UrlFrequency> = emptyList()
)

/** Statistiche per singolo sticker */
@Serializable
data class StickerStat(
    val filePath: String, // "Media/WhatsApp Stickers/STK-20240406-WA0029.webp"
    val fileName: String, // "STK-20240406-WA0029.webp"
    val count: Int
)

/** Media file (foto/video) per galleria */
@Serializable
data class MediaStat(
    val filePath: String,    // percorso relativo dal DB (es. "Media/WhatsApp Images/IMG-xxx.jpg")
    val mimeType: String,    // "image/jpeg", "video/mp4", ecc.
    val timestamp: Long      // timestamp del messaggio in ms
)

/** Singolo messaggio trovato dalla ricerca Radar */
data class WordMessageResult(
    val text: String,
    val timestamp: Long,
    val chatRowId: Long,
    val chatDisplayName: String
)

data class LexicalDelta(
    val words: Map<String, Int>,
    val emojis: Map<String, Int>,
    val monthlyWords: Map<String, Map<String, Int>>,
    val monthlyEmojis: Map<String, Map<String, Int>>,
    val maxTimestamp: Long
)

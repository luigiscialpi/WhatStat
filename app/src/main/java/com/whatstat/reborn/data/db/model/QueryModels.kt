package com.whatstat.reborn.data.db.model

/**
 * Modelli dati per i risultati delle query SQL.
 * Questi sono i DTO (Data Transfer Objects) usati dai DAO
 * per restituire i risultati delle query al cursor.
 */

/** Conteggio messaggi per singola chat */
data class ChatMessageCount(
    val chatId: Long,
    val jid: String,
    val name: String?,
    val total: Int,
    val sent: Int,
    val received: Int
)

/** Conteggio per ora del giorno (0–23) */
data class HourlyCount(
    val hour: Int,
    val count: Int
)

/** Conteggio per giorno della settimana (0=domenica, 6=sabato) */
data class DayCount(
    val dayOfWeek: Int,
    val count: Int
)

/** Conteggio mensile (formato "YYYY-MM") */
data class MonthlyCount(
    val month: String,
    val count: Int
)

/** Frequenza di una parola */
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
    val lastMessageTimestamp: Long
)

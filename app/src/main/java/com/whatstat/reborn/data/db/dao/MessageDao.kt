package com.whatstat.reborn.data.db.dao

import android.database.Cursor
import com.whatstat.reborn.data.db.WhatsAppDatabase
import com.whatstat.reborn.data.db.model.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DAO per le query sui messaggi.
 * Legge dalla tabella `message` del database msgstore.db.
 */
@Singleton
class MessageDao @Inject constructor(
    private val database: WhatsAppDatabase
) {

    /** Conta i messaggi totali nel database. */
    fun getTotalMessageCount(): Int {
        val cursor = database.getMsgDb().rawQuery(
            "SELECT COUNT(*) FROM message WHERE message_type = 0", null
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** Conta i messaggi per una chat specifica (totale, inviati, ricevuti). */
    fun getMessageCountForChat(chatId: Long): ChatMessageCount {
        val query = """
            SELECT
                ? AS chat_id,
                '' AS jid,
                '' AS name,
                COUNT(*) AS total,
                SUM(CASE WHEN from_me = 1 THEN 1 ELSE 0 END) AS sent,
                SUM(CASE WHEN from_me = 0 THEN 1 ELSE 0 END) AS received
            FROM message
            WHERE chat_row_id = ? AND message_type = 0
        """.trimIndent()
        val cursor = database.getMsgDb().rawQuery(
            query, arrayOf(chatId.toString(), chatId.toString())
        )
        return cursor.use {
            if (it.moveToFirst()) ChatMessageCount(
                chatId   = chatId,
                jid      = "",
                name     = null,
                total    = it.getInt(it.getColumnIndexOrThrow("total")),
                sent     = it.getInt(it.getColumnIndexOrThrow("sent")),
                received = it.getInt(it.getColumnIndexOrThrow("received"))
            ) else ChatMessageCount(chatId, "", null, 0, 0, 0)
        }
    }

    /**
     * Conta i messaggi totali per ogni chat, separando inviati e ricevuti.
     * Include i nomi dei contatti tramite JOIN con jid.
     */
    fun getMessageCountByChat(): List<ChatMessageCount> {
        val query = """
            SELECT
                c._id               AS chat_id,
                j.raw_string        AS jid,
                COALESCE(j.cached_name, c.subject) AS name,
                COUNT(*)            AS total,
                SUM(CASE WHEN m.from_me = 1 THEN 1 ELSE 0 END) AS sent,
                SUM(CASE WHEN m.from_me = 0 THEN 1 ELSE 0 END) AS received
            FROM message m
            JOIN chat c ON m.chat_row_id = c._id
            JOIN jid j  ON c.jid_row_id  = j._id
            WHERE m.message_type = 0
            GROUP BY c._id
            ORDER BY total DESC
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, null)
        return cursor.use { parseChatMessageCount(it) }
    }

    /** Distribuzione oraria dei messaggi per una chat specifica (24 valori). */
    fun getHourlyDistribution(chatId: Long): List<HourlyCount> {
        val query = """
            SELECT
                CAST(strftime('%H', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS hour,
                COUNT(*) AS count
            FROM message
            WHERE chat_row_id = ? AND message_type = 0
            GROUP BY hour
            ORDER BY hour
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        return cursor.use { parseHourlyCount(it) }
    }

    /** Distribuzione oraria globale (tutte le chat). */
    fun getGlobalHourlyDistribution(): List<HourlyCount> {
        val query = """
            SELECT
                CAST(strftime('%H', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS hour,
                COUNT(*) AS count
            FROM message
            WHERE message_type = 0
            GROUP BY hour
            ORDER BY hour
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, null)
        return cursor.use { parseHourlyCount(it) }
    }

    /** Distribuzione per giorno della settimana (0=domenica, 6=sabato). */
    fun getDayOfWeekDistribution(chatId: Long): List<DayCount> {
        val query = """
            SELECT
                CAST(strftime('%w', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS day,
                COUNT(*) AS count
            FROM message
            WHERE chat_row_id = ? AND message_type = 0
            GROUP BY day
            ORDER BY day
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        return cursor.use { parseDayCount(it) }
    }

    /** Distribuzione settimanale globale. */
    fun getGlobalWeeklyDistribution(): List<DayCount> {
        val query = """
            SELECT
                CAST(strftime('%w', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS day,
                COUNT(*) AS count
            FROM message
            WHERE message_type = 0
            GROUP BY day
            ORDER BY day
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, null)
        return cursor.use { parseDayCount(it) }
    }

    /** Evoluzione mensile del numero di messaggi. */
    fun getMonthlyTrend(chatId: Long): List<MonthlyCount> {
        val query = """
            SELECT
                strftime('%Y-%m', datetime(timestamp / 1000, 'unixepoch')) AS month,
                COUNT(*) AS count
            FROM message
            WHERE chat_row_id = ? AND message_type = 0
            GROUP BY month
            ORDER BY month
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        return cursor.use { parseMonthlyCount(it) }
    }

    /**
     * Parole più frequenti in una chat (esclude stop words italiane e inglesi).
     */
    fun getTopWords(chatId: Long, limit: Int = 50): List<WordFrequency> {
        val query = """
            SELECT text_data
            FROM message
            WHERE chat_row_id = ? AND text_data IS NOT NULL AND message_type = 0
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        val texts = cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val text = it.getString(0)
                    if (!text.isNullOrBlank()) add(text)
                }
            }
        }
        return countWords(texts, limit)
    }

    /** Statistiche sui media per tipo MIME. */
    fun getMediaStats(chatId: Long): List<MediaTypeStat> {
        val query = """
            SELECT
                COALESCE(mm.mime_type, 'unknown') AS mime_type,
                COUNT(*) AS count,
                COALESCE(SUM(mm.file_size), 0) AS total_size
            FROM message m
            JOIN message_media mm ON m._id = mm.message_row_id
            WHERE m.chat_row_id = ?
            GROUP BY mm.mime_type
            ORDER BY count DESC
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        return cursor.use { parseMediaStats(it) }
    }

    /**
     * Chi ha iniziato più conversazioni.
     * Una "nuova conversazione" = messaggio dopo almeno 3 ore di silenzio.
     */
    fun getConversationStarters(chatId: Long): ConversationStarterStats {
        val query = """
            SELECT
                from_me,
                COUNT(*) AS starter_count
            FROM (
                SELECT
                    from_me,
                    timestamp,
                    LAG(timestamp) OVER (ORDER BY timestamp) AS prev_timestamp
                FROM message
                WHERE chat_row_id = ? AND message_type = 0
            )
            WHERE prev_timestamp IS NULL
               OR (timestamp - prev_timestamp) > 10800000
            GROUP BY from_me
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        return cursor.use { parseConversationStarters(it) }
    }

    /** Conta i file media totali. */
    fun getMediaCount(): Int {
        val cursor = database.getMsgDb().rawQuery(
            "SELECT COUNT(*) FROM message_media", null
        )
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /** Timestamp del messaggio più vecchio (ms). */
    fun getOldestTimestamp(): Long {
        val cursor = database.getMsgDb().rawQuery(
            "SELECT MIN(timestamp) FROM message WHERE message_type = 0 AND timestamp > 0", null
        )
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }

    /** Timestamp del primo messaggio di una chat. */
    fun getFirstMessageTimestamp(chatId: Long): Long {
        val cursor = database.getMsgDb().rawQuery(
            "SELECT MIN(timestamp) FROM message WHERE chat_row_id = ? AND message_type = 0 AND timestamp > 0",
            arrayOf(chatId.toString())
        )
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }

    /** Timestamp dell'ultimo messaggio di una chat. */
    fun getLastMessageTimestamp(chatId: Long): Long {
        val cursor = database.getMsgDb().rawQuery(
            "SELECT MAX(timestamp) FROM message WHERE chat_row_id = ? AND message_type = 0",
            arrayOf(chatId.toString())
        )
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }

    /** Messaggio più lungo di una chat. */
    fun getLongestMessage(chatId: Long): String {
        val cursor = database.getMsgDb().rawQuery(
            """SELECT text_data FROM message 
               WHERE chat_row_id = ? AND message_type = 0 AND text_data IS NOT NULL
               ORDER BY LENGTH(text_data) DESC LIMIT 1""",
            arrayOf(chatId.toString())
        )
        return cursor.use { if (it.moveToFirst()) it.getString(0) ?: "" else "" }
    }

    /** Lunghezza media dei messaggi in una chat. */
    fun getAverageMessageLength(chatId: Long): Float {
        val cursor = database.getMsgDb().rawQuery(
            """SELECT AVG(LENGTH(text_data)) FROM message 
               WHERE chat_row_id = ? AND message_type = 0 AND text_data IS NOT NULL""",
            arrayOf(chatId.toString())
        )
        return cursor.use { if (it.moveToFirst()) it.getFloat(0) else 0f }
    }

    /**
     * Restituisce le date (come epoch day) in cui c'è stato almeno un messaggio.
     * Usato per calcolare il longest streak.
     */
    fun getActiveDates(): List<Long> {
        val query = """
            SELECT DISTINCT CAST(timestamp / 86400000 AS INTEGER) AS epoch_day
            FROM message
            WHERE message_type = 0 AND timestamp > 0
            ORDER BY epoch_day
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, null)
        return cursor.use {
            buildList {
                while (it.moveToNext()) add(it.getLong(0))
            }
        }
    }

    /**
     * Calcola il tempo medio di risposta (ms) per una chat.
     * Considera solo i cambi di mittente e ignora risposte dopo 24h.
     */
    fun getAverageResponseTime(chatId: Long): Long {
        val query = """
            SELECT AVG(response_time) FROM (
                SELECT
                    timestamp - LAG(timestamp) OVER (ORDER BY timestamp) AS response_time,
                    from_me,
                    LAG(from_me) OVER (ORDER BY timestamp) AS prev_from_me
                FROM message
                WHERE chat_row_id = ? AND message_type = 0
            )
            WHERE prev_from_me IS NOT NULL AND from_me != prev_from_me
              AND response_time > 0 AND response_time < 86400000
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
    }

    // ═══════════════════════════════════════════════════════════════
    // Parsing helpers
    // ═══════════════════════════════════════════════════════════════

    private fun parseChatMessageCount(cursor: Cursor): List<ChatMessageCount> = buildList {
        while (cursor.moveToNext()) {
            add(ChatMessageCount(
                chatId   = cursor.getLong(cursor.getColumnIndexOrThrow("chat_id")),
                jid      = cursor.getString(cursor.getColumnIndexOrThrow("jid")) ?: "",
                name     = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                total    = cursor.getInt(cursor.getColumnIndexOrThrow("total")),
                sent     = cursor.getInt(cursor.getColumnIndexOrThrow("sent")),
                received = cursor.getInt(cursor.getColumnIndexOrThrow("received"))
            ))
        }
    }

    private fun parseHourlyCount(cursor: Cursor): List<HourlyCount> = buildList {
        while (cursor.moveToNext()) {
            add(HourlyCount(hour = cursor.getInt(0), count = cursor.getInt(1)))
        }
    }

    private fun parseDayCount(cursor: Cursor): List<DayCount> = buildList {
        while (cursor.moveToNext()) {
            add(DayCount(dayOfWeek = cursor.getInt(0), count = cursor.getInt(1)))
        }
    }

    private fun parseMonthlyCount(cursor: Cursor): List<MonthlyCount> = buildList {
        while (cursor.moveToNext()) {
            add(MonthlyCount(month = cursor.getString(0) ?: "", count = cursor.getInt(1)))
        }
    }

    private fun parseMediaStats(cursor: Cursor): List<MediaTypeStat> = buildList {
        while (cursor.moveToNext()) {
            add(MediaTypeStat(
                mimeType  = cursor.getString(0) ?: "unknown",
                count     = cursor.getInt(1),
                totalSize = cursor.getLong(2)
            ))
        }
    }

    private fun parseConversationStarters(cursor: Cursor): ConversationStarterStats {
        var iStarted = 0
        var theyStarted = 0
        while (cursor.moveToNext()) {
            val fromMe = cursor.getInt(0)
            val count  = cursor.getInt(1)
            if (fromMe == 1) iStarted = count else theyStarted = count
        }
        return ConversationStarterStats(iStarted, theyStarted)
    }

    /**
     * Conta le parole escludendo stop words italiane e inglesi.
     * Filtra parole con meno di 3 caratteri.
     */
    private fun countWords(texts: List<String>, limit: Int): List<WordFrequency> {
        val stopWords = setOf(
            // Italiano
            "il", "lo", "la", "i", "gli", "le", "un", "uno", "una",
            "di", "da", "in", "con", "su", "per", "tra", "fra",
            "e", "o", "ma", "se", "che", "non", "ho", "hai", "ha",
            "a", "al", "del", "della", "dei", "degli", "delle",
            "mi", "ti", "ci", "si", "ne", "è", "sono", "sei", "siamo",
            "come", "quando", "dove", "perché", "poi", "già", "anche",
            "più", "questo", "questa", "quello", "quella", "cosa",
            "tutto", "tutti", "molto", "bene", "solo", "ancora",
            "ora", "qui", "così", "fare", "fatto", "dire", "detto",
            "stato", "stata", "essere", "avere", "dopo", "prima",
            // Inglese
            "the", "a", "is", "in", "of", "and", "to", "it", "for",
            "on", "are", "was", "with", "that", "this", "but", "not",
            "you", "all", "can", "had", "her", "one", "our", "out",
            // WhatsApp-specifiche (da escludere)
            "http", "https", "www", "com", "media", "omitted"
        )

        val freq = mutableMapOf<String, Int>()
        texts.forEach { text ->
            text.lowercase()
                .replace(Regex("[^a-zàáâãäåæçèéêëìíîïðñòóôõöøùúûüý\\s]"), "")
                .split(Regex("\\s+"))
                .filter { it.length > 2 && it !in stopWords }
                .forEach { word -> freq[word] = (freq[word] ?: 0) + 1 }
        }

        return freq.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { WordFrequency(it.key, it.value) }
    }
}

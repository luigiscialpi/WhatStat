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

    /** Conta i messaggi per una chat specifica (totale, inviati, ricevuti) con filtro temporale opzionale. */
    fun getMessageCountForChat(chatId: Long, startTime: Long? = null, endTime: Long? = null): ChatMessageCount {
        val queryBuilder = StringBuilder("""
            SELECT
                ? AS chat_id,
                '' AS jid,
                '' AS name,
                COUNT(*) AS total,
                SUM(CASE WHEN from_me = 1 THEN 1 ELSE 0 END) AS sent,
                SUM(CASE WHEN from_me = 0 THEN 1 ELSE 0 END) AS received
            FROM message
            WHERE chat_row_id = ? AND message_type = 0
        """.trimIndent())
        val args = mutableListOf(chatId.toString(), chatId.toString())
        
        if (startTime != null) {
            queryBuilder.append(" AND timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            queryBuilder.append(" AND timestamp <= ?")
            args.add(endTime.toString())
        }
        
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
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
                COALESCE(j.user, c.subject) AS name,
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

    /** Distribuzione oraria dei messaggi per una chat specifica (24 valori) con filtro temporale opzionale. */
    fun getHourlyDistribution(chatId: Long, startTime: Long? = null, endTime: Long? = null): List<HourlyCount> {
        val queryBuilder = StringBuilder("""
            SELECT
                CAST(strftime('%H', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS hour,
                COUNT(*) AS count
            FROM message
            WHERE chat_row_id = ? AND message_type = 0
        """.trimIndent())
        val args = mutableListOf(chatId.toString())
        
        if (startTime != null) {
            queryBuilder.append(" AND timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            queryBuilder.append(" AND timestamp <= ?")
            args.add(endTime.toString())
        }
        queryBuilder.append(" GROUP BY hour ORDER BY hour")
        
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
        return cursor.use { parseHourlyCount(it) }
    }

    /** Distribuzione oraria globale (tutte le chat) con filtro temporale opzionale. */
    fun getGlobalHourlyDistribution(startTime: Long? = null, endTime: Long? = null): List<HourlyCount> {
        val queryBuilder = StringBuilder("""
            SELECT
                CAST(strftime('%H', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS hour,
                COUNT(*) AS count
            FROM message
            WHERE message_type = 0
        """.trimIndent())
        val args = mutableListOf<String>()
        if (startTime != null) {
            queryBuilder.append(" AND timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            queryBuilder.append(" AND timestamp <= ?")
            args.add(endTime.toString())
        }
        queryBuilder.append(" GROUP BY hour ORDER BY hour")
        
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
        return cursor.use { parseHourlyCount(it) }
    }

    /** Distribuzione per giorno della settimana (0=domenica, 6=sabato) con filtro temporale opzionale. */
    fun getDayOfWeekDistribution(chatId: Long, startTime: Long? = null, endTime: Long? = null): List<DayCount> {
        val queryBuilder = StringBuilder("""
            SELECT
                CAST(strftime('%w', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS day,
                COUNT(*) AS count
            FROM message
            WHERE chat_row_id = ? AND message_type = 0
        """.trimIndent())
        val args = mutableListOf(chatId.toString())
        
        if (startTime != null) {
            queryBuilder.append(" AND timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            queryBuilder.append(" AND timestamp <= ?")
            args.add(endTime.toString())
        }
        queryBuilder.append(" GROUP BY day ORDER BY day")
        
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
        return cursor.use { parseDayCount(it) }
    }

    /** Distribuzione settimanale globale con filtro temporale opzionale. */
    fun getGlobalWeeklyDistribution(startTime: Long? = null, endTime: Long? = null): List<DayCount> {
        val queryBuilder = StringBuilder("""
            SELECT
                CAST(strftime('%w', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS day,
                COUNT(*) AS count
            FROM message
            WHERE message_type = 0
        """.trimIndent())
        val args = mutableListOf<String>()
        if (startTime != null) {
            queryBuilder.append(" AND timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            queryBuilder.append(" AND timestamp <= ?")
            args.add(endTime.toString())
        }
        queryBuilder.append(" GROUP BY day ORDER BY day")
        
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
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
     * Supporta filtro temporale opzionale.
     */
    fun getTopWords(chatId: Long, startTime: Long? = null, endTime: Long? = null, limit: Int = 50): List<WordFrequency> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        
        conditions.add("chat_row_id = ? AND text_data IS NOT NULL AND message_type = 0")
        args.add(chatId.toString())
        
        if (startTime != null) {
            conditions.add("timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            conditions.add("timestamp <= ?")
            args.add(endTime.toString())
        }
        
        val query = """
            SELECT text_data
            FROM message
            WHERE ${conditions.joinToString(" AND ")}
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
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
     * Supporta filtro temporale opzionale.
     */
    fun getActiveDates(startTime: Long? = null): List<Long> {
        val queryBuilder = StringBuilder("""
            SELECT DISTINCT CAST(timestamp / 86400000 AS INTEGER) AS epoch_day
            FROM message
            WHERE message_type = 0 AND timestamp > 0
        """.trimIndent())
        val args = mutableListOf<String>()
        if (startTime != null) {
            queryBuilder.append(" AND timestamp >= ?")
            args.add(startTime.toString())
        }
        queryBuilder.append(" ORDER BY epoch_day")
        
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
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

    /**
     * Conta i messaggi totali nel database con filtro temporale opzionale.
     */
    fun getTotalMessageCount(startTime: Long? = null, endTime: Long? = null): Int {
        val queryBuilder = StringBuilder("SELECT COUNT(*) FROM message WHERE message_type = 0")
        val args = mutableListOf<String>()
        if (startTime != null) {
            queryBuilder.append(" AND timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            queryBuilder.append(" AND timestamp <= ?")
            args.add(endTime.toString())
        }
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    /**
     * Ricerca una parola e restituisce il conteggio OCCORRENZE per chat.
     * Gruppa per chat per mostrare in quali conversazioni appare la parola.
     * Usa JOIN con chat e jid per ottenere nomi dei contatti.
     */
    fun searchWordFrequency(word: String, chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.WordSearchResult> {
        val searchWord = word.lowercase()
        
        val args = mutableListOf<String>()
        args.add("%$searchWord%")
        
        val conditions = mutableListOf<String>("m.text_data IS NOT NULL AND LOWER(m.text_data) LIKE ?")
        
        if (chatId != null) {
            conditions.add("m.chat_row_id = ?")
            args.add(chatId.toString())
        }
        if (startTime != null) {
            conditions.add("m.timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            conditions.add("m.timestamp <= ?")
            args.add(endTime.toString())
        }
        
        val query = """
            SELECT
                j.raw_string AS jid,
                COALESCE(j.user, j.raw_string) AS display_name,
                SUM(
                    (LENGTH(LOWER(COALESCE(m.text_data, ''))) - LENGTH(REPLACE(LOWER(COALESCE(m.text_data, '')), ?, ''))
                ) / LENGTH(?) AS count
            FROM message m
            INNER JOIN chat c ON m.chat_row_id = c._id
            INNER JOIN jid j ON c.jid_row_id = j._id
            WHERE ${conditions.joinToString(" AND ")}
            GROUP BY j.raw_string
            HAVING count > 0
            ORDER BY count DESC
            LIMIT 50
        """.trimIndent()
        
        val finalArgs = listOf(searchWord, searchWord) + args
        val cursor = database.getMsgDb().rawQuery(query, finalArgs.toTypedArray())
        return cursor.use { parseWordSearchResults(it) }
    }

    /**
     * Estrazione statistiche chiamate (audio vs video, perse, durata).
     * Usa la tabella call_log dedicata.
     * Supporta filtro per jid (chat) e temporale opzionale.
     * NOTA: call_log non ha chat_row_id, quindi filtriamo via jid_row_id.
     */
    fun getCallStats(chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.CallStat> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        
        // Try to filter by chat's jid via subquery
        if (chatId != null) {
            // Filtra le chiamate dove uno dei partecipanti è il jid della chat
            conditions.add("""
                EXISTS (
                    SELECT 1 FROM chat c 
                    JOIN jid j2 ON c.jid_row_id = j2._id 
                    WHERE c._id = ? AND (
                        cl.jid_row_id = c.jid_row_id 
                        OR cl.group_jid_row_id = c.jid_row_id
                    )
                )
            """.trimIndent())
            args.add(chatId.toString())
        }
        
        if (startTime != null) {
            conditions.add("cl.timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            conditions.add("cl.timestamp <= ?")
            args.add(endTime.toString())
        }
        
        val whereClause = if (conditions.isNotEmpty()) "WHERE ${conditions.joinToString(" AND ")}" else ""
        
        val query = """
            SELECT
                COALESCE(j.raw_string, cl.jid_row_id || '@s.whatsapp.net') AS jid,
                COUNT(*) AS total_calls,
                SUM(CASE WHEN cl.video_call = 1 THEN 1 ELSE 0 END) AS video_calls,
                SUM(CASE WHEN cl.call_result = 0 THEN 1 ELSE 0 END) AS missed_calls,
                COALESCE(SUM(cl.duration), 0) AS total_duration
            FROM call_log cl
            LEFT JOIN jid j ON cl.jid_row_id = j._id
            $whereClause
            GROUP BY cl.jid_row_id
            ORDER BY total_calls DESC
            LIMIT 50
        """.trimIndent()
        val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
        return cursor.use { parseCallStats(it) }
    }

    /**
     * Estrazione statistiche link (count per utente).
     * Usa la tabella message_link con JOIN a message.
     * Supporta filtro per chat e temporale opzionale.
     */
    /**
     * Estrazione statistiche link E media.
     * Raggruppa per chat - mostra link e media per ogni conversazione.
     */
    fun getLinkStats(chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.LinkStat> {
        val conditions = mutableListOf<String>("m.message_type = 0")
        val args = mutableListOf<String>()
        
        if (chatId != null) {
            conditions.add("m.chat_row_id = ?")
            args.add(chatId.toString())
        }
        if (startTime != null) {
            conditions.add("m.timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            conditions.add("m.timestamp <= ?")
            args.add(endTime.toString())
        }
        
        val query = """
            SELECT
                j.raw_string AS jid,
                COALESCE(j.user, j.raw_string) AS display_name,
                SUM(CASE WHEN m.text_data LIKE 'http%' OR m.text_data LIKE 'https%' THEN 1 ELSE 0 END) AS total_links,
                COUNT(DISTINCT mm.message_row_id) AS media_count
            FROM message m
            INNER JOIN chat c ON m.chat_row_id = c._id
            INNER JOIN jid j ON c.jid_row_id = j._id
            LEFT JOIN message_media mm ON m._id = mm.message_row_id
            WHERE ${conditions.joinToString(" AND ")}
            GROUP BY j.raw_string
            HAVING total_links > 0 OR media_count > 0
            ORDER BY (total_links + media_count) DESC
            LIMIT 50
        """.trimIndent()
        
        val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
        return cursor.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(LinkStat(
                        jid = cursor.getString(0) ?: "",
                        displayName = cursor.getString(1),
                        totalLinks = cursor.getInt(2),
                        uniqueDomains = cursor.getInt(3)
                    ))
                }
            }
        }
    }

    private fun parseWordSearchResults(cursor: Cursor): List<com.whatstat.reborn.data.db.model.WordSearchResult> = buildList {
        while (cursor.moveToNext()) {
            add(WordSearchResult(
                jid = cursor.getString(0) ?: "",
                displayName = cursor.getString(1),
                count = cursor.getInt(2)
            ))
        }
    }

    private fun parseCallStats(cursor: Cursor): List<com.whatstat.reborn.data.db.model.CallStat> = buildList {
        while (cursor.moveToNext()) {
            add(com.whatstat.reborn.data.db.model.CallStat(
                jid = cursor.getString(0) ?: "",
                displayName = null,
                totalCalls = cursor.getInt(1),
                videoCalls = cursor.getInt(2),
                missedCalls = cursor.getInt(3),
                totalDurationSec = cursor.getLong(4)
            ))
        }
    }

    private fun parseLinkStats(cursor: Cursor): List<com.whatstat.reborn.data.db.model.LinkStat> = buildList {
        while (cursor.moveToNext()) {
            add(com.whatstat.reborn.data.db.model.LinkStat(
                jid = cursor.getString(0) ?: "",
                displayName = null,
                totalLinks = cursor.getInt(1),
                uniqueDomains = cursor.getInt(2)
            ))
        }
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
        // Rimuovi duplicati consecutivi nelle parole
        val normalize = { word: String -> word.replace(Regex("(.)\\1+"), "$1") }
        
        texts.forEach { text ->
            text.lowercase()
                .replace(Regex("[^a-zàáâãäåæçèéêëìíîïðñòóôõöøùúûüý\\s]"), "")
                .split(Regex("\\s+"))
                .filter { it.length > 2 && it !in stopWords }
                .forEach { word -> 
                    val normalized = normalize(word)
                    freq[normalized] = (freq[normalized] ?: 0) + 1 
                }
        }

        return freq.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { WordFrequency(it.key, it.value) }
    }
}

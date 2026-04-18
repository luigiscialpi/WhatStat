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
                COALESCE(c.subject, j.raw_string) AS name,
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

    fun getGlobalTopEmojis(startTime: Long? = null, endTime: Long? = null, limit: Int = 30): List<EmojiStat> {
        val queryBuilder = StringBuilder("""
            SELECT text_data FROM message WHERE message_type = 0 AND text_data IS NOT NULL
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
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
        val texts = cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val text = it.getString(0)
                    if (!text.isNullOrBlank()) add(text)
                }
            }
        }
        return countEmojis(texts, limit)
    }

    /**
     * Estrae parole e emoji in un'unica query.
     * Risparmio: una sola SELECT invece di due.
     */
    fun getGlobalWordsAndEmojis(
        startTime: Long? = null,
        endTime: Long? = null,
        wordsLimit: Int = 30,
        emojisLimit: Int = 30
    ): Pair<List<WordFrequency>, List<EmojiStat>> {
        val queryBuilder = StringBuilder("""
            SELECT text_data FROM message WHERE message_type = 0 AND text_data IS NOT NULL
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
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
        val texts = cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val text = it.getString(0)
                    if (!text.isNullOrBlank()) add(text)
                }
            }
        }
        val words = countWords(texts, wordsLimit)
        val emojis = countEmojis(texts, emojisLimit)
        return words to emojis
    }

    /** Distribuzione per giorno del mese (1-31) globale. */
    fun getGlobalDayOfMonthDistribution(startTime: Long? = null, endTime: Long? = null): List<DayOfMonthCount> {
        val queryBuilder = StringBuilder("""
            SELECT
                CAST(strftime('%d', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS day,
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
        return cursor.use { parseDayOfMonthCount(it) }
    }

    /** Distribuzione per mese dell'anno (1-12) globale. */
    fun getGlobalMonthOfYearDistribution(startTime: Long? = null, endTime: Long? = null): List<MonthOfYearCount> {
        val queryBuilder = StringBuilder("""
            SELECT
                CAST(strftime('%m', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS month,
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
        queryBuilder.append(" GROUP BY month ORDER BY month")
        
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
        return cursor.use { parseMonthOfYearCount(it) }
    }

    /** Distribuzione per anno (Trend) globale. */
    fun getGlobalYearTrendDistribution(startTime: Long? = null, endTime: Long? = null): List<YearTrendCount> {
        val queryBuilder = StringBuilder("""
            SELECT
                CAST(strftime('%Y', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS year,
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
        queryBuilder.append(" GROUP BY year ORDER BY year")
        
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
        return cursor.use { parseYearTrendCount(it) }
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

    /** Distribuzione per giorno del mese (1-31) per chat. */
    fun getDayOfMonthDistribution(chatId: Long, startTime: Long? = null, endTime: Long? = null): List<DayOfMonthCount> {
        val queryBuilder = StringBuilder("""
            SELECT
                CAST(strftime('%d', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS day,
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
        return cursor.use { parseDayOfMonthCount(it) }
    }

    /** Distribuzione per mese dell'anno (1-12) per chat. */
    fun getMonthOfYearDistribution(chatId: Long, startTime: Long? = null, endTime: Long? = null): List<MonthOfYearCount> {
        val queryBuilder = StringBuilder("""
            SELECT
                CAST(strftime('%m', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS month,
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
        queryBuilder.append(" GROUP BY month ORDER BY month")
        
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
        return cursor.use { parseMonthOfYearCount(it) }
    }

    /** Distribuzione per anno (Trend) per chat. */
    fun getYearTrendDistribution(chatId: Long, startTime: Long? = null, endTime: Long? = null): List<YearTrendCount> {
        val queryBuilder = StringBuilder("""
            SELECT
                CAST(strftime('%Y', datetime(timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS year,
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
        queryBuilder.append(" GROUP BY year ORDER BY year")
        
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
        return cursor.use { parseYearTrendCount(it) }
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
    fun getTopWords(chatId: Long, jid: String? = null, startTime: Long? = null, endTime: Long? = null, limit: Int = 50): List<WordFrequency> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        
        conditions.add("chat_row_id = ? AND text_data IS NOT NULL AND message_type = 0")
        args.add(chatId.toString())
        
        if (jid != null) {
            if (jid == "me@s.whatsapp.net") {
                conditions.add("from_me = 1")
            } else {
                // In chat 1-a-1, sender_jid_row_id potrebbe essere 0, 
                // quindi controlliamo sia sender_jid_row_id che from_me = 0 
                // (se il JID fornito è quello del contatto della chat).
                conditions.add("""
                    (
                        (sender_jid_row_id = (SELECT _id FROM jid WHERE raw_string = ?))
                        OR 
                        (from_me = 0 AND 0 = (SELECT sender_jid_row_id FROM message m2 WHERE m2._id = m._id))
                    )
                """.trimIndent())
                args.add(jid)
            }
        }
        
        if (startTime != null) {
            conditions.add("timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            conditions.add("m.timestamp <= ?")
            args.add(endTime.toString())
        }
        
        val query = """
            SELECT text_data
            FROM message m
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

    fun getTopEmojis(chatId: Long, jid: String? = null, startTime: Long? = null, endTime: Long? = null, limit: Int = 50): List<EmojiStat> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        
        conditions.add("chat_row_id = ? AND text_data IS NOT NULL AND message_type = 0")
        args.add(chatId.toString())
        
        if (jid != null) {
            if (jid == "me@s.whatsapp.net") {
                conditions.add("from_me = 1")
            } else {
                conditions.add("""
                    (
                        (sender_jid_row_id = (SELECT _id FROM jid WHERE raw_string = ?))
                        OR 
                        (from_me = 0 AND 0 = (SELECT sender_jid_row_id FROM message m2 WHERE m2._id = m._id))
                    )
                """.trimIndent())
                args.add(jid)
            }
        }
        
        if (startTime != null) {
            conditions.add("timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            conditions.add("m.timestamp <= ?")
            args.add(endTime.toString())
        }
        
        val query = """
            SELECT text_data
            FROM message m
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
        return countEmojis(texts, limit)
    }

    /**
     * Estrae parole e emoji per una chat in un'unica query.
     */
    fun getWordsAndEmojis(
        chatId: Long,
        jid: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        wordsLimit: Int = 30,
        emojisLimit: Int = 30
    ): Pair<List<WordFrequency>, List<EmojiStat>> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        
        conditions.add("chat_row_id = ? AND text_data IS NOT NULL AND message_type = 0")
        args.add(chatId.toString())
        
        if (jid != null) {
            if (jid == "me@s.whatsapp.net") {
                conditions.add("from_me = 1")
            } else {
                conditions.add("""
                    (
                        (sender_jid_row_id = (SELECT _id FROM jid WHERE raw_string = ?))
                        OR 
                        (from_me = 0 AND 0 = (SELECT sender_jid_row_id FROM message m2 WHERE m2._id = m._id))
                    )
                """.trimIndent())
                args.add(jid)
            }
        }
        
        if (startTime != null) {
            conditions.add("timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            conditions.add("m.timestamp <= ?")
            args.add(endTime.toString())
        }
        
        val query = """
            SELECT text_data
            FROM message m
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
        val words = countWords(texts, wordsLimit)
        val emojis = countEmojis(texts, emojisLimit)
        return words to emojis
    }

    private fun countEmojis(texts: List<String>, limit: Int): List<EmojiStat> {
        val freq = mutableMapOf<String, Int>()
        try {
            texts.forEach { text ->
                extractEmojis(text).forEach { emoji ->
                    val base = stripSkinTone(emoji)
                    freq[base] = (freq[base] ?: 0) + 1
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("WhatStat", "Error counting emojis: ${e.message}")
        }
        return freq.entries.sortedByDescending { it.value }.take(limit).map { EmojiStat(it.key, it.value) }
    }

    private fun stripSkinTone(emoji: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < emoji.length) {
            val cp = emoji.codePointAt(i)
            if (!isSkinToneModifier(cp)) {
                sb.appendCodePoint(cp)
            }
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    private fun extractEmojis(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val emojis = mutableListOf<String>()
        var i = 0
        try {
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                val isKeycapBase = codePoint in 0x30..0x39 || codePoint == 0x23 || codePoint == 0x2A

                if (isEmoji(codePoint) || isKeycapBase) {
                    val emojiChar = String(Character.toChars(codePoint))
                    val nextIndex = i + Character.charCount(codePoint)

                    if (nextIndex < text.length) {
                        var j = nextIndex
                        val combined = StringBuilder(emojiChar)
                        var hasValidModifier = false

                        while (j < text.length) {
                            val nextCodePoint = text.codePointAt(j)
                            if (nextCodePoint == 0xFE0F.toInt()) {
                                combined.appendCodePoint(nextCodePoint)
                                j += Character.charCount(nextCodePoint)
                                hasValidModifier = true
                            } else if (nextCodePoint == 0x200D.toInt()) {
                                combined.appendCodePoint(nextCodePoint)
                                j += Character.charCount(nextCodePoint)
                                if (j < text.length && isEmoji(text.codePointAt(j))) {
                                    combined.appendCodePoint(text.codePointAt(j))
                                    j += Character.charCount(text.codePointAt(j))
                                    hasValidModifier = true
                                }
                            } else if (isSkinToneModifier(nextCodePoint)) {
                                combined.appendCodePoint(nextCodePoint)
                                j += Character.charCount(nextCodePoint)
                                hasValidModifier = true
                            } else if (isRegionalIndicator(nextCodePoint)) {
                                val nextPos = j + Character.charCount(nextCodePoint)
                                if (nextPos < text.length && isRegionalIndicator(text.codePointAt(nextPos))) {
                                    combined.appendCodePoint(nextCodePoint)
                                    combined.appendCodePoint(text.codePointAt(nextPos))
                                    j = nextPos + Character.charCount(text.codePointAt(nextPos))
                                    hasValidModifier = true
                                } else {
                                    break
                                }
                            } else {
                                break
                            }
                        }

                        if (isKeycapBase && !hasValidModifier) {
                            i += Character.charCount(codePoint)
                            continue
                        }
                        emojis.add(combined.toString())
                        i = j
                        continue
                    }

                    if (isKeycapBase) {
                        i += Character.charCount(codePoint)
                        continue
                    }
                    emojis.add(emojiChar)
                    i = nextIndex
                } else {
                    i += Character.charCount(codePoint)
                }
            }
            return emojis
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun isEmoji(codePoint: Int): Boolean = when (codePoint) {
        in 0x1F600..0x1F64F, in 0x1F300..0x1F5FF, in 0x1F680..0x1F6FF, in 0x1F1E0..0x1F1FF,
        in 0x2700..0x27BF, in 0x2600..0x26FF, in 0x1F0A0..0x1F0FF, in 0x1F900..0x1F9FF, in 0x1FA00..0x1FAFF -> true
        else -> false
    }

    private fun isSkinToneModifier(codePoint: Int): Boolean = codePoint in 0x1F3FB..0x1F3FF

    private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

    /** Statistiche sui media per tipo MIME. */
    fun getMediaStats(chatId: Long, startTime: Long? = null, endTime: Long? = null): List<MediaTypeStat> {
        val queryBuilder = StringBuilder("""
            SELECT
                COALESCE(mm.mime_type, 'unknown') AS mime_type,
                COUNT(*) AS count,
                COALESCE(SUM(mm.file_size), 0) AS total_size
            FROM message m
            JOIN message_media mm ON m._id = mm.message_row_id
            WHERE m.chat_row_id = ?
        """.trimIndent())
        val args = mutableListOf(chatId.toString())
        
        if (startTime != null) {
            queryBuilder.append(" AND m.timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            queryBuilder.append(" AND m.timestamp <= ?")
            args.add(endTime.toString())
        }
        
        queryBuilder.append(" GROUP BY mm.mime_type ORDER BY count DESC")

        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
        return cursor.use { parseMediaStats(it) }
    }

    /**
     * Conteggio messaggi per mittente in una chat (per pie chart).
     * Usa from_me + sender_jid per distinguere correttamente in chat 1-a-1 e gruppi.
     */
    fun getMessageCountBySender(chatId: Long, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.SenderStat> {
        val conditions = mutableListOf("m.chat_row_id = ?", "m.message_type = 0")
        val args = mutableListOf(chatId.toString())

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
                CASE 
                    WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                    WHEN sender_jid.raw_string IS NOT NULL THEN sender_jid.raw_string
                    ELSE COALESCE(chat_jid.raw_string, 'unknown')
                END AS sender_jid,
                CASE 
                    WHEN m.from_me = 1 THEN 'Tu'
                    WHEN sender_jid.raw_string IS NOT NULL THEN sender_jid.raw_string
                    ELSE COALESCE(chat_jid.raw_string, 'Sconosciuto')
                END AS sender_name,
                COUNT(*) AS msg_count
            FROM message m
            LEFT JOIN jid sender_jid ON m.sender_jid_row_id = sender_jid._id AND m.sender_jid_row_id > 0
            LEFT JOIN chat c ON m.chat_row_id = c._id
            LEFT JOIN jid chat_jid ON c.jid_row_id = chat_jid._id
            WHERE ${conditions.joinToString(" AND ")}
            GROUP BY sender_jid
            ORDER BY msg_count DESC
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
        return cursor.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(com.whatstat.reborn.data.db.model.SenderStat(
                        jid = cursor.getString(0) ?: "",
                        displayName = cursor.getString(1) ?: "Sconosciuto",
                        count = cursor.getInt(2)
                    ))
                }
            }
        }
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

    /**
     * Chi ha iniziato più conversazioni, per singolo mittente.
     * Per gruppi: ritorna il dettaglio per ogni membro.
     * Per 1-a-1: ritorna Tu vs l'altra persona (con JID per risoluzione nome).
     */
    fun getConversationStartersBySender(chatId: Long, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.SenderStat> {
        val timeConditions = mutableListOf<String>()
        val args = mutableListOf(chatId.toString())

        if (startTime != null) {
            timeConditions.add("AND timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            timeConditions.add("AND timestamp <= ?")
            args.add(endTime.toString())
        }
        val timeFilter = timeConditions.joinToString(" ")

        val query = """
            SELECT
                CASE 
                    WHEN from_me = 1 THEN 'me@s.whatsapp.net'
                    WHEN sender_jid IS NOT NULL THEN sender_jid
                    ELSE COALESCE(chat_jid, 'unknown')
                END AS starter_jid,
                COUNT(*) AS starter_count
            FROM (
                SELECT
                    m.from_me,
                    CASE WHEN m.sender_jid_row_id > 0 THEN sj.raw_string ELSE NULL END AS sender_jid,
                    cj.raw_string AS chat_jid,
                    m.timestamp,
                    LAG(m.timestamp) OVER (ORDER BY m.timestamp) AS prev_timestamp
                FROM message m
                LEFT JOIN jid sj ON m.sender_jid_row_id = sj._id AND m.sender_jid_row_id > 0
                LEFT JOIN chat c ON m.chat_row_id = c._id
                LEFT JOIN jid cj ON c.jid_row_id = cj._id
                WHERE m.chat_row_id = ? AND m.message_type = 0 $timeFilter
            )
            WHERE prev_timestamp IS NULL
               OR (timestamp - prev_timestamp) > 10800000
            GROUP BY starter_jid
            ORDER BY starter_count DESC
        """.trimIndent()

        val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
        return cursor.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(com.whatstat.reborn.data.db.model.SenderStat(
                        jid = cursor.getString(0) ?: "",
                        displayName = cursor.getString(0) ?: "Sconosciuto",
                        count = cursor.getInt(1)
                    ))
                }
            }
        }
    }

    /** Conta i file media filtrati per data. */
    fun getMediaCount(startTime: Long? = null, endTime: Long? = null): Int {
        val queryBuilder = StringBuilder("""
            SELECT COUNT(*) 
            FROM message m
            JOIN message_media mm ON m._id = mm.message_row_id
            WHERE 1=1
        """.trimIndent())
        val args = mutableListOf<String>()
        if (startTime != null) {
            queryBuilder.append(" AND m.timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            queryBuilder.append(" AND m.timestamp <= ?")
            args.add(endTime.toString())
        }
        
        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
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
     * Ricerca una parola e restituisce il conteggio OCCORRENZE ESATTE per mittente.
     * Usa from_me per distinguere i messaggi propri da quelli ricevuti,
     * poiché nelle chat 1-a-1 sender_jid_row_id è sempre 0.
     * Per i gruppi, usa sender_jid_row_id quando disponibile.
     * Conta solo occorrenze esatte della parola (non sottostringhe).
     */
    fun searchWordFrequency(word: String, chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.WordSearchResult> {
        val searchWord = word.lowercase()
        
        val conditions = mutableListOf("m.text_data IS NOT NULL", "m.message_type = 0")
        val args = mutableListOf<String>()
        
        // Pre-filtro SQL: carica solo messaggi che contengono la parola
        conditions.add("m.text_data LIKE '%' || ? || '%'")
        args.add(searchWord)
        
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
        
        // Carica solo i messaggi che contengono la parola
        val query = """
            SELECT
                m.from_me,
                CASE 
                    WHEN j.raw_string IS NOT NULL THEN j.raw_string
                    ELSE COALESCE(chat_jid.raw_string, 'unknown')
                END AS other_jid,
                CASE 
                    WHEN j.raw_string IS NOT NULL THEN j.raw_string
                    ELSE chat_jid.raw_string
                END AS other_name,
                m.text_data
            FROM message m
            LEFT JOIN jid j ON m.sender_jid_row_id = j._id AND m.sender_jid_row_id > 0
            LEFT JOIN chat c ON m.chat_row_id = c._id
            LEFT JOIN jid chat_jid ON c.jid_row_id = chat_jid._id
            WHERE ${conditions.joinToString(" AND ")}
        """.trimIndent()
        
        android.util.Log.d("WhatStat", "searchWordFrequency: chatId=$chatId, word=$searchWord")
        
        val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
        
        // Conta occorrenze esatte della parola per ogni mittente
        data class SenderInfo(val jid: String, val displayName: String?)
        val counts = mutableMapOf<SenderInfo, Int>()
        
        cursor.use { cur ->
            while (cur.moveToNext()) {
                val fromMe = cur.getInt(0)
                val otherJid = cur.getString(1) ?: "unknown"
                val otherName = cur.getString(2)
                val text = cur.getString(3) ?: continue
                
                val sender = if (fromMe == 1) {
                    SenderInfo("me@s.whatsapp.net", "Tu")
                } else {
                    SenderInfo(otherJid, otherName)
                }
                
                // Conta occorrenze esatte: split sugli spazi e match esatto
                val wordCount = text.lowercase()
                    .replace(Regex("[^a-zàáâãäåæçèéêëìíîïðñòóôõöøùúûüý\\s]"), "")
                    .split(Regex("\\s+"))
                    .count { it == searchWord }
                
                if (wordCount > 0) {
                    counts[sender] = (counts[sender] ?: 0) + wordCount
                }
            }
        }
        
        android.util.Log.d("WhatStat", "searchWordFrequency results: ${counts.size} senders")
        
        return counts.entries
            .sortedByDescending { it.value }
            .take(50)
            .map { (sender, count) ->
                com.whatstat.reborn.data.db.model.WordSearchResult(
                    jid = sender.jid,
                    displayName = sender.displayName,
                    count = count
                )
            }
    }

    /**
     * Estrazione statistiche chiamate (audio vs video, perse, durata).
     * Usa la tabella call_log dedicata.
     * Supporta filtro per jid (chat) e temporale opzionale.
     * Per una chat specifica, splitta per from_me (Tu vs Altro).
     */
    fun getCallStats(chatId: Long? = null, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.CallStat> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()
        
        if (chatId != null) {
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
        
        if (chatId != null) {
            // Per una chat specifica: splitta per from_me (Tu vs Altro)
            val query = """
                SELECT
                    CASE WHEN cl.from_me = 1 THEN 'me@s.whatsapp.net' ELSE COALESCE(j.raw_string, cl.jid_row_id || '@s.whatsapp.net') END AS jid,
                    COUNT(*) AS total_calls,
                    SUM(CASE WHEN cl.video_call = 1 THEN 1 ELSE 0 END) AS video_calls,
                    SUM(CASE WHEN cl.call_result = 0 THEN 1 ELSE 0 END) AS missed_calls,
                    COALESCE(SUM(cl.duration), 0) AS total_duration,
                    COALESCE(SUM(CASE WHEN cl.video_call = 0 THEN cl.duration ELSE 0 END), 0) AS audio_duration,
                    COALESCE(SUM(CASE WHEN cl.video_call = 1 THEN cl.duration ELSE 0 END), 0) AS video_duration
                FROM call_log cl
                LEFT JOIN jid j ON cl.jid_row_id = j._id
                $whereClause
                GROUP BY cl.from_me
                ORDER BY total_calls DESC
            """.trimIndent()
            val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
            return cursor.use { parseCallStats(it) }
        } else {
            // Vista globale: raggruppa per contatto
            val query = """
                SELECT
                    COALESCE(j.raw_string, cl.jid_row_id || '@s.whatsapp.net') AS jid,
                    COUNT(*) AS total_calls,
                    SUM(CASE WHEN cl.video_call = 1 THEN 1 ELSE 0 END) AS video_calls,
                    SUM(CASE WHEN cl.call_result = 0 THEN 1 ELSE 0 END) AS missed_calls,
                    COALESCE(SUM(cl.duration), 0) AS total_duration,
                    COALESCE(SUM(CASE WHEN cl.video_call = 0 THEN cl.duration ELSE 0 END), 0) AS audio_duration,
                    COALESCE(SUM(CASE WHEN cl.video_call = 1 THEN cl.duration ELSE 0 END), 0) AS video_duration
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
        val conditions = mutableListOf<String>("m.message_type != 7")
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
                j.raw_string AS display_name,
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
                        mediaCount = cursor.getInt(3)
                    ))
                }
            }
        }
    }

    /**
     * Estrazione link per sender in una specifica chat.
     * Per chat singole: ritorna Tu vs Altro.
     * Per gruppi: ritorna tutti i partecipanti con i rispettivi link.
     */
    fun getLinkStatsBySender(chatId: Long, startTime: Long? = null, endTime: Long? = null): List<com.whatstat.reborn.data.db.model.LinkStat> {
        val conditions = mutableListOf("m.chat_row_id = ?", "m.message_type != 7")
        val args = mutableListOf(chatId.toString())
        
        if (startTime != null) {
            conditions.add("m.timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            conditions.add("m.timestamp <= ?")
            args.add(endTime.toString())
        }
        
        // Usa from_me + chat_jid per distinguere correttamente nelle chat 1-a-1
        val query = """
            SELECT
                CASE 
                    WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                    WHEN sender_jid.raw_string IS NOT NULL THEN sender_jid.raw_string
                    ELSE COALESCE(chat_jid.raw_string, 'other@s.whatsapp.net')
                END AS sender_jid,
                CASE 
                    WHEN m.from_me = 1 THEN 'Tu'
                    WHEN sender_jid.raw_string IS NOT NULL THEN sender_jid.raw_string
                    ELSE chat_jid.raw_string
                END AS sender_name,
                SUM(CASE WHEN m.text_data LIKE 'http%' OR m.text_data LIKE 'https%' THEN 1 ELSE 0 END) AS total_links,
                COUNT(DISTINCT CASE WHEN mm.message_row_id IS NOT NULL THEN m._id ELSE NULL END) AS media_count
            FROM message m
            LEFT JOIN jid sender_jid ON m.sender_jid_row_id = sender_jid._id AND m.sender_jid_row_id > 0
            LEFT JOIN chat c ON m.chat_row_id = c._id
            LEFT JOIN jid chat_jid ON c.jid_row_id = chat_jid._id
            LEFT JOIN message_media mm ON m._id = mm.message_row_id
            WHERE ${conditions.joinToString(" AND ")}
            GROUP BY m.from_me, CASE 
                WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                WHEN sender_jid.raw_string IS NOT NULL THEN sender_jid.raw_string
                ELSE COALESCE(chat_jid.raw_string, 'other@s.whatsapp.net')
            END
            HAVING total_links > 0 OR media_count > 0
            ORDER BY (total_links + media_count) DESC
        """.trimIndent()
        
        val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
        return cursor.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(LinkStat(
                        jid = cursor.getString(0) ?: "",
                        displayName = cursor.getString(1),
                        totalLinks = cursor.getInt(2),
                        mediaCount = cursor.getInt(3)
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
                totalDurationSec = cursor.getLong(4),
                audioDurationSec = cursor.getLong(5),
                videoDurationSec = cursor.getLong(6)
            ))
        }
    }

    private fun parseLinkStats(cursor: Cursor): List<com.whatstat.reborn.data.db.model.LinkStat> = buildList {
        while (cursor.moveToNext()) {
            add(com.whatstat.reborn.data.db.model.LinkStat(
                jid = cursor.getString(0) ?: "",
                displayName = null,
                totalLinks = cursor.getInt(1),
                mediaCount = cursor.getInt(2)
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

    private fun parseDayOfMonthCount(cursor: Cursor): List<DayOfMonthCount> = buildList {
        while (cursor.moveToNext()) {
            add(DayOfMonthCount(day = cursor.getInt(0), count = cursor.getInt(1)))
        }
    }

    private fun parseMonthOfYearCount(cursor: Cursor): List<MonthOfYearCount> = buildList {
        while (cursor.moveToNext()) {
            add(MonthOfYearCount(month = cursor.getInt(0), count = cursor.getInt(1)))
        }
    }

    private fun parseYearTrendCount(cursor: Cursor): List<YearTrendCount> = buildList {
        while (cursor.moveToNext()) {
            add(YearTrendCount(year = cursor.getInt(0), count = cursor.getInt(1)))
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
        // Normalizza solo ripetizioni eccessive (3+ dello stesso carattere → 2)
        val normalize = { word: String -> word.replace(Regex("(.)\\1{2,}"), "$1$1") }
        
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

    /**
     * Trova il chat_id dato un JID della chat.
     */
    fun getChatIdByJid(jid: String): Long? {
        val cursor = database.getMsgDb().rawQuery(
            "SELECT c._id FROM chat c INNER JOIN jid j ON c.jid_row_id = j._id WHERE j.raw_string = ? LIMIT 1",
            arrayOf(jid)
        )
        return cursor.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    /**
     * Recupera i testi di tutti i messaggi che contengono potenzialmente link.
     * Serve per l'estrazione granulare dei domini in Kotlin.
     */
    fun getLinkTexts(chatId: Long? = null, jid: String? = null, startTime: Long? = null, endTime: Long? = null): List<String> {
        val queryBuilder = StringBuilder("""
            SELECT text_data 
            FROM message m
        """.trimIndent())
        
        val conditions = mutableListOf<String>("text_data IS NOT NULL", "(text_data LIKE '%http://%' OR text_data LIKE '%https://%')")
        val args = mutableListOf<String>()

        if (chatId != null && jid != null) {
            // Filtro per sender dentro una chat specifica
            queryBuilder.append(" LEFT JOIN jid sender_jid ON m.sender_jid_row_id = sender_jid._id AND m.sender_jid_row_id > 0")
            queryBuilder.append(" LEFT JOIN jid_map jm ON jm.lid_row_id = sender_jid._id")
            queryBuilder.append(" LEFT JOIN jid real_jid ON real_jid._id = jm.jid_row_id")
            queryBuilder.append(" LEFT JOIN chat c ON m.chat_row_id = c._id")
            queryBuilder.append(" LEFT JOIN jid chat_jid ON c.jid_row_id = chat_jid._id")
            conditions.add("m.chat_row_id = ?")
            args.add(chatId.toString())
            if (jid == "me@s.whatsapp.net") {
                conditions.add("m.from_me = 1")
            } else {
                conditions.add("m.from_me = 0")
                conditions.add("(sender_jid.raw_string = ? OR real_jid.raw_string = ? OR chat_jid.raw_string = ?)")
                args.add(jid)
                args.add(jid)
                args.add(jid)
            }
        } else if (chatId != null) {
            conditions.add("m.chat_row_id = ?")
            args.add(chatId.toString())
        } else if (jid != null) {
            queryBuilder.append(" INNER JOIN chat c ON m.chat_row_id = c._id")
            queryBuilder.append(" INNER JOIN jid j ON c.jid_row_id = j._id")
            conditions.add("j.raw_string = ?")
            args.add(jid)
        }

        if (startTime != null) {
            conditions.add("m.timestamp >= ?")
            args.add(startTime.toString())
        }
        if (endTime != null) {
            conditions.add("m.timestamp <= ?")
            args.add(endTime.toString())
        }

        queryBuilder.append(" WHERE ${conditions.joinToString(" AND ")}")

        val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
        return cursor.use { 
            buildList {
                while (it.moveToNext()) {
                    it.getString(0)?.let { add(it) }
                }
            }
        }
    }

    /**
     * Ritorna gli sticker più usati in una chat.
     * Filtro: message_type = 20 (sticker).
     * Supporta filtro per mittente e range temporale.
     */
    fun getTopStickers(
        chatId: Long,
        jid: String? = null,
        startTime: Long? = null,
        endTime: Long? = null,
        limit: Int = 10,
        globalQuery: Boolean = false
    ): List<StickerStat> {
        val conditions = mutableListOf("m.message_type = 20")
        val args = mutableListOf<String>()

        if (!globalQuery) {
            conditions.add(0, "m.chat_row_id = ?")
            args.add(0, chatId.toString())
        }

        if (jid != null) {
            if (jid == "me@s.whatsapp.net") {
                conditions.add("m.from_me = 1")
            } else {
                conditions.add("""
                    (
                        (m.sender_jid_row_id = (SELECT _id FROM jid WHERE raw_string = ?))
                        OR 
                        (m.from_me = 0 AND 0 = (SELECT sender_jid_row_id FROM message m2 WHERE m2._id = m._id))
                    )
                """.trimIndent())
                args.add(jid)
            }
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
                MIN(mm.file_path) AS file_path,
                COUNT(*) AS cnt
            FROM message m
            JOIN message_media mm ON m._id = mm.message_row_id
            WHERE ${conditions.joinToString(" AND ")}
              AND mm.file_hash IS NOT NULL AND mm.file_hash != ''
            GROUP BY mm.file_hash
            ORDER BY cnt DESC
            LIMIT ?
        """.trimIndent()

        args.add(limit.toString())

        val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
        return cursor.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val filePath = cursor.getString(0) ?: ""
                    val fileName = filePath.substringAfterLast("/")
                    add(StickerStat(
                        filePath = filePath,
                        fileName = fileName,
                        count = cursor.getInt(1)
                    ))
                }
            }
        }
    }
}

package com.whatstat.reborn.data.db.dao

import android.database.Cursor
import com.whatstat.reborn.data.db.DatabaseProvider
import com.whatstat.reborn.data.db.model.CallStat
import com.whatstat.reborn.data.db.model.ChatMessageCount
import com.whatstat.reborn.data.db.model.ConversationStarterStats
import com.whatstat.reborn.data.db.model.DayCount
import com.whatstat.reborn.data.db.model.DayOfMonthCount
import com.whatstat.reborn.data.db.model.EmojiStat
import com.whatstat.reborn.data.db.model.GroupNameChangeEntity
import com.whatstat.reborn.data.db.model.GroupParticipantHistoryEntity
import com.whatstat.reborn.data.db.model.HourlyCount
import com.whatstat.reborn.data.db.model.LinkStat
import com.whatstat.reborn.data.db.model.MediaStat
import com.whatstat.reborn.data.db.model.MediaTypeStat
import com.whatstat.reborn.data.db.model.MonthOfYearCount
import com.whatstat.reborn.data.db.model.MonthlyCount
import com.whatstat.reborn.data.db.model.SenderStat
import com.whatstat.reborn.data.db.model.StickerStat
import com.whatstat.reborn.data.db.model.UserEmojiStat
import com.whatstat.reborn.data.db.model.WordFrequency
import com.whatstat.reborn.data.db.model.WordSearchResult
import com.whatstat.reborn.data.db.model.YearTrendCount
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DAO per le query sui messaggi.
 * Legge dalla tabella `message` del database msgstore.db.
 */
@Singleton
class MessageDao
    @Inject
    constructor(
        private val database: DatabaseProvider,
    ) {
        companion object {
            val STOP_WORDS: Set<String> =
                setOf(
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
                    // WhatsApp-specifiche
                    "http", "https", "www", "com", "media", "omitted",
                )
            private val WORD_NORMALIZER = Regex("(.)\\1{2,}")
            private val WORD_SPLITTER = Regex("\\s+")
            private val WORD_CLEANER = Regex("[^a-zàáâãäåæçèéêëìíîïðñòóôõöøùúûüý\\s]")
        }

        /** Conta i messaggi totali nel database. */
        fun getTotalMessageCount(): Int {
            val cursor =
                database.getMsgDb().rawQuery(
                    "SELECT COUNT(*) FROM message WHERE message_type NOT IN (7, 15, 90)",
                    null,
                )
            return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }

        /** Restituisce il timestamp del primo messaggio (più antico) nel database. */
        fun getFirstMessageTimestamp(): Long? {
            val cursor =
                database.getMsgDb().rawQuery(
                    "SELECT MIN(timestamp) FROM message WHERE message_type NOT IN (7, 15, 90)",
                    null,
                )
            return cursor.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
            }
        }

        /** Conta i messaggi per una chat specifica (totale, inviati, ricevuti) con filtro temporale opzionale. */
        fun getMessageCountForChat(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): ChatMessageCount {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT
                        ? AS chat_id,
                        '' AS jid,
                        '' AS name,
                        COUNT(*) AS total,
                        SUM(CASE WHEN from_me = 1 THEN 1 ELSE 0 END) AS sent,
                        SUM(CASE WHEN from_me = 0 THEN 1 ELSE 0 END) AS received,
                        SUM(CASE WHEN from_me = 1 AND message_type = 0 AND text_data IS NOT NULL THEN LENGTH(text_data) - LENGTH(REPLACE(text_data, ' ', '')) + 1 ELSE 0 END) AS sent_words,
                        SUM(CASE WHEN from_me = 0 AND message_type = 0 AND text_data IS NOT NULL THEN LENGTH(text_data) - LENGTH(REPLACE(text_data, ' ', '')) + 1 ELSE 0 END) AS received_words
                    FROM message
                    WHERE chat_row_id = ? AND message_type NOT IN (7, 15, 90)
                    """.trimIndent(),
                )
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
                if (it.moveToFirst()) {
                    ChatMessageCount(
                        chatId = chatId,
                        jid = "",
                        name = null,
                        total = it.getInt(it.getColumnIndexOrThrow("total")),
                        sent = it.getInt(it.getColumnIndexOrThrow("sent")),
                        received = it.getInt(it.getColumnIndexOrThrow("received")),
                        sentWords = it.getInt(it.getColumnIndexOrThrow("sent_words")),
                        receivedWords = it.getInt(it.getColumnIndexOrThrow("received_words")),
                    )
                } else {
                    ChatMessageCount(chatId, "", null, 0, 0, 0, 0, 0)
                }
            }
        }

        /**
         * Conta i messaggi totali per ogni chat, separando inviati e ricevuti.
         * Include i nomi dei contatti tramite JOIN con jid.
         */
        fun getMessageCountByChat(): List<ChatMessageCount> {
            val query =
                """
                SELECT
                    c._id               AS chat_id,
                    j.raw_string        AS jid,
                    COALESCE(c.subject, j.raw_string) AS name,
                    COUNT(*)            AS total,
                    SUM(CASE WHEN m.from_me = 1 THEN 1 ELSE 0 END) AS sent,
                    SUM(CASE WHEN m.from_me = 0 THEN 1 ELSE 0 END) AS received,
                    SUM(CASE WHEN m.from_me = 1 AND m.message_type = 0 AND m.text_data IS NOT NULL THEN LENGTH(m.text_data) - LENGTH(REPLACE(m.text_data, ' ', '')) + 1 ELSE 0 END) AS sent_words,
                    SUM(CASE WHEN m.from_me = 0 AND m.message_type = 0 AND m.text_data IS NOT NULL THEN LENGTH(m.text_data) - LENGTH(REPLACE(m.text_data, ' ', '')) + 1 ELSE 0 END) AS received_words
                FROM message m
                JOIN chat c ON m.chat_row_id = c._id
                JOIN jid j  ON c.jid_row_id  = j._id
                WHERE m.message_type NOT IN (7, 15, 90)
                GROUP BY c._id
                ORDER BY total DESC
                """.trimIndent()

            val cursor = database.getMsgDb().rawQuery(query, null)
            return cursor.use { parseChatMessageCount(it) }
        }

        /** Distribuzione oraria dei messaggi per una chat specifica (24 valori) con filtro temporale opzionale. */
        fun getHourlyDistribution(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<HourlyCount> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT
                        CAST(strftime('%H', datetime(m.timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS hour,
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            WHEN m.sender_jid_row_id > 0 THEN j_sender.raw_string
                            ELSE j_chat.raw_string
                        END AS jid,
                        COUNT(*) AS count
                    FROM message m
                    LEFT JOIN jid j_sender ON m.sender_jid_row_id = j_sender._id
                    JOIN chat c ON m.chat_row_id = c._id
                    JOIN jid j_chat ON c.jid_row_id = j_chat._id
                    WHERE m.chat_row_id = ? AND m.message_type NOT IN (7, 15, 90)
                    """.trimIndent(),
                )
            val args = mutableListOf(chatId.toString())

            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }
            queryBuilder.append(" GROUP BY hour, jid ORDER BY hour")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { parseHourlyCount(it) }
        }

        /** Distribuzione oraria globale (tutte le chat) con filtro temporale opzionale. */
        fun getGlobalHourlyDistribution(
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<HourlyCount> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT
                        CAST(strftime('%H', datetime(m.timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS hour,
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            WHEN m.sender_jid_row_id > 0 THEN j_sender.raw_string
                            ELSE j_chat.raw_string
                        END AS jid,
                        COUNT(*) AS count
                    FROM message m
                    LEFT JOIN jid j_sender ON m.sender_jid_row_id = j_sender._id
                    JOIN chat c ON m.chat_row_id = c._id
                    JOIN jid j_chat ON c.jid_row_id = j_chat._id
                    WHERE m.message_type NOT IN (7, 15, 90)
                    """.trimIndent(),
                )
            val args = mutableListOf<String>()
            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }
            queryBuilder.append(" GROUP BY hour, jid ORDER BY hour")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { parseHourlyCount(it) }
        }

        fun getGlobalTopEmojis(
            startTime: Long? = null,
            endTime: Long? = null,
            limit: Int = 20,
        ): List<EmojiStat> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT text_data FROM message WHERE message_type = 0 AND text_data IS NOT NULL
                    """.trimIndent(),
                )
            val args = mutableListOf<String>()
            if (startTime != null) {
                queryBuilder.append(" AND timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND timestamp <= ?")
                args.add(endTime.toString())
            }
            val freqEmojis = mutableMapOf<String, Int>()
            database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val text = cursor.getString(0)
                    if (text.isNullOrBlank()) continue
                    extractEmojis(text).forEach { emoji ->
                        val base = stripSkinTone(emoji)
                        freqEmojis[base] = (freqEmojis[base] ?: 0) + 1
                    }
                }
            }
            return freqEmojis.entries.sortedByDescending { it.value }.take(limit).map { EmojiStat(it.key, it.value) }
        }

        /**
         * Estrae parole e emoji in un'unica query.
         * Risparmio: una sola SELECT invece di due.
         */
        fun getGlobalWordsAndEmojis(
            startTime: Long? = null,
            endTime: Long? = null,
            wordsLimit: Int = 20,
            emojisLimit: Int = 20,
        ): Pair<List<WordFrequency>, List<EmojiStat>> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT text_data FROM message WHERE message_type = 0 AND text_data IS NOT NULL
                    """.trimIndent(),
                )
            val args = mutableListOf<String>()
            if (startTime != null) {
                queryBuilder.append(" AND timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND timestamp <= ?")
                args.add(endTime.toString())
            }

            val freqWords = mutableMapOf<String, Int>()
            val freqEmojis = mutableMapOf<String, Int>()
            val urlPattern = Pattern.compile("(https?://\\S+|www\\.\\S+|\\S+\\.(com|it|org|net|me)/\\S+)", Pattern.CASE_INSENSITIVE)

            database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val text = cursor.getString(0)
                    if (text.isNullOrBlank()) continue

                    extractEmojis(text).forEach { emoji ->
                        val base = stripSkinTone(emoji)
                        freqEmojis[base] = (freqEmojis[base] ?: 0) + 1
                    }

                    val textWithoutUrls = urlPattern.matcher(text).replaceAll(" ")
                    textWithoutUrls.lowercase()
                        .replace(WORD_CLEANER, " ")
                        .split(WORD_SPLITTER)
                        .forEach { word ->
                            if (word.length > 2 && word !in STOP_WORDS) {
                                val norm = word.replace(WORD_NORMALIZER, "$1$1")
                                if (norm.isNotBlank()) freqWords[norm] = (freqWords[norm] ?: 0) + 1
                            }
                        }
                }
            }

            val topWords = freqWords.entries.sortedByDescending { it.value }.take(wordsLimit).map { WordFrequency(it.key, it.value) }
            val topEmojis = freqEmojis.entries.sortedByDescending { it.value }.take(emojisLimit).map { EmojiStat(it.key, it.value) }
            return topWords to topEmojis
        }

        /** Distribuzione per giorno del mese (1-31) globale. */
        fun getGlobalDayOfMonthDistribution(
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<DayOfMonthCount> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT
                        CAST(strftime('%d', datetime(m.timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS day,
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            WHEN m.sender_jid_row_id > 0 THEN j_sender.raw_string
                            ELSE j_chat.raw_string
                        END AS jid,
                        COUNT(*) AS count
                    FROM message m
                    LEFT JOIN jid j_sender ON m.sender_jid_row_id = j_sender._id
                    JOIN chat c ON m.chat_row_id = c._id
                    JOIN jid j_chat ON c.jid_row_id = j_chat._id
                    WHERE m.message_type NOT IN (7, 15, 90)
                    """.trimIndent(),
                )
            val args = mutableListOf<String>()
            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }
            queryBuilder.append(" GROUP BY day, jid ORDER BY day")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { parseDayOfMonthCount(it) }
        }

        /** Distribuzione per mese dell'anno (1-12) globale. */
        fun getGlobalMonthOfYearDistribution(
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<MonthOfYearCount> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT
                        CAST(strftime('%m', datetime(m.timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS month,
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            WHEN m.sender_jid_row_id > 0 THEN j_sender.raw_string
                            ELSE j_chat.raw_string
                        END AS jid,
                        COUNT(*) AS count
                    FROM message m
                    LEFT JOIN jid j_sender ON m.sender_jid_row_id = j_sender._id
                    JOIN chat c ON m.chat_row_id = c._id
                    JOIN jid j_chat ON c.jid_row_id = j_chat._id
                    WHERE m.message_type NOT IN (7, 15, 90)
                    """.trimIndent(),
                )
            val args = mutableListOf<String>()
            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }
            queryBuilder.append(" GROUP BY month, jid ORDER BY month")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { parseMonthOfYearCount(it) }
        }

        /** Distribuzione per anno (Trend) globale. */
        fun getGlobalYearTrendDistribution(
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<YearTrendCount> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT
                        CAST(strftime('%Y', datetime(m.timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS year,
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            WHEN m.sender_jid_row_id > 0 THEN j_sender.raw_string
                            ELSE j_chat.raw_string
                        END AS jid,
                        COUNT(*) AS count
                    FROM message m
                    LEFT JOIN jid j_sender ON m.sender_jid_row_id = j_sender._id
                    JOIN chat c ON m.chat_row_id = c._id
                    JOIN jid j_chat ON c.jid_row_id = j_chat._id
                    WHERE m.message_type NOT IN (7, 15, 90)
                    """.trimIndent(),
                )
            val args = mutableListOf<String>()
            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }
            queryBuilder.append(" GROUP BY year, jid ORDER BY year")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { parseYearTrendCount(it) }
        }

        /** Distribuzione per giorno della settimana (0=domenica, 6=sabato) con filtro temporale opzionale. */
        fun getDayOfWeekDistribution(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<DayCount> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT
                        CAST(strftime('%w', datetime(m.timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS day,
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            WHEN m.sender_jid_row_id > 0 THEN j_sender.raw_string
                            ELSE j_chat.raw_string
                        END AS jid,
                        COUNT(*) AS count
                    FROM message m
                    LEFT JOIN jid j_sender ON m.sender_jid_row_id = j_sender._id
                    JOIN chat c ON m.chat_row_id = c._id
                    JOIN jid j_chat ON c.jid_row_id = j_chat._id
                    WHERE m.chat_row_id = ? AND m.message_type NOT IN (7, 15, 90)
                    """.trimIndent(),
                )
            val args = mutableListOf(chatId.toString())

            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }
            queryBuilder.append(" GROUP BY day, jid ORDER BY day")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { parseDayCount(it) }
        }

        /** Distribuzione settimanale globale con filtro temporale opzionale. */
        fun getGlobalWeeklyDistribution(
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<DayCount> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT
                        CAST(strftime('%w', datetime(m.timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS day,
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            WHEN m.sender_jid_row_id > 0 THEN j_sender.raw_string
                            ELSE j_chat.raw_string
                        END AS jid,
                        COUNT(*) AS count
                    FROM message m
                    LEFT JOIN jid j_sender ON m.sender_jid_row_id = j_sender._id
                    JOIN chat c ON m.chat_row_id = c._id
                    JOIN jid j_chat ON c.jid_row_id = j_chat._id
                    WHERE m.message_type NOT IN (7, 15, 90)
                    """.trimIndent(),
                )
            val args = mutableListOf<String>()
            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }
            queryBuilder.append(" GROUP BY day, jid ORDER BY day")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { parseDayCount(it) }
        }

        /** Distribuzione per giorno del mese (1-31) per chat. */
        fun getDayOfMonthDistribution(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<DayOfMonthCount> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT
                        CAST(strftime('%d', datetime(m.timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS day,
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            WHEN m.sender_jid_row_id > 0 THEN j_sender.raw_string
                            ELSE j_chat.raw_string
                        END AS jid,
                        COUNT(*) AS count
                    FROM message m
                    LEFT JOIN jid j_sender ON m.sender_jid_row_id = j_sender._id
                    JOIN chat c ON m.chat_row_id = c._id
                    JOIN jid j_chat ON c.jid_row_id = j_chat._id
                    WHERE m.chat_row_id = ? AND m.message_type NOT IN (7, 15, 90)
                    """.trimIndent(),
                )
            val args = mutableListOf(chatId.toString())
            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }
            queryBuilder.append(" GROUP BY day, jid ORDER BY day")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { parseDayOfMonthCount(it) }
        }

        /** Distribuzione per mese dell'anno (1-12) per chat. */
        fun getMonthOfYearDistribution(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<MonthOfYearCount> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT
                        CAST(strftime('%m', datetime(m.timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS month,
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            WHEN m.sender_jid_row_id > 0 THEN j_sender.raw_string
                            ELSE j_chat.raw_string
                        END AS jid,
                        COUNT(*) AS count
                    FROM message m
                    LEFT JOIN jid j_sender ON m.sender_jid_row_id = j_sender._id
                    JOIN chat c ON m.chat_row_id = c._id
                    JOIN jid j_chat ON c.jid_row_id = j_chat._id
                    WHERE m.chat_row_id = ? AND m.message_type NOT IN (7, 15, 90)
                    """.trimIndent(),
                )
            val args = mutableListOf(chatId.toString())
            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }
            queryBuilder.append(" GROUP BY month, jid ORDER BY month")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { parseMonthOfYearCount(it) }
        }

        /** Distribuzione per anno (Trend) per chat. */
        fun getYearTrendDistribution(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<YearTrendCount> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT
                        CAST(strftime('%Y', datetime(m.timestamp / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS year,
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            WHEN m.sender_jid_row_id > 0 THEN j_sender.raw_string
                            ELSE j_chat.raw_string
                        END AS jid,
                        COUNT(*) AS count
                    FROM message m
                    LEFT JOIN jid j_sender ON m.sender_jid_row_id = j_sender._id
                    JOIN chat c ON m.chat_row_id = c._id
                    JOIN jid j_chat ON c.jid_row_id = j_chat._id
                    WHERE m.chat_row_id = ? AND m.message_type NOT IN (7, 15, 90)
                    """.trimIndent(),
                )
            val args = mutableListOf(chatId.toString())
            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }
            queryBuilder.append(" GROUP BY year, jid ORDER BY year")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { parseYearTrendCount(it) }
        }

        /** Evoluzione mensile del numero di messaggi con filtro temporale. */
        fun getMonthlyTrend(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<MonthlyCount> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT
                        strftime('%Y-%m', datetime(m.timestamp / 1000, 'unixepoch')) AS month,
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            WHEN m.sender_jid_row_id > 0 THEN j_sender.raw_string
                            ELSE j_chat.raw_string
                        END AS jid,
                        COUNT(*) AS count
                    FROM message m
                    LEFT JOIN jid j_sender ON m.sender_jid_row_id = j_sender._id
                    JOIN chat c ON m.chat_row_id = c._id
                    JOIN jid j_chat ON c.jid_row_id = j_chat._id
                    WHERE m.chat_row_id = ? AND m.message_type NOT IN (7, 15, 90)
                    """.trimIndent(),
                )
            val args = mutableListOf(chatId.toString())

            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }

            queryBuilder.append(" GROUP BY month, jid ORDER BY month")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { parseMonthlyCount(it) }
        }

        /**
         * Parole più frequenti in una chat (esclude stop words italiane e inglesi).
         * Supporta filtro temporale opzionale.
         */
        fun getTopWords(
            chatId: Long,
            jid: String? = null,
            startTime: Long? = null,
            endTime: Long? = null,
            limit: Int = 20,
        ): List<WordFrequency> {
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
                    // (se il JID fornito e' quello del contatto della chat).
                    conditions.add(
                        """
                        (
                            (sender_jid_row_id = (SELECT _id FROM jid WHERE raw_string = ?))
                            OR 
                            (from_me = 0 AND sender_jid_row_id = 0)
                        )
                        """.trimIndent(),
                    )
                    args.add(jid)
                }
            }

            if (startTime != null) {
                conditions.add("timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                conditions.add("timestamp <= ?")
                args.add(endTime.toString())
            }

            val query =
                """
                SELECT text_data
                FROM message
                WHERE ${conditions.joinToString(" AND ")}
                """.trimIndent()

            val freqWords = mutableMapOf<String, Int>()
            val urlPattern = Pattern.compile("(https?://\\S+|www\\.\\S+|\\S+\\.(com|it|org|net|me)/\\S+)", Pattern.CASE_INSENSITIVE)

            database.getMsgDb().rawQuery(query, args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val text = cursor.getString(0)
                    if (text.isNullOrBlank()) continue
                    val textWithoutUrls = urlPattern.matcher(text).replaceAll(" ")
                    textWithoutUrls.lowercase()
                        .replace(WORD_CLEANER, " ")
                        .split(WORD_SPLITTER)
                        .forEach { word ->
                            if (word.length > 2 && word !in STOP_WORDS) {
                                val norm = word.replace(WORD_NORMALIZER, "$1$1")
                                if (norm.isNotBlank()) freqWords[norm] = (freqWords[norm] ?: 0) + 1
                            }
                        }
                }
            }
            return freqWords.entries.sortedByDescending { it.value }.take(limit).map { WordFrequency(it.key, it.value) }
        }

        fun getTopEmojis(
            chatId: Long,
            jid: String? = null,
            startTime: Long? = null,
            endTime: Long? = null,
            limit: Int = 20,
        ): List<EmojiStat> {
            val conditions = mutableListOf<String>()
            val args = mutableListOf<String>()

            conditions.add("chat_row_id = ? AND text_data IS NOT NULL AND message_type = 0")
            args.add(chatId.toString())

            if (jid != null) {
                if (jid == "me@s.whatsapp.net") {
                    conditions.add("from_me = 1")
                } else {
                    conditions.add(
                        """
                        (
                            (sender_jid_row_id = (SELECT _id FROM jid WHERE raw_string = ?))
                            OR 
                            (from_me = 0 AND sender_jid_row_id = 0)
                        )
                        """.trimIndent(),
                    )
                    args.add(jid)
                }
            }

            if (startTime != null) {
                conditions.add("timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                conditions.add("timestamp <= ?")
                args.add(endTime.toString())
            }

            val query =
                """
                SELECT text_data
                FROM message
                WHERE ${conditions.joinToString(" AND ")}
                """.trimIndent()

            val freqEmojis = mutableMapOf<String, Int>()

            database.getMsgDb().rawQuery(query, args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val text = cursor.getString(0)
                    if (text.isNullOrBlank()) continue
                    extractEmojis(text).forEach { emoji ->
                        val base = stripSkinTone(emoji)
                        freqEmojis[base] = (freqEmojis[base] ?: 0) + 1
                    }
                }
            }
            return freqEmojis.entries.sortedByDescending { it.value }.take(limit).map { EmojiStat(it.key, it.value) }
        }

        /**
         * Estrae parole e emoji per una chat in un'unica query.
         */
        fun getWordsAndEmojis(
            chatId: Long,
            jid: String? = null,
            startTime: Long? = null,
            endTime: Long? = null,
            wordsLimit: Int = 20,
            emojisLimit: Int = 20,
        ): Pair<List<WordFrequency>, List<EmojiStat>> {
            val conditions = mutableListOf<String>()
            val args = mutableListOf<String>()

            conditions.add("chat_row_id = ? AND text_data IS NOT NULL AND message_type = 0")
            args.add(chatId.toString())

            if (jid != null) {
                if (jid == "me@s.whatsapp.net") {
                    conditions.add("from_me = 1")
                } else {
                    conditions.add(
                        """
                        (
                            (sender_jid_row_id = (SELECT _id FROM jid WHERE raw_string = ?))
                            OR 
                            (from_me = 0 AND sender_jid_row_id = 0)
                        )
                        """.trimIndent(),
                    )
                    args.add(jid)
                }
            }

            if (startTime != null) {
                conditions.add("timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                conditions.add("timestamp <= ?")
                args.add(endTime.toString())
            }

            val query =
                """
                SELECT text_data
                FROM message
                WHERE ${conditions.joinToString(" AND ")}
                """.trimIndent()

            val freqWords = mutableMapOf<String, Int>()
            val freqEmojis = mutableMapOf<String, Int>()
            val urlPattern = Pattern.compile("(https?://\\S+|www\\.\\S+|\\S+\\.(com|it|org|net|me)/\\S+)", Pattern.CASE_INSENSITIVE)

            database.getMsgDb().rawQuery(query, args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val text = cursor.getString(0)
                    if (text.isNullOrBlank()) continue

                    extractEmojis(text).forEach { emoji ->
                        val base = stripSkinTone(emoji)
                        freqEmojis[base] = (freqEmojis[base] ?: 0) + 1
                    }

                    val textWithoutUrls = urlPattern.matcher(text).replaceAll(" ")
                    textWithoutUrls.lowercase()
                        .replace(WORD_CLEANER, " ")
                        .split(WORD_SPLITTER)
                        .forEach { word ->
                            if (word.length > 2 && word !in STOP_WORDS) {
                                val norm = word.replace(WORD_NORMALIZER, "$1$1")
                                if (norm.isNotBlank()) freqWords[norm] = (freqWords[norm] ?: 0) + 1
                            }
                        }
                }
            }

            val topWords = freqWords.entries.sortedByDescending { it.value }.take(wordsLimit).map { WordFrequency(it.key, it.value) }
            val topEmojis = freqEmojis.entries.sortedByDescending { it.value }.take(emojisLimit).map { EmojiStat(it.key, it.value) }
            return topWords to topEmojis
        }

        fun getEmojiStatsBySender(
            chatId: Long?,
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<UserEmojiStat> {
            val queryBuilder = StringBuilder()
            val args = mutableListOf<String>()

            if (chatId != null) {
                queryBuilder.append(
                    """
                    SELECT 
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            ELSE COALESCE(j.raw_string, 'unknown')
                        END AS jid,
                        m.text_data
                    FROM message m
                    LEFT JOIN jid j ON m.sender_jid_row_id = j._id
                    WHERE m.chat_row_id = ? AND m.message_type = 0 AND m.text_data IS NOT NULL
                    """.trimIndent(),
                )
                args.add(chatId.toString())
            } else {
                queryBuilder.append(
                    """
                    SELECT 
                        CASE 
                            WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                            ELSE COALESCE(j.raw_string, 'unknown')
                        END AS jid,
                        m.text_data
                    FROM message m
                    LEFT JOIN jid j ON m.sender_jid_row_id = j._id
                    WHERE m.message_type = 0 AND m.text_data IS NOT NULL
                    """.trimIndent(),
                )
            }

            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())

            val senderTexts = mutableMapOf<String, MutableList<String>>()
            cursor.use {
                while (it.moveToNext()) {
                    val jid = it.getString(0)
                    val text = it.getString(1)
                    if (!text.isNullOrBlank()) {
                        senderTexts.getOrPut(jid) { mutableListOf() }.add(text)
                    }
                }
            }

            return senderTexts.map { (jid, texts) ->
                val emojiFreqs = buildEmojiFrequencyMap(texts)
                val totalCount = emojiFreqs.values.sum()
                val topEmoji =
                    emojiFreqs.entries
                        .sortedByDescending { it.value }
                        .take(5)
                        .map { EmojiStat(it.key, it.value) }

                UserEmojiStat(
                    jid = jid,
                    displayName = null,
                    totalEmojiCount = totalCount,
                    topEmoji = topEmoji,
                )
            }.filter { it.totalEmojiCount > 0 }
        }

        fun guessUserJid(): String? {
            val query =
                """
                SELECT j.raw_string
                FROM message m
                JOIN jid j ON m.sender_jid_row_id = j._id
                WHERE m.from_me = 1 AND j.raw_string LIKE '%@s.whatsapp.net'
                GROUP BY j.raw_string
                ORDER BY COUNT(*) DESC
                LIMIT 1
                """.trimIndent()

            val cursor = database.getMsgDb().rawQuery(query, null)
            return cursor.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }

        fun getGroupParticipantHistory(chatId: Long): List<GroupParticipantHistoryEntity> {
            val query =
                """
                SELECT 
                    m.timestamp, 
                    c.jid_row_id,
                    COALESCE(j_part.raw_string, j_sender.raw_string, 'unknown') AS user_jid, 
                    sys.action_type,
                    CASE 
                        WHEN m.sender_jid_row_id > 0 THEN j_sender.raw_string
                        WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                        ELSE 'unknown'
                    END AS admin_jid,
                    m.text_data
                FROM message m
                JOIN chat c ON m.chat_row_id = c._id
                JOIN message_system sys ON m._id = sys.message_row_id
                LEFT JOIN message_system_chat_participant mscp ON m._id = mscp.message_row_id
                LEFT JOIN jid j_part ON mscp.user_jid_row_id = j_part._id
                LEFT JOIN jid j_sender ON m.sender_jid_row_id = j_sender._id
                WHERE m.chat_row_id = ? AND sys.action_type IN (4, 5, 6, 7, 12, 13, 14, 15, 20, 27)
                ORDER BY m.timestamp DESC
                """.trimIndent()

            val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
            return cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        val rawAction = it.getInt(3)
                        val textData = it.getString(5)
                        val mappedAction =
                            when (rawAction) {
                                4, 12, 13, 20 -> 1 // Add (aggiunto il 13)
                                5, 7 -> 6 // Leave
                                14 -> 3 // Promote (spostato il 14 qui)
                                15 -> if (textData.isNullOrEmpty()) 6 else 8 // 6=Leave se vuoto, 8=Description se pieno
                                27 -> if (textData.isNullOrEmpty()) 0 else 8 // Description Change
                                else -> 0
                            }
                        if (mappedAction > 0) {
                            add(
                                GroupParticipantHistoryEntity(
                                    timestamp = it.getLong(0),
                                    gjid = "",
                                    jid = it.getString(2),
                                    action = mappedAction,
                                    textData = textData,
                                ),
                            )
                        }
                    }
                }
            }
        }

        fun getGroupNameHistory(chatId: Long): List<GroupNameChangeEntity> {
            val query =
                """
                SELECT 
                    m.timestamp, 
                    svc.old_data, 
                    CASE 
                        WHEN m.sender_jid_row_id > 0 THEN j.raw_string
                        WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                        ELSE 'unknown'
                    END AS sender_jid,
                    m.text_data
                FROM message m
                JOIN message_system sys ON m._id = sys.message_row_id
                LEFT JOIN message_system_value_change svc ON m._id = svc.message_row_id
                LEFT JOIN jid j ON m.sender_jid_row_id = j._id
                WHERE m.chat_row_id = ? AND sys.action_type = 1
                ORDER BY m.timestamp DESC
                """.trimIndent()

            val cursor = database.getMsgDb().rawQuery(query, arrayOf(chatId.toString()))
            return cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        add(
                            GroupNameChangeEntity(
                                timestamp = it.getLong(0),
                                oldData = it.getString(1) ?: "",
                                senderJid = it.getString(2) ?: "unknown",
                                newNameFallback = it.getString(3),
                            ),
                        )
                    }
                }
            }
        }

        private fun countEmojis(
            texts: List<String>,
            limit: Int,
        ): List<EmojiStat> {
            return buildEmojiFrequencyMap(texts)
                .entries
                .sortedByDescending { it.value }
                .take(limit)
                .map { EmojiStat(it.key, it.value) }
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

        private fun isEmoji(codePoint: Int): Boolean =
            when (codePoint) {
                in 0x1F600..0x1F64F, in 0x1F300..0x1F5FF, in 0x1F680..0x1F6FF, in 0x1F1E0..0x1F1FF,
                in 0x2700..0x27BF, in 0x2600..0x26FF, in 0x1F0A0..0x1F0FF, in 0x1F900..0x1F9FF, in 0x1FA00..0x1FAFF,
                -> true
                else -> false
            }

        private fun isSkinToneModifier(codePoint: Int): Boolean = codePoint in 0x1F3FB..0x1F3FF

        private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

        /** Statistiche sui media per tipo MIME. */
        fun getMediaStats(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<MediaTypeStat> {
            val queryBuilder =
                StringBuilder(
                    """
                    WITH all_media AS (
                        SELECT message_row_id, mime_type, file_size FROM message_media
                        UNION ALL
                        SELECT message_row_id, mime_type, file_size FROM addon_message_media
                    )
                    SELECT
                        CASE
                            WHEN mm.mime_type IS NOT NULL THEN mm.mime_type
                            WHEN m.message_type = 1 THEN 'image/jpeg'
                            WHEN m.message_type = 2 THEN 'audio/ogg'
                            WHEN m.message_type = 3 THEN 'video/mp4'
                            WHEN m.message_type = 13 THEN 'video/mp4'
                            ELSE 'unknown'
                        END AS mime_type,
                        COUNT(*) AS count,
                        COALESCE(SUM(mm.file_size), 0) AS total_size
                    FROM message m
                    JOIN all_media mm ON m._id = mm.message_row_id
                    WHERE m.chat_row_id = ? AND m.message_type != 20 AND m.message_type != 7
                    """.trimIndent(),
                )
            val args = mutableListOf(chatId.toString())

            if (startTime != null) {
                queryBuilder.append(" AND m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND m.timestamp <= ?")
                args.add(endTime.toString())
            }

            queryBuilder.append(" GROUP BY CASE ")
            queryBuilder.append("WHEN mm.mime_type IS NOT NULL THEN mm.mime_type ")
            queryBuilder.append("WHEN m.message_type = 1 THEN 'image/jpeg' ")
            queryBuilder.append("WHEN m.message_type = 2 THEN 'audio/ogg' ")
            queryBuilder.append("WHEN m.message_type = 3 THEN 'video/mp4' ")
            queryBuilder.append("WHEN m.message_type = 13 THEN 'video/mp4' ")
            queryBuilder.append("ELSE 'unknown' END ORDER BY count DESC")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { parseMediaStats(it) }
        }

        /**
         * Conteggio messaggi per mittente in una chat (per pie chart).
         * Usa from_me + sender_jid per distinguere correttamente in chat 1-a-1 e gruppi.
         */
        fun getMessageCountBySender(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<com.whatstat.reborn.data.db.model.SenderStat> {
            val conditions = mutableListOf("m.chat_row_id = ?", "m.message_type NOT IN (7, 15, 90)")
            val args = mutableListOf(chatId.toString())

            if (startTime != null) {
                conditions.add("m.timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                conditions.add("m.timestamp <= ?")
                args.add(endTime.toString())
            }

            val query =
                """
                SELECT
                    CASE 
                        WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                        WHEN real_jid.raw_string IS NOT NULL THEN real_jid.raw_string
                        WHEN sender_jid.raw_string IS NOT NULL THEN sender_jid.raw_string
                        ELSE COALESCE(chat_jid.raw_string, 'unknown')
                    END AS canonical_jid,
                    CASE 
                        WHEN m.from_me = 1 THEN 'Tu'
                        WHEN real_jid.raw_string IS NOT NULL THEN real_jid.raw_string
                        WHEN sender_jid.raw_string IS NOT NULL THEN sender_jid.raw_string
                        ELSE COALESCE(chat_jid.raw_string, 'Sconosciuto')
                    END AS sender_name,
                    COUNT(*) AS msg_count
                FROM message m
                LEFT JOIN jid sender_jid ON m.sender_jid_row_id = sender_jid._id AND m.sender_jid_row_id > 0
                LEFT JOIN jid_map jm ON jm.lid_row_id = sender_jid._id
                LEFT JOIN jid real_jid ON real_jid._id = jm.jid_row_id
                LEFT JOIN chat c ON m.chat_row_id = c._id
                LEFT JOIN jid chat_jid ON c.jid_row_id = chat_jid._id
                WHERE ${conditions.joinToString(" AND ")}
                GROUP BY canonical_jid
                ORDER BY msg_count DESC
                """.trimIndent()

            val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
            return cursor.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            com.whatstat.reborn.data.db.model.SenderStat(
                                jid = cursor.getString(0) ?: "",
                                displayName = cursor.getString(1) ?: "Sconosciuto",
                                count = cursor.getInt(2),
                            ),
                        )
                    }
                }
            }
        }

        /**
         * Chi ha iniziato più conversazioni.
         * Una "nuova conversazione" = messaggio dopo almeno 3 ore di silenzio.
         */
        fun getConversationStarters(chatId: Long): ConversationStarterStats {
            val query =
                """
                SELECT
                    from_me,
                    COUNT(*) AS starter_count
                FROM (
                    SELECT
                        from_me,
                        timestamp,
                        LAG(timestamp) OVER (ORDER BY timestamp) AS prev_timestamp
                    FROM message
                    WHERE chat_row_id = ? AND message_type NOT IN (7, 15, 90)
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
        fun getConversationStartersBySender(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<com.whatstat.reborn.data.db.model.SenderStat> {
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

            val query =
                """
                SELECT
                    CASE 
                        WHEN from_me = 1 THEN 'me@s.whatsapp.net'
                        WHEN real_jid IS NOT NULL THEN real_jid
                        WHEN sender_jid IS NOT NULL THEN sender_jid
                        ELSE COALESCE(chat_jid, 'unknown')
                    END AS starter_jid,
                    COUNT(*) AS starter_count
                FROM (
                    SELECT
                        m.from_me,
                        CASE WHEN m.sender_jid_row_id > 0 THEN sj.raw_string ELSE NULL END AS sender_jid,
                        CASE WHEN m.sender_jid_row_id > 0 THEN rj.raw_string ELSE NULL END AS real_jid,
                        cj.raw_string AS chat_jid,
                        m.timestamp,
                        LAG(m.timestamp) OVER (ORDER BY m.timestamp) AS prev_timestamp
                    FROM message m
                    LEFT JOIN jid sj ON m.sender_jid_row_id = sj._id AND m.sender_jid_row_id > 0
                    LEFT JOIN jid_map jm ON jm.lid_row_id = sj._id
                    LEFT JOIN jid rj ON rj._id = jm.jid_row_id
                    LEFT JOIN chat c ON m.chat_row_id = c._id
                    LEFT JOIN jid cj ON c.jid_row_id = cj._id
                    WHERE m.chat_row_id = ? AND m.message_type NOT IN (7, 15, 90) $timeFilter
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
                        add(
                            com.whatstat.reborn.data.db.model.SenderStat(
                                jid = cursor.getString(0) ?: "",
                                displayName = cursor.getString(0) ?: "Sconosciuto",
                                count = cursor.getInt(1),
                            ),
                        )
                    }
                }
            }
        }

        /** Conta i file media filtrati per data. */
        fun getMediaCount(
            startTime: Long? = null,
            endTime: Long? = null,
        ): Int {
            val queryBuilder =
                StringBuilder(
                    """
                    WITH all_media AS (
                        SELECT message_row_id FROM message_media
                        UNION ALL
                        SELECT message_row_id FROM addon_message_media
                    )
                    SELECT COUNT(*) 
                    FROM message m
                    JOIN all_media mm ON m._id = mm.message_row_id
                    WHERE m.message_type != 20 AND m.message_type != 7
                    """.trimIndent(),
                )
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
            val cursor =
                database.getMsgDb().rawQuery(
                    "SELECT MIN(timestamp) FROM message WHERE message_type NOT IN (7, 15, 90) AND timestamp > 0",
                    null,
                )
            return cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
        }

        /** Timestamp del primo messaggio di una chat con filtro temporale. */
        fun getFirstMessageTimestamp(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): Long {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT MIN(timestamp) FROM message WHERE chat_row_id = ? AND message_type NOT IN (7, 15, 90) AND timestamp > 0
                    """.trimIndent(),
                )
            val args = mutableListOf(chatId.toString())

            if (startTime != null) {
                queryBuilder.append(" AND timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND timestamp <= ?")
                args.add(endTime.toString())
            }

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
        }

        /** Timestamp dell'ultimo messaggio di una chat con filtro temporale. */
        fun getLastMessageTimestamp(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): Long {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT MAX(timestamp) FROM message WHERE chat_row_id = ? AND message_type NOT IN (7, 15, 90) AND timestamp > 0
                    """.trimIndent(),
                )
            val args = mutableListOf(chatId.toString())

            if (startTime != null) {
                queryBuilder.append(" AND timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND timestamp <= ?")
                args.add(endTime.toString())
            }

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
        }

        /** Messaggio più lungo di una chat. */
        fun getLongestMessage(chatId: Long): String {
            val cursor =
                database.getMsgDb().rawQuery(
                    """SELECT text_data FROM message 
               WHERE chat_row_id = ? AND message_type = 0 AND text_data IS NOT NULL
               ORDER BY LENGTH(text_data) DESC LIMIT 1""",
                    arrayOf(chatId.toString()),
                )
            return cursor.use { if (it.moveToFirst()) it.getString(0) ?: "" else "" }
        }

        /** Lunghezza media dei messaggi in una chat con filtro temporale. */
        fun getAverageMessageLength(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
        ): Float {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT AVG(LENGTH(text_data)) FROM message 
                    WHERE chat_row_id = ? AND message_type = 0 AND text_data IS NOT NULL
                    """.trimIndent(),
                )
            val args = mutableListOf(chatId.toString())

            if (startTime != null) {
                queryBuilder.append(" AND timestamp >= ?")
                args.add(startTime.toString())
            }
            if (endTime != null) {
                queryBuilder.append(" AND timestamp <= ?")
                args.add(endTime.toString())
            }

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { if (it.moveToFirst()) it.getFloat(0) else 0f }
        }

        /**
         * Restituisce le date (come epoch day) in cui c'è stato almeno un messaggio.
         * Usato per calcolare il longest streak.
         * Supporta filtro temporale opzionale.
         */
        fun getActiveDates(startTime: Long? = null): List<Long> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT DISTINCT CAST(timestamp / 86400000 AS INTEGER) AS epoch_day
                    FROM message
                    WHERE message_type NOT IN (7, 15, 90) AND timestamp > 0
                    """.trimIndent(),
                )
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
            val query =
                """
                SELECT AVG(response_time) FROM (
                    SELECT
                        timestamp - LAG(timestamp) OVER (ORDER BY timestamp) AS response_time,
                        from_me,
                        LAG(from_me) OVER (ORDER BY timestamp) AS prev_from_me
                    FROM message
                    WHERE chat_row_id = ? AND message_type NOT IN (7, 15, 90)
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
        fun getTotalMessageCount(
            startTime: Long? = null,
            endTime: Long? = null,
        ): Int {
            val queryBuilder = StringBuilder("SELECT COUNT(*) FROM message WHERE message_type NOT IN (7, 15, 90)")
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
         * Conta le occorrenze di [searchWord] in [text].
         * Se [isCaseSensitive] è true, confronta rispettando maiuscole/minuscole.
         * Se [isExactMatch] è true, conta solo parole intere (confini Unicode).
         */
        private fun countOccurrences(
            text: String,
            searchWord: String,
            isCaseSensitive: Boolean,
            isExactMatch: Boolean,
        ): Int {
            if (isExactMatch) {
                // Pattern con lookbehind/lookahead Unicode per word boundary
                val flags = if (isCaseSensitive) 0 else java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.UNICODE_CASE
                val pattern =
                    java.util.regex.Pattern.compile(
                        "(?<!\\p{L})" + java.util.regex.Pattern.quote(searchWord) + "(?!\\p{L})",
                        flags,
                    )
                val matcher = pattern.matcher(text)
                var count = 0
                while (matcher.find()) count++
                return count
            } else {
                val haystack = if (isCaseSensitive) text else text.lowercase()
                val needle = if (isCaseSensitive) searchWord else searchWord.lowercase()
                var count = 0
                var index = 0
                while (true) {
                    val found = haystack.indexOf(needle, index)
                    if (found == -1) break
                    count++
                    index = found + 1
                }
                return count
            }
        }

        /**
         * Verifica se [text] contiene [searchWord] rispettando le opzioni di ricerca.
         */
        private fun textContains(
            text: String,
            searchWord: String,
            isCaseSensitive: Boolean,
            isExactMatch: Boolean,
        ): Boolean {
            return countOccurrences(text, searchWord, isCaseSensitive, isExactMatch) > 0
        }

        /**
         * Ricerca una parola e restituisce il conteggio OCCORRENZE ESATTE per mittente.
         * Usa from_me per distinguere i messaggi propri da quelli ricevuti,
         * poiché nelle chat 1-a-1 sender_jid_row_id è sempre 0.
         * Per i gruppi, usa sender_jid_row_id quando disponibile.
         * Conta solo occorrenze esatte della parola (non sottostringhe).
         */
        fun searchWordFrequency(
            word: String,
            chatId: Long? = null,
            startTime: Long? = null,
            endTime: Long? = null,
            isCaseSensitive: Boolean = false,
            isExactMatch: Boolean = false,
            onlyQuestions: Boolean = false,
            multiWordMode: Int = 0,
            limit: Int = 20,
        ): List<com.whatstat.reborn.data.db.model.WordSearchResult> {
            val isMulti = multiWordMode != 0
            val terms =
                if (isMulti) {
                    word.split(" ").filter { it.isNotBlank() }
                } else {
                    listOf(word)
                }

            if (terms.isEmpty()) return emptyList()

            val conditions = mutableListOf("m.text_data IS NOT NULL", "m.message_type = 0")
            val args = mutableListOf<String>()

            // Pre-filtro SQL
            if (multiWordMode == 2) { // OR logic
                val orConditions = mutableListOf<String>()
                for (term in terms) {
                    val searchTerm = if (isCaseSensitive) term else term.lowercase()
                    orConditions.add("m.text_data LIKE '%' || ? || '%'")
                    args.add(searchTerm)
                }
                conditions.add("(" + orConditions.joinToString(" OR ") + ")")
            } else { // OFF (single term) or AND logic
                for (term in terms) {
                    val searchTerm = if (isCaseSensitive) term else term.lowercase()
                    conditions.add("m.text_data LIKE '%' || ? || '%'")
                    args.add(searchTerm)
                }
            }

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
            val query =
                """
                SELECT
                    m.from_me,
                    CASE 
                        WHEN m.from_me = 1 THEN 'me@s.whatsapp.net'
                        WHEN rj.raw_string IS NOT NULL THEN rj.raw_string
                        WHEN sj.raw_string IS NOT NULL THEN sj.raw_string
                        ELSE COALESCE(cj.raw_string, 'unknown')
                    END AS other_jid,
                    CASE 
                        WHEN m.from_me = 1 THEN 'Tu'
                        WHEN rj.raw_string IS NOT NULL THEN rj.raw_string
                        WHEN sj.raw_string IS NOT NULL THEN sj.raw_string
                        ELSE cj.raw_string
                    END AS other_name,
                    m.text_data
                FROM message m
                LEFT JOIN jid sj ON m.sender_jid_row_id = sj._id AND m.sender_jid_row_id > 0
                LEFT JOIN jid_map jm ON jm.lid_row_id = sj._id
                LEFT JOIN jid rj ON rj._id = jm.jid_row_id
                LEFT JOIN chat c ON m.chat_row_id = c._id
                LEFT JOIN jid cj ON c.jid_row_id = cj._id
                WHERE ${conditions.joinToString(" AND ")}
                """.trimIndent()

            android.util.Log.d("WhatStat", "searchWordFrequency: chatId=$chatId, word=$word")

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

                    // Filtro "Solo Domande": se attivo, salta i messaggi senza "?"
                    if (onlyQuestions && !text.contains("?")) continue

                    val sender =
                        if (fromMe == 1) {
                            SenderInfo("me@s.whatsapp.net", "Tu")
                        } else {
                            SenderInfo(otherJid, otherName)
                        }

                    var totalOccurrences = 0
                    var matchFound = false

                    if (multiWordMode == 2) { // OR logic: at least one term
                        for (term in terms) {
                            val count = countOccurrences(text, term, isCaseSensitive, isExactMatch)
                            if (count > 0) {
                                matchFound = true
                                totalOccurrences += count
                            }
                        }
                    } else { // OFF or AND logic: all terms must be present
                        var allTermsPresent = true
                        for (term in terms) {
                            val count = countOccurrences(text, term, isCaseSensitive, isExactMatch)
                            if (count == 0) {
                                allTermsPresent = false
                                break
                            }
                            totalOccurrences += count
                        }
                        matchFound = allTermsPresent
                    }

                    if (matchFound && totalOccurrences > 0) {
                        // In modalita' Multi (AND/OR), conta 1 per messaggio
                        val increment = if (isMulti) 1 else totalOccurrences
                        counts[sender] = (counts[sender] ?: 0) + increment
                    }
                }
            }

            android.util.Log.d("WhatStat", "searchWordFrequency results: ${counts.size} senders")

            return counts.entries
                .sortedByDescending { it.value }
                .take(limit)
                .map { (sender, count) ->
                    com.whatstat.reborn.data.db.model.WordSearchResult(
                        jid = sender.jid,
                        displayName = sender.displayName,
                        count = count,
                    )
                }
        }

        /**
         * Restituisce i messaggi che contengono la parola cercata,
         * filtrati per mittente (JID). Supporta filtro temporale e per chat.
         * Ordinamento: timestamp DESC (messaggi piu' recenti prima).
         */
        fun searchWordMessages(
            word: String,
            senderJid: String,
            chatId: Long? = null,
            startTime: Long? = null,
            endTime: Long? = null,
            isCaseSensitive: Boolean = false,
            isExactMatch: Boolean = false,
            onlyQuestions: Boolean = false,
            multiWordMode: Int = 0,
        ): List<com.whatstat.reborn.data.db.model.WordMessageResult> {
            val isMulti = multiWordMode != 0
            val terms =
                if (isMulti) {
                    word.split(" ").filter { it.isNotBlank() }
                } else {
                    listOf(word)
                }

            if (terms.isEmpty()) return emptyList()

            val conditions = mutableListOf("m.text_data IS NOT NULL", "m.message_type = 0")
            val args = mutableListOf<String>()

            // Pre-filtro SQL
            if (multiWordMode == 2) { // OR logic
                val orConditions = mutableListOf<String>()
                for (term in terms) {
                    val searchTerm = if (isCaseSensitive) term else term.lowercase()
                    orConditions.add("m.text_data LIKE '%' || ? || '%'")
                    args.add(searchTerm)
                }
                conditions.add("(" + orConditions.joinToString(" OR ") + ")")
            } else { // OFF or AND logic
                for (term in terms) {
                    val searchTerm = if (isCaseSensitive) term else term.lowercase()
                    conditions.add("m.text_data LIKE '%' || ? || '%'")
                    args.add(searchTerm)
                }
            }

            if (senderJid == "me@s.whatsapp.net") {
                conditions.add("m.from_me = 1")
            } else {
                conditions.add("m.from_me = 0")
                conditions.add("(rj.raw_string = ? OR sj.raw_string = ? OR cj.raw_string = ?)")
                args.add(senderJid)
                args.add(senderJid)
                args.add(senderJid)
            }

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

            val query =
                """
                SELECT 
                    m.text_data, 
                    m.timestamp, 
                    m.chat_row_id,
                    COALESCE(cj.raw_string, '') AS chat_jid,
                    c.subject AS chat_subject
                FROM message m
                LEFT JOIN jid sj ON m.sender_jid_row_id = sj._id AND m.sender_jid_row_id > 0
                LEFT JOIN jid_map jm ON jm.lid_row_id = sj._id
                LEFT JOIN jid rj ON rj._id = jm.jid_row_id
                LEFT JOIN chat c ON m.chat_row_id = c._id
                LEFT JOIN jid cj ON c.jid_row_id = cj._id
                WHERE ${conditions.joinToString(" AND ")}
                ORDER BY m.timestamp DESC
                """.trimIndent()

            val results = mutableListOf<com.whatstat.reborn.data.db.model.WordMessageResult>()
            val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())

            cursor.use { cur ->
                while (cur.moveToNext()) {
                    val text = cur.getString(0) ?: continue

                    // Filtro "Solo Domande": se attivo, salta i messaggi senza "?"
                    if (onlyQuestions && !text.contains("?")) continue

                    // Verifica presenza termini
                    var matchFound = false
                    if (multiWordMode == 2) { // OR
                        for (term in terms) {
                            if (textContains(text, term, isCaseSensitive, isExactMatch)) {
                                matchFound = true
                                break
                            }
                        }
                    } else { // OFF or AND
                        var allTermsPresent = true
                        for (term in terms) {
                            if (!textContains(text, term, isCaseSensitive, isExactMatch)) {
                                allTermsPresent = false
                                break
                            }
                        }
                        matchFound = allTermsPresent
                    }

                    if (matchFound) {
                        val timestamp = cur.getLong(1)
                        val chatRowId = cur.getLong(2)
                        val chatJid = cur.getString(3) ?: ""
                        val chatSubject = cur.getString(4)

                        val displayName = chatSubject ?: chatJid

                        results.add(
                            com.whatstat.reborn.data.db.model.WordMessageResult(
                                text = text,
                                timestamp = timestamp,
                                chatRowId = chatRowId,
                                chatDisplayName = displayName,
                            ),
                        )
                    }
                }
            }
            return results
        }

        /**
         * Estrazione statistiche chiamate (audio vs video, perse, durata).
         * Usa la tabella call_log dedicata.
         * Supporta filtro per jid (chat) e temporale opzionale.
         * Per una chat specifica, splitta per from_me (Tu vs Altro).
         */
        fun getCallStats(
            chatId: Long? = null,
            startTime: Long? = null,
            endTime: Long? = null,
            limit: Int = 20,
        ): List<com.whatstat.reborn.data.db.model.CallStat> {
            val conditions = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (chatId != null) {
                conditions.add(
                    """
                    EXISTS (
                        SELECT 1 FROM chat c 
                        JOIN jid j2 ON c.jid_row_id = j2._id 
                        WHERE c._id = ? AND (
                            cl.jid_row_id = c.jid_row_id 
                            OR cl.group_jid_row_id = c.jid_row_id
                        )
                    )
                    """.trimIndent(),
                )
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
                val query =
                    """
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
                val query =
                    """
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
                    LIMIT $limit
                    """.trimIndent()
                val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
                return cursor.use { parseCallStats(it) }
            }
        }

        /**
         * Estrazione statistiche link E media.
         * Raggruppa per chat - mostra link e media per ogni conversazione.
         * Usa la tabella message_link con JOIN a message.
         * Supporta filtro per chat e temporale opzionale.
         */
        fun getLinkStats(
            chatId: Long? = null,
            startTime: Long? = null,
            endTime: Long? = null,
            limit: Int = 20,
            includeStickers: Boolean = false,
        ): List<com.whatstat.reborn.data.db.model.LinkStat> {
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

            // Condizione per includere sticker nel conteggio media se richiesto
            val stickerJoin = if (includeStickers) "" else "AND m.message_type != 20"
            val stickerCase = if (includeStickers) "OR m.message_type = 20" else ""

            val query =
                """
                             WITH all_media AS (
                                 SELECT message_row_id, mime_type FROM message_media
                                 UNION ALL
                                 SELECT message_row_id, mime_type FROM addon_message_media
                             )
                             SELECT
                                 j.raw_string AS jid,
                                 j.raw_string AS display_name,
                COUNT(DISTINCT CASE WHEN m.text_data LIKE 'http%' OR m.text_data LIKE 'https%' THEN m._id ELSE NULL END) AS total_links,
                                  COUNT(DISTINCT CASE WHEN mm.mime_type LIKE 'image/%' OR mm.mime_type LIKE 'video/%' OR mm.mime_type LIKE 'audio/%' OR (mm.mime_type IS NULL AND m.message_type IN (1, 2, 3, 13)) $stickerCase THEN m._id ELSE NULL END) AS media_count
                              FROM message m
                              INNER JOIN chat c ON m.chat_row_id = c._id
                              INNER JOIN jid j ON c.jid_row_id = j._id
                              LEFT JOIN all_media mm ON m._id = mm.message_row_id $stickerJoin
                              WHERE ${conditions.joinToString(" AND ")}
                              GROUP BY j.raw_string
                              HAVING total_links > 0 OR media_count > 0
                              ORDER BY (total_links + media_count) DESC
                              LIMIT $limit
                """.trimIndent()

            val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
            return cursor.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            LinkStat(
                                jid = cursor.getString(0) ?: "",
                                displayName = cursor.getString(1),
                                totalLinks = cursor.getInt(2),
                                mediaCount = cursor.getInt(3),
                            ),
                        )
                    }
                }
            }
        }

        /**
         * Estrazione link per sender in una specifica chat.
         * Per chat singole: ritorna Tu vs Altro.
         * Per gruppi: ritorna tutti i partecipanti con i rispettivi link.
         */
        fun getLinkStatsBySender(
            chatId: Long,
            startTime: Long? = null,
            endTime: Long? = null,
            includeStickers: Boolean = false,
        ): List<com.whatstat.reborn.data.db.model.LinkStat> {
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

            val stickerJoin = if (includeStickers) "" else "AND m.message_type != 20"
            val stickerCase = if (includeStickers) "OR m.message_type = 20" else ""

            // Usa from_me + chat_jid per distinguere correttamente nelle chat 1-a-1
            val query =
                """
                             WITH all_media AS (
                                 SELECT message_row_id, mime_type FROM message_media
                                 UNION ALL
                                 SELECT message_row_id, mime_type FROM addon_message_media
                             )
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
                COUNT(DISTINCT CASE WHEN m.text_data LIKE 'http%' OR m.text_data LIKE 'https%' THEN m._id ELSE NULL END) AS total_links,
                                  COUNT(DISTINCT CASE WHEN mm.mime_type LIKE 'image/%' OR mm.mime_type LIKE 'video/%' OR mm.mime_type LIKE 'audio/%' OR (mm.mime_type IS NULL AND m.message_type IN (1, 2, 3, 13)) $stickerCase THEN m._id ELSE NULL END) AS media_count
                              FROM message m
                              LEFT JOIN jid sender_jid ON m.sender_jid_row_id = sender_jid._id AND m.sender_jid_row_id > 0
                              LEFT JOIN chat c ON m.chat_row_id = c._id
                              LEFT JOIN jid chat_jid ON c.jid_row_id = chat_jid._id
                              LEFT JOIN all_media mm ON m._id = mm.message_row_id $stickerJoin
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
                        add(
                            LinkStat(
                                jid = cursor.getString(0) ?: "",
                                displayName = cursor.getString(1),
                                totalLinks = cursor.getInt(2),
                                mediaCount = cursor.getInt(3),
                            ),
                        )
                    }
                }
            }
        }

        private fun parseWordSearchResults(cursor: Cursor): List<com.whatstat.reborn.data.db.model.WordSearchResult> =
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        WordSearchResult(
                            jid = cursor.getString(0) ?: "",
                            displayName = cursor.getString(1),
                            count = cursor.getInt(2),
                        ),
                    )
                }
            }

        private fun parseCallStats(cursor: Cursor): List<com.whatstat.reborn.data.db.model.CallStat> =
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        com.whatstat.reborn.data.db.model.CallStat(
                            jid = cursor.getString(0) ?: "",
                            displayName = null,
                            totalCalls = cursor.getInt(1),
                            videoCalls = cursor.getInt(2),
                            missedCalls = cursor.getInt(3),
                            totalDurationSec = cursor.getLong(4),
                            audioDurationSec = cursor.getLong(5),
                            videoDurationSec = cursor.getLong(6),
                        ),
                    )
                }
            }

        private fun parseLinkStats(cursor: Cursor): List<com.whatstat.reborn.data.db.model.LinkStat> =
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        com.whatstat.reborn.data.db.model.LinkStat(
                            jid = cursor.getString(0) ?: "",
                            displayName = null,
                            totalLinks = cursor.getInt(1),
                            mediaCount = cursor.getInt(2),
                        ),
                    )
                }
            }

        // ═══════════════════════════════════════════════════════════════
        // Parsing helpers
        // ═══════════════════════════════════════════════════════════════

        private fun parseChatMessageCount(cursor: Cursor): List<ChatMessageCount> =
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ChatMessageCount(
                            chatId = cursor.getLong(cursor.getColumnIndexOrThrow("chat_id")),
                            jid = cursor.getString(cursor.getColumnIndexOrThrow("jid")) ?: "",
                            name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                            total = cursor.getInt(cursor.getColumnIndexOrThrow("total")),
                            sent = cursor.getInt(cursor.getColumnIndexOrThrow("sent")),
                            received = cursor.getInt(cursor.getColumnIndexOrThrow("received")),
                            sentWords = cursor.getInt(cursor.getColumnIndexOrThrow("sent_words")),
                            receivedWords = cursor.getInt(cursor.getColumnIndexOrThrow("received_words")),
                        ),
                    )
                }
            }

        private fun parseHourlyCount(cursor: Cursor): List<HourlyCount> {
            val hourlyMap = mutableMapOf<Int, MutableList<SenderStat>>()
            while (cursor.moveToNext()) {
                val hour = cursor.getInt(0)
                val jid = cursor.getString(1) ?: "unknown"
                val count = cursor.getInt(2)
                val stats = hourlyMap.getOrPut(hour) { mutableListOf() }
                stats.add(SenderStat(jid = jid, displayName = "", count = count))
            }
            return hourlyMap.map { (hour, breakdown) ->
                HourlyCount(hour = hour, count = breakdown.sumOf { it.count }, senderBreakdown = breakdown.sortedByDescending { it.count })
            }.sortedBy { it.hour }
        }

        private fun parseDayCount(cursor: Cursor): List<DayCount> {
            val dailyMap = mutableMapOf<Int, MutableList<SenderStat>>()
            while (cursor.moveToNext()) {
                val day = cursor.getInt(0)
                val jid = cursor.getString(1) ?: "unknown"
                val count = cursor.getInt(2)
                val stats = dailyMap.getOrPut(day) { mutableListOf() }
                stats.add(SenderStat(jid = jid, displayName = "", count = count))
            }
            return dailyMap.map { (day, breakdown) ->
                DayCount(dayOfWeek = day, count = breakdown.sumOf { it.count }, senderBreakdown = breakdown.sortedByDescending { it.count })
            }.sortedBy { it.dayOfWeek }
        }

        private fun parseMonthlyCount(cursor: Cursor): List<MonthlyCount> {
            val monthlyMap = mutableMapOf<String, MutableList<SenderStat>>()
            while (cursor.moveToNext()) {
                val month = cursor.getString(0) ?: ""
                val jid = cursor.getString(1) ?: "unknown"
                val count = cursor.getInt(2)
                val stats = monthlyMap.getOrPut(month) { mutableListOf() }
                stats.add(SenderStat(jid = jid, displayName = "", count = count))
            }
            return monthlyMap.map { (month, breakdown) ->
                MonthlyCount(
                    month = month,
                    count = breakdown.sumOf { it.count },
                    senderBreakdown = breakdown.sortedByDescending { it.count },
                )
            }.sortedBy { it.month }
        }

        private fun parseDayOfMonthCount(cursor: Cursor): List<DayOfMonthCount> {
            val dailyMap = mutableMapOf<Int, MutableList<SenderStat>>()
            while (cursor.moveToNext()) {
                val day = cursor.getInt(0)
                val jid = cursor.getString(1) ?: "unknown"
                val count = cursor.getInt(2)
                val stats = dailyMap.getOrPut(day) { mutableListOf() }
                stats.add(SenderStat(jid = jid, displayName = "", count = count))
            }
            return dailyMap.map { (day, breakdown) ->
                DayOfMonthCount(
                    day = day,
                    count = breakdown.sumOf { it.count },
                    senderBreakdown = breakdown.sortedByDescending { it.count },
                )
            }.sortedBy { it.day }
        }

        private fun parseMonthOfYearCount(cursor: Cursor): List<MonthOfYearCount> {
            val monthlyMap = mutableMapOf<Int, MutableList<SenderStat>>()
            while (cursor.moveToNext()) {
                val month = cursor.getInt(0)
                val jid = cursor.getString(1) ?: "unknown"
                val count = cursor.getInt(2)
                val stats = monthlyMap.getOrPut(month) { mutableListOf() }
                stats.add(SenderStat(jid = jid, displayName = "", count = count))
            }
            return monthlyMap.map { (month, breakdown) ->
                MonthOfYearCount(
                    month = month,
                    count = breakdown.sumOf { it.count },
                    senderBreakdown = breakdown.sortedByDescending { it.count },
                )
            }.sortedBy { it.month }
        }

        private fun parseYearTrendCount(cursor: Cursor): List<YearTrendCount> {
            val yearlyMap = mutableMapOf<Int, MutableList<SenderStat>>()
            while (cursor.moveToNext()) {
                val year = cursor.getInt(0)
                val jid = cursor.getString(1) ?: "unknown"
                val count = cursor.getInt(2)
                val stats = yearlyMap.getOrPut(year) { mutableListOf() }
                stats.add(SenderStat(jid = jid, displayName = "", count = count))
            }
            return yearlyMap.map { (year, breakdown) ->
                YearTrendCount(
                    year = year,
                    count = breakdown.sumOf { it.count },
                    senderBreakdown = breakdown.sortedByDescending { it.count },
                )
            }.sortedBy { it.year }
        }

        private fun parseMediaStats(cursor: Cursor): List<MediaTypeStat> =
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MediaTypeStat(
                            mimeType = cursor.getString(0) ?: "unknown",
                            count = cursor.getInt(1),
                            totalSize = cursor.getLong(2),
                        ),
                    )
                }
            }

        private fun parseConversationStarters(cursor: Cursor): ConversationStarterStats {
            var iStarted = 0
            var theyStarted = 0
            while (cursor.moveToNext()) {
                val fromMe = cursor.getInt(0)
                val count = cursor.getInt(1)
                if (fromMe == 1) iStarted = count else theyStarted = count
            }
            return ConversationStarterStats(iStarted, theyStarted)
        }

        // =====================================================================
        // === Word/Emoji Index Building (metodi pubblici per delta increment) ===
        // =====================================================================

        /**
         * Ritorna coppie (text, timestamp) per costruire o aggiornare l'indice lessicale.
         * - [chatId] null = tutte le chat (globale)
         * - [sinceTimestamp] null = tutti i messaggi; altrimenti solo quelli più recenti
         */
        fun getTextsWithTimestamps(
            chatId: Long? = null,
            sinceTimestamp: Long? = null,
        ): List<Pair<String, Long>> {
            val conditions = mutableListOf("text_data IS NOT NULL", "message_type = 0")
            val args = mutableListOf<String>()
            if (chatId != null) {
                conditions.add("chat_row_id = ?")
                args.add(chatId.toString())
            }
            if (sinceTimestamp != null && sinceTimestamp > 0L) {
                conditions.add("timestamp > ?")
                args.add(sinceTimestamp.toString())
            }
            val query = "SELECT text_data, timestamp FROM message WHERE ${conditions.joinToString(" AND ")}"
            val cursor = database.getMsgDb().rawQuery(query, args.toTypedArray())
            return cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        val text = it.getString(0)
                        val ts = it.getLong(1)
                        if (!text.isNullOrBlank()) add(text to ts)
                    }
                }
            }
        }

        /**
         * Calcola in modo efficiente l'indice lessicale scorrendo il cursore
         * senza allocare l'intera lista di testi in memoria.
         */
        fun buildLexicalDeltaStream(
            chatId: Long? = null,
            sinceTimestamp: Long? = null,
        ): com.whatstat.reborn.data.db.model.LexicalDelta {
            val conditions = mutableListOf("text_data IS NOT NULL", "message_type = 0")
            val args = mutableListOf<String>()
            if (chatId != null) {
                conditions.add("chat_row_id = ?")
                args.add(chatId.toString())
            }
            if (sinceTimestamp != null && sinceTimestamp > 0L) {
                conditions.add("timestamp > ?")
                args.add(sinceTimestamp.toString())
            }
            val query = "SELECT text_data, timestamp FROM message WHERE ${conditions.joinToString(" AND ")}"

            val urlPattern = Pattern.compile("(https?://\\S+|www\\.\\S+|\\S+\\.(com|it|org|net|me)/\\S+)", Pattern.CASE_INSENSITIVE)

            val globalWords = mutableMapOf<String, Int>()
            val globalEmojis = mutableMapOf<String, Int>()
            val monthlyWords = mutableMapOf<String, MutableMap<String, Int>>()
            val monthlyEmojis = mutableMapOf<String, MutableMap<String, Int>>()
            var maxTs = 0L

            val cal = java.util.Calendar.getInstance()

            database.getMsgDb().rawQuery(query, args.toTypedArray()).use { cursor ->
                while (cursor.moveToNext()) {
                    val text = cursor.getString(0)
                    if (text.isNullOrBlank()) continue
                    val ts = cursor.getLong(1)
                    if (ts > maxTs) maxTs = ts

                    cal.timeInMillis = ts
                    val month = "${cal.get(java.util.Calendar.YEAR)}-${String.format("%02d", cal.get(java.util.Calendar.MONTH) + 1)}"

                    // EMOJI
                    extractEmojis(text).forEach { emoji ->
                        val base = stripSkinTone(emoji)
                        globalEmojis[base] = (globalEmojis[base] ?: 0) + 1
                        val me = monthlyEmojis.getOrPut(month) { mutableMapOf() }
                        me[base] = (me[base] ?: 0) + 1
                    }

                    // PAROLE
                    val textWithoutUrls = urlPattern.matcher(text).replaceAll(" ")
                    textWithoutUrls.lowercase()
                        .replace(WORD_CLEANER, " ")
                        .split(WORD_SPLITTER)
                        .forEach { word ->
                            if (word.length > 2 && word !in STOP_WORDS) {
                                val norm = word.replace(WORD_NORMALIZER, "$1$1")
                                if (norm.isNotBlank()) {
                                    globalWords[norm] = (globalWords[norm] ?: 0) + 1
                                    val mw = monthlyWords.getOrPut(month) { mutableMapOf() }
                                    mw[norm] = (mw[norm] ?: 0) + 1
                                }
                            }
                        }
                }
            }
            return com.whatstat.reborn.data.db.model.LexicalDelta(
                globalWords,
                globalEmojis,
                monthlyWords,
                monthlyEmojis,
                maxTs,
            )
        }

        /** Numero di messaggi testuali globali. Usato per sanity check dell'indice. */
        fun getGlobalTextMessageCount(): Int {
            val cursor =
                database.getMsgDb().rawQuery(
                    "SELECT COUNT(*) FROM message WHERE message_type = 0 AND text_data IS NOT NULL",
                    null,
                )
            return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }

        /** Numero di messaggi testuali per chat. Usato per sanity check dell'indice per-chat. */
        fun getChatTextMessageCount(chatId: Long): Int {
            val cursor =
                database.getMsgDb().rawQuery(
                    "SELECT COUNT(*) FROM message WHERE chat_row_id = ? AND message_type = 0 AND text_data IS NOT NULL",
                    arrayOf(chatId.toString()),
                )
            return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
        }

        /**
         * Costruisce una mappa word→frequenza (nessun limite) da una lista di testi.
         * Usa la stessa logica di normalizzazione e filtro stop-words di [countWords].
         * Esposto pubblicamente per la costruzione/aggiornamento dell'indice lessicale.
         */
        fun buildWordFrequencyMap(texts: List<String>): Map<String, Int> {
            val freq = mutableMapOf<String, Int>()
            // Match both http(s):// and www. and common domain paths
            val urlPattern = Pattern.compile("(https?://\\S+|www\\.\\S+|\\S+\\.(com|it|org|net|me)/\\S+)", Pattern.CASE_INSENSITIVE)

            texts.forEach { text ->
                // 1. Rimuovi URL prima di pulire i caratteri
                val textWithoutUrls = urlPattern.matcher(text).replaceAll(" ")

                // 2. Normalizza e conta
                textWithoutUrls.lowercase()
                    .replace(WORD_CLEANER, " ") // Sostituisci con spazio invece di stringa vuota per evitare fusioni
                    .split(WORD_SPLITTER)
                    .filter { it.length > 2 && it !in STOP_WORDS }
                    .forEach { word ->
                        val norm = word.replace(WORD_NORMALIZER, "$1$1")
                        if (norm.isNotBlank()) {
                            freq[norm] = (freq[norm] ?: 0) + 1
                        }
                    }
            }
            return freq
        }

        /**
         * Costruisce una mappa emoji→frequenza (nessun limite) da una lista di testi.
         * Esposto pubblicamente per la costruzione/aggiornamento dell'indice lessicale.
         */
        fun buildEmojiFrequencyMap(texts: List<String>): Map<String, Int> {
            val freq = mutableMapOf<String, Int>()
            try {
                texts.forEach { text ->
                    extractEmojis(text).forEach { emoji ->
                        val base = stripSkinTone(emoji)
                        freq[base] = (freq[base] ?: 0) + 1
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("WhatStat", "buildEmojiFrequencyMap error: ${e.message}")
            }
            return freq
        }

        // =====================================================================

        /**
         * Conta le parole escludendo stop words italiane e inglesi.
         * Filtra parole con meno di 3 caratteri.
         */
        private fun countWords(
            texts: List<String>,
            limit: Int,
        ): List<WordFrequency> {
            return buildWordFrequencyMap(texts)
                .entries
                .sortedByDescending { it.value }
                .take(limit)
                .map { WordFrequency(it.key, it.value) }
        }

        /**
         * Trova il chat_id dato un JID della chat.
         */
        fun getChatIdByJid(jid: String): Long? {
            val cursor =
                database.getMsgDb().rawQuery(
                    "SELECT c._id FROM chat c INNER JOIN jid j ON c.jid_row_id = j._id WHERE j.raw_string = ? LIMIT 1",
                    arrayOf(jid),
                )
            return cursor.use { if (it.moveToFirst()) it.getLong(0) else null }
        }

        /**
         * Recupera i testi di tutti i messaggi che contengono potenzialmente link.
         * Serve per l'estrazione granulare dei domini in Kotlin.
         * Ritorna coppie (text, timestamp) per calcolare ultima condivisione di ogni URL.
         */
        fun getLinkTexts(
            chatId: Long? = null,
            jid: String? = null,
            startTime: Long? = null,
            endTime: Long? = null,
        ): List<Pair<String, Long>> {
            val queryBuilder =
                StringBuilder(
                    """
                    SELECT text_data, m.timestamp
                    FROM message m
                    """.trimIndent(),
                )

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
                        val text = it.getString(0)
                        val timestamp = it.getLong(1)
                        if (text != null) add(Pair(text, timestamp))
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
            globalQuery: Boolean = false,
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
                    conditions.add(
                        """
                        (
                            (m.sender_jid_row_id = (SELECT _id FROM jid WHERE raw_string = ?))
                            OR 
                            (m.from_me = 0 AND 0 = (SELECT sender_jid_row_id FROM message m2 WHERE m2._id = m._id))
                        )
                        """.trimIndent(),
                    )
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

            val query =
                """
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
                        add(
                            StickerStat(
                                filePath = filePath,
                                fileName = fileName,
                                count = cursor.getInt(1),
                            ),
                        )
                    }
                }
            }
        }

        fun getMediaFiles(
            chatId: Long? = null,
            jid: String? = null,
            startTime: Long? = null,
            endTime: Long? = null,
            includeStickers: Boolean = false,
        ): List<MediaStat> {
            val queryBuilder =
                StringBuilder(
                    """
                    WITH all_media AS (
                        SELECT message_row_id, file_path, mime_type FROM message_media
                        UNION ALL
                        SELECT message_row_id, file_path, mime_type FROM addon_message_media
                    )
                    SELECT
                        mm.file_path AS file_path,
                        CASE
                            WHEN mm.mime_type IS NOT NULL THEN mm.mime_type
                            WHEN m.message_type = 1 THEN 'image/jpeg'
                            WHEN m.message_type = 2 THEN 'audio/ogg'
                            WHEN m.message_type = 3 THEN 'video/mp4'
                            WHEN m.message_type = 13 THEN 'video/mp4'
                            ELSE 'application/octet-stream'
                        END AS mime_type,
                        m.timestamp AS timestamp
                    FROM message m
                    JOIN all_media mm ON m._id = mm.message_row_id
                    """.trimIndent(),
                )

            val conditions = mutableListOf<String>()
            val args = mutableListOf<String>()

            if (chatId != null && jid != null) {
                conditions.add("m.chat_row_id = ?")
                args.add(chatId.toString())
                if (jid == "me@s.whatsapp.net") {
                    conditions.add("m.from_me = 1")
                } else {
                    conditions.add(
                        """
                        (
                            m.sender_jid_row_id IN (
                                SELECT _id FROM jid WHERE raw_string = ?
                                UNION
                                SELECT lid_row_id FROM jid_map WHERE jid_row_id = (SELECT _id FROM jid WHERE raw_string = ?)
                            )
                            OR
                            (m.from_me = 0 AND m.sender_jid_row_id = 0)
                        )
                        """.trimIndent(),
                    )
                    args.add(jid)
                    args.add(jid)
                }
            } else if (chatId != null) {
                conditions.add("m.chat_row_id = ?")
                args.add(chatId.toString())
            } else if (jid != null) {
                conditions.add("m.chat_row_id = (SELECT c._id FROM chat c JOIN jid j ON c.jid_row_id = j._id WHERE j.raw_string = ?)")
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

            // Escludi messaggi di sistema (7)
            conditions.add("m.message_type != 7")

            // Condizione MIME: includi foto/video/audio, oppure sticker se richiesto
            val stickerOr = if (includeStickers) " OR m.message_type = 20" else ""
            conditions.add(
                "(mm.mime_type LIKE 'image/%' OR mm.mime_type LIKE 'video/%' OR mm.mime_type LIKE 'audio/%' " +
                    "OR (mm.mime_type IS NULL AND m.message_type IN (1, 2, 3, 13))" +
                    "$stickerOr)",
            )
            queryBuilder.append(" WHERE ${conditions.joinToString(" AND ")}")
            queryBuilder.append(" ORDER BY m.timestamp DESC")

            val cursor = database.getMsgDb().rawQuery(queryBuilder.toString(), args.toTypedArray())
            return cursor.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            MediaStat(
                                filePath = cursor.getString(0) ?: "",
                                mimeType = cursor.getString(1) ?: "",
                                timestamp = cursor.getLong(2),
                            ),
                        )
                    }
                }
            }
        }
    }

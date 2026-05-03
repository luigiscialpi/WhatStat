package com.whatstat.reborn.data.lite

import com.whatstat.reborn.domain.model.ChatStats
import com.whatstat.reborn.domain.model.SlowChatStats
import com.whatstat.reborn.data.db.model.WordFrequency
import com.whatstat.reborn.data.db.model.EmojiStat
import com.whatstat.reborn.data.db.model.SenderStat
import java.time.ZoneId

object LiteMetricsCalculator {
    val WORD_CLEANER = Regex("[^a-zàáâãäåæçèéêëìíîïðñòóôõöøùúûüý\\s]")
    val WORD_SPLITTER = Regex("\\s+")
    val WORD_NORMALIZER = Regex("(.)\\1{2,}")
    val MEDIA_FILENAME_REGEX = Regex("(?:IMG|VID|AUD|PTT|DOC)-\\d{8}-WA\\d{4,}(?:\\.[a-z0-9]+)?", RegexOption.IGNORE_CASE)
    val STOP_WORDS = setOf(
        "di", "a", "da", "in", "con", "su", "per", "tra", "fra", "il", "lo", "la", "i", "gli", "le",
        "un", "uno", "una", "un", "e", "ed", "o", "ma", "che", "se", "perché", "quindi", "non",
        "mi", "ti", "ci", "vi", "si", "lo", "la", "li", "le", "ne", "ce", "ve", "io", "tu", "lui",
        "lei", "noi", "voi", "loro", "mio", "tuo", "suo", "nostro", "vostro", "loro",
        "questo", "quello", "tutto", "tutti", "tutta", "tutte", "niente", "qualcosa", "nulla",
        "qui", "qua", "lì", "là", "giù", "sù", "su", "più", "meno", "anche", "ancora", "solo",
        "sempre", "mai", "già", "poi", "dopo", "prima", "ora", "adesso", "subito",
        "the", "be", "to", "of", "and", "a", "in", "that", "have", "i",
        "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
        "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
        "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
        "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
        // Whatsapp
        "http", "https", "www", "com", "media", "omitted"
    )

    private fun isSkinToneModifier(codePoint: Int): Boolean = codePoint in 0x1F3FB..0x1F3FF

    fun stripSkinTone(emoji: String): String {
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

    private fun isEmoji(codePoint: Int): Boolean = when (codePoint) {
        in 0x1F600..0x1F64F, in 0x1F300..0x1F5FF, in 0x1F680..0x1F6FF, in 0x1F1E0..0x1F1FF,
        in 0x2700..0x27BF, in 0x2600..0x26FF, in 0x1F0A0..0x1F0FF, in 0x1F900..0x1F9FF, in 0x1FA00..0x1FAFF -> true
        else -> false
    }

    private fun isRegionalIndicator(codePoint: Int): Boolean = codePoint in 0x1F1E6..0x1F1FF

    fun extractEmojis(text: String): List<String> {
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
                            if (nextCodePoint == 0xFE0F.toInt() || nextCodePoint == 0x200D.toInt()) {
                                combined.appendCodePoint(nextCodePoint)
                                j += Character.charCount(nextCodePoint)
                                if (nextCodePoint == 0x200D.toInt() && j < text.length && isEmoji(text.codePointAt(j))) {
                                    combined.appendCodePoint(text.codePointAt(j))
                                    j += Character.charCount(text.codePointAt(j))
                                }
                                hasValidModifier = true
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
    fun calculateQuickStats(
        messages: List<ParsedMessage>,
        mediaStats: LiteMediaStats?,
        chatName: String,
        startTime: Long?,
        endTime: Long?
    ): ChatStats {
        val filtered = messages.filter { msg ->
            val ts = try { msg.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (e: Exception) { 0L }
            (startTime == null || ts >= startTime) && (endTime == null || ts <= endTime)
        }

        val totalMsgs = filtered.size
        val sentByMe = filtered.count { it.isCurrentUser }
        val sentRatio = if (totalMsgs > 0) sentByMe.toFloat() / totalMsgs else 0f

        val firstTs = filtered.firstOrNull()?.let { 
            try { it.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (e: Exception) { 0L }
        } ?: 0L
        val lastTs = filtered.lastOrNull()?.let { 
            try { it.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (e: Exception) { 0L }
        } ?: 0L

        // Average length (excluding media which has 0 length inside text property)
        val textMsgs = filtered.filter { !it.hasMedia }
        val avgLen = if (textMsgs.isNotEmpty()) textMsgs.sumOf { it.content.length }.toFloat() / textMsgs.size else 0f

        // Re-use GlobalStats grouping because we are just mapping
        val hourly = IntArray(24)
        val weekly = IntArray(7)
        val dayOfMonth = mutableMapOf<Int, Int>()
        val monthOfYear = IntArray(13)
        val yearTrend = mutableMapOf<Int, Int>()
        
        filtered.forEach { msg ->
            val dt = msg.timestamp
            hourly[dt.hour]++
            val dow = if (dt.dayOfWeek.value == 7) 0 else dt.dayOfWeek.value
            weekly[dow]++
            dayOfMonth[dt.dayOfMonth] = dayOfMonth.getOrDefault(dt.dayOfMonth, 0) + 1
            monthOfYear[dt.monthValue]++
            yearTrend[dt.year] = yearTrend.getOrDefault(dt.year, 0) + 1
        }

        return ChatStats(
            chatId = -1L,
            contactName = chatName,
            jid = chatName, // Fallback
            isGroup = messages.map { it.senderName }.distinct().size > 2,
            totalMessages = totalMsgs,
            sentByMe = sentByMe,
            receivedFromThem = totalMsgs - sentByMe,
            sentRatio = sentRatio,
            firstMessageDate = firstTs,
            lastMessageDate = lastTs,
            averageResponseTimeMs = 0L, // will be updated in slow stats
            iStartedConversations = 0, // will be updated in slow stats
            theyStartedConversations = 0, // will be updated in slow stats
            longestMessage = "", // will be updated in slow stats
            averageMessageLength = avgLen,
            mediaCount = mediaStats?.totalCount ?: 0,
            audioCount = mediaStats?.audioFiles ?: 0,
            videoCount = mediaStats?.videos ?: 0,
            hourlyDistribution = hourly.mapIndexed { h, c -> com.whatstat.reborn.data.db.model.HourlyCount(h, c) },
            weeklyDistribution = weekly.mapIndexed { d, c -> com.whatstat.reborn.data.db.model.DayCount(d, c) },
            dayOfMonthDistribution = dayOfMonth.map { com.whatstat.reborn.data.db.model.DayOfMonthCount(it.key, it.value) }.sortedBy { it.day },
            monthOfYearDistribution = monthOfYear.mapIndexed { m, c -> com.whatstat.reborn.data.db.model.MonthOfYearCount(m, c) }.filter { it.month > 0 },
            yearTrendDistribution = yearTrend.map { com.whatstat.reborn.data.db.model.YearTrendCount(it.key, it.value) }.sortedBy { it.year },
            monthlyTrend = emptyList(), // Not fully implemented in Lite yet
            topWords = emptyList(),
            senderBreakdown = emptyList(),
            conversationStarterBreakdown = emptyList()
        )
    }

    fun calculateSlowStats(
        messages: List<ParsedMessage>,
        mediaStats: LiteMediaStats?,
        startTime: Long?,
        endTime: Long?
    ): SlowChatStats {
        val filtered = messages.filter { msg ->
            val ts = try { msg.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (e: Exception) { 0L }
            (startTime == null || ts >= startTime) && (endTime == null || ts <= endTime)
        }

        // Sender Breakdown
        val senderBreakdown = filtered.groupBy { it.senderName }.map { (sender, msgs) ->
            if (msgs.isNotEmpty() && msgs.first().isCurrentUser) {
                SenderStat("me@s.whatsapp.net", "Tu", msgs.size)
            } else {
                SenderStat(sender, sender, msgs.size)
            }
        }.sortedByDescending { it.count }

        // Starters & Response Time
        var iStarted = 0
        var theyStarted = 0
        var lastSender = ""
        var lastTs = 0L
        var totalRespTimeMs = 0L
        var respTimeCounts = 0
        val starterMap = mutableMapOf<String, Int>()

        var longestMsg = ""

        filtered.forEach { msg ->
            val ts = try { msg.timestamp.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() } catch (e: Exception) { 0L }
            
            // Longest message
            if (!msg.hasMedia && msg.content.length > longestMsg.length) {
                longestMsg = msg.content
            }

            // Conversations logic (turn based with 1 hour threshold)
            if (lastSender.isEmpty() || lastSender != msg.senderName) {
                if (ts - lastTs > 3600_000) {
                    if (msg.isCurrentUser) iStarted++ else theyStarted++
                    starterMap[msg.senderName] = starterMap.getOrDefault(msg.senderName, 0) + 1
                } else if (lastSender.isNotEmpty()) {
                    totalRespTimeMs += (ts - lastTs)
                    respTimeCounts++
                }
            }
            lastSender = msg.senderName
            lastTs = ts
        }

        val starters = starterMap.entries.map { (sender, count) -> 
            val isMe = filtered.firstOrNull { it.senderName == sender }?.isCurrentUser == true
            if (isMe) {
                SenderStat("me@s.whatsapp.net", "Tu", count)
            } else {
                SenderStat(sender, sender, count)
            }
        }.sortedByDescending { it.count }

        val texts = filtered.filter { !it.hasMedia }.map { it.content }
        
        val wordFreq = mutableMapOf<String, Int>()
        val urlPattern = Regex("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=]+")
        
        texts.forEach { text ->
            val cleanText = text.replace(urlPattern, "")
            cleanText.lowercase()
                .replace(WORD_CLEANER, " ")
                .split(WORD_SPLITTER)
                .filter { it.length > 2 && it !in STOP_WORDS }
                .forEach { word ->
                    val norm = word.replace(WORD_NORMALIZER, "$1$1")
                    wordFreq[norm] = (wordFreq[norm] ?: 0) + 1
                }
        }
        val topWords = wordFreq.entries.sortedByDescending { it.value }.take(50).map { WordFrequency(it.key, it.value) }

        val emojiFreq = mutableMapOf<String, Int>()
        texts.forEach { text ->
            extractEmojis(text).forEach { emoji ->
                val base = stripSkinTone(emoji)
                emojiFreq[base] = (emojiFreq[base] ?: 0) + 1
            }
        }
        val topEmojis = emojiFreq.entries.sortedByDescending { it.value }.take(50).map { EmojiStat(it.key, it.value) }

        return SlowChatStats(
            topWords = topWords,
            topEmojis = topEmojis,
            topStickers = emptyList(),
            senderBreakdown = senderBreakdown,
            conversationStarterBreakdown = starters,
            averageResponseTimeMs = if (respTimeCounts > 0) totalRespTimeMs / respTimeCounts else 0L,
            iStartedConversations = iStarted,
            theyStartedConversations = theyStarted,
            longestMessage = longestMsg
        )
    }
}

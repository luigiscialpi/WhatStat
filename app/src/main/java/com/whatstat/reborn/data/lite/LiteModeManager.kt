package com.whatstat.reborn.data.lite

import android.net.Uri
import com.whatstat.reborn.data.db.model.*
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.domain.model.GlobalStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteModeManager @Inject constructor(
    private val fileExtractor: FileExtractor,
    private val parser: TextChatParser,
    private val mediaManager: LiteMediaManager,
    private val cacheRepository: LiteCacheRepository
) {

    /**
     * Processa lo ZIP importato, esegue il parsing e salva in cache.
     */
    suspend fun importAndProcess(zipUri: Uri, ownerName: String): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            // 1. Estrazione ZIP
            val folder = fileExtractor.extractFullChatFolder(zipUri).getOrThrow()
            val txtFile = folder.listFiles()?.firstOrNull { it.extension == "txt" } 
                ?: throw Exception("Nessun file .txt trovato nello ZIP")

            // 2. Parsing Testo
            val parseResult = parser.parse(txtFile, ownerName)
            val identifiedSenders = parseResult.messages.map { it.senderName }.distinct()
            
            // 3. Analisi Media
            val mediaStats = mediaManager.extractMediaFromFolder(folder)

            // 4. Calcolo Statistiche Globali
            val stats = calculateGlobalStats(parseResult.messages, mediaStats, folder.name)

            // 5. Salvataggio Cache
            val cacheData = LiteCacheData(
                sourceFileName = folder.name,
                lastModified = System.currentTimeMillis(),
                totalLines = parseResult.messages.size,
                ownerName = ownerName,
                globalStats = stats,
                messages = parseResult.messages,
                mediaStats = mediaStats
            )
            cacheRepository.saveCache(cacheData)
            
            ImportResult(stats, identifiedSenders)
        }
    }

    suspend fun getCachedStats(): GlobalStats? {
        return cacheRepository.loadCache()?.globalStats
    }

    suspend fun getCachedMessages(): List<ParsedMessage> {
        return cacheRepository.loadCache()?.messages ?: emptyList()
    }

    suspend fun getCachedMedia(): LiteMediaStats? {
        return cacheRepository.loadCache()?.mediaStats
    }

    private fun calculateGlobalStats(
        messages: List<ParsedMessage>, 
        mediaStats: LiteMediaStats,
        folderName: String
    ): GlobalStats {
        val totalMsgs = messages.size
        if (totalMsgs == 0) return createEmptyStats()

        val hourly = IntArray(24)
        val weekly = IntArray(7)
        val dayOfMonth = mutableMapOf<Int, Int>()
        val monthOfYear = IntArray(13) // 1-12
        val yearTrend = mutableMapOf<Int, Int>()
        
        val wordsMap = mutableMapOf<String, Int>()
        val emojisMap = mutableMapOf<String, Int>()

        messages.forEach { msg ->
            val dt = msg.timestamp
            hourly[dt.hour]++
            // DayOfWeek in java.time: 1 (Mon) to 7 (Sun). 
            // WhatStat: 0=Sunday, 1=Monday, ... 6=Saturday
            val dow = if (dt.dayOfWeek.value == 7) 0 else dt.dayOfWeek.value
            weekly[dow]++
            
            dayOfMonth[dt.dayOfMonth] = dayOfMonth.getOrDefault(dt.dayOfMonth, 0) + 1
            monthOfYear[dt.monthValue]++
            yearTrend[dt.year] = yearTrend.getOrDefault(dt.year, 0) + 1

            // Word & Emoji counts
            if (!msg.isSystemMessage) {
                // Word count with LTR cleaning
                val systemWords = setOf("media", "omesso", "omessi", "allegato", "file")
                msg.content.replace("\u200E", "").split(Regex("\\s+")).forEach { word ->
                    val clean = word.lowercase().filter { it.isLetter() }
                    if (clean.length > 3 && clean !in systemWords) {
                        wordsMap[clean] = wordsMap.getOrDefault(clean, 0) + 1
                    }
                }
                
                // Emoji count using codePoints to handle surrogate pairs
                msg.content.codePoints().forEach { cp ->
                    if (isEmoji(cp)) {
                        val emoji = String(Character.toChars(cp))
                        emojisMap[emoji] = emojisMap.getOrDefault(emoji, 0) + 1
                    }
                }
            }
        }

        val oldestTs = messages.first().timestamp.toInstant(ZoneOffset.UTC).toEpochMilli()
        val daysCount = ((System.currentTimeMillis() - oldestTs) / 86400000f).coerceAtLeast(1f)

        return GlobalStats(
            totalMessages = totalMsgs,
            totalChats = 1, // Modalità Lite analizza una chat alla volta
            totalContacts = messages.map { it.senderName }.distinct().size,
            totalMediaFiles = mediaStats.totalCount,
            oldestMessageDate = oldestTs,
            mostActiveChat = folderName,
            mostActiveHour = hourly.indices.maxByOrNull { hourly[it] } ?: 0,
            mostActiveDayOfWeek = weekly.indices.maxByOrNull { weekly[it] } ?: 0,
            averageMessagesPerDay = totalMsgs / daysCount,
            longestStreak = 0, // TODO: Implementare se necessario
            hourlyDistribution = hourly.mapIndexed { h, c -> HourlyCount(h, c) },
            weeklyDistribution = weekly.mapIndexed { d, c -> DayCount(d, c) },
            dayOfMonthDistribution = dayOfMonth.map { DayOfMonthCount(it.key, it.value) }.sortedBy { it.day },
            monthOfYearDistribution = monthOfYear.mapIndexed { m, c -> MonthOfYearCount(m, c) }.filter { it.month > 0 },
            yearTrendDistribution = yearTrend.map { YearTrendCount(it.key, it.value) }.sortedBy { it.year },
            topWords = wordsMap.entries.sortedByDescending { it.value }.take(50).map { WordFrequency(it.key, it.value) },
            topEmojis = emojisMap.entries.sortedByDescending { it.value }.take(50).map { EmojiStat(it.key, it.value) },
            topStickers = mediaStats.mediaItems
                .filter { it.type == "IMAGE" && it.fileName.endsWith(".webp") } // Gli sticker esportati sono .webp
                .take(50)
                .map { StickerStat(it.relativePath, it.fileName, 1) }, // Conteggio semplificato a 1 per importazione ZIP
            isLiteMode = true
        )
    }

    private fun createEmptyStats() = GlobalStats(
        0, 0, 0, 0, 0, "", 0, 0, 0f, 0,
        isLiteMode = true
    )

    private fun isEmoji(codePoint: Int): Boolean {
        return codePoint in 0x1F600..0x1F64F || 
               codePoint in 0x1F300..0x1F5FF || 
               codePoint in 0x1F680..0x1F6FF || 
               codePoint in 0x1F1E6..0x1F1FF || 
               codePoint in 0x2600..0x26FF ||   
               codePoint in 0x2700..0x27BF ||   
               codePoint in 0x1F900..0x1F9FF    
    }
}

data class ImportResult(
    val stats: com.whatstat.reborn.domain.model.GlobalStats,
    val identifiedSenders: List<String>
)

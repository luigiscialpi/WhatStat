package com.whatstat.reborn.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whatstat.reborn.data.db.model.*
import com.whatstat.reborn.domain.model.ChatStats
import com.whatstat.reborn.domain.model.GlobalStats
import com.whatstat.reborn.domain.model.SlowChatStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "stats_cache")

private val cacheJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// ── Modelli serializzabili (mirror dei domain model) ─────────────

@Serializable
private data class CachedGlobalStats(
    val totalMessages: Int,
    val totalChats: Int,
    val totalContacts: Int,
    val totalMediaFiles: Int,
    val oldestMessageDate: Long,
    val mostActiveChat: String,
    val mostActiveHour: Int,
    val mostActiveDayOfWeek: Int,
    val averageMessagesPerDay: Float,
    val longestStreak: Int,
    val hourlyDistribution: List<CachedHourlyCount> = emptyList(),
    val weeklyDistribution: List<CachedDayCount> = emptyList(),
    val dayOfMonthDistribution: List<CachedDayOfMonthCount> = emptyList(),
    val monthOfYearDistribution: List<CachedMonthOfYearCount> = emptyList(),
    val yearTrendDistribution: List<CachedYearTrendCount> = emptyList(),
    val topWords: List<CachedWordFrequency> = emptyList(),
    val topEmojis: List<CachedEmojiStat> = emptyList()
)

@Serializable
private data class CachedSlowChatStats(
    val topWords: List<CachedWordFrequency> = emptyList(),
    val topEmojis: List<CachedEmojiStat> = emptyList(),
    val topStickers: List<CachedStickerStat> = emptyList(),
    val senderBreakdown: List<CachedSenderStat> = emptyList(),
    val conversationStarterBreakdown: List<CachedSenderStat> = emptyList(),
    val averageResponseTimeMs: Long = 0L,
    val iStartedConversations: Int = 0,
    val theyStartedConversations: Int = 0,
    val longestMessage: String = ""
)

@Serializable
private data class CachedQuickChatStats(
    val contactName: String,
    val jid: String,
    val isGroup: Boolean,
    val totalMessages: Int,
    val sentByMe: Int,
    val receivedFromThem: Int,
    val sentRatio: Float,
    val firstMessageDate: Long,
    val lastMessageDate: Long,
    val averageMessageLength: Float,
    val mediaCount: Int,
    val audioCount: Int,
    val videoCount: Int,
    val hourlyDistribution: List<CachedHourlyCount> = emptyList(),
    val weeklyDistribution: List<CachedDayCount> = emptyList(),
    val dayOfMonthDistribution: List<CachedDayOfMonthCount> = emptyList(),
    val monthOfYearDistribution: List<CachedMonthOfYearCount> = emptyList(),
    val yearTrendDistribution: List<CachedYearTrendCount> = emptyList(),
    val monthlyTrend: List<CachedMonthlyCount> = emptyList()
)

@Serializable private data class CachedHourlyCount(val hour: Int, val count: Int)
@Serializable private data class CachedDayCount(val dayOfWeek: Int, val count: Int)
@Serializable private data class CachedDayOfMonthCount(val day: Int, val count: Int)
@Serializable private data class CachedMonthOfYearCount(val month: Int, val count: Int)
@Serializable private data class CachedYearTrendCount(val year: Int, val count: Int)
@Serializable private data class CachedMonthlyCount(val month: String, val count: Int)
@Serializable private data class CachedWordFrequency(val word: String, val frequency: Int)
@Serializable private data class CachedEmojiStat(val emoji: String, val count: Int)
@Serializable private data class CachedSenderStat(val jid: String, val displayName: String, val count: Int)
@Serializable private data class CachedLinkStat(val jid: String, val displayName: String?, val totalLinks: Int, val mediaCount: Int)
@Serializable private data class CachedDomainStat(val domain: String, val count: Int)
@Serializable private data class CachedMediaPageStats(
    val linkStats: List<CachedLinkStat> = emptyList(),
    val topDomains: List<CachedDomainStat> = emptyList()
)
@Serializable private data class CachedStickerStat(val filePath: String, val fileName: String, val count: Int)

// ── Indice lessicale incrementale ─────────────────────────────────

/**
 * Indice globale (o per-chat) delle frequenze di parole ed emoji.
 * Persistito su disco come file gzippato, sopravvive alle sessioni.
 * [lastProcessedTimestamp] = timestamp dell'ultimo messaggio processato.
 * [totalMessageCount] = conteggio messaggi al momento del salvataggio (sanity check).
 */
data class WordFrequencyIndexData(
    val wordFrequencies: Map<String, Int>,
    val emojiFrequencies: Map<String, Int>,
    val lastProcessedTimestamp: Long,
    val totalMessageCount: Int
)

/**
 * Indice mensile delle frequenze per supportare le query con filtri temporali
 * senza dover rileggere l'intero database.
 * Chiave di [monthlyWords] e [monthlyEmojis]: "YYYY-MM".
 */
data class MonthlyWordIndexData(
    val monthlyWords: Map<String, Map<String, Int>>,
    val monthlyEmojis: Map<String, Map<String, Int>>,
    val lastProcessedTimestamp: Long
)

// ── Conversioni domain ↔ cache ───────────────────────────────────

private fun GlobalStats.toCached() = CachedGlobalStats(
    totalMessages = totalMessages,
    totalChats = totalChats,
    totalContacts = totalContacts,
    totalMediaFiles = totalMediaFiles,
    oldestMessageDate = oldestMessageDate,
    mostActiveChat = mostActiveChat,
    mostActiveHour = mostActiveHour,
    mostActiveDayOfWeek = mostActiveDayOfWeek,
    averageMessagesPerDay = averageMessagesPerDay,
    longestStreak = longestStreak,
    hourlyDistribution = hourlyDistribution.map { CachedHourlyCount(it.hour, it.count) },
    weeklyDistribution = weeklyDistribution.map { CachedDayCount(it.dayOfWeek, it.count) },
    dayOfMonthDistribution = dayOfMonthDistribution.map { CachedDayOfMonthCount(it.day, it.count) },
    monthOfYearDistribution = monthOfYearDistribution.map { CachedMonthOfYearCount(it.month, it.count) },
    yearTrendDistribution = yearTrendDistribution.map { CachedYearTrendCount(it.year, it.count) },
    topWords = topWords.map { CachedWordFrequency(it.word, it.frequency) },
    topEmojis = topEmojis.map { CachedEmojiStat(it.emoji, it.count) }
)

private fun CachedGlobalStats.toDomain() = GlobalStats(
    totalMessages = totalMessages,
    totalChats = totalChats,
    totalContacts = totalContacts,
    totalMediaFiles = totalMediaFiles,
    oldestMessageDate = oldestMessageDate,
    mostActiveChat = mostActiveChat,
    mostActiveHour = mostActiveHour,
    mostActiveDayOfWeek = mostActiveDayOfWeek,
    averageMessagesPerDay = averageMessagesPerDay,
    longestStreak = longestStreak,
    hourlyDistribution = hourlyDistribution.map { HourlyCount(it.hour, it.count) },
    weeklyDistribution = weeklyDistribution.map { DayCount(it.dayOfWeek, it.count) },
    dayOfMonthDistribution = dayOfMonthDistribution.map { DayOfMonthCount(it.day, it.count) },
    monthOfYearDistribution = monthOfYearDistribution.map { MonthOfYearCount(it.month, it.count) },
    yearTrendDistribution = yearTrendDistribution.map { YearTrendCount(it.year, it.count) },
    topWords = topWords.map { WordFrequency(it.word, it.frequency) },
    topEmojis = topEmojis.map { EmojiStat(it.emoji, it.count) }
)

private fun SlowChatStats.toCached() = CachedSlowChatStats(
    topWords = topWords.map { CachedWordFrequency(it.word, it.frequency) },
    topEmojis = topEmojis.map { CachedEmojiStat(it.emoji, it.count) },
    topStickers = topStickers.map { CachedStickerStat(it.filePath, it.fileName, it.count) },
    senderBreakdown = senderBreakdown.map { CachedSenderStat(it.jid, it.displayName, it.count) },
    conversationStarterBreakdown = conversationStarterBreakdown.map { CachedSenderStat(it.jid, it.displayName, it.count) },
    averageResponseTimeMs = averageResponseTimeMs,
    iStartedConversations = iStartedConversations,
    theyStartedConversations = theyStartedConversations,
    longestMessage = longestMessage
)

private fun CachedSlowChatStats.toDomain() = SlowChatStats(
    topWords = topWords.map { WordFrequency(it.word, it.frequency) },
    topEmojis = topEmojis.map { EmojiStat(it.emoji, it.count) },
    topStickers = topStickers.map { StickerStat(it.filePath, it.fileName, it.count) },
    senderBreakdown = senderBreakdown.map { SenderStat(it.jid, it.displayName, it.count) },
    conversationStarterBreakdown = conversationStarterBreakdown.map { SenderStat(it.jid, it.displayName, it.count) },
    averageResponseTimeMs = averageResponseTimeMs,
    iStartedConversations = iStartedConversations,
    theyStartedConversations = theyStartedConversations,
    longestMessage = longestMessage
)

private fun ChatStats.toQuickCached() = CachedQuickChatStats(
    contactName = contactName,
    jid = jid,
    isGroup = isGroup,
    totalMessages = totalMessages,
    sentByMe = sentByMe,
    receivedFromThem = receivedFromThem,
    sentRatio = sentRatio,
    firstMessageDate = firstMessageDate,
    lastMessageDate = lastMessageDate,
    averageMessageLength = averageMessageLength,
    mediaCount = mediaCount,
    audioCount = audioCount,
    videoCount = videoCount,
    hourlyDistribution = hourlyDistribution.map { CachedHourlyCount(it.hour, it.count) },
    weeklyDistribution = weeklyDistribution.map { CachedDayCount(it.dayOfWeek, it.count) },
    dayOfMonthDistribution = dayOfMonthDistribution.map { CachedDayOfMonthCount(it.day, it.count) },
    monthOfYearDistribution = monthOfYearDistribution.map { CachedMonthOfYearCount(it.month, it.count) },
    yearTrendDistribution = yearTrendDistribution.map { CachedYearTrendCount(it.year, it.count) },
    monthlyTrend = monthlyTrend.map { CachedMonthlyCount(it.month, it.count) }
)

private fun CachedQuickChatStats.toDomain(chatId: Long) = ChatStats(
    chatId = chatId,
    contactName = contactName,
    jid = jid,
    isGroup = isGroup,
    totalMessages = totalMessages,
    sentByMe = sentByMe,
    receivedFromThem = receivedFromThem,
    sentRatio = sentRatio,
    firstMessageDate = firstMessageDate,
    lastMessageDate = lastMessageDate,
    averageResponseTimeMs = 0L,
    iStartedConversations = 0,
    theyStartedConversations = 0,
    longestMessage = "",
    averageMessageLength = averageMessageLength,
    mediaCount = mediaCount,
    audioCount = audioCount,
    videoCount = videoCount,
    hourlyDistribution = hourlyDistribution.map { HourlyCount(it.hour, it.count) },
    weeklyDistribution = weeklyDistribution.map { DayCount(it.dayOfWeek, it.count) },
    dayOfMonthDistribution = dayOfMonthDistribution.map { DayOfMonthCount(it.day, it.count) },
    monthOfYearDistribution = monthOfYearDistribution.map { MonthOfYearCount(it.month, it.count) },
    yearTrendDistribution = yearTrendDistribution.map { YearTrendCount(it.year, it.count) },
    monthlyTrend = monthlyTrend.map { MonthlyCount(it.month, it.count) },
    topWords = emptyList(),
    senderBreakdown = emptyList(),
    conversationStarterBreakdown = emptyList()
)

// ── CacheManager ─────────────────────────────────────────────────

@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.cacheDataStore

    companion object {
        private val KEY_DB_HASH = stringPreferencesKey("db_file_hash")
        private val KEY_LAST_UPDATE = longPreferencesKey("last_cache_update")
        
        // Versione v3/v4: aggiungiamo suffisso _root/_lite per separazione totale
        private fun keyGlobalStats(isLite: Boolean) = stringPreferencesKey("global_stats_v3_${if (isLite) "lite" else "root"}")
        private fun keyGlobalSlow(isLite: Boolean) = stringPreferencesKey("global_slow_v4_${if (isLite) "lite" else "root"}")
        private fun keyGlobalMedia(isLite: Boolean) = stringPreferencesKey("media_global_v2_${if (isLite) "lite" else "root"}")
        private val KEY_MEDIA_INDEX_FILE_ROOT = "media_index_root.gz"
        private val KEY_MEDIA_INDEX_FILE_LITE = "media_index_lite.gz"
    }

    // ── Hash DB ──────────────────────────────────────────────────

    fun calculateFileHash(filePath: String): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            java.io.FileInputStream(filePath).use { fis ->
                val buffer = ByteArray(8192)
                var read: Int
                while (fis.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveDbHash(hash: String, filePath: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DB_HASH] = hash
            prefs[KEY_LAST_UPDATE] = System.currentTimeMillis()
        }
    }

    suspend fun getStoredDbHash(): String? {
        return dataStore.data.first()[KEY_DB_HASH]
    }

    suspend fun isDbChanged(newHash: String): Boolean {
        val stored = dataStore.data.first()[KEY_DB_HASH]
        return stored != newHash
    }

    // ── Global Stats (quick) ─────────────────────────────────────
    
    suspend fun getGlobalStats(isLite: Boolean): GlobalStats? {
        val json = dataStore.data.first()[keyGlobalStats(isLite)] ?: return null
        return try {
            cacheJson.decodeFromString<CachedGlobalStats>(json).toDomain()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveGlobalStats(stats: GlobalStats, isLite: Boolean) {
        val json = cacheJson.encodeToString(stats.toCached())
        dataStore.edit { prefs ->
            prefs[keyGlobalStats(isLite)] = json
            prefs[KEY_LAST_UPDATE] = System.currentTimeMillis()
        }
    }

    // ── Global Slow Stats (words + emoji) ────────────────────────

    suspend fun getGlobalSlowStats(isLite: Boolean): Triple<List<WordFrequency>, List<EmojiStat>, List<StickerStat>>? {
        val json = dataStore.data.first()[keyGlobalSlow(isLite)] ?: return null
        return try {
            val cached = cacheJson.decodeFromString<CachedSlowChatStats>(json)
            Triple(
                cached.topWords.map { WordFrequency(it.word, it.frequency) },
                cached.topEmojis.map { EmojiStat(it.emoji, it.count) },
                cached.topStickers.map { StickerStat(it.filePath, it.fileName, it.count) }
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveGlobalSlowStats(topWords: List<WordFrequency>, topEmojis: List<EmojiStat>, topStickers: List<StickerStat>, isLite: Boolean) {
        val cached = CachedSlowChatStats(
            topWords = topWords.map { CachedWordFrequency(it.word, it.frequency) },
            topEmojis = topEmojis.map { CachedEmojiStat(it.emoji, it.count) },
            topStickers = topStickers.map { CachedStickerStat(it.filePath, it.fileName, it.count) }
        )
        dataStore.edit { prefs ->
            prefs[keyGlobalSlow(isLite)] = cacheJson.encodeToString(cached)
        }
    }

    // ── Chat Slow Stats (per chat) ───────────────────────────────

    suspend fun getChatSlowStats(chatId: Long): SlowChatStats? {
        // v3: chiave aggiornata per forzare ricalcolo con pool da 50 emoji/parole
        val key = stringPreferencesKey("chat_slow_${chatId}_v3")
        val json = dataStore.data.first()[key] ?: return null
        return try {
            cacheJson.decodeFromString<CachedSlowChatStats>(json).toDomain()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveChatSlowStats(chatId: Long, stats: SlowChatStats) {
        val key = stringPreferencesKey("chat_slow_${chatId}_v3")
        dataStore.edit { prefs ->
            prefs[key] = cacheJson.encodeToString(stats.toCached())
        }
    }

    // ── Chat Quick Stats (per chat) ──────────────────────────────

    suspend fun getChatQuickStats(chatId: Long): ChatStats? {
        val key = stringPreferencesKey("chat_quick_${chatId}_v3")
        val json = dataStore.data.first()[key] ?: return null
        return try {
            cacheJson.decodeFromString<CachedQuickChatStats>(json).toDomain(chatId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveChatQuickStats(chatId: Long, stats: ChatStats) {
        val key = stringPreferencesKey("chat_quick_${chatId}_v3")
        dataStore.edit { prefs ->
            prefs[key] = cacheJson.encodeToString(stats.toQuickCached())
        }
    }

    // ── Media & Link Stats (global) ───────────────────────────────

    suspend fun getGlobalMediaStats(isLite: Boolean): Pair<List<LinkStat>, List<DomainStat>>? {
        val key = keyGlobalMedia(isLite)
        val json = dataStore.data.first()[key] ?: return null
        return try {
            val cached = cacheJson.decodeFromString<CachedMediaPageStats>(json)
            Pair(
                cached.linkStats.map { LinkStat(it.jid, it.displayName, it.totalLinks, it.mediaCount) },
                cached.topDomains.map { DomainStat(it.domain, it.count) }
            )
        } catch (e: Exception) { null }
    }

    suspend fun saveGlobalMediaStats(linkStats: List<LinkStat>, topDomains: List<DomainStat>, isLite: Boolean) {
        val key = keyGlobalMedia(isLite)
        val cached = CachedMediaPageStats(
            linkStats = linkStats.map { CachedLinkStat(it.jid, it.displayName, it.totalLinks, it.mediaCount) },
            topDomains = topDomains.map { CachedDomainStat(it.domain, it.count) }
        )
        dataStore.edit { prefs -> prefs[key] = cacheJson.encodeToString(cached) }
    }

    // ── Media & Link Stats (per chat) ────────────────────────────

    suspend fun getChatMediaStats(chatId: Long): Pair<List<LinkStat>, List<DomainStat>>? {
        val key = stringPreferencesKey("media_chat_${chatId}_v2")
        val json = dataStore.data.first()[key] ?: return null
        return try {
            val cached = cacheJson.decodeFromString<CachedMediaPageStats>(json)
            Pair(
                cached.linkStats.map { LinkStat(it.jid, it.displayName, it.totalLinks, it.mediaCount) },
                cached.topDomains.map { DomainStat(it.domain, it.count) }
            )
        } catch (e: Exception) { null }
    }

    suspend fun saveChatMediaStats(chatId: Long, linkStats: List<LinkStat>, topDomains: List<DomainStat>) {
        val key = stringPreferencesKey("media_chat_${chatId}_v2")
        val cached = CachedMediaPageStats(
            linkStats = linkStats.map { CachedLinkStat(it.jid, it.displayName, it.totalLinks, it.mediaCount) },
            topDomains = topDomains.map { CachedDomainStat(it.domain, it.count) }
        )
        dataStore.edit { prefs -> prefs[key] = cacheJson.encodeToString(cached) }
    }

    // ── Sticker Stats (per chat) ────────────────────────────────────

    suspend fun getChatStickerStats(chatId: Long): List<StickerStat>? {
        val key = stringPreferencesKey("sticker_chat_${chatId}_v1")
        val json = dataStore.data.first()[key] ?: return null
        return try {
            val cached = cacheJson.decodeFromString<List<CachedStickerStat>>(json)
            cached.map { StickerStat(it.filePath, it.fileName, it.count) }
        } catch (e: Exception) { null }
    }

    suspend fun saveChatStickerStats(chatId: Long, stickers: List<StickerStat>) {
        val key = stringPreferencesKey("sticker_chat_${chatId}_v1")
        val cached = stickers.map { CachedStickerStat(it.filePath, it.fileName, it.count) }
        dataStore.edit { prefs -> prefs[key] = cacheJson.encodeToString(cached) }
    }

    // ── Indice lessicale (word/emoji frequency, incrementale) ─────────────────────

    @Volatile private var globalWordIndexRoot: WordFrequencyIndexData? = null
    @Volatile private var globalWordIndexLite: WordFrequencyIndexData? = null
    @Volatile private var monthlyWordIndexRoot: MonthlyWordIndexData? = null
    @Volatile private var monthlyWordIndexLite: MonthlyWordIndexData? = null
    private val chatWordIndexCache = java.util.concurrent.ConcurrentHashMap<Long, WordFrequencyIndexData>()

    // filesDir: persistente tra sessioni (costoso da ricostruire)
    private fun globalWordIndexFile(isLite: Boolean) = java.io.File(context.filesDir, "word_index_global_${if (isLite) "lite" else "root"}.gz")
    private fun monthlyWordIndexFile(isLite: Boolean) = java.io.File(context.filesDir, "word_index_monthly_${if (isLite) "lite" else "root"}.gz")
    private fun chatWordIndexFile(chatId: Long) = java.io.File(context.cacheDir, "word_index_chat_${chatId}.gz")

    fun loadGlobalWordIndexFromDisk(isLite: Boolean): WordFrequencyIndexData? {
        if (isLite) globalWordIndexLite?.let { return it } else globalWordIndexRoot?.let { return it }
        
        val file = globalWordIndexFile(isLite)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            parseWordIndexFile(file).also { if (isLite) globalWordIndexLite = it else globalWordIndexRoot = it }
        } catch (_: Exception) { null }
    }

    fun saveGlobalWordIndexToDisk(data: WordFrequencyIndexData, isLite: Boolean) {
        if (isLite) globalWordIndexLite = data else globalWordIndexRoot = data
        try {
            val tmp = java.io.File(context.filesDir, "word_index_global_${if (isLite) "lite" else "root"}.tmp")
            writeWordIndexFile(tmp, data)
            tmp.renameTo(globalWordIndexFile(isLite))
        } catch (_: Exception) { }
    }

    fun loadChatWordIndexFromDisk(chatId: Long): WordFrequencyIndexData? {
        chatWordIndexCache[chatId]?.let { return it }
        val file = chatWordIndexFile(chatId)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            parseWordIndexFile(file).also { chatWordIndexCache[chatId] = it }
        } catch (_: Exception) { null }
    }

    fun saveChatWordIndexToDisk(chatId: Long, data: WordFrequencyIndexData) {
        chatWordIndexCache[chatId] = data
        try {
            val tmp = java.io.File(context.cacheDir, "word_index_chat_${chatId}.tmp")
            writeWordIndexFile(tmp, data)
            tmp.renameTo(chatWordIndexFile(chatId))
        } catch (_: Exception) { }
    }

    fun loadMonthlyWordIndexFromDisk(isLite: Boolean): MonthlyWordIndexData? {
        if (isLite) monthlyWordIndexLite?.let { return it } else monthlyWordIndexRoot?.let { return it }
        
        val file = monthlyWordIndexFile(isLite)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val mWords = mutableMapOf<String, MutableMap<String, Int>>()
            val mEmojis = mutableMapOf<String, MutableMap<String, Int>>()
            var lastTs = 0L
            java.util.zip.GZIPInputStream(file.inputStream().buffered()).bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    val parts = line.split('\t', ignoreCase = false, limit = 4)
                    when {
                        parts.size >= 2 && parts[0] == "LAST_TS" -> lastTs = parts[1].toLongOrNull() ?: 0L
                        parts.size == 4 && parts[1] == "W" ->
                            mWords.getOrPut(parts[0]) { mutableMapOf() }[parts[2]] = parts[3].toIntOrNull() ?: 0
                        parts.size == 4 && parts[1] == "E" ->
                            mEmojis.getOrPut(parts[0]) { mutableMapOf() }[parts[2]] = parts[3].toIntOrNull() ?: 0
                    }
                }
            }
            MonthlyWordIndexData(mWords, mEmojis, lastTs).also { if (isLite) monthlyWordIndexLite = it else monthlyWordIndexRoot = it }
        } catch (_: Exception) { null }
    }

    fun saveMonthlyWordIndexToDisk(data: MonthlyWordIndexData, isLite: Boolean) {
        if (isLite) monthlyWordIndexLite = data else monthlyWordIndexRoot = data
        try {
            val tmp = java.io.File(context.filesDir, "word_index_monthly_${if (isLite) "lite" else "root"}.tmp")
            java.util.zip.GZIPOutputStream(tmp.outputStream().buffered()).bufferedWriter().use { writer ->
                writer.write("LAST_TS\t${data.lastProcessedTimestamp}"); writer.newLine()
                data.monthlyWords.forEach { (month, freqs) ->
                    freqs.forEach { (word, count) ->
                        writer.write("$month\tW\t$word\t$count"); writer.newLine()
                    }
                }
                data.monthlyEmojis.forEach { (month, freqs) ->
                    freqs.forEach { (emoji, count) ->
                        writer.write("$month\tE\t$emoji\t$count"); writer.newLine()
                    }
                }
            }
            tmp.renameTo(monthlyWordIndexFile(isLite))
        } catch (_: Exception) { }
    }

    private fun parseWordIndexFile(file: java.io.File): WordFrequencyIndexData {
        val wordFreqs = mutableMapOf<String, Int>()
        val emojiFreqs = mutableMapOf<String, Int>()
        var lastTs = 0L
        var msgCount = 0
        java.util.zip.GZIPInputStream(file.inputStream().buffered()).bufferedReader().use { reader ->
            reader.forEachLine { line ->
                val parts = line.split('\t', ignoreCase = false, limit = 3)
                when {
                    parts.size >= 2 && parts[0] == "LAST_TS"   -> lastTs   = parts[1].toLongOrNull() ?: 0L
                    parts.size >= 2 && parts[0] == "MSG_COUNT" -> msgCount = parts[1].toIntOrNull()  ?: 0
                    parts.size == 3 && parts[0] == "W"         -> wordFreqs[parts[1]]  = parts[2].toIntOrNull() ?: 0
                    parts.size == 3 && parts[0] == "E"         -> emojiFreqs[parts[1]] = parts[2].toIntOrNull() ?: 0
                }
            }
        }
        return WordFrequencyIndexData(wordFreqs, emojiFreqs, lastTs, msgCount)
    }

    private fun writeWordIndexFile(file: java.io.File, data: WordFrequencyIndexData) {
        java.util.zip.GZIPOutputStream(file.outputStream().buffered()).bufferedWriter().use { writer ->
            writer.write("LAST_TS\t${data.lastProcessedTimestamp}");  writer.newLine()
            writer.write("MSG_COUNT\t${data.totalMessageCount}"); writer.newLine()
            data.wordFrequencies.forEach  { (word,  count) -> writer.write("W\t$word\t$count");  writer.newLine() }
            data.emojiFrequencies.forEach { (emoji, count) -> writer.write("E\t$emoji\t$count"); writer.newLine() }
        }
    }

    // ── Indice filesystem media (cache in-memory + file gzippato) ─────────────────
    
    @Volatile private var mediaFileIndexRoot: Set<String>? = null
    @Volatile private var mediaFileIndexLite: Set<String>? = null

    private fun mediaIndexFile(isLite: Boolean) = java.io.File(context.cacheDir, if (isLite) KEY_MEDIA_INDEX_FILE_LITE else KEY_MEDIA_INDEX_FILE_ROOT)

    /**
     * Carica l'indice dal file gzippato su disco (se esiste).
     */
    fun loadMediaIndexFromDisk(isLite: Boolean): Set<String>? {
        if (isLite) mediaFileIndexLite?.let { return it } else mediaFileIndexRoot?.let { return it }
        
        val file = mediaIndexFile(isLite)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            java.util.zip.GZIPInputStream(file.inputStream().buffered()).bufferedReader().use { reader ->
                reader.lines().collect(java.util.stream.Collectors.toSet()).also {
                    if (isLite) mediaFileIndexLite = it else mediaFileIndexRoot = it
                }
            }
        } catch (_: Exception) { null }
    }

    /**
     * Salva l'indice su disco in formato gzippato (operazione in background).
     */
    fun saveMediaIndexToDisk(index: Set<String>, isLite: Boolean) {
        if (isLite) mediaFileIndexLite = index else mediaFileIndexRoot = index
        try {
            val tmp = java.io.File(context.cacheDir, if (isLite) "media_index_lite.tmp" else "media_index_root.tmp")
            java.util.zip.GZIPOutputStream(tmp.outputStream().buffered()).bufferedWriter().use { writer ->
                index.forEach { writer.write(it); writer.newLine() }
            }
            tmp.renameTo(mediaIndexFile(isLite))
        } catch (_: Exception) { }
    }

    // ── Invalidazione ────────────────────────────────────────────

    suspend fun clearCache(resetWordIndexes: Boolean = false) {
        dataStore.edit { it.clear() }
        mediaFileIndexRoot = null
        mediaFileIndexLite = null
        mediaIndexFile(false).delete()
        mediaIndexFile(true).delete()
        globalWordIndexRoot = null
        globalWordIndexLite = null
        monthlyWordIndexRoot = null
        monthlyWordIndexLite = null
        chatWordIndexCache.clear()
        if (resetWordIndexes) {
            globalWordIndexFile(false).delete()
            globalWordIndexFile(true).delete()
            monthlyWordIndexFile(false).delete()
            monthlyWordIndexFile(true).delete()
            context.cacheDir.listFiles()
                ?.filter { it.name.startsWith("word_index_chat_") && it.name.endsWith(".gz") }
                ?.forEach { it.delete() }
        }
    }
}

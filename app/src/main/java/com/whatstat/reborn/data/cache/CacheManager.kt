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
        private val KEY_GLOBAL_STATS = stringPreferencesKey("global_stats_v2")
        // v3: bump per rigenerare la cache con pool da 50 words/emoji
        private val KEY_GLOBAL_SLOW = stringPreferencesKey("global_slow_v3")
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

    suspend fun getGlobalStats(): GlobalStats? {
        val json = dataStore.data.first()[KEY_GLOBAL_STATS] ?: return null
        return try {
            cacheJson.decodeFromString<CachedGlobalStats>(json).toDomain()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveGlobalStats(stats: GlobalStats) {
        val json = cacheJson.encodeToString(stats.toCached())
        dataStore.edit { prefs ->
            prefs[KEY_GLOBAL_STATS] = json
            prefs[KEY_LAST_UPDATE] = System.currentTimeMillis()
        }
    }

    // ── Global Slow Stats (words + emoji) ────────────────────────

    suspend fun getGlobalSlowStats(): Pair<List<WordFrequency>, List<EmojiStat>>? {
        val json = dataStore.data.first()[KEY_GLOBAL_SLOW] ?: return null
        return try {
            val cached = cacheJson.decodeFromString<CachedSlowChatStats>(json)
            Pair(
                cached.topWords.map { WordFrequency(it.word, it.frequency) },
                cached.topEmojis.map { EmojiStat(it.emoji, it.count) }
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveGlobalSlowStats(topWords: List<WordFrequency>, topEmojis: List<EmojiStat>) {
        val cached = CachedSlowChatStats(
            topWords = topWords.map { CachedWordFrequency(it.word, it.frequency) },
            topEmojis = topEmojis.map { CachedEmojiStat(it.emoji, it.count) }
        )
        dataStore.edit { prefs ->
            prefs[KEY_GLOBAL_SLOW] = cacheJson.encodeToString(cached)
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
        val key = stringPreferencesKey("chat_quick_${chatId}_v2")
        val json = dataStore.data.first()[key] ?: return null
        return try {
            cacheJson.decodeFromString<CachedQuickChatStats>(json).toDomain(chatId)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveChatQuickStats(chatId: Long, stats: ChatStats) {
        val key = stringPreferencesKey("chat_quick_${chatId}_v2")
        dataStore.edit { prefs ->
            prefs[key] = cacheJson.encodeToString(stats.toQuickCached())
        }
    }

    // ── Media & Link Stats (global) ───────────────────────────────

    suspend fun getGlobalMediaStats(): Pair<List<LinkStat>, List<DomainStat>>? {
        val key = stringPreferencesKey("media_global_v1")
        val json = dataStore.data.first()[key] ?: return null
        return try {
            val cached = cacheJson.decodeFromString<CachedMediaPageStats>(json)
            Pair(
                cached.linkStats.map { LinkStat(it.jid, it.displayName, it.totalLinks, it.mediaCount) },
                cached.topDomains.map { DomainStat(it.domain, it.count) }
            )
        } catch (e: Exception) { null }
    }

    suspend fun saveGlobalMediaStats(linkStats: List<LinkStat>, topDomains: List<DomainStat>) {
        val key = stringPreferencesKey("media_global_v1")
        val cached = CachedMediaPageStats(
            linkStats = linkStats.map { CachedLinkStat(it.jid, it.displayName, it.totalLinks, it.mediaCount) },
            topDomains = topDomains.map { CachedDomainStat(it.domain, it.count) }
        )
        dataStore.edit { prefs -> prefs[key] = cacheJson.encodeToString(cached) }
    }

    // ── Media & Link Stats (per chat) ────────────────────────────

    suspend fun getChatMediaStats(chatId: Long): Pair<List<LinkStat>, List<DomainStat>>? {
        val key = stringPreferencesKey("media_chat_${chatId}_v1")
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
        val key = stringPreferencesKey("media_chat_${chatId}_v1")
        val cached = CachedMediaPageStats(
            linkStats = linkStats.map { CachedLinkStat(it.jid, it.displayName, it.totalLinks, it.mediaCount) },
            topDomains = topDomains.map { CachedDomainStat(it.domain, it.count) }
        )
        dataStore.edit { prefs -> prefs[key] = cacheJson.encodeToString(cached) }
    }

    // ── Invalidazione ────────────────────────────────────────────

    suspend fun clearCache() {
        dataStore.edit { it.clear() }
    }
}
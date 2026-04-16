package com.whatstat.reborn.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whatstat.reborn.domain.model.ChatStats
import com.whatstat.reborn.domain.model.GlobalStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "stats_cache")

@Singleton
class CacheManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.cacheDataStore

    companion object {
        private val KEY_DB_HASH = stringPreferencesKey("db_file_hash")
        private val KEY_DB_PATH = stringPreferencesKey("db_file_path")
        private val KEY_LAST_UPDATE = longPreferencesKey("last_cache_update")
        private val KEY_LAST_MESSAGE_TIMESTAMP = longPreferencesKey("last_message_timestamp")
        private val KEY_USER_JID_HASH = stringPreferencesKey("user_jid_hash")
        
        private val KEY_CACHED_GLOBAL_STATS = stringPreferencesKey("cached_global_stats")
        private val KEY_CACHED_CHAT_PREFIX = stringPreferencesKey("cached_chat_")
    }

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
            prefs[KEY_DB_PATH] = filePath
            prefs[KEY_LAST_UPDATE] = System.currentTimeMillis()
        }
    }

    suspend fun isDbChanged(newHash: String): Boolean {
        val stored = dataStore.data.first()[KEY_DB_HASH]
        return stored != newHash
    }

    suspend fun isSameUser(jidHash: String): Boolean {
        val stored = dataStore.data.first()[KEY_USER_JID_HASH]
        return stored == jidHash
    }

    suspend fun saveUserJid(jidHash: String) {
        dataStore.edit { prefs ->
            prefs[KEY_USER_JID_HASH] = jidHash
        }
    }

    suspend fun getLastMessageTimestamp(): Long {
        return dataStore.data.first()[KEY_LAST_MESSAGE_TIMESTAMP] ?: 0L
    }

    suspend fun saveLastMessageTimestamp(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_MESSAGE_TIMESTAMP] = timestamp
        }
    }

    suspend fun clearCache() {
        dataStore.edit { it.clear() }
    }

    suspend fun getLastCacheTime(): Long {
        return dataStore.data.first()[KEY_LAST_UPDATE] ?: 0L
    }

    fun getGlobalStatsFlow(): Flow<GlobalStats?> = dataStore.data.map { prefs ->
        val json = prefs[KEY_CACHED_GLOBAL_STATS] ?: return@map null
        try {
            parseGlobalStatsJson(json)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveGlobalStats(stats: GlobalStats) {
        val json = globalStatsToJson(stats)
        dataStore.edit { prefs ->
            prefs[KEY_CACHED_GLOBAL_STATS] = json
            prefs[KEY_LAST_UPDATE] = System.currentTimeMillis()
        }
    }

    fun getChatStatsFlow(chatId: Long): Flow<ChatStats?> = dataStore.data.map { prefs ->
        val json = prefs[stringPreferencesKey("cached_chat_$chatId")] ?: return@map null
        try {
            parseChatStatsJson(json)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun saveChatStats(chatId: Long, stats: ChatStats) {
        val json = chatStatsToJson(stats)
        dataStore.edit { prefs ->
            prefs[stringPreferencesKey("cached_chat_$chatId")] = json
        }
    }

    // Simple JSON serialization (without external library)
    private fun globalStatsToJson(stats: GlobalStats): String {
        return buildString {
            append("{")
            append("\"totalMessages\":${stats.totalMessages},")
            append("\"totalChats\":${stats.totalChats},")
            append("\"totalContacts\":${stats.totalContacts},")
            append("\"totalMediaFiles\":${stats.totalMediaFiles},")
            append("\"oldestMessageDate\":${stats.oldestMessageDate},")
            append("\"mostActiveChat\":\"${stats.mostActiveChat.replace("\"", "\\\"")}\",")
            append("\"mostActiveHour\":${stats.mostActiveHour},")
            append("\"mostActiveDayOfWeek\":${stats.mostActiveDayOfWeek},")
            append("\"averageMessagesPerDay\":${stats.averageMessagesPerDay},")
            append("\"longestStreak\":${stats.longestStreak}")
            append("}")
        }
    }

    private fun parseGlobalStatsJson(json: String): GlobalStats? {
        if (!json.startsWith("{")) return null
        // Minimal parser for key fields
        val totalMessages = json.extractLong("totalMessages") ?: return null
        val totalChats = json.extractLong("totalChats") ?: return null
        val totalContacts = json.extractLong("totalContacts") ?: 0
        val totalMediaFiles = json.extractLong("totalMediaFiles") ?: 0
        val oldestMessageDate = json.extractLong("oldestMessageDate") ?: 0L
        val mostActiveChat = json.extractString("mostActiveChat") ?: ""
        val mostActiveHour = json.extractInt("mostActiveHour") ?: 0
        val mostActiveDayOfWeek = json.extractInt("mostActiveDayOfWeek") ?: 0
        val averageMessagesPerDay = json.extractFloat("averageMessagesPerDay") ?: 0f
        val longestStreak = json.extractInt("longestStreak") ?: 0

        return GlobalStats(
            totalMessages = totalMessages.toInt(),
            totalChats = totalChats.toInt(),
            totalContacts = totalContacts.toInt(),
            totalMediaFiles = totalMediaFiles.toInt(),
            oldestMessageDate = oldestMessageDate,
            mostActiveChat = mostActiveChat,
            mostActiveHour = mostActiveHour,
            mostActiveDayOfWeek = mostActiveDayOfWeek,
            averageMessagesPerDay = averageMessagesPerDay,
            longestStreak = longestStreak
        )
    }

    private fun chatStatsToJson(stats: ChatStats): String {
        return buildString {
            append("{\"chatId\":${stats.chatId},")
            append("\"totalMessages\":${stats.totalMessages},")
            append("\"contactName\":\"${stats.contactName.replace("\"", "\\\"")}\",")
            append("\"mediaCount\":${stats.mediaCount}")
            append("}")
        }
    }

    private fun parseChatStatsJson(json: String): ChatStats? {
        val chatId = json.extractLong("chatId") ?: return null
        val totalMessages = json.extractLong("totalMessages")?.toInt() ?: return null
        val contactName = json.extractString("contactName") ?: return null
        val mediaCount = json.extractInt("mediaCount") ?: 0

        return ChatStats(
            chatId = chatId,
            contactName = contactName,
            jid = "",
            isGroup = false,
            totalMessages = totalMessages,
            sentByMe = 0,
            receivedFromThem = 0,
            sentRatio = 0f,
            firstMessageDate = 0L,
            lastMessageDate = 0L,
            averageResponseTimeMs = 0L,
            iStartedConversations = 0,
            theyStartedConversations = 0,
            longestMessage = "",
            averageMessageLength = 0f,
            mediaCount = mediaCount,
            audioCount = 0,
            videoCount = 0,
            hourlyDistribution = emptyList(),
            weeklyDistribution = emptyList(),
            monthlyTrend = emptyList(),
            topWords = emptyList(),
            topEmojis = emptyList(),
            senderBreakdown = emptyList(),
            conversationStarterBreakdown = emptyList()
        )
    }

    private fun String.extractLong(key: String): Long? {
        val regex = "\"$key\":(\\d+)".toRegex()
        return regex.find(this)?.groupValues?.get(1)?.toLongOrNull()
    }

    private fun String.extractInt(key: String): Int? {
        val regex = "\"$key\":(-?\\d+)".toRegex()
        return regex.find(this)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun String.extractFloat(key: String): Float? {
        val regex = "\"$key\":([\\d.]+)".toRegex()
        return regex.find(this)?.groupValues?.get(1)?.toFloatOrNull()
    }

    private fun String.extractString(key: String): String? {
        val regex = "\"$key\":\"([^\"]*)\"".toRegex()
        return regex.find(this)?.groupValues?.get(1)
    }
}
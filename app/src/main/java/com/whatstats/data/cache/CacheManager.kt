package com.whatstats.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whatstats.data.db.ActiveDatabaseProvider
import com.whatstats.data.db.model.ChatListItem
import com.whatstats.data.db.model.DayCount
import com.whatstats.data.db.model.DayOfMonthCount
import com.whatstats.data.db.model.DomainStat
import com.whatstats.data.db.model.EmojiStat
import com.whatstats.data.db.model.HourlyCount
import com.whatstats.data.db.model.LinkStat
import com.whatstats.data.db.model.MonthOfYearCount
import com.whatstats.data.db.model.MonthlyCount
import com.whatstats.data.db.model.SenderStat
import com.whatstats.data.db.model.StickerStat
import com.whatstats.data.db.model.WordFrequency
import com.whatstats.data.db.model.YearTrendCount
import com.whatstats.domain.model.ChatStats
import com.whatstats.domain.model.GlobalStats
import com.whatstats.domain.model.SlowChatStats
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rootCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "stats_cache_root")
private val Context.liteCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "stats_cache_lite")

private val cacheJson =
    Json {
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
    val mostActiveChatId: Long? = null,
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
    val topEmojis: List<CachedEmojiStat> = emptyList(),
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
    val longestMessage: String = "",
    val sentEmojis: Int = 0,
    val receivedEmojis: Int = 0,
)

@Serializable
private data class CachedQuickChatStats(
    val contactName: String,
    val jid: String,
    val isGroup: Boolean,
    val totalMessages: Int,
    val sentByMe: Int,
    val receivedFromThem: Int,
    val sentWords: Int = 0,
    val receivedWords: Int = 0,
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
    val monthlyTrend: List<CachedMonthlyCount> = emptyList(),
)

/**
 * Modello serializzabile per la cache di HourlyCount.
 * Mantiene il breakdown dei mittenti per non perdere il dettaglio utenti nelle nuvolette dell'Activity Card.
 */
@Serializable
private data class CachedHourlyCount(
    val hour: Int,
    val count: Int,
    val senderBreakdown: List<CachedSenderStat> = emptyList(),
)

/**
 * Modello serializzabile per la cache di DayCount.
 * Mantiene il breakdown dei mittenti per non perdere il dettaglio utenti nelle nuvolette dell'Activity Card.
 */
@Serializable
private data class CachedDayCount(
    val dayOfWeek: Int,
    val count: Int,
    val senderBreakdown: List<CachedSenderStat> = emptyList(),
)

/**
 * Modello serializzabile per la cache di DayOfMonthCount.
 * Mantiene il breakdown dei mittenti per non perdere il dettaglio utenti nelle nuvolette dell'Activity Card.
 */
@Serializable
private data class CachedDayOfMonthCount(
    val day: Int,
    val count: Int,
    val senderBreakdown: List<CachedSenderStat> = emptyList(),
)

/**
 * Modello serializzabile per la cache di MonthOfYearCount.
 * Mantiene il breakdown dei mittenti per non perdere il dettaglio utenti nelle nuvolette dell'Activity Card.
 */
@Serializable
private data class CachedMonthOfYearCount(
    val month: Int,
    val count: Int,
    val senderBreakdown: List<CachedSenderStat> = emptyList(),
)

/**
 * Modello serializzabile per la cache di YearTrendCount.
 * Mantiene il breakdown dei mittenti per non perdere il dettaglio utenti nelle nuvolette dell'Activity Card.
 */
@Serializable
private data class CachedYearTrendCount(
    val year: Int,
    val count: Int,
    val senderBreakdown: List<CachedSenderStat> = emptyList(),
)

/**
 * Modello serializzabile per la cache di MonthlyCount.
 * Mantiene il breakdown dei mittenti per non perdere il dettaglio utenti nelle nuvolette dell'Activity Card.
 */
@Serializable
private data class CachedMonthlyCount(
    val month: String,
    val count: Int,
    val senderBreakdown: List<CachedSenderStat> = emptyList(),
)

@Serializable private data class CachedWordFrequency(val word: String, val frequency: Int)

@Serializable private data class CachedEmojiStat(val emoji: String, val count: Int)

@Serializable private data class CachedSenderStat(val jid: String, val displayName: String, val count: Int)

@Serializable private data class CachedLinkStat(val jid: String, val displayName: String?, val totalLinks: Int, val mediaCount: Int)

@Serializable private data class CachedDomainStat(val domain: String, val count: Int)

@Serializable private data class CachedMediaPageStats(
    val linkStats: List<CachedLinkStat> = emptyList(),
    val topDomains: List<CachedDomainStat> = emptyList(),
)

@Serializable private data class CachedStickerStat(val filePath: String, val fileName: String, val count: Int)

@Serializable
internal data class CachedChatListItem(
    val chatId: Long,
    val jid: String,
    val displayName: String,
    val isGroup: Boolean,
    val totalMessages: Int,
    val lastMessageTimestamp: Long,
    val importTimestamp: Long? = null,
    val avatarPath: String? = null,
)

@Serializable
internal data class CachedChatList(val chats: List<CachedChatListItem>)

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
    val totalMessageCount: Int,
)

/**
 * Indice mensile delle frequenze per supportare le query con filtri temporali
 * senza dover rileggere l'intero database.
 * Chiave di [monthlyWords] e [monthlyEmojis]: "YYYY-MM".
 */
data class MonthlyWordIndexData(
    val monthlyWords: Map<String, Map<String, Int>>,
    val monthlyEmojis: Map<String, Map<String, Int>>,
    val lastProcessedTimestamp: Long,
)

// ── Conversioni domain ↔ cache ───────────────────────────────────

/**
 * Converte GlobalStats nel rispettivo modello serializzabile per la cache.
 * Include il mapping dei breakdown per mittente in tutte le distribuzioni orarie, settimanali,
 * giornaliere, mensili e annuali per prevenire perdita dei dettagli utente.
 */
private fun GlobalStats.toCached() =
    CachedGlobalStats(
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
        hourlyDistribution =
            hourlyDistribution.map { it ->
                CachedHourlyCount(
                    hour = it.hour,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> CachedSenderStat(s.jid, s.displayName, s.count) },
                )
            },
        weeklyDistribution =
            weeklyDistribution.map { it ->
                CachedDayCount(
                    dayOfWeek = it.dayOfWeek,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> CachedSenderStat(s.jid, s.displayName, s.count) },
                )
            },
        dayOfMonthDistribution =
            dayOfMonthDistribution.map { it ->
                CachedDayOfMonthCount(
                    day = it.day,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> CachedSenderStat(s.jid, s.displayName, s.count) },
                )
            },
        monthOfYearDistribution =
            monthOfYearDistribution.map { it ->
                CachedMonthOfYearCount(
                    month = it.month,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> CachedSenderStat(s.jid, s.displayName, s.count) },
                )
            },
        yearTrendDistribution =
            yearTrendDistribution.map { it ->
                CachedYearTrendCount(
                    year = it.year,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> CachedSenderStat(s.jid, s.displayName, s.count) },
                )
            },
        topWords = topWords.map { CachedWordFrequency(it.word, it.frequency) },
        topEmojis = topEmojis.map { CachedEmojiStat(it.emoji, it.count) },
    )

/**
 * Converte il modello CachedGlobalStats nel rispettivo domain model GlobalStats.
 * Mappa all'indietro tutti i breakdown per mittente salvati per l'uso immediato nell'interfaccia.
 */
private fun CachedGlobalStats.toDomain() =
    GlobalStats(
        totalMessages = totalMessages,
        totalChats = totalChats,
        totalContacts = totalContacts,
        totalMediaFiles = totalMediaFiles,
        oldestMessageDate = oldestMessageDate,
        mostActiveChat = mostActiveChat,
        mostActiveChatId = mostActiveChatId,
        mostActiveHour = mostActiveHour,
        mostActiveDayOfWeek = mostActiveDayOfWeek,
        averageMessagesPerDay = averageMessagesPerDay,
        longestStreak = longestStreak,
        hourlyDistribution =
            hourlyDistribution.map { it ->
                HourlyCount(
                    hour = it.hour,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> SenderStat(s.jid, s.displayName, s.count) },
                )
            },
        weeklyDistribution =
            weeklyDistribution.map { it ->
                DayCount(
                    dayOfWeek = it.dayOfWeek,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> SenderStat(s.jid, s.displayName, s.count) },
                )
            },
        dayOfMonthDistribution =
            dayOfMonthDistribution.map { it ->
                DayOfMonthCount(
                    day = it.day,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> SenderStat(s.jid, s.displayName, s.count) },
                )
            },
        monthOfYearDistribution =
            monthOfYearDistribution.map { it ->
                MonthOfYearCount(
                    month = it.month,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> SenderStat(s.jid, s.displayName, s.count) },
                )
            },
        yearTrendDistribution =
            yearTrendDistribution.map { it ->
                YearTrendCount(
                    year = it.year,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> SenderStat(s.jid, s.displayName, s.count) },
                )
            },
        topWords = topWords.map { WordFrequency(it.word, it.frequency) },
        topEmojis = topEmojis.map { EmojiStat(it.emoji, it.count) },
    )

private fun SlowChatStats.toCached() =
    CachedSlowChatStats(
        topWords = topWords.map { CachedWordFrequency(it.word, it.frequency) },
        topEmojis = topEmojis.map { CachedEmojiStat(it.emoji, it.count) },
        topStickers = topStickers.map { CachedStickerStat(it.filePath, it.fileName, it.count) },
        senderBreakdown = senderBreakdown.map { CachedSenderStat(it.jid, it.displayName, it.count) },
        conversationStarterBreakdown = conversationStarterBreakdown.map { CachedSenderStat(it.jid, it.displayName, it.count) },
        averageResponseTimeMs = averageResponseTimeMs,
        iStartedConversations = iStartedConversations,
        theyStartedConversations = theyStartedConversations,
        longestMessage = longestMessage,
        sentEmojis = sentEmojis,
        receivedEmojis = receivedEmojis,
    )

private fun CachedSlowChatStats.toDomain() =
    SlowChatStats(
        topWords = topWords.map { WordFrequency(it.word, it.frequency) },
        topEmojis = topEmojis.map { EmojiStat(it.emoji, it.count) },
        topStickers = topStickers.map { StickerStat(it.filePath, it.fileName, it.count) },
        senderBreakdown = senderBreakdown.map { SenderStat(it.jid, it.displayName, it.count) },
        conversationStarterBreakdown = conversationStarterBreakdown.map { SenderStat(it.jid, it.displayName, it.count) },
        averageResponseTimeMs = averageResponseTimeMs,
        iStartedConversations = iStartedConversations,
        theyStartedConversations = theyStartedConversations,
        longestMessage = longestMessage,
        sentEmojis = sentEmojis,
        receivedEmojis = receivedEmojis,
    )

/**
 * Converte ChatStats nel modello serializzabile rapido CachedQuickChatStats per la cache.
 * Include il mapping dei breakdown dei mittenti in tutte le distribuzioni temporali.
 */
private fun ChatStats.toQuickCached() =
    CachedQuickChatStats(
        contactName = contactName,
        jid = jid,
        isGroup = isGroup,
        totalMessages = totalMessages,
        sentByMe = sentByMe,
        receivedFromThem = receivedFromThem,
        sentWords = sentWords,
        receivedWords = receivedWords,
        sentRatio = sentRatio,
        firstMessageDate = firstMessageDate,
        lastMessageDate = lastMessageDate,
        averageMessageLength = averageMessageLength,
        mediaCount = mediaCount,
        audioCount = audioCount,
        videoCount = videoCount,
        hourlyDistribution =
            hourlyDistribution.map { it ->
                CachedHourlyCount(
                    hour = it.hour,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> CachedSenderStat(s.jid, s.displayName, s.count) },
                )
            },
        weeklyDistribution =
            weeklyDistribution.map { it ->
                CachedDayCount(
                    dayOfWeek = it.dayOfWeek,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> CachedSenderStat(s.jid, s.displayName, s.count) },
                )
            },
        dayOfMonthDistribution =
            dayOfMonthDistribution.map { it ->
                CachedDayOfMonthCount(
                    day = it.day,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> CachedSenderStat(s.jid, s.displayName, s.count) },
                )
            },
        monthOfYearDistribution =
            monthOfYearDistribution.map { it ->
                CachedMonthOfYearCount(
                    month = it.month,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> CachedSenderStat(s.jid, s.displayName, s.count) },
                )
            },
        yearTrendDistribution =
            yearTrendDistribution.map { it ->
                CachedYearTrendCount(
                    year = it.year,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> CachedSenderStat(s.jid, s.displayName, s.count) },
                )
            },
        monthlyTrend =
            monthlyTrend.map { it ->
                CachedMonthlyCount(
                    month = it.month,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> CachedSenderStat(s.jid, s.displayName, s.count) },
                )
            },
    )

/**
 * Converte il modello rapido CachedQuickChatStats nel rispettivo domain model ChatStats.
 * Ripristina i breakdown dei mittenti per ripopolare correttamente le nuvolette dei grafici.
 */
private fun CachedQuickChatStats.toDomain(chatId: Long) =
    ChatStats(
        chatId = chatId,
        contactName = contactName,
        jid = jid,
        isGroup = isGroup,
        totalMessages = totalMessages,
        sentByMe = sentByMe,
        receivedFromThem = receivedFromThem,
        sentWords = sentWords,
        receivedWords = receivedWords,
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
        hourlyDistribution =
            hourlyDistribution.map { it ->
                HourlyCount(
                    hour = it.hour,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> SenderStat(s.jid, s.displayName, s.count) },
                )
            },
        weeklyDistribution =
            weeklyDistribution.map { it ->
                DayCount(
                    dayOfWeek = it.dayOfWeek,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> SenderStat(s.jid, s.displayName, s.count) },
                )
            },
        dayOfMonthDistribution =
            dayOfMonthDistribution.map { it ->
                DayOfMonthCount(
                    day = it.day,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> SenderStat(s.jid, s.displayName, s.count) },
                )
            },
        monthOfYearDistribution =
            monthOfYearDistribution.map { it ->
                MonthOfYearCount(
                    month = it.month,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> SenderStat(s.jid, s.displayName, s.count) },
                )
            },
        yearTrendDistribution =
            yearTrendDistribution.map { it ->
                YearTrendCount(
                    year = it.year,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> SenderStat(s.jid, s.displayName, s.count) },
                )
            },
        monthlyTrend =
            monthlyTrend.map { it ->
                MonthlyCount(
                    month = it.month,
                    count = it.count,
                    senderBreakdown = it.senderBreakdown.map { s -> SenderStat(s.jid, s.displayName, s.count) },
                )
            },
        topWords = emptyList(),
        senderBreakdown = emptyList(),
        conversationStarterBreakdown = emptyList(),
    )

// ── CacheManager ─────────────────────────────────────────────────

@Singleton
class CacheManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val activeDb: ActiveDatabaseProvider,
    ) {
        private val dataStore: DataStore<Preferences>
            get() = if (activeDb.isLiteMode) context.liteCacheDataStore else context.rootCacheDataStore

        private val ns: String
            get() = if (activeDb.isLiteMode) "lite" else "root"

        companion object {
            private val KEY_DB_HASH = stringPreferencesKey("db_file_hash")
            private val KEY_LAST_UPDATE = longPreferencesKey("last_cache_update")

            // v4: bump per includere il senderBreakdown nelle distribuzioni temporali
            private val KEY_GLOBAL_STATS = stringPreferencesKey("global_stats_v4")

            // v3: bump per rigenerare la cache con pool da 50 words/emoji
            private val KEY_GLOBAL_SLOW = stringPreferencesKey("global_slow_v4")
            private val KEY_CHAT_LIST = stringPreferencesKey("chat_list_v1")

            /**
             * Soglia minima di variazione (in byte) della dimensione del DB live
             * per considerare il database realmente cambiato. Filtra i falsi positivi
             * causati da auto-checkpoint SQLite (es. WhatsApp in background).
             * 128 KB ≈ centinaia di messaggi dopo checkpoint, allineato ai backup notturni.
             */
            private const val SIGNATURE_CHANGE_THRESHOLD = 131_072L
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

        suspend fun saveDbHash(
            hash: String,
            filePath: String,
        ) {
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

        /** Confronta l'hash sempre contro il DataStore Root, indipendentemente dal modo corrente.
         *  Per le signature basate su dimensione file (live DB), usa una soglia di 128 KB
         *  per evitare falsi positivi da auto-checkpoint SQLite. */
        suspend fun isRootDbChanged(newHash: String): Boolean {
            val stored =
                context.rootCacheDataStore.data.first()[KEY_DB_HASH]
                    ?: return true // primo avvio, nessun hash salvato
            // Se entrambi sono numerici puri (signature = dimensione file live DB),
            // confronta con soglia per ignorare piccoli checkpoint
            val newSize = newHash.toLongOrNull()
            val storedSize = stored.toLongOrNull()
            if (newSize != null && storedSize != null) {
                return kotlin.math.abs(newSize - storedSize) >= SIGNATURE_CHANGE_THRESHOLD
            }
            // Fallback: confronto esatto per signature crypt14 (size_lastModified)
            return stored != newHash
        }

        /** Salva l'hash sempre nel DataStore Root. */
        suspend fun saveRootDbHash(hash: String) {
            context.rootCacheDataStore.edit { prefs ->
                prefs[KEY_DB_HASH] = hash
                prefs[KEY_LAST_UPDATE] = System.currentTimeMillis()
            }
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

        suspend fun getGlobalSlowStats(): Triple<List<WordFrequency>, List<EmojiStat>, List<StickerStat>>? {
            val json = dataStore.data.first()[KEY_GLOBAL_SLOW] ?: return null
            return try {
                val cached = cacheJson.decodeFromString<CachedSlowChatStats>(json)
                Triple(
                    cached.topWords.map { WordFrequency(it.word, it.frequency) },
                    cached.topEmojis.map { EmojiStat(it.emoji, it.count) },
                    cached.topStickers.map { StickerStat(it.filePath, it.fileName, it.count) },
                )
            } catch (e: Exception) {
                null
            }
        }

        suspend fun saveGlobalSlowStats(
            topWords: List<WordFrequency>,
            topEmojis: List<EmojiStat>,
            topStickers: List<StickerStat>,
        ) {
            val cached =
                CachedSlowChatStats(
                    topWords = topWords.map { CachedWordFrequency(it.word, it.frequency) },
                    topEmojis = topEmojis.map { CachedEmojiStat(it.emoji, it.count) },
                    topStickers = topStickers.map { CachedStickerStat(it.filePath, it.fileName, it.count) },
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

        suspend fun saveChatSlowStats(
            chatId: Long,
            stats: SlowChatStats,
        ) {
            val key = stringPreferencesKey("chat_slow_${chatId}_v3")
            dataStore.edit { prefs ->
                prefs[key] = cacheJson.encodeToString(stats.toCached())
            }
        }

        // ── Chat Quick Stats (per chat) ──────────────────────────────

        /**
         * Recupera le statistiche veloci cached per una singola chat.
         * Aggiornato a v4 per supportare il breakdown mittenti nelle distribuzioni.
         */
        suspend fun getChatQuickStats(chatId: Long): ChatStats? {
            val key = stringPreferencesKey("chat_quick_${chatId}_v4")
            val json = dataStore.data.first()[key] ?: return null
            return try {
                cacheJson.decodeFromString<CachedQuickChatStats>(json).toDomain(chatId)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Salva le statistiche veloci per una singola chat nella cache persistente.
         * Aggiornato a v4 per supportare il breakdown mittenti nelle distribuzioni.
         */
        suspend fun saveChatQuickStats(
            chatId: Long,
            stats: ChatStats,
        ) {
            val key = stringPreferencesKey("chat_quick_${chatId}_v4")
            dataStore.edit { prefs ->
                prefs[key] = cacheJson.encodeToString(stats.toQuickCached())
            }
        }

        // ── Media & Link Stats (global) ───────────────────────────────

        suspend fun getGlobalMediaStats(): Pair<List<LinkStat>, List<DomainStat>>? {
            val key = stringPreferencesKey("media_global_v3")
            val json = dataStore.data.first()[key] ?: return null
            return try {
                val cached = cacheJson.decodeFromString<CachedMediaPageStats>(json)
                Pair(
                    cached.linkStats.map { LinkStat(it.jid, it.displayName, it.totalLinks, it.mediaCount) },
                    cached.topDomains.map { DomainStat(it.domain, it.count) },
                )
            } catch (e: Exception) {
                null
            }
        }

        suspend fun saveGlobalMediaStats(
            linkStats: List<LinkStat>,
            topDomains: List<DomainStat>,
        ) {
            val key = stringPreferencesKey("media_global_v3")
            val cached =
                CachedMediaPageStats(
                    linkStats = linkStats.map { CachedLinkStat(it.jid, it.displayName, it.totalLinks, it.mediaCount) },
                    topDomains = topDomains.map { CachedDomainStat(it.domain, it.count) },
                )
            dataStore.edit { prefs -> prefs[key] = cacheJson.encodeToString(cached) }
        }

        // ── Media & Link Stats (per chat) ────────────────────────────

        suspend fun getChatMediaStats(chatId: Long): Pair<List<LinkStat>, List<DomainStat>>? {
            val key = stringPreferencesKey("media_chat_${chatId}_v3")
            val json = dataStore.data.first()[key] ?: return null
            return try {
                val cached = cacheJson.decodeFromString<CachedMediaPageStats>(json)
                Pair(
                    cached.linkStats.map { LinkStat(it.jid, it.displayName, it.totalLinks, it.mediaCount) },
                    cached.topDomains.map { DomainStat(it.domain, it.count) },
                )
            } catch (e: Exception) {
                null
            }
        }

        suspend fun saveChatMediaStats(
            chatId: Long,
            linkStats: List<LinkStat>,
            topDomains: List<DomainStat>,
        ) {
            val key = stringPreferencesKey("media_chat_${chatId}_v3")
            val cached =
                CachedMediaPageStats(
                    linkStats = linkStats.map { CachedLinkStat(it.jid, it.displayName, it.totalLinks, it.mediaCount) },
                    topDomains = topDomains.map { CachedDomainStat(it.domain, it.count) },
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
            } catch (e: Exception) {
                null
            }
        }

        suspend fun saveChatStickerStats(
            chatId: Long,
            stickers: List<StickerStat>,
        ) {
            val key = stringPreferencesKey("sticker_chat_${chatId}_v1")
            val cached = stickers.map { CachedStickerStat(it.filePath, it.fileName, it.count) }
            dataStore.edit { prefs -> prefs[key] = cacheJson.encodeToString(cached) }
        }

        // ── Chat List ─────────────────────────────────────────────────

        suspend fun getChatList(): List<ChatListItem>? {
            val json = dataStore.data.first()[KEY_CHAT_LIST] ?: return null
            return try {
                val wrapper = cacheJson.decodeFromString<CachedChatList>(json)
                wrapper.chats.map {
                    ChatListItem(
                        chatId = it.chatId,
                        jid = it.jid,
                        displayName = it.displayName,
                        isGroup = it.isGroup,
                        totalMessages = it.totalMessages,
                        lastMessageTimestamp = it.lastMessageTimestamp,
                        importTimestamp = it.importTimestamp,
                        avatarPath = it.avatarPath,
                    )
                }
            } catch (e: Exception) {
                null
            }
        }

        suspend fun saveChatList(chats: List<ChatListItem>) {
            val cached =
                chats.map {
                    CachedChatListItem(
                        chatId = it.chatId,
                        jid = it.jid,
                        displayName = it.displayName,
                        isGroup = it.isGroup,
                        totalMessages = it.totalMessages,
                        lastMessageTimestamp = it.lastMessageTimestamp,
                        importTimestamp = it.importTimestamp,
                        avatarPath = it.avatarPath,
                    )
                }
            val json = cacheJson.encodeToString(CachedChatList(cached))
            dataStore.edit { prefs -> prefs[KEY_CHAT_LIST] = json }
        }

        @Volatile var globalWordIndex: WordFrequencyIndexData? = null

        @Volatile var monthlyWordIndex: MonthlyWordIndexData? = null
        private val chatWordIndexCache = java.util.concurrent.ConcurrentHashMap<Long, WordFrequencyIndexData>()

        /** Traccia il namespace dell'ultima cache in-memory. Quando cambia, azzera le variabili volatili. */
        private var lastInMemoryNs: String? = null

        private fun ensureInMemoryNs() {
            val current = ns
            if (lastInMemoryNs != null && lastInMemoryNs != current) {
                globalWordIndex = null
                monthlyWordIndex = null
                chatWordIndexCache.clear()
                mediaFileIndex = null
            }
            lastInMemoryNs = current
        }

        // filesDir: persistente tra sessioni (costoso da ricostruire)
        private val globalWordIndexFile get() = java.io.File(context.filesDir, "${ns}_word_index_global.gz")
        private val monthlyWordIndexFile get() = java.io.File(context.filesDir, "${ns}_word_index_monthly.gz")

        private fun chatWordIndexFile(chatId: Long) = java.io.File(context.cacheDir, "${ns}_word_index_chat_$chatId.gz")

        fun loadGlobalWordIndexFromDisk(): WordFrequencyIndexData? {
            ensureInMemoryNs()
            globalWordIndex?.let { return it }
            val file = globalWordIndexFile
            if (!file.exists() || file.length() == 0L) return null
            return try {
                parseWordIndexFile(file).also { globalWordIndex = it }
            } catch (_: Exception) {
                null
            }
        }

        fun saveGlobalWordIndexToDisk(data: WordFrequencyIndexData) {
            ensureInMemoryNs()
            globalWordIndex = data
            try {
                val tmp = java.io.File(context.filesDir, "${ns}_word_index_global.tmp")
                writeWordIndexFile(tmp, data)
                tmp.renameTo(globalWordIndexFile)
            } catch (_: Exception) {
            }
        }

        fun loadChatWordIndexFromDisk(chatId: Long): WordFrequencyIndexData? {
            ensureInMemoryNs()
            chatWordIndexCache[chatId]?.let { return it }
            val file = chatWordIndexFile(chatId)
            if (!file.exists() || file.length() == 0L) return null
            return try {
                parseWordIndexFile(file).also { chatWordIndexCache[chatId] = it }
            } catch (_: Exception) {
                null
            }
        }

        fun saveChatWordIndexToDisk(
            chatId: Long,
            data: WordFrequencyIndexData,
        ) {
            ensureInMemoryNs()
            chatWordIndexCache[chatId] = data
            try {
                val tmp = java.io.File(context.cacheDir, "${ns}_word_index_chat_$chatId.tmp")
                writeWordIndexFile(tmp, data)
                tmp.renameTo(chatWordIndexFile(chatId))
            } catch (_: Exception) {
            }
        }

        fun loadMonthlyWordIndexFromDisk(): MonthlyWordIndexData? {
            ensureInMemoryNs()
            monthlyWordIndex?.let { return it }
            val file = monthlyWordIndexFile
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
                MonthlyWordIndexData(mWords, mEmojis, lastTs).also { monthlyWordIndex = it }
            } catch (_: Exception) {
                null
            }
        }

        fun saveMonthlyWordIndexToDisk(data: MonthlyWordIndexData) {
            monthlyWordIndex = data
            try {
                val tmp = java.io.File(context.filesDir, "${ns}_word_index_monthly.tmp")
                java.util.zip.GZIPOutputStream(tmp.outputStream().buffered()).bufferedWriter().use { writer ->
                    writer.write("LAST_TS\t${data.lastProcessedTimestamp}")
                    writer.newLine()
                    data.monthlyWords.forEach { (month, freqs) ->
                        freqs.forEach { (word, count) ->
                            writer.write("$month\tW\t$word\t$count")
                            writer.newLine()
                        }
                    }
                    data.monthlyEmojis.forEach { (month, freqs) ->
                        freqs.forEach { (emoji, count) ->
                            writer.write("$month\tE\t$emoji\t$count")
                            writer.newLine()
                        }
                    }
                }
                tmp.renameTo(monthlyWordIndexFile)
            } catch (_: Exception) {
            }
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
                        parts.size >= 2 && parts[0] == "LAST_TS" -> lastTs = parts[1].toLongOrNull() ?: 0L
                        parts.size >= 2 && parts[0] == "MSG_COUNT" -> msgCount = parts[1].toIntOrNull() ?: 0
                        parts.size == 3 && parts[0] == "W" -> wordFreqs[parts[1]] = parts[2].toIntOrNull() ?: 0
                        parts.size == 3 && parts[0] == "E" -> emojiFreqs[parts[1]] = parts[2].toIntOrNull() ?: 0
                    }
                }
            }
            return WordFrequencyIndexData(wordFreqs, emojiFreqs, lastTs, msgCount)
        }

        private fun writeWordIndexFile(
            file: java.io.File,
            data: WordFrequencyIndexData,
        ) {
            java.util.zip.GZIPOutputStream(file.outputStream().buffered()).bufferedWriter().use { writer ->
                writer.write("LAST_TS\t${data.lastProcessedTimestamp}")
                writer.newLine()
                writer.write("MSG_COUNT\t${data.totalMessageCount}")
                writer.newLine()
                data.wordFrequencies.forEach { (word, count) ->
                    writer.write("W\t$word\t$count")
                    writer.newLine()
                }
                data.emojiFrequencies.forEach { (emoji, count) ->
                    writer.write("E\t$emoji\t$count")
                    writer.newLine()
                }
            }
        }

        // ── Indice filesystem media (cache in-memory + file gzippato) ─────────────────

        @Volatile var mediaFileIndex: Set<String>? = null

        private val mediaIndexFile get() = java.io.File(context.cacheDir, "${ns}_media_index.gz")

        /**
         * Carica l'indice dal file gzippato su disco (se esiste).
         * Dimensione attesa: ~0.5 MB per 50.000 file (prefissi molto ripetuti → alta compressione).
         */
        fun loadMediaIndexFromDisk(): Set<String>? {
            ensureInMemoryNs()
            val file = mediaIndexFile
            if (!file.exists() || file.length() == 0L) return null
            return try {
                java.util.zip.GZIPInputStream(file.inputStream().buffered()).bufferedReader().use { reader ->
                    reader.lines().collect(java.util.stream.Collectors.toSet())
                }
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Salva l'indice su disco in formato gzippato (operazione in background).
         */
        fun saveMediaIndexToDisk(index: Set<String>) {
            try {
                java.util.zip.GZIPOutputStream(mediaIndexFile.outputStream().buffered()).bufferedWriter().use { writer ->
                    index.forEach {
                        writer.write(it)
                        writer.newLine()
                    }
                }
            } catch (_: Exception) {
                // non bloccante
            }
        }

        // ── Invalidazione ────────────────────────────────────────────

        suspend fun clearCache(resetWordIndexes: Boolean = false) {
            // Cancella solo il DataStore del namespace corrente (root o lite)
            dataStore.edit { it.clear() }
            mediaFileIndex = null
            mediaIndexFile.delete()
            globalWordIndex = null
            monthlyWordIndex = null
            chatWordIndexCache.clear()
            if (resetWordIndexes) {
                globalWordIndexFile.delete()
                monthlyWordIndexFile.delete()
                val prefix = "${ns}_word_index_chat_"
                context.cacheDir.listFiles()
                    ?.filter { it.name.startsWith(prefix) && it.name.endsWith(".gz") }
                    ?.forEach { it.delete() }
            }
        }

        suspend fun clearRootCache(resetWordIndexes: Boolean = false) {
            context.rootCacheDataStore.edit { it.clear() }
            java.io.File(context.cacheDir, "root_media_index.gz").delete()
            // Se siamo attivamente nel namespace root in memoria, resettiamo anche le var
            if (!activeDb.isLiteMode) {
                mediaFileIndex = null
                globalWordIndex = null
                monthlyWordIndex = null
                chatWordIndexCache.clear()
            }
            if (resetWordIndexes) {
                java.io.File(context.filesDir, "root_word_index_global.gz").delete()
                java.io.File(context.filesDir, "root_word_index_monthly.gz").delete()
                val prefix = "root_word_index_chat_"
                context.cacheDir.listFiles()
                    ?.filter { it.name.startsWith(prefix) && it.name.endsWith(".gz") }
                    ?.forEach { it.delete() }
            }
        }

        suspend fun clearLiteCache(resetWordIndexes: Boolean = false) {
            context.liteCacheDataStore.edit { it.clear() }
            java.io.File(context.cacheDir, "lite_media_index.gz").delete()
            // Se siamo attivamente nel namespace lite in memoria, resettiamo anche le var
            if (activeDb.isLiteMode) {
                mediaFileIndex = null
                globalWordIndex = null
                monthlyWordIndex = null
                chatWordIndexCache.clear()
            }
            if (resetWordIndexes) {
                java.io.File(context.filesDir, "lite_word_index_global.gz").delete()
                java.io.File(context.filesDir, "lite_word_index_monthly.gz").delete()
                val prefix = "lite_word_index_chat_"
                context.cacheDir.listFiles()
                    ?.filter { it.name.startsWith(prefix) && it.name.endsWith(".gz") }
                    ?.forEach { it.delete() }
            }
        }

        /**
         * Patcha il nome visualizzato di una chat in tutte le cache persistenti in cui compare.
         * Evita di ricalcolare statistiche pesanti solo per un cambio di nome.
         */
        suspend fun patchChatNameInCache(
            chatId: Long,
            jid: String,
            newName: String,
        ) {
            dataStore.edit { prefs ->
                // 1. Patch Quick Chat Stats (v4)
                val chatKey = stringPreferencesKey("chat_quick_${chatId}_v4")
                prefs[chatKey]?.let { json ->
                    try {
                        val cached = cacheJson.decodeFromString<CachedQuickChatStats>(json)
                        val updated = cached.copy(contactName = newName)
                        prefs[chatKey] = cacheJson.encodeToString(updated)
                    } catch (_: Exception) {
                    }
                }

                // 2. Patch Global Stats (v4, se la chat rinominata e' la piu' attiva)
                val globalKey = stringPreferencesKey("global_stats_v4")
                prefs[globalKey]?.let { json ->
                    try {
                        val cached = cacheJson.decodeFromString<CachedGlobalStats>(json)
                        if (cached.mostActiveChat == jid || cached.mostActiveChat.contains(jid.substringBefore("@"))) {
                            val updated = cached.copy(mostActiveChat = newName)
                            prefs[globalKey] = cacheJson.encodeToString(updated)
                        }
                    } catch (_: Exception) {
                    }
                }

                // 3. Patch Media Global Stats
                val mediaKey = stringPreferencesKey("media_global_v3")
                prefs[mediaKey]?.let { json ->
                    try {
                        val cached = cacheJson.decodeFromString<CachedMediaPageStats>(json)
                        val anyChange = cached.linkStats.any { it.jid == jid }
                        if (anyChange) {
                            val updatedList =
                                cached.linkStats.map {
                                    if (it.jid == jid) it.copy(displayName = newName) else it
                                }
                            val updated = cached.copy(linkStats = updatedList)
                            prefs[mediaKey] = cacheJson.encodeToString(updated)
                        }
                    } catch (_: Exception) {
                    }
                }

                // 4. Invalida la Slow Chat Stats cache.
                // Il senderBreakdown in cache ha il nome originale del sender (es. "Matilde Barbaro"),
                // mentre il jid della chat è diverso (es. "Matilde lavoro") — non è possibile fare
                // un patch affidabile per JID comparison. È più corretto invalidare la voce così che
                // getSlowChatStats la ricalcoli da zero, trovando il nome aggiornato in wa_contacts
                // (aggiornato dal passo 5 di LiteDbWriter.renameChat).
                val slowKey = stringPreferencesKey("chat_slow_${chatId}_v3")
                prefs.remove(slowKey)
            }
        }
    }

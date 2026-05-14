package com.whatstat.reborn.ui.screen.chatdetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.db.dao.ChatDao
import com.whatstat.reborn.data.db.dao.ContactDao
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.EmojiStat
import com.whatstat.reborn.data.db.model.SenderStat
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.db.model.WordFrequency
import com.whatstat.reborn.data.preferences.SenderColorOverrideStore
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.domain.model.ChatStats
import com.whatstat.reborn.ui.state.FilterStateHolder
import com.whatstat.reborn.ui.theme.senderColorOverrideKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatDetailViewModel
    @Inject
    constructor(
        private val analyticsRepository: AnalyticsRepository,
        private val chatDao: ChatDao,
        private val contactDao: ContactDao,
        @ApplicationContext private val context: Context,
        private val filterState: FilterStateHolder,
        private val senderColorOverrideStore: SenderColorOverrideStore,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        enum class WordViewMode { AGGREGATED, INDIVIDUAL }

        enum class EmojiViewMode { AGGREGATED, INDIVIDUAL }

        private var chatId: Long = savedStateHandle["chatId"] ?: -1L

        private val avatarDir: File by lazy {
            File(context.filesDir, FileExtractor.AVATAR_DIR_DEST)
        }

        private fun getAvatarPath(jid: String): String? {
            if (!avatarDir.exists()) return null
            if (jid == "me@s.whatsapp.net") {
                val me = File(avatarDir, "me.jpg")
                return if (me.exists()) me.absolutePath else null
            }
            val avatarFile = File(avatarDir, jid + ".jpg")
            return if (avatarFile.exists()) avatarFile.absolutePath else null
        }

        private val _chatStats = MutableStateFlow<ChatStats?>(null)
        val chatStats: StateFlow<ChatStats?> = _chatStats.asStateFlow()

        val isLiteMode = analyticsRepository.isLiteMode

        val senderColorArgbOverrides: StateFlow<Map<String, Int>> = senderColorOverrideStore.overrides

        // Nome + avatar caricati immediatamente prima delle stats complete
        data class HeaderInfo(val name: String, val jid: String, val isGroup: Boolean, val avatarPath: String?)

        private val _headerInfo = MutableStateFlow<HeaderInfo?>(null)
        val headerInfo: StateFlow<HeaderInfo?> = _headerInfo.asStateFlow()

        private val _isLoading = MutableStateFlow(true)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _isLoadingSlow = MutableStateFlow(true)
        val isLoadingSlow: StateFlow<Boolean> = _isLoadingSlow.asStateFlow()

        private val _loadingProgress = MutableStateFlow<Int?>(null)
        val loadingProgress: StateFlow<Int?> = _loadingProgress.asStateFlow()

        private val _loadingProgressSlow = MutableStateFlow<Int?>(null)
        val loadingProgressSlow: StateFlow<Int?> = _loadingProgressSlow.asStateFlow()

        private val _error = MutableStateFlow<String?>(null)
        val error: StateFlow<String?> = _error.asStateFlow()

        private val _selectedTimeRange = MutableStateFlow(filterState.chatTimeRange)
        val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

        private val _customDateRange = MutableStateFlow(filterState.chatCustomRange)
        val customDateRange: StateFlow<DateRange?> = _customDateRange.asStateFlow()

        private val _firstMessageTimestamp = MutableStateFlow<Long?>(null)
        val firstMessageTimestamp: StateFlow<Long?> = _firstMessageTimestamp.asStateFlow()

        private val _wordViewMode = MutableStateFlow(WordViewMode.AGGREGATED)
        val wordViewMode: StateFlow<WordViewMode> = _wordViewMode.asStateFlow()

        private val _selectedSenderForWords = MutableStateFlow<SenderStat?>(null)
        val selectedSenderForWords: StateFlow<SenderStat?> = _selectedSenderForWords.asStateFlow()

        private val _topWordsLimit = MutableStateFlow(30) // Default 30 per "Tutti"
        val topWordsLimit: StateFlow<Int> = _topWordsLimit.asStateFlow()

        private val _senderTopWords = MutableStateFlow<List<WordFrequency>>(emptyList())
        val senderTopWords: StateFlow<List<WordFrequency>> = _senderTopWords.asStateFlow()

        private val _isSenderWordsLoading = MutableStateFlow(false)
        val isSenderWordsLoading: StateFlow<Boolean> = _isSenderWordsLoading.asStateFlow()

        // Pool for individual mode words to avoid DB queries on limit changes
        private var senderWordsPool: List<WordFrequency> = emptyList()
        private var senderWordsPoolSender: String? = null
        private var senderWordsPoolStartTime: Long? = null
        private var senderWordsPoolEndTime: Long? = null

        private val _emojiViewMode = MutableStateFlow(EmojiViewMode.AGGREGATED)
        val emojiViewMode: StateFlow<EmojiViewMode> = _emojiViewMode.asStateFlow()
        private val _selectedSenderForEmojis = MutableStateFlow<SenderStat?>(null)
        val selectedSenderForEmojis: StateFlow<SenderStat?> = _selectedSenderForEmojis.asStateFlow()
        private val _topEmojiLimit = MutableStateFlow(30)
        val topEmojiLimit: StateFlow<Int> = _topEmojiLimit.asStateFlow()
        private val _senderTopEmojis = MutableStateFlow<List<EmojiStat>>(emptyList())
        val senderTopEmojis: StateFlow<List<EmojiStat>> = _senderTopEmojis.asStateFlow()
        private val _isSenderEmojisLoading = MutableStateFlow(false)
        val isSenderEmojisLoading: StateFlow<Boolean> = _isSenderEmojisLoading.asStateFlow()

        // Pool for individual mode emojis to avoid DB queries on limit changes
        private var senderEmojisPool: List<EmojiStat> = emptyList()
        private var senderEmojisPoolSender: String? = null
        private var senderEmojisPoolStartTime: Long? = null
        private var senderEmojisPoolEndTime: Long? = null

        enum class StickerViewMode { AGGREGATED, INDIVIDUAL }

        private val _stickerViewMode = MutableStateFlow(StickerViewMode.AGGREGATED)
        val stickerViewMode: StateFlow<StickerViewMode> = _stickerViewMode.asStateFlow()
        private val _selectedSenderForStickers = MutableStateFlow<SenderStat?>(null)
        val selectedSenderForStickers: StateFlow<SenderStat?> = _selectedSenderForStickers.asStateFlow()
        private val _topStickerLimit = MutableStateFlow(10)
        val topStickerLimit: StateFlow<Int> = _topStickerLimit.asStateFlow()
        private val _topStickers = MutableStateFlow<List<com.whatstat.reborn.data.db.model.StickerStat>>(emptyList())
        val topStickers: StateFlow<List<com.whatstat.reborn.data.db.model.StickerStat>> = _topStickers.asStateFlow()
        private val _senderTopStickers = MutableStateFlow<List<com.whatstat.reborn.data.db.model.StickerStat>>(emptyList())
        val senderTopStickers: StateFlow<List<com.whatstat.reborn.data.db.model.StickerStat>> = _senderTopStickers.asStateFlow()
        private val _isStickerLoading = MutableStateFlow(false)
        val isStickerLoading: StateFlow<Boolean> = _isStickerLoading.asStateFlow()

        // Pool for individual mode stickers to avoid DB queries on limit changes
        private var senderStickersPool: List<com.whatstat.reborn.data.db.model.StickerStat> = emptyList()
        private var senderStickersPoolSender: String? = null
        private var senderStickersPoolStartTime: Long? = null
        private var senderStickersPoolEndTime: Long? = null

        // Pool di 50 sticker caricato con slow stats — evita query DB per cambi di limite in modalità AGGREGATED
        private var stickerPool: List<com.whatstat.reborn.data.db.model.StickerStat> = emptyList()

        init {
            // In LiteMode, ricarica quando viene importato un nuovo ZIP
            if (analyticsRepository.isLiteMode.value) {
                viewModelScope.launch {
                    var firstEmit = true
                    analyticsRepository.liteCacheVersion.collect { _ ->
                        if (firstEmit) {
                            firstEmit = false
                            loadChatStats()
                            return@collect
                        }
                        loadChatStats()
                    }
                }
                // Osserva i rename di chat Lite: aggiorna solo il nome nell'header
                // senza rieseguire le query pesanti di emoji, parole, ecc.
                viewModelScope.launch {
                    analyticsRepository.liteRenameVersion.collect { _ ->
                        refreshHeaderOnly()
                    }
                }
            } else {
                loadChatStats()
            }
            // Carica il timestamp del primo messaggio (non bloccante su IO)
            viewModelScope.launch(Dispatchers.IO) {
                _firstMessageTimestamp.value = analyticsRepository.getFirstMessageTimestamp()
            }
        }

        private fun loadChatStats() {
            viewModelScope.launch {
                _isLoading.value = true
                _isLoadingSlow.value = true
                _error.value = null

                try {
                    // 1. Carica subito nome+avatar per l'header
                    if (_headerInfo.value == null && chatId != -1L) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val basic = chatDao.getChatBasicInfo(chatId)
                            val name =
                                if (basic.isGroup) {
                                    basic.subject ?: contactDao.resolveContactName(basic.jid) ?: basic.jid.substringBefore("@")
                                } else {
                                    contactDao.resolveContactName(basic.jid) ?: basic.jid.substringBefore("@")
                                }
                            _headerInfo.value =
                                HeaderInfo(
                                    name = name,
                                    jid = basic.jid,
                                    isGroup = basic.isGroup,
                                    avatarPath = getAvatarPath(basic.jid),
                                )
                        }
                    }

                    val (startTime, endTime) = getTimeBounds()

                    _loadingProgress.value = 0
                    val quickStats =
                        analyticsRepository.getQuickChatStats(
                            chatId = chatId,
                            startTime = startTime,
                            endTime = endTime,
                            onProgress = { progress -> _loadingProgress.value = progress },
                        )

                    val avatarPath = getAvatarPath(quickStats.jid)
                    _chatStats.value = quickStats.copy(avatarPath = avatarPath)
                    _isLoading.value = false
                    _loadingProgress.value = null

                    _isLoadingSlow.value = true
                    _loadingProgressSlow.value = 0

                    try {
                        val slowStats =
                            analyticsRepository.getSlowChatStats(
                                chatId = chatId,
                                startTime = startTime,
                                endTime = endTime,
                                onProgress = { progress -> _loadingProgressSlow.value = progress },
                            )

                        _chatStats.value =
                            quickStats.copy(
                                topWords = slowStats.topWords,
                                topEmojis = slowStats.topEmojis,
                                senderBreakdown = slowStats.senderBreakdown,
                                conversationStarterBreakdown = slowStats.conversationStarterBreakdown,
                                averageResponseTimeMs = slowStats.averageResponseTimeMs,
                                iStartedConversations = slowStats.iStartedConversations,
                                theyStartedConversations = slowStats.theyStartedConversations,
                                longestMessage = slowStats.longestMessage,
                                sentEmojis = slowStats.sentEmojis,
                                receivedEmojis = slowStats.receivedEmojis,
                                avatarPath = avatarPath,
                            )
                        // Popola gli sticker AGGREGATED dal pool slow stats (stesso pattern di parole/emoji)
                        stickerPool = slowStats.topStickers
                        _topStickers.value = stickerPool.take(_topStickerLimit.value)
                    } catch (e: Exception) {
                        android.util.Log.w("WhatStat", "Slow stats failed: ${e.message}")
                    } finally {
                        _isLoadingSlow.value = false
                        _loadingProgressSlow.value = null
                    }
                } catch (e: Exception) {
                    _error.value = e.message ?: "Errore nel caricamento delle statistiche"
                    _isLoading.value = false
                    _isLoadingSlow.value = false
                    _loadingProgress.value = null
                    _loadingProgressSlow.value = null
                }
            }
        }

        /**
         * Ricarica dal DB solo il nome della chat (campo [subject] aggiornato dal rename)
         * e patcha [_headerInfo] e [_chatStats].contactName senza rieseguire le query
         * pesanti di emoji, parole, sticker o distribuzioni temporali.
         *
         * Chiamato quando [analyticsRepository.liteRenameVersion] emette un nuovo valore,
         * ovvero subito dopo [ChatListViewModel.renameChat].
         */
        private fun refreshHeaderOnly() {
            if (chatId == -1L) {
                // chatId = -1L e' la sentinella Lite da Dashboard: non ha una riga chat
                // direttamente accessibile via chatDao, ma getQuickChatStats risolve il nome
                // dalla prima chat Lite disponibile. Forziamo un reload leggero ripartendo
                // da getQuickChatStats senza le slow stats.
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        val (startTime, endTime) = getTimeBounds()
                        val quickStats =
                            analyticsRepository.getQuickChatStats(
                                chatId = chatId,
                                startTime = startTime,
                                endTime = endTime,
                            )
                        // Patch del solo contactName per non perdere i dati slow gia' caricati
                        val currentStats = _chatStats.value
                        if (currentStats != null) {
                            val updatedSenderBreakdown =
                                currentStats.senderBreakdown.map {
                                    it.copy(displayName = contactDao.resolveContactName(it.jid) ?: it.displayName)
                                }
                            val updatedStarterBreakdown =
                                currentStats.conversationStarterBreakdown.map {
                                    it.copy(displayName = contactDao.resolveContactName(it.jid) ?: it.displayName)
                                }
                            _chatStats.value =
                                currentStats.copy(
                                    contactName = quickStats.contactName,
                                    senderBreakdown = updatedSenderBreakdown,
                                    conversationStarterBreakdown = updatedStarterBreakdown,
                                )
                            // Patch anche dei mittenti selezionati per filtri parole/emoji
                            _selectedSenderForWords.value =
                                _selectedSenderForWords.value?.let { sel ->
                                    updatedSenderBreakdown.find { it.jid == sel.jid } ?: sel
                                }
                            _selectedSenderForEmojis.value =
                                _selectedSenderForEmojis.value?.let { sel ->
                                    updatedSenderBreakdown.find { it.jid == sel.jid } ?: sel
                                }
                            _selectedSenderForStickers.value =
                                _selectedSenderForStickers.value?.let { sel ->
                                    updatedSenderBreakdown.find { it.jid == sel.jid } ?: sel
                                }
                        }
                        _headerInfo.value = _headerInfo.value?.copy(name = quickStats.contactName)
                    }.onFailure { e ->
                        android.util.Log.w("WhatStat", "refreshHeaderOnly (chatId=-1): ${e.message}")
                    }
                }
                return
            }

            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val basic = chatDao.getChatBasicInfo(chatId)
                    // Il subject e' il campo aggiornato da LiteDbWriter.renameChat()
                    val name =
                        basic.subject
                            ?: contactDao.resolveContactName(basic.jid)
                            ?: basic.jid.substringBefore("@")
                    _headerInfo.value =
                        HeaderInfo(
                            name = name,
                            jid = basic.jid,
                            isGroup = basic.isGroup,
                            avatarPath = getAvatarPath(basic.jid),
                        )
                    // Patcha anche chatStats e breakdowns
                    val currentStats = _chatStats.value
                    if (currentStats != null) {
                        val updatedSenderBreakdown =
                            currentStats.senderBreakdown.map {
                                it.copy(displayName = contactDao.resolveContactName(it.jid) ?: it.displayName)
                            }
                        val updatedStarterBreakdown =
                            currentStats.conversationStarterBreakdown.map {
                                it.copy(displayName = contactDao.resolveContactName(it.jid) ?: it.displayName)
                            }
                        _chatStats.value =
                            currentStats.copy(
                                contactName = name,
                                senderBreakdown = updatedSenderBreakdown,
                                conversationStarterBreakdown = updatedStarterBreakdown,
                            )
                        // Patch anche dei mittenti selezionati per filtri parole/emoji
                        _selectedSenderForWords.value =
                            _selectedSenderForWords.value?.let { sel ->
                                updatedSenderBreakdown.find { it.jid == sel.jid } ?: sel
                            }
                        _selectedSenderForEmojis.value =
                            _selectedSenderForEmojis.value?.let { sel ->
                                updatedSenderBreakdown.find { it.jid == sel.jid } ?: sel
                            }
                        _selectedSenderForStickers.value =
                            _selectedSenderForStickers.value?.let { sel ->
                                updatedSenderBreakdown.find { it.jid == sel.jid } ?: sel
                            }
                    }
                }.onFailure { e ->
                    android.util.Log.w("WhatStat", "refreshHeaderOnly: ${e.message}")
                }
            }
        }

        fun syncFilterState() {
            val newRange = filterState.chatTimeRange
            val newCustom = filterState.chatCustomRange
            if (newRange != _selectedTimeRange.value || newCustom != _customDateRange.value) {
                _selectedTimeRange.value = newRange
                _customDateRange.value = newCustom
                loadChatStats()
            }
        }

        fun setTimeRange(range: TimeRange) {
            _selectedTimeRange.value = range
            if (range != TimeRange.CUSTOM) {
                _customDateRange.value = null
            }
            filterState.setChat(range, _customDateRange.value)
            loadChatStats()
        }

        fun setCustomDateRange(dateRange: DateRange?) {
            val wasCustom = _selectedTimeRange.value == TimeRange.CUSTOM
            _customDateRange.value = dateRange
            if (dateRange != null) {
                _selectedTimeRange.value = TimeRange.CUSTOM
                filterState.setChat(TimeRange.CUSTOM, dateRange)
            } else {
                if (wasCustom) {
                    _selectedTimeRange.value = TimeRange.ALL_TIME
                    filterState.setChat(TimeRange.ALL_TIME)
                    loadChatStats()
                }
            }
        }

        fun applyCustomDateRange() {
            loadChatStats()
        }

        fun setWordViewMode(mode: WordViewMode) {
            _wordViewMode.value = mode
            if (mode == WordViewMode.INDIVIDUAL) {
                _topWordsLimit.value = 30 // Default per individuale
                if (_selectedSenderForWords.value == null) {
                    _selectedSenderForWords.value = SenderStat("me@s.whatsapp.net", "Tu", 0)
                }
            } else {
                _topWordsLimit.value = 30 // Default per tutti
            }
            loadSenderTopWords()
        }

        fun setSelectedSenderForWords(sender: SenderStat?) {
            _selectedSenderForWords.value = sender
            // Clear pool when sender changes
            senderWordsPool = emptyList()
            senderWordsPoolSender = null
            senderWordsPoolStartTime = null
            senderWordsPoolEndTime = null
            loadSenderTopWords()
        }

        fun setTopWordsLimit(limit: Int) {
            _topWordsLimit.value = limit
            loadSenderTopWords()
        }

        private fun loadSenderTopWords() {
            if (_wordViewMode.value == WordViewMode.AGGREGATED) return

            val sender = _selectedSenderForWords.value ?: return
            val limit = _topWordsLimit.value.toInt()
            val (startTime, endTime) = getTimeBounds()

            // Check if we can use cached pool (same sender and time bounds)
            if (senderWordsPoolSender == sender.jid &&
                senderWordsPoolStartTime == startTime &&
                senderWordsPoolEndTime == endTime
            ) {
                // Use cached pool, just apply limit
                _senderTopWords.value = senderWordsPool.take(limit)
                return
            }

            viewModelScope.launch {
                _isSenderWordsLoading.value = true
                try {
                    val words =
                        analyticsRepository.getTopWordsBySender(
                            chatId = chatId,
                            jid = sender.jid,
                            startTime = startTime,
                            endTime = endTime,
                            // Load pool of 50 like other individual modes
                            limit = 50,
                        )
                    // Update cache
                    senderWordsPool = words
                    senderWordsPoolSender = sender.jid
                    senderWordsPoolStartTime = startTime
                    senderWordsPoolEndTime = endTime
                    // Apply requested limit
                    _senderTopWords.value = words.take(limit)
                } catch (e: Exception) {
                    _senderTopWords.value = emptyList()
                    // Clear cache on error
                    senderWordsPool = emptyList()
                    senderWordsPoolSender = null
                    senderWordsPoolStartTime = null
                    senderWordsPoolEndTime = null
                } finally {
                    _isSenderWordsLoading.value = false
                }
            }
        }

        private fun getTimeBounds(): Pair<Long?, Long?> {
            return if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                _customDateRange.value?.let {
                    analyticsRepository.getDateRangeBounds(it)
                } ?: (null to null)
            } else {
                analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
            }
        }

        fun setEmojiViewMode(mode: EmojiViewMode) {
            _emojiViewMode.value = mode
            if (mode == EmojiViewMode.INDIVIDUAL) {
                _topEmojiLimit.value = 30
                if (_selectedSenderForEmojis.value == null) {
                    _selectedSenderForEmojis.value = SenderStat("me@s.whatsapp.net", "Tu", 0)
                }
            } else {
                _topEmojiLimit.value = 30
            }
            loadSenderTopEmojis()
        }

        fun setSelectedSenderForEmojis(sender: SenderStat?) {
            _selectedSenderForEmojis.value = sender
            // Clear pool when sender changes
            senderEmojisPool = emptyList()
            senderEmojisPoolSender = null
            senderEmojisPoolStartTime = null
            senderEmojisPoolEndTime = null
            loadSenderTopEmojis()
        }

        fun setTopEmojiLimit(limit: Int) {
            _topEmojiLimit.value = limit
            loadSenderTopEmojis()
        }

        private fun loadSenderTopEmojis() {
            if (_emojiViewMode.value == EmojiViewMode.AGGREGATED) return
            val sender = _selectedSenderForEmojis.value ?: return
            val limit = _topEmojiLimit.value.toInt()
            val (startTime, endTime) = getTimeBounds()

            // Check if we can use cached pool (same sender and time bounds)
            if (senderEmojisPoolSender == sender.jid &&
                senderEmojisPoolStartTime == startTime &&
                senderEmojisPoolEndTime == endTime
            ) {
                // Use cached pool, just apply limit
                _senderTopEmojis.value = senderEmojisPool.take(limit)
                return
            }

            viewModelScope.launch {
                _isSenderEmojisLoading.value = true
                try {
                    val emojis = analyticsRepository.getTopEmojisBySender(chatId, sender.jid, startTime, endTime, 50) // Load pool of 50
                    // Update cache
                    senderEmojisPool = emojis
                    senderEmojisPoolSender = sender.jid
                    senderEmojisPoolStartTime = startTime
                    senderEmojisPoolEndTime = endTime
                    // Apply requested limit
                    _senderTopEmojis.value = emojis.take(limit)
                } catch (e: Exception) {
                    _senderTopEmojis.value = emptyList()
                    // Clear cache on error
                    senderEmojisPool = emptyList()
                    senderEmojisPoolSender = null
                    senderEmojisPoolStartTime = null
                    senderEmojisPoolEndTime = null
                } finally {
                    _isSenderEmojisLoading.value = false
                }
            }
        }

        fun setStickerViewMode(mode: StickerViewMode) {
            _stickerViewMode.value = mode
            if (mode == StickerViewMode.INDIVIDUAL) {
                _topStickerLimit.value = 30
                if (_selectedSenderForStickers.value == null) {
                    _selectedSenderForStickers.value = SenderStat("me@s.whatsapp.net", "Tu", 0)
                }
            } else {
                _topStickerLimit.value = 10
            }
            loadTopStickers()
        }

        fun setSelectedSenderForStickers(sender: SenderStat?) {
            _selectedSenderForStickers.value = sender
            // Clear pool when sender changes
            senderStickersPool = emptyList()
            senderStickersPoolSender = null
            senderStickersPoolStartTime = null
            senderStickersPoolEndTime = null
            loadTopStickers()
        }

        fun setTopStickerLimit(limit: Int) {
            _topStickerLimit.value = limit
            loadTopStickers()
        }

        private fun loadTopStickers() {
            if (_stickerViewMode.value == StickerViewMode.AGGREGATED) {
                // Nessuna query DB: usa il pool già caricato dai slow stats
                _topStickers.value = stickerPool.take(_topStickerLimit.value)
                return
            }
            viewModelScope.launch {
                _isStickerLoading.value = true
                try {
                    val (startTime, endTime) = getTimeBounds()
                    val sender = _selectedSenderForStickers.value ?: return@launch
                    val limit = _topStickerLimit.value

                    // Check if we can use cached pool (same sender and time bounds)
                    if (senderStickersPoolSender == sender.jid &&
                        senderStickersPoolStartTime == startTime &&
                        senderStickersPoolEndTime == endTime
                    ) {
                        // Use cached pool, just apply limit
                        _topStickers.value = senderStickersPool.take(limit)
                        _senderTopStickers.value = senderStickersPool.take(limit)
                        return@launch
                    }
                } catch (e: Exception) {
                    _topStickers.value = emptyList()
                    _senderTopStickers.value = emptyList()
                    // Clear cache on error
                    senderStickersPool = emptyList()
                    senderStickersPoolSender = null
                    senderStickersPoolStartTime = null
                    senderStickersPoolEndTime = null
                } finally {
                    _isStickerLoading.value = false
                }
            }
        }

        fun setSenderColorOverride(
            jid: String,
            colorArgb: Int,
        ) {
            viewModelScope.launch {
                senderColorOverrideStore.setOverride(senderColorOverrideKey(chatId, jid), colorArgb)
            }
        }

        fun clearSenderColorOverride(jid: String) {
            viewModelScope.launch {
                senderColorOverrideStore.removeOverride(senderColorOverrideKey(chatId, jid))
            }
        }
    }

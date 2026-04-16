package com.whatstat.reborn.ui.screen.chatdetail

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.domain.model.ChatStats
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.whatstat.reborn.data.db.model.WordFrequency
import com.whatstat.reborn.data.db.model.SenderStat
import com.whatstat.reborn.data.db.model.EmojiStat
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    enum class WordViewMode { AGGREGATED, INDIVIDUAL }
    enum class EmojiViewMode { AGGREGATED, INDIVIDUAL }

    private val chatId: Long = savedStateHandle["chatId"] ?: -1L

    private val avatarDir: File by lazy {
        File(context.filesDir, FileExtractor.AVATAR_DIR_DEST)
    }

    private fun getAvatarPath(jid: String): String? {
        if (!avatarDir.exists()) return null
        val avatarFile = File(avatarDir, jid + ".jpg")
        return if (avatarFile.exists()) avatarFile.absolutePath else null
    }

    private val _chatStats = MutableStateFlow<ChatStats?>(null)
    val chatStats: StateFlow<ChatStats?> = _chatStats.asStateFlow()

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

    private val _selectedTimeRange = MutableStateFlow(TimeRange.ALL_TIME)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()
    
    private val _customDateRange = MutableStateFlow<DateRange?>(null)
    val customDateRange: StateFlow<DateRange?> = _customDateRange.asStateFlow()

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

    init {
        if (chatId != -1L) loadChatStats()
    }

    private fun loadChatStats() {
        viewModelScope.launch {
            _isLoading.value = true
            _isLoadingSlow.value = true
            _error.value = null

            try {
                val (startTime, endTime) = getTimeBounds()

                _loadingProgress.value = 0
                val quickStats = analyticsRepository.getQuickChatStats(
                    chatId = chatId,
                    startTime = startTime,
                    endTime = endTime,
                    onProgress = { progress -> _loadingProgress.value = progress }
                )

                val avatarPath = getAvatarPath(quickStats.jid)
                _chatStats.value = quickStats.copy(avatarPath = avatarPath)
                _isLoading.value = false
                _loadingProgress.value = null

                _isLoadingSlow.value = true
                _loadingProgressSlow.value = 0

                try {
                    val slowStats = analyticsRepository.getSlowChatStats(
                        chatId = chatId,
                        startTime = startTime,
                        endTime = endTime,
                        onProgress = { progress -> _loadingProgressSlow.value = progress }
                    )

                    _chatStats.value = quickStats.copy(
                        topWords = slowStats.topWords,
                        topEmojis = slowStats.topEmojis,
                        senderBreakdown = slowStats.senderBreakdown,
                        conversationStarterBreakdown = slowStats.conversationStarterBreakdown,
                        averageResponseTimeMs = slowStats.averageResponseTimeMs,
                        iStartedConversations = slowStats.iStartedConversations,
                        theyStartedConversations = slowStats.theyStartedConversations,
                        longestMessage = slowStats.longestMessage,
                        avatarPath = avatarPath
                    )
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

    fun setTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        loadChatStats()
    }
    
    fun setCustomDateRange(dateRange: DateRange?) {
        _customDateRange.value = dateRange
        if (dateRange != null) {
            _selectedTimeRange.value = TimeRange.CUSTOM
        }
        loadChatStats()
    }

    fun setWordViewMode(mode: WordViewMode) {
        _wordViewMode.value = mode
        if (mode == WordViewMode.INDIVIDUAL) {
            _topWordsLimit.value = 5 // Default per individuale
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

        viewModelScope.launch {
            _isSenderWordsLoading.value = true
            try {
                val (startTime, endTime) = getTimeBounds()
                val words = analyticsRepository.getTopWordsBySender(
                    chatId = chatId,
                    jid = sender.jid,
                    startTime = startTime,
                    endTime = endTime,
                    limit = limit
                )
                _senderTopWords.value = words
            } catch (e: Exception) {
                _senderTopWords.value = emptyList()
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
            _topEmojiLimit.value = 5
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
        loadSenderTopEmojis()
    }

    fun setTopEmojiLimit(limit: Int) {
        _topEmojiLimit.value = limit
        loadSenderTopEmojis()
    }

    private fun loadSenderTopEmojis() {
        if (_emojiViewMode.value == EmojiViewMode.AGGREGATED) return
        val sender = _selectedSenderForEmojis.value ?: return
        viewModelScope.launch {
            _isSenderEmojisLoading.value = true
            try {
                val (startTime, endTime) = getTimeBounds()
                _senderTopEmojis.value = analyticsRepository.getTopEmojisBySender(chatId, sender.jid, startTime, endTime, _topEmojiLimit.value.toInt())
            } catch (e: Exception) {
                _senderTopEmojis.value = emptyList()
            } finally {
                _isSenderEmojisLoading.value = false
            }
        }
    }
}

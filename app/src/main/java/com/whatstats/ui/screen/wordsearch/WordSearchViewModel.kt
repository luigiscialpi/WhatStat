package com.whatstats.ui.screen.wordsearch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstats.data.db.dao.ContactDao
import com.whatstats.data.db.model.DateRange
import com.whatstats.data.db.model.TimeRange
import com.whatstats.data.db.model.WordMessageResult
import com.whatstats.data.db.model.WordSearchResult
import com.whatstats.data.repository.AnalyticsRepository
import com.whatstats.data.root.FileExtractor
import com.whatstats.ui.state.FilterStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class MultiWordMode {
    NONE, // 0: OFF
    AND, // 1: Tutte le parole
    OR, // 2: Almeno una parola
}

@HiltViewModel
class WordSearchViewModel
    @Inject
    constructor(
        private val analyticsRepository: AnalyticsRepository,
        private val contactDao: ContactDao,
        private val filterState: FilterStateHolder,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _searchQuery = MutableStateFlow("")
        val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

        private val _results = MutableStateFlow<List<WordSearchResult>>(emptyList())
        val results: StateFlow<List<WordSearchResult>> = _results.asStateFlow()

        private val _isLoading = MutableStateFlow(false)
        val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

        private val _isSearching = MutableStateFlow(false)
        val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

        private val _hasSearched = MutableStateFlow(false)
        val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

        private val _suggestions = MutableStateFlow<List<String>>(emptyList())
        val suggestions: StateFlow<List<String>> = _suggestions.asStateFlow()

        private val _selectedTimeRange = MutableStateFlow(TimeRange.ALL_TIME)
        val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

        private val _customDateRange = MutableStateFlow<DateRange?>(null)
        val customDateRange: StateFlow<DateRange?> = _customDateRange.asStateFlow()

        private val _firstMessageTimestamp = MutableStateFlow<Long?>(null)
        val firstMessageTimestamp: StateFlow<Long?> = _firstMessageTimestamp.asStateFlow()

        private val chatIdFlow = MutableStateFlow<Long?>(null)

        private val _chatContextName = MutableStateFlow<String?>(null)
        val chatContextName: StateFlow<String?> = _chatContextName.asStateFlow()

        private val _chatContextAvatarPath = MutableStateFlow<String?>(null)
        val chatContextAvatarPath: StateFlow<String?> = _chatContextAvatarPath.asStateFlow()

        // Stato per la bottom sheet: (senderName, senderAvatarPath, lista messaggi)
        private val _messagePreview = MutableStateFlow<Triple<String, String?, List<WordMessageResult>>?>(null)
        val messagePreview: StateFlow<Triple<String, String?, List<WordMessageResult>>?> = _messagePreview.asStateFlow()

        private val _isLoadingMessages = MutableStateFlow(false)
        val isLoadingMessages: StateFlow<Boolean> = _isLoadingMessages.asStateFlow()

        private var messagesJob: kotlinx.coroutines.Job? = null

        private val _isCaseSensitive = MutableStateFlow(false)
        val isCaseSensitive: StateFlow<Boolean> = _isCaseSensitive.asStateFlow()

        private val _isExactMatch = MutableStateFlow(false)
        val isExactMatch: StateFlow<Boolean> = _isExactMatch.asStateFlow()

        private val _onlyQuestions = MutableStateFlow(false)
        val onlyQuestions: StateFlow<Boolean> = _onlyQuestions.asStateFlow()

        private val _isMultiWord = MutableStateFlow(false)
        val isMultiWord: StateFlow<Boolean> = _isMultiWord.asStateFlow()

        private val _multiWordMode = MutableStateFlow(MultiWordMode.NONE)
        val multiWordMode: StateFlow<MultiWordMode> = _multiWordMode.asStateFlow()

        private var searchJob: Job? = null

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

        companion object {
            private const val DEBOUNCE_MS = 300L
        }

        private var initialized = false
        private var lastKnownCacheVersion = 0L

        init {
            if (analyticsRepository.isLiteMode.value) {
                viewModelScope.launch {
                    analyticsRepository.liteCacheVersion.collect { version ->
                        if (version > 0L && version != lastKnownCacheVersion) {
                            lastKnownCacheVersion = version
                            initialized = false
                            // Ricarica immediatamente con il chatId corrente
                            initialize(chatIdFlow.value, force = true)
                        }
                    }
                }
                // Osserva i rename di chat Lite: aggiorna solo il nome nel titolo
                // senza re-inizializzare la ricerca o svuotare i risultati.
                viewModelScope.launch {
                    analyticsRepository.liteRenameVersion.collect { _ ->
                        refreshContextName()
                        refreshGlobalNames()
                    }
                }
            }
            // Carica il timestamp del primo messaggio
            viewModelScope.launch(Dispatchers.IO) {
                _firstMessageTimestamp.value = analyticsRepository.getFirstMessageTimestamp()
            }
        }

        /**
         * Rilegge dal DB solo il nome della chat corrente e aggiorna [_chatContextName].
         * Chiamato su [analyticsRepository.liteRenameVersion] senza toccare i risultati di ricerca.
         */
        private fun refreshContextName() {
            val chatId = chatIdFlow.value ?: return
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    analyticsRepository.getChatContextInfo(chatId)
                }.onSuccess { info ->
                    _chatContextName.value = info.displayName
                }.onFailure { e ->
                    android.util.Log.w("WhatStat", "refreshContextName (Words): ${e.message}")
                }
            }
        }

        /**
         * Ricarica solo i nomi dei contatti nei risultati di ricerca globali.
         */
        private fun refreshGlobalNames() {
            if (chatIdFlow.value != null) return
            val currentResults = _results.value
            if (currentResults.isEmpty()) return

            viewModelScope.launch(Dispatchers.IO) {
                val jids = currentResults.map { it.jid }
                val contactNames = contactDao.resolveContactNames(jids)
                _results.value =
                    currentResults.map { res ->
                        res.copy(displayName = contactNames[res.jid] ?: res.displayName)
                    }
            }
        }

        fun initialize(
            chatId: Long?,
            force: Boolean = false,
        ) {
            if (!initialized || force) {
                initialized = true
                _hasSearched.value = false
                chatIdFlow.value = chatId
                if (chatId != null) {
                    _selectedTimeRange.value = filterState.chatTimeRange
                    _customDateRange.value = filterState.chatCustomRange
                    viewModelScope.launch {
                        runCatching {
                            analyticsRepository.getChatContextInfo(chatId)
                        }.onSuccess { info ->
                            _chatContextName.value = info.displayName
                            _chatContextAvatarPath.value = getAvatarPath(info.jid)
                        }
                    }
                } else {
                    _selectedTimeRange.value = filterState.globalTimeRange
                    _customDateRange.value = filterState.globalCustomRange
                    _chatContextName.value = null
                    _chatContextAvatarPath.value = null
                }
            }
        }

        fun onSearchQueryChange(query: String) {
            _searchQuery.value = query
            _hasSearched.value = false
            if (query.isEmpty()) {
                _results.value = emptyList()
            }
            if (query.length >= 3) {
                viewModelScope.launch {
                    _suggestions.value = analyticsRepository.getWordSuggestions(query, chatIdFlow.value)
                }
            } else {
                _suggestions.value = emptyList()
            }
        }

        fun startSearch() {
            val query = _searchQuery.value
            _suggestions.value = emptyList()
            if (query.length >= 2) {
                _isSearching.value = true
                searchJob?.cancel()
                searchJob =
                    viewModelScope.launch {
                        search(query)
                    }
            }
        }

        fun cancelSearch() {
            searchJob?.cancel()
            _isSearching.value = false
            _isLoading.value = false
        }

        fun toggleCaseSensitive() {
            _isCaseSensitive.value = !_isCaseSensitive.value
        }

        fun toggleExactMatch() {
            _isExactMatch.value = !_isExactMatch.value
        }

        fun toggleOnlyQuestions() {
            _onlyQuestions.value = !_onlyQuestions.value
        }

        fun toggleMultiWord() {
            _isMultiWord.value = !_isMultiWord.value
        }

        fun cycleMultiWordMode() {
            _multiWordMode.value =
                when (_multiWordMode.value) {
                    MultiWordMode.NONE -> MultiWordMode.AND
                    MultiWordMode.AND -> MultiWordMode.OR
                    MultiWordMode.OR -> MultiWordMode.NONE
                }
        }

        fun syncFilterState() {
            val (newRange, newCustom) =
                if (chatIdFlow.value != null) {
                    filterState.chatTimeRange to filterState.chatCustomRange
                } else {
                    filterState.globalTimeRange to filterState.globalCustomRange
                }
            if (newRange != _selectedTimeRange.value || newCustom != _customDateRange.value) {
                _selectedTimeRange.value = newRange
                _customDateRange.value = newCustom
            }
        }

        fun setTimeRange(range: TimeRange) {
            _selectedTimeRange.value = range
            if (range != TimeRange.CUSTOM) {
                _customDateRange.value = null
            }
            if (chatIdFlow.value != null) {
                filterState.setChat(range, _customDateRange.value)
            } else {
                filterState.setGlobal(range, _customDateRange.value)
            }
        }

        fun setCustomDateRange(dateRange: DateRange?) {
            _customDateRange.value = dateRange
            if (dateRange != null) {
                _selectedTimeRange.value = TimeRange.CUSTOM
                if (chatIdFlow.value != null) {
                    filterState.setChat(TimeRange.CUSTOM, dateRange)
                } else {
                    filterState.setGlobal(TimeRange.CUSTOM, dateRange)
                }
            }
        }

        private suspend fun search(query: String) {
            _isLoading.value = true
            try {
                val (startTime, endTime) =
                    if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                        _customDateRange.value?.let {
                            analyticsRepository.getDateRangeBounds(it)
                        } ?: (null to null)
                    } else {
                        analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                    }
                // Try direct query first
                try {
                    val directResults =
                        analyticsRepository.searchWordDirect(
                            query,
                            chatIdFlow.value,
                            startTime,
                            endTime,
                            _isCaseSensitive.value,
                            _isExactMatch.value,
                            _onlyQuestions.value,
                            _multiWordMode.value.ordinal,
                        )
                    if (directResults.isNotEmpty()) {
                        _results.value = directResults.map { it.copy(avatarPath = getAvatarPath(it.jid)) }
                    }
                } catch (e2: Exception) {
                    // Ignored
                }

                // Then try via repository
                val searchResults =
                    analyticsRepository.searchWord(
                        query,
                        chatIdFlow.value,
                        startTime,
                        endTime,
                        _isCaseSensitive.value,
                        _isExactMatch.value,
                        _onlyQuestions.value,
                        _multiWordMode.value.ordinal,
                    )
                _results.value = searchResults.map { it.copy(avatarPath = getAvatarPath(it.jid)) }
                _hasSearched.value = true
            } catch (e: Exception) {
                _results.value = emptyList()
            } finally {
                _isLoading.value = false
                _isSearching.value = false
            }
        }

        /**
         * Carica i messaggi contenenti la parola cercata per il mittente selezionato.
         * Apre la bottom sheet con i risultati.
         */
        fun loadMessagesForSender(
            senderJid: String,
            senderName: String,
        ) {
            messagesJob?.cancel()
            messagesJob =
                viewModelScope.launch {
                    _isLoadingMessages.value = true
                    try {
                        val (startTime, endTime) =
                            if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                                _customDateRange.value?.let {
                                    analyticsRepository.getDateRangeBounds(it)
                                } ?: (null to null)
                            } else {
                                analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                            }
                        val messages =
                            analyticsRepository.getWordMessages(
                                word = _searchQuery.value,
                                senderJid = senderJid,
                                chatId = chatIdFlow.value,
                                startTime = startTime,
                                endTime = endTime,
                                isCaseSensitive = _isCaseSensitive.value,
                                isExactMatch = _isExactMatch.value,
                                onlyQuestions = _onlyQuestions.value,
                                multiWordMode = _multiWordMode.value.ordinal,
                            )
                        _messagePreview.value =
                            Triple(
                                senderName,
                                getAvatarPath(senderJid),
                                messages,
                            )
                    } catch (e: Exception) {
                        // Canceled or failed
                    } finally {
                        _isLoadingMessages.value = false
                    }
                }
        }

        fun cancelMessagesLoad() {
            messagesJob?.cancel()
            _isLoadingMessages.value = false
        }

        /**
         * Chiude l'anteprima dei messaggi.
         */
        fun dismissMessagePreview() {
            _messagePreview.value = null
        }
    }

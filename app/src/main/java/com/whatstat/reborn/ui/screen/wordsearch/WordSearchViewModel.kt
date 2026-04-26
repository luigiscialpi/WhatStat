package com.whatstat.reborn.ui.screen.wordsearch

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.db.model.WordSearchResult
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.ui.state.FilterStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class WordSearchViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val filterState: FilterStateHolder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _results = MutableStateFlow<List<WordSearchResult>>(emptyList())
    val results: StateFlow<List<WordSearchResult>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.ALL_TIME)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _customDateRange = MutableStateFlow<DateRange?>(null)
    val customDateRange: StateFlow<DateRange?> = _customDateRange.asStateFlow()

    private val _chatId = MutableStateFlow<Long?>(null)

    private val _chatContextName = MutableStateFlow<String?>(null)
    val chatContextName: StateFlow<String?> = _chatContextName.asStateFlow()

    private val _chatContextAvatarPath = MutableStateFlow<String?>(null)
    val chatContextAvatarPath: StateFlow<String?> = _chatContextAvatarPath.asStateFlow()

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

    fun initialize(chatId: Long?) {
        if (!initialized) {
            initialized = true
            _chatId.value = chatId
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
    }

    fun startSearch() {
        val query = _searchQuery.value
        if (query.length >= 2) {
            _isSearching.value = true
            searchDebounced(query)
        }
    }

    fun cancelSearch() {
        searchJob?.cancel()
        _isSearching.value = false
    }

    private fun searchDebounced(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            search(query)
        }
    }

    fun syncFilterState() {
        val (newRange, newCustom) = if (_chatId.value != null) {
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
        if (_chatId.value != null) filterState.setChat(range, _customDateRange.value)
        else filterState.setGlobal(range, _customDateRange.value)
    }

    fun setCustomDateRange(dateRange: DateRange?) {
        _customDateRange.value = dateRange
        if (dateRange != null) {
            _selectedTimeRange.value = TimeRange.CUSTOM
            if (_chatId.value != null) filterState.setChat(TimeRange.CUSTOM, dateRange)
            else filterState.setGlobal(TimeRange.CUSTOM, dateRange)
        }
    }

    fun applyCustomDateRange() {
        startSearch()
    }

    private fun search(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val (startTime, endTime) = if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                    _customDateRange.value?.let {
                        analyticsRepository.getDateRangeBounds(it)
                    } ?: (null to null)
                } else {
                    analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                }
                // Try direct query first
                try {
                    val directResults = analyticsRepository.searchWordDirect(query, _chatId.value, startTime, endTime)
                    if (directResults.isNotEmpty()) {
                        _results.value = directResults.map { it.copy(avatarPath = getAvatarPath(it.jid)) }
                    }
                } catch (e2: Exception) {
                    // Ignored
                }
                
                // Then try via repository
                val searchResults = analyticsRepository.searchWord(query, _chatId.value, startTime, endTime)
                _results.value = searchResults.map { it.copy(avatarPath = getAvatarPath(it.jid)) }
            } catch (e: Exception) {
                _results.value = emptyList()
            } finally {
                _isLoading.value = false
                _isSearching.value = false
            }
        }
    }
}
package com.whatstat.reborn.ui.screen.wordsearch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.db.model.WordSearchResult
import com.whatstat.reborn.data.repository.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WordSearchViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _results = MutableStateFlow<List<WordSearchResult>>(emptyList())
    val results: StateFlow<List<WordSearchResult>> = _results.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _debugInfo = MutableStateFlow("")
    val debugInfo: StateFlow<String> = _debugInfo.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.ALL_TIME)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _customDateRange = MutableStateFlow<DateRange?>(null)
    val customDateRange: StateFlow<DateRange?> = _customDateRange.asStateFlow()

    private val _chatId = MutableStateFlow<Long?>(null)

    private var searchJob: Job? = null

    companion object {
        private const val DEBOUNCE_MS = 300L
    }

    fun setChatId(id: Long) {
        _chatId.value = id
        if (_searchQuery.value.length >= 2) {
            searchDebounced(_searchQuery.value)
        }
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.length >= 2) {
            searchDebounced(query)
        } else {
            searchJob?.cancel()
            _results.value = emptyList()
        }
    }

    private fun searchDebounced(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            search(query)
        }
    }

    fun setTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        if (_searchQuery.value.length >= 2) {
            searchDebounced(_searchQuery.value)
        }
    }

    fun setCustomDateRange(dateRange: DateRange?) {
        _customDateRange.value = dateRange
        if (dateRange != null) {
            _selectedTimeRange.value = TimeRange.CUSTOM
        }
        if (_searchQuery.value.length >= 2) {
            searchDebounced(_searchQuery.value)
        }
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
                _debugInfo.value = "DEBUG: search('$query', chatId=${_chatId.value}, startTime=$startTime, endTime=$endTime)"
                
                // Try direct query first
                try {
                    val directResults = analyticsRepository.searchWordDirect(query, _chatId.value, startTime, endTime)
                    _debugInfo.value += "\n    -> Direct DAO results: ${directResults.size}"
                    if (directResults.isNotEmpty()) {
                        _debugInfo.value += "\n    -> Result[0]: jid=${directResults[0].jid}, count=${directResults[0].count}"
                        _results.value = directResults
                    }
                } catch (e2: Exception) {
                    _debugInfo.value += "\n    -> Direct error: ${e2.message}"
                }
                
                // Then try via repository
                val searchResults = analyticsRepository.searchWord(query, _chatId.value, startTime, endTime)
                _debugInfo.value += "\n    -> Repository results: ${searchResults.size}"
                _results.value = searchResults
            } catch (e: Exception) {
                _debugInfo.value = "ERROR: ${e.message}\n${e.stackTraceToString()}"
                _results.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
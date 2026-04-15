package com.whatstat.reborn.ui.screen.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.LinkStat
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.repository.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MediaStatsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _linkStats = MutableStateFlow<List<LinkStat>>(emptyList())
    val linkStats: StateFlow<List<LinkStat>> = _linkStats.asStateFlow()

    private val _linkStatsBySender = MutableStateFlow<List<LinkStat>>(emptyList())
    val linkStatsBySender: StateFlow<List<LinkStat>> = _linkStatsBySender.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.ALL_TIME)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _customDateRange = MutableStateFlow<DateRange?>(null)
    val customDateRange: StateFlow<DateRange?> = _customDateRange.asStateFlow()

    private val _chatId = MutableStateFlow<Long?>(null)
    val chatId: StateFlow<Long?> = _chatId.asStateFlow()

    private val _selectedChatJid = MutableStateFlow<String?>(null)
    val selectedChatJid: StateFlow<String?> = _selectedChatJid.asStateFlow()

    private val _debugInfo = MutableStateFlow("")
    val debugInfo: StateFlow<String> = _debugInfo.asStateFlow()

    fun setChatId(id: Long) {
        _chatId.value = id
        loadStats()
    }

    fun setSelectedChatJid(jid: String?) {
        _selectedChatJid.value = jid
    }

    fun clearSelection() {
        _selectedChatJid.value = null
        _linkStatsBySender.value = emptyList()
    }

    init {
        loadStats()
    }

    fun setTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        loadStats()
    }

    fun setCustomDateRange(dateRange: DateRange?) {
        _customDateRange.value = dateRange
        if (dateRange != null) {
            _selectedTimeRange.value = TimeRange.CUSTOM
        }
        loadStats()
    }

    private fun loadStats() {
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
                val cid = _chatId.value
                if (cid != null) {
                    // Per una chat specifica: mostra direttamente lo split per mittente
                    _linkStats.value = analyticsRepository.getLinkStatsBySender(cid, startTime, endTime)
                } else {
                    _linkStats.value = analyticsRepository.getLinkStats(null, startTime, endTime)
                }
            } catch (e: Exception) {
                _linkStats.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadLinkStatsBySenderForJid(jid: String) {
        viewModelScope.launch {
            try {
                val (startTime, endTime) = if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                    _customDateRange.value?.let {
                        analyticsRepository.getDateRangeBounds(it)
                    } ?: (null to null)
                } else {
                    analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                }
                _linkStatsBySender.value = analyticsRepository.getLinkStatsByJid(jid, startTime, endTime)
            } catch (e: Exception) {
                _linkStatsBySender.value = emptyList()
            }
        }
    }

    fun loadLinkStatsBySender(chatId: Long) {
        viewModelScope.launch {
            try {
                val (startTime, endTime) = if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                    _customDateRange.value?.let {
                        analyticsRepository.getDateRangeBounds(it)
                    } ?: (null to null)
                } else {
                    analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                }
                val result = analyticsRepository.getLinkStatsBySender(chatId, startTime, endTime)
                _debugInfo.value = "DEBUG Resolve:\n" + result.map { "${it.jid} -> ${it.displayName}" }.joinToString("\n")
                _linkStatsBySender.value = result
            } catch (e: Exception) {
                _linkStatsBySender.value = emptyList()
                _debugInfo.value = "Error: ${e.message}"
            }
        }
    }
}
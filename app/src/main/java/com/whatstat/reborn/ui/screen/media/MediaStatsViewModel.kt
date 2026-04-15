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

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.LAST_YEAR)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _customDateRange = MutableStateFlow<DateRange?>(null)
    val customDateRange: StateFlow<DateRange?> = _customDateRange.asStateFlow()

    private val _chatId = MutableStateFlow<Long?>(null)

    fun setChatId(id: Long) {
        _chatId.value = id
        loadStats()
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
                _linkStats.value = analyticsRepository.getLinkStats(_chatId.value, startTime, endTime)
            } catch (e: Exception) {
                _linkStats.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
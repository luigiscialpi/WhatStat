package com.whatstat.reborn.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.DayCount
import com.whatstat.reborn.data.db.model.DayOfMonthCount
import com.whatstat.reborn.data.db.model.HourlyCount
import com.whatstat.reborn.data.db.model.MonthOfYearCount
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.db.model.YearTrendCount
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.domain.model.GlobalStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _globalStats = MutableStateFlow<GlobalStats?>(null)
    val globalStats: StateFlow<GlobalStats?> = _globalStats.asStateFlow()

    private val _hourlyData = MutableStateFlow<List<HourlyCount>>(emptyList())
    val hourlyData: StateFlow<List<HourlyCount>> = _hourlyData.asStateFlow()

    private val _weeklyData = MutableStateFlow<List<DayCount>>(emptyList())
    val weeklyData: StateFlow<List<DayCount>> = _weeklyData.asStateFlow()

    private val _dayOfMonthData = MutableStateFlow<List<DayOfMonthCount>>(emptyList())
    val dayOfMonthData: StateFlow<List<DayOfMonthCount>> = _dayOfMonthData.asStateFlow()

    private val _monthOfYearData = MutableStateFlow<List<MonthOfYearCount>>(emptyList())
    val monthOfYearData: StateFlow<List<MonthOfYearCount>> = _monthOfYearData.asStateFlow()

    private val _yearTrendData = MutableStateFlow<List<YearTrendCount>>(emptyList())
    val yearTrendData: StateFlow<List<YearTrendCount>> = _yearTrendData.asStateFlow()

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

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            _isLoadingSlow.value = true
            _error.value = null
            try {
                val (startTime, endTime) = if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                    _customDateRange.value?.let {
                        analyticsRepository.getDateRangeBounds(it)
                    } ?: (null to null)
                } else {
                    analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                }
                
                _loadingProgress.value = 0
                val quickStats = analyticsRepository.getQuickGlobalStats(
                    timeRange = _selectedTimeRange.value,
                    customRange = _customDateRange.value,
                    onProgress = { progress -> _loadingProgress.value = progress }
                )
                _globalStats.value = quickStats

                if (quickStats != null) {
                    _hourlyData.value = quickStats.hourlyDistribution
                    _weeklyData.value = quickStats.weeklyDistribution
                    _dayOfMonthData.value = quickStats.dayOfMonthDistribution
                    _monthOfYearData.value = quickStats.monthOfYearDistribution
                    _yearTrendData.value = quickStats.yearTrendDistribution
                }
                
                _isLoading.value = false
                _loadingProgress.value = null
                
                _isLoadingSlow.value = true
                _loadingProgressSlow.value = 0
                try {
                    val slowStats = analyticsRepository.getSlowGlobalStats(
                        startTime = startTime,
                        endTime = endTime,
                        onProgress = { progress -> _loadingProgressSlow.value = progress }
                    )
                    _globalStats.value = _globalStats.value?.copy(topEmojis = slowStats.topEmojis)
                } catch (e: Exception) {
                    android.util.Log.w("WhatStat", "Slow stats failed: ${e.message}")
                } finally {
                    _isLoadingSlow.value = false
                    _loadingProgressSlow.value = null
                }
            } catch (e: Exception) {
                _error.value = "Errore: ${e.message}"
                _isLoading.value = false
                _isLoadingSlow.value = false
                _loadingProgress.value = null
                _loadingProgressSlow.value = null
            }
        }
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

    fun retry() {
        loadStats()
    }
}

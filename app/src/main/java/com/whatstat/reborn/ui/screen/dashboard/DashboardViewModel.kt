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
import com.whatstat.reborn.ui.state.FilterStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val filterState: FilterStateHolder
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

    private val _isLoadingSlow = MutableStateFlow(false)
    val isLoadingSlow: StateFlow<Boolean> = _isLoadingSlow.asStateFlow()

    private val _loadingProgress = MutableStateFlow<Int?>(null)
    val loadingProgress: StateFlow<Int?> = _loadingProgress.asStateFlow()

    private val _loadingProgressSlow = MutableStateFlow<Int?>(null)
    val loadingProgressSlow: StateFlow<Int?> = _loadingProgressSlow.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(filterState.globalTimeRange)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _customDateRange = MutableStateFlow(filterState.globalCustomRange)
    val customDateRange: StateFlow<DateRange?> = _customDateRange.asStateFlow()

    private val _slowStatsRequested = MutableStateFlow(false)
    val slowStatsRequested: StateFlow<Boolean> = _slowStatsRequested.asStateFlow()

    private val _selectedLimit = MutableStateFlow(30)
    val selectedLimit: StateFlow<Int> = _selectedLimit.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadStats()
    }

    fun cancelLoad() {
        loadJob?.cancel()
        _isLoading.value = false
        _isLoadingSlow.value = false
    }

    fun retry() {
        loadStats()
    }

    private fun loadStats() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            _isLoadingSlow.value = false
            _slowStatsRequested.value = false
            _error.value = null
            try {
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

                // Prova a caricare slow stats dalla cache (senza bloccare la UI)
                if (_selectedTimeRange.value == TimeRange.ALL_TIME) {
                    tryLoadSlowStatsFromCache()
                }
            } catch (e: CancellationException) {
                // Ricerca annullata dall'utente, ignoriamo l'errore
            } catch (e: Exception) {
                _error.value = "Errore: ${e.message}"
            } finally {
                _isLoading.value = false
                _loadingProgress.value = null
            }
        }
    }

    private fun tryLoadSlowStatsFromCache() {
        viewModelScope.launch {
            try {
                val slowStats = analyticsRepository.getSlowGlobalStats(
                    startTime = null,
                    endTime = null,
                    onProgress = {}
                )
                if (slowStats.topWords.isNotEmpty() || slowStats.topEmojis.isNotEmpty() || slowStats.topStickers.isNotEmpty()) {
                    _globalStats.value = _globalStats.value?.copy(
                        topWords = slowStats.topWords,
                        topEmojis = slowStats.topEmojis,
                        topStickers = slowStats.topStickers
                    )
                    _slowStatsRequested.value = true
                }
            } catch (_: Exception) { }
        }
    }

    fun loadSlowStats() {
        _slowStatsRequested.value = true
        viewModelScope.launch {
            _isLoadingSlow.value = true
            _loadingProgressSlow.value = 0
            try {
                val (startTime, endTime) = if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                    _customDateRange.value?.let {
                        analyticsRepository.getDateRangeBounds(it)
                    } ?: (null to null)
                } else {
                    analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                }
                val slowStats = analyticsRepository.getSlowGlobalStats(
                    startTime = startTime,
                    endTime = endTime,
                    onProgress = { progress -> _loadingProgressSlow.value = progress }
                )
                _globalStats.value = _globalStats.value?.copy(
                    topWords = slowStats.topWords,
                    topEmojis = slowStats.topEmojis,
                    topStickers = slowStats.topStickers
                )
            } catch (e: CancellationException) {
                // Annullato
            } catch (e: Exception) {
                android.util.Log.w("WhatStat", "Slow stats failed: ${e.message}")
            } finally {
                _isLoadingSlow.value = false
                _loadingProgressSlow.value = null
            }
        }
    }

    fun setSelectedLimit(limit: Int) {
        _selectedLimit.value = limit
    }

    fun setTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        if (range != TimeRange.CUSTOM) {
            _customDateRange.value = null
        }
        filterState.setGlobal(range, _customDateRange.value)
        loadStats()
    }

    fun setCustomDateRange(dateRange: DateRange?) {
        val wasCustom = _selectedTimeRange.value == TimeRange.CUSTOM
        _customDateRange.value = dateRange
        if (dateRange != null) {
            _selectedTimeRange.value = TimeRange.CUSTOM
            filterState.setGlobal(TimeRange.CUSTOM, dateRange)
        } else {
            if (wasCustom) {
                _selectedTimeRange.value = TimeRange.ALL_TIME
                filterState.setGlobal(TimeRange.ALL_TIME)
                loadStats()
            }
        }
    }

}

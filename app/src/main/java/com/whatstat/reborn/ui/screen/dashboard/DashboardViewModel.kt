package com.whatstat.reborn.ui.screen.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.analysis.LexicalAnalysisManager
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.DayCount
import com.whatstat.reborn.data.db.model.DayOfMonthCount
import com.whatstat.reborn.data.db.model.HourlyCount
import com.whatstat.reborn.data.db.model.MonthOfYearCount
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.db.model.YearTrendCount
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.domain.model.GlobalStats
import com.whatstat.reborn.service.LexicalAnalysisService
import com.whatstat.reborn.ui.state.FilterStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val filterState: FilterStateHolder,
    private val lexicalAnalysisManager: LexicalAnalysisManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _globalStats = MutableStateFlow<GlobalStats?>(null)
    val globalStats: StateFlow<GlobalStats?> = _globalStats.asStateFlow()

    val isLiteMode = analyticsRepository.isLiteMode

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

    private val _selectedLimit = MutableStateFlow(if (analyticsRepository.isLiteMode.value) 30 else 20)
    val selectedLimit: StateFlow<Int> = _selectedLimit.asStateFlow()

    private val _firstMessageTimestamp = MutableStateFlow<Long?>(null)
    val firstMessageTimestamp: StateFlow<Long?> = _firstMessageTimestamp.asStateFlow()

    private var loadJob: Job? = null

    init {
        val isLite = analyticsRepository.isLiteMode.value
        // Carica il timestamp del primo messaggio (non bloccante su IO)
        viewModelScope.launch(Dispatchers.IO) {
            _firstMessageTimestamp.value = analyticsRepository.getFirstMessageTimestamp()
        }
        // Non forzare il refresh: usa la cache se disponibile.
        // Sarà il collect su liteCacheVersion a triggerare il reload quando serve.
        loadStats(forceRefresh = false)

        // Ricarica ogni volta che viene completata una nuova importazione Lite
        if (isLite) {
            val baselineVersion = analyticsRepository.liteCacheVersion.value
            viewModelScope.launch {
                analyticsRepository.liteCacheVersion
                    .collect { version ->
                        if (version > baselineVersion) {
                            loadStats(forceRefresh = true)
                        }
                    }
            }
        }
    }

    fun cancelLoad() {
        loadJob?.cancel()
        _isLoading.value = false
        _isLoadingSlow.value = false
    }

    fun retry() {
        loadStats()
    }

    private fun loadStats(forceRefresh: Boolean = false) {
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
                    forceRefresh = forceRefresh,
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
            startLexicalNotification()
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
                    onProgress = { progress ->
                        _loadingProgressSlow.value = progress
                        lexicalAnalysisManager.updateProgress(progress)
                    }
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
                stopLexicalNotification()
            }
        }
    }

    private fun startLexicalNotification() {
        lexicalAnalysisManager.updateProgress(0)
        context.startService(
            Intent(context, LexicalAnalysisService::class.java).apply {
                action = LexicalAnalysisService.ACTION_START
            }
        )
    }

    private fun stopLexicalNotification() {
        context.startService(
            Intent(context, LexicalAnalysisService::class.java).apply {
                action = LexicalAnalysisService.ACTION_STOP
            }
        )
    }

    fun setSelectedLimit(limit: Int) {
        _selectedLimit.value = limit
    }

    fun syncFilterState() {
        val newRange = filterState.globalTimeRange
        val newCustom = filterState.globalCustomRange
        if (newRange != _selectedTimeRange.value || newCustom != _customDateRange.value) {
            _selectedTimeRange.value = newRange
            _customDateRange.value = newCustom
            loadStats()
        }
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

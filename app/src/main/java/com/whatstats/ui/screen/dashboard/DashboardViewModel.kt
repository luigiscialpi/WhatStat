package com.whatstats.ui.screen.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstats.data.analysis.LexicalAnalysisManager
import com.whatstats.data.db.model.DateRange
import com.whatstats.data.db.model.DayCount
import com.whatstats.data.db.model.DayOfMonthCount
import com.whatstats.data.db.model.HourlyCount
import com.whatstats.data.db.model.MonthOfYearCount
import com.whatstats.data.db.model.TimeRange
import com.whatstats.data.db.model.YearTrendCount
import com.whatstats.data.repository.AnalyticsRepository
import com.whatstats.domain.model.GlobalStats
import com.whatstats.service.LexicalAnalysisService
import com.whatstats.ui.state.FilterStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel
    @Inject
    constructor(
        private val analyticsRepository: AnalyticsRepository,
        private val filterState: FilterStateHolder,
        private val lexicalAnalysisManager: LexicalAnalysisManager,
        @ApplicationContext private val context: Context,
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
                // Osserva i rename di chat Lite: aggiorna solo il nome della chat piu' attiva
                // senza ricaricare tutte le statistiche pesanti del dashboard.
                viewModelScope.launch {
                    analyticsRepository.liteRenameVersion.collect { _ ->
                        refreshMostActiveChatName()
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
            loadJob =
                viewModelScope.launch {
                    _isLoading.value = true
                    _isLoadingSlow.value = false
                    _slowStatsRequested.value = false
                    _error.value = null
                    try {
                        _loadingProgress.value = 0
                        val quickStats =
                            analyticsRepository.getQuickGlobalStats(
                                timeRange = _selectedTimeRange.value,
                                customRange = _customDateRange.value,
                                forceRefresh = forceRefresh,
                                onProgress = { progress -> _loadingProgress.value = progress },
                            )
                        _globalStats.value = quickStats

                        _hourlyData.value = quickStats.hourlyDistribution
                        _weeklyData.value = quickStats.weeklyDistribution
                        _dayOfMonthData.value = quickStats.dayOfMonthDistribution
                        _monthOfYearData.value = quickStats.monthOfYearDistribution
                        _yearTrendData.value = quickStats.yearTrendDistribution

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
                    // Legge SOLO dalla cache DataStore: non avvia mai il calcolo dell'indice.
                    // Il calcolo completo parte solo quando l'utente preme "Avvia analisi".
                    val slowStats = analyticsRepository.getCachedSlowGlobalStats() ?: return@launch
                    if (slowStats.topWords.isNotEmpty() || slowStats.topEmojis.isNotEmpty() || slowStats.topStickers.isNotEmpty()) {
                        _globalStats.value =
                            _globalStats.value?.copy(
                                topWords = slowStats.topWords,
                                topEmojis = slowStats.topEmojis,
                                topStickers = slowStats.topStickers,
                            )
                        _slowStatsRequested.value = true
                    }
                } catch (_: Exception) {
                }
            }
        }

        fun loadSlowStats() {
            _slowStatsRequested.value = true
            viewModelScope.launch {
                _isLoadingSlow.value = true
                _loadingProgressSlow.value = 0
                startLexicalNotification()
                try {
                    val (startTime, endTime) =
                        if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                            _customDateRange.value?.let {
                                analyticsRepository.getDateRangeBounds(it)
                            } ?: (null to null)
                        } else {
                            analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                        }
                    val slowStats =
                        analyticsRepository.getSlowGlobalStats(
                            startTime = startTime,
                            endTime = endTime,
                            onProgress = { progress ->
                                _loadingProgressSlow.value = progress
                                lexicalAnalysisManager.updateProgress(progress)
                            },
                        )
                    _globalStats.value =
                        _globalStats.value?.copy(
                            topWords = slowStats.topWords,
                            topEmojis = slowStats.topEmojis,
                            topStickers = slowStats.topStickers,
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
                },
            )
        }

        private fun stopLexicalNotification() {
            context.startService(
                Intent(context, LexicalAnalysisService::class.java).apply {
                    action = LexicalAnalysisService.ACTION_STOP
                },
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

        /**
         * Ricarica solo il nome della chat piu' attiva ri-interrogando il DB
         * senza ricaricare i conteggi dei messaggi o le distribuzioni.
         */
        private fun refreshMostActiveChatName() {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    val (startTime, endTime) =
                        if (_selectedTimeRange.value == TimeRange.CUSTOM) {
                            _customDateRange.value?.let {
                                analyticsRepository.getDateRangeBounds(it)
                            } ?: (null to null)
                        } else {
                            analyticsRepository.getTimeRangeBounds(_selectedTimeRange.value, _customDateRange.value)
                        }

                    // Ricarichiamo solo il JID e lo risolviamo di nuovo
                    val quickStats =
                        analyticsRepository.getQuickGlobalStats(
                            timeRange = _selectedTimeRange.value,
                            customRange = _customDateRange.value,
                            // Forziamo per bypassare la cache vecchia nell'AnalyticsRepository
                            forceRefresh = true,
                        )

                    _globalStats.value =
                        _globalStats.value?.copy(
                            mostActiveChat = quickStats.mostActiveChat,
                            mostActiveChatId = quickStats.mostActiveChatId,
                        )
                }.onFailure { e ->
                    android.util.Log.w("WhatStat", "refreshMostActiveChatName failed: ${e.message}")
                }
            }
        }
    }

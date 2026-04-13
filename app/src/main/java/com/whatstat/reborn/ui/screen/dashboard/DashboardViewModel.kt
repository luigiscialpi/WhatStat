package com.whatstat.reborn.ui.screen.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.db.model.HourlyCount
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

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadStats()
    }

    private fun loadStats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                android.util.Log.d("DashboardVM", "Caricamento statistiche in corso...")
                val stats = analyticsRepository.getGlobalStats()
                android.util.Log.d("DashboardVM", "Stats caricate: $stats")
                _globalStats.value = stats

                val hourly = analyticsRepository.getGlobalHourlyDistribution()
                android.util.Log.d("DashboardVM", "Hourly data: ${hourly.size} entries")
                _hourlyData.value = hourly
            } catch (e: Exception) {
                android.util.Log.e("DashboardVM", "Errore caricamento stats", e)
                _error.value = "Errore: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun retry() {
        loadStats()
    }
}

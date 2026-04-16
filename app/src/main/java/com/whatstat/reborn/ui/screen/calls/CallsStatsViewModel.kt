package com.whatstat.reborn.ui.screen.calls

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.db.model.CallStat
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.data.root.FileExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CallsStatsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _callStats = MutableStateFlow<List<CallStat>>(emptyList())
    val callStats: StateFlow<List<CallStat>> = _callStats.asStateFlow()

    private val avatarDir: File by lazy { File(context.filesDir, FileExtractor.AVATAR_DIR_DEST) }
    private fun getAvatarPath(jid: String): String? {
        if (!avatarDir.exists()) return null
        val f = File(avatarDir, "$jid.jpg")
        return if (f.exists()) f.absolutePath else null
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.ALL_TIME)
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

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
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
                _callStats.value = analyticsRepository.getCallStats(_chatId.value, startTime, endTime)
                    .map { it.copy(avatarPath = getAvatarPath(it.jid)) }
            } catch (e: Exception) {
                _callStats.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
package com.whatstat.reborn.ui.screen.calls

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.db.model.CallStat
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.ui.state.FilterStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File
import javax.inject.Inject

@HiltViewModel
class CallsStatsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val filterState: FilterStateHolder,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _callStats = MutableStateFlow<List<CallStat>>(emptyList())
    val callStats: StateFlow<List<CallStat>> = _callStats.asStateFlow()

    private val avatarDir: File by lazy { File(context.filesDir, FileExtractor.AVATAR_DIR_DEST) }
    private fun getAvatarPath(jid: String): String? {
        if (!avatarDir.exists()) return null
        if (jid == "me@s.whatsapp.net") {
            val me = File(avatarDir, "me.jpg")
            return if (me.exists()) me.absolutePath else null
        }
        val f = File(avatarDir, "$jid.jpg")
        return if (f.exists()) f.absolutePath else null
    }

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Il filtro iniziale viene letto da FilterStateHolder dopo che chatId è noto (in initialize)
    private val _selectedTimeRange = MutableStateFlow(TimeRange.ALL_TIME)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()

    private val _customDateRange = MutableStateFlow<DateRange?>(null)
    val customDateRange: StateFlow<DateRange?> = _customDateRange.asStateFlow()

    private val _firstMessageTimestamp = MutableStateFlow<Long?>(null)
    val firstMessageTimestamp: StateFlow<Long?> = _firstMessageTimestamp.asStateFlow()

    private val _chatId = MutableStateFlow<Long?>(null)

    private val _chatContextName = MutableStateFlow<String?>(null)
    val chatContextName: StateFlow<String?> = _chatContextName.asStateFlow()

    private val _chatContextAvatarPath = MutableStateFlow<String?>(null)
    val chatContextAvatarPath: StateFlow<String?> = _chatContextAvatarPath.asStateFlow()

    private var initialized = false

    fun initialize(chatId: Long?) {
        if (!initialized) {
            initialized = true
            _chatId.value = chatId
            // Ripristina il filtro dal contesto corretto
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
            // Carica il timestamp del primo messaggio
            viewModelScope.launch(Dispatchers.IO) {
                _firstMessageTimestamp.value = analyticsRepository.getFirstMessageTimestamp()
            }
            loadStats()
        }
    }

    fun setChatId(id: Long) {
        _chatId.value = id
        loadStats()
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
            loadStats()
        }
    }

    fun setTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        if (range != TimeRange.CUSTOM) {
            _customDateRange.value = null
        }
        if (_chatId.value != null) filterState.setChat(range, _customDateRange.value)
        else filterState.setGlobal(range, _customDateRange.value)
        loadStats()
    }

    fun setCustomDateRange(dateRange: DateRange?) {
        val wasCustom = _selectedTimeRange.value == TimeRange.CUSTOM
        _customDateRange.value = dateRange
        if (dateRange != null) {
            _selectedTimeRange.value = TimeRange.CUSTOM
            if (_chatId.value != null) filterState.setChat(TimeRange.CUSTOM, dateRange)
            else filterState.setGlobal(TimeRange.CUSTOM, dateRange)
        } else {
            if (wasCustom) {
                _selectedTimeRange.value = TimeRange.ALL_TIME
                if (_chatId.value != null) filterState.setChat(TimeRange.ALL_TIME)
                else filterState.setGlobal(TimeRange.ALL_TIME)
                loadStats()
            }
        }
    }

    fun applyCustomDateRange() {
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
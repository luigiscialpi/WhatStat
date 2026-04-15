package com.whatstat.reborn.ui.screen.chatdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.db.model.DateRange
import com.whatstat.reborn.data.db.model.TimeRange
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.domain.model.ChatStats
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val chatId: Long = savedStateHandle["chatId"] ?: -1L

    private val _chatStats = MutableStateFlow<ChatStats?>(null)
    val chatStats: StateFlow<ChatStats?> = _chatStats.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingSlow = MutableStateFlow(true)
    val isLoadingSlow: StateFlow<Boolean> = _isLoadingSlow.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedTimeRange = MutableStateFlow(TimeRange.ALL_TIME)
    val selectedTimeRange: StateFlow<TimeRange> = _selectedTimeRange.asStateFlow()
    
    private val _customDateRange = MutableStateFlow<DateRange?>(null)
    val customDateRange: StateFlow<DateRange?> = _customDateRange.asStateFlow()

    init {
        if (chatId != -1L) loadChatStats()
    }

    private fun loadChatStats() {
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

                // Fase 1: statistiche veloci — la UI appare subito
                val quickStats = analyticsRepository.getQuickChatStats(chatId, startTime, endTime)
                _chatStats.value = quickStats
                _isLoading.value = false

                // Fase 2: statistiche lente — aggiornano la UI progressivamente
                try {
                    val slowStats = analyticsRepository.getSlowChatStats(chatId, startTime, endTime)
                    _chatStats.value = quickStats.copy(
                        topWords                 = slowStats.topWords,
                        senderBreakdown          = slowStats.senderBreakdown,
                        conversationStarterBreakdown = slowStats.conversationStarterBreakdown,
                        averageResponseTimeMs    = slowStats.averageResponseTimeMs,
                        iStartedConversations    = slowStats.iStartedConversations,
                        theyStartedConversations = slowStats.theyStartedConversations,
                        longestMessage           = slowStats.longestMessage
                    )
                } catch (e: Exception) {
                    // Le stat veloci restano visibili anche se le lente falliscono
                    android.util.Log.w("WhatStat", "Slow stats failed: ${e.message}")
                } finally {
                    _isLoadingSlow.value = false
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Errore nel caricamento delle statistiche"
                _isLoading.value = false
                _isLoadingSlow.value = false
            }
        }
    }

    fun setTimeRange(range: TimeRange) {
        _selectedTimeRange.value = range
        loadChatStats()
    }
    
    fun setCustomDateRange(dateRange: DateRange?) {
        _customDateRange.value = dateRange
        if (dateRange != null) {
            _selectedTimeRange.value = TimeRange.CUSTOM
        }
        loadChatStats()
    }
}

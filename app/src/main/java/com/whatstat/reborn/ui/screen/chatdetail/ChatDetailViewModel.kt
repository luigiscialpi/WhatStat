package com.whatstat.reborn.ui.screen.chatdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        if (chatId != -1L) loadChatStats()
    }

    private fun loadChatStats() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val stats = analyticsRepository.getChatStats(chatId)
                _chatStats.value = stats
            } catch (e: Exception) {
                _error.value = e.message ?: "Errore nel caricamento delle statistiche"
            } finally {
                _isLoading.value = false
            }
        }
    }
}

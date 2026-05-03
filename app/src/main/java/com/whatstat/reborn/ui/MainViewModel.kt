package com.whatstat.reborn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.repository.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {
    val isLiteMode: StateFlow<Boolean> = analyticsRepository.isLiteMode

    fun resetLiteMode() {
        analyticsRepository.resetLiteMode()
    }
}

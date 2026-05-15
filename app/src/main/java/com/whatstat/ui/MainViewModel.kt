package com.whatstat.ui

import androidx.lifecycle.ViewModel
import com.whatstat.data.repository.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val analyticsRepository: AnalyticsRepository,
    ) : ViewModel() {
        val isLiteMode: StateFlow<Boolean> = analyticsRepository.isLiteMode

        private val _incomingZipUri = MutableStateFlow<String?>(null)
        val incomingZipUri: StateFlow<String?> = _incomingZipUri.asStateFlow()

        fun setIncomingZipUri(uri: String?) {
            _incomingZipUri.value = uri
        }

        fun resetLiteMode() {
            analyticsRepository.resetLiteMode()
        }
    }

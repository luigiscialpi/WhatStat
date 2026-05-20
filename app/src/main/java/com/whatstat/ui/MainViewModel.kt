package com.whatstat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.data.repository.AnalyticsRepository
import com.whatstat.data.store.AppPreferencesStore
import com.whatstat.data.store.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val analyticsRepository: AnalyticsRepository,
        private val appPreferencesStore: AppPreferencesStore,
    ) : ViewModel() {
        val isLiteMode: StateFlow<Boolean> = analyticsRepository.isLiteMode

        val themeMode: StateFlow<ThemeMode> =
            appPreferencesStore.themeMode.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = ThemeMode.SYSTEM,
            )

        val appLanguage: StateFlow<String> =
            appPreferencesStore.language.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = "system",
            )

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch {
                appPreferencesStore.setThemeMode(mode)
            }
        }

        fun setAppLanguage(lang: String) {
            viewModelScope.launch {
                appPreferencesStore.setLanguage(lang)
            }
        }

        private val _incomingZipUri = MutableStateFlow<String?>(null)
        val incomingZipUri: StateFlow<String?> = _incomingZipUri.asStateFlow()

        fun setIncomingZipUri(uri: String?) {
            _incomingZipUri.value = uri
        }

        fun resetLiteMode() {
            analyticsRepository.resetLiteMode()
        }
    }

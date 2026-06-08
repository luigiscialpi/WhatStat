package com.whatstats.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstats.ads.AdsRepository
import com.whatstats.ads.AdsUiState
import com.whatstats.data.repository.AnalyticsRepository
import com.whatstats.data.repository.WhatsAppRepository
import com.whatstats.data.store.AppPreferencesStore
import com.whatstats.data.store.ThemeMode
import com.whatstats.telemetry.TelemetryRepository
import com.whatstats.update.ForceUpdateState
import com.whatstats.update.UpdateGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        private val analyticsRepository: AnalyticsRepository,
        private val whatsAppRepository: WhatsAppRepository,
        private val appPreferencesStore: AppPreferencesStore,
        private val adsRepository: AdsRepository,
        private val telemetryRepository: TelemetryRepository,
        private val updateGate: UpdateGate,
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    ) : ViewModel() {
        val isLiteMode: StateFlow<Boolean> = analyticsRepository.isLiteMode
        val adsUiState: StateFlow<AdsUiState> = adsRepository.uiState

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

        val hasSeenTutorial: StateFlow<Boolean> =
            appPreferencesStore.hasSeenTutorial.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = true,
            )

        private val _showTutorial = MutableStateFlow(false)
        val showTutorial: StateFlow<Boolean> = _showTutorial.asStateFlow()

        private val _forceUpdateState = MutableStateFlow<ForceUpdateState>(ForceUpdateState.Idle)
        val forceUpdateState: StateFlow<ForceUpdateState> = _forceUpdateState.asStateFlow()

        fun initializeTelemetry() {
            telemetryRepository.initialize()
        }

        fun checkForUpdate(activity: Activity) {
            if (_forceUpdateState.value is ForceUpdateState.Required) return
            updateGate.check(activity) { customMessage ->
                _forceUpdateState.value = ForceUpdateState.Required(customMessage)
            }
        }

        fun trackAppOpen() {
            telemetryRepository.trackAppOpen()
        }

        fun trackScreenView(
            screenName: String,
            graphName: String,
        ) {
            telemetryRepository.trackScreenView(screenName, graphName)
        }

        fun trackClick(
            screenName: String,
            ctaName: String,
            section: String,
        ) {
            telemetryRepository.trackClick(screenName, ctaName, section)
        }

        fun trackAnalysisEvent(
            eventName: String,
            flowType: String,
            source: String,
        ) {
            telemetryRepository.trackAnalysisEvent(eventName, flowType, source)
        }

        fun trackScreenTime(
            screenName: String,
            stayMs: Long,
        ) {
            telemetryRepository.trackScreenTime(screenName, stayMs)
        }

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

        fun requestTutorial() {
            _showTutorial.value = true
        }

        fun dismissTutorial() {
            _showTutorial.value = false
        }

        fun completeTutorial() {
            viewModelScope.launch {
                appPreferencesStore.setTutorialSeen(true)
                _showTutorial.value = false
            }
        }

        fun requestAdsConsent(activity: Activity) {
            adsRepository.requestConsent(activity)
        }

        fun showAdsPrivacyOptions(activity: Activity) {
            adsRepository.showPrivacyOptions(activity)
        }

        fun dismissAdsMessage() {
            adsRepository.clearMessage()
        }

        fun tryShowInterstitialAfterLongOperation(
            activity: Activity,
            source: String,
        ): Boolean {
            return adsRepository.tryShowInterstitialAfterLongOperation(activity, source)
        }

        fun requestRewardedTabsUnlock(
            activity: Activity,
            source: String,
            onResult: (Boolean) -> Unit,
        ) {
            adsRepository.requestRewardedTabsUnlock(activity, source, onResult)
        }

        fun requestRewardedPredictionUnlock(
            activity: Activity,
            source: String,
            onResult: (Boolean) -> Unit,
        ) {
            adsRepository.requestRewardedPredictionUnlock(activity, source, onResult)
        }

        fun refreshRewardedInsightsLockState() {
            adsRepository.refreshRewardedInsightsLockState()
        }

        fun resetLiteMode() {
            analyticsRepository.resetLiteMode()
        }

        private val _resetComplete = MutableSharedFlow<Unit>()
        val resetComplete = _resetComplete.asSharedFlow()

        fun fullReset(
            clearRoot: Boolean,
            clearLite: Boolean,
        ) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    if (clearRoot) {
                        whatsAppRepository.cleanup()
                        File(context.filesDir, "avatars").deleteRecursively()
                    }
                    if (clearLite) {
                        analyticsRepository.fullResetLiteMode()
                        File(context.filesDir, "lite_media").deleteRecursively()
                    }
                }
                _resetComplete.emit(Unit)
            }
        }
    }

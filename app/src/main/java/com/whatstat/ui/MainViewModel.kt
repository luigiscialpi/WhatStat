package com.whatstat.ui

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.billing.BillingRepository
import com.whatstat.billing.ProBillingUiState
import com.whatstat.data.repository.AnalyticsRepository
import com.whatstat.data.repository.WhatsAppRepository
import com.whatstat.data.store.AppPreferencesStore
import com.whatstat.data.store.ThemeMode
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
        private val billingRepository: BillingRepository,
        @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    ) : ViewModel() {
        val isLiteMode: StateFlow<Boolean> = analyticsRepository.isLiteMode
        val proBillingUiState: StateFlow<ProBillingUiState> = billingRepository.uiState

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

        fun purchasePro(activity: Activity) {
            billingRepository.launchPurchase(activity)
        }

        fun restoreProPurchases() {
            billingRepository.restorePurchases()
        }

        fun dismissProBillingMessage() {
            billingRepository.clearMessage()
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

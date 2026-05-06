package com.whatstat.reborn.ui.screen.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.Context.MODE_PRIVATE
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.lite.LiteModeManager
import com.whatstat.reborn.data.repository.WhatsAppRepository
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.service.LiteImportService
import com.whatstat.reborn.util.DebugLogManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class LiteSetupViewModel @Inject constructor(
    private val liteModeManager: LiteModeManager,
    private val repository: WhatsAppRepository,
    private val analyticsRepository: AnalyticsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    companion object {
        private const val PREFS_NAME = "whatstat_lite_prefs"
        private const val LAST_OWNER_KEY = "last_owner_name"
    }

    private val _state = MutableStateFlow(LiteSetupState())
    val state: StateFlow<LiteSetupState> = _state.asStateFlow()
    private val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

    private var progressJob: Job? = null
    private var lastRequestedUri: String? = null
    private var lastRequestedAtMs: Long = 0L

    fun onZipSelected(uri: Uri) {
        val uriString = uri.toString()
        val now = System.currentTimeMillis()
        if (_state.value.isLoading && lastRequestedUri == uriString && (now - lastRequestedAtMs) < 5000L) {
            DebugLogManager.log("Lite setup zip ignored duplicate while loading | uri=$uriString")
            return
        }
        lastRequestedUri = uriString
        lastRequestedAtMs = now
        DebugLogManager.log("Lite setup zip selected | uri=$uri")
        _state.update {
            it.copy(
                selectedZipUri = uri,
                error = null,
                isComplete = false,
                showSenderSelection = false,
                identifiedSenders = emptyList(),
                progressPercent = 0f,
                progressLabel = ""
            )
        }
        startProcessing(uri, "Guest")
    }

    private fun startProcessing(uri: Uri, ownerName: String) {
        _state.update { it.copy(isLoading = true, progressPercent = 0f, progressLabel = "") }
        startForegroundService()

        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            liteModeManager.importProgress.collect { progress ->
                _state.update { it.copy(progressPercent = progress.percent, progressLabel = progress.label) }
            }
        }

        viewModelScope.launch {
            liteModeManager.importAndProcess(uri, ownerName)
                .onSuccess { result ->
                    progressJob?.cancel()
                    stopForegroundService()
                    DebugLogManager.log("Lite setup sender selection ready | senders=${result.identifiedSenders.joinToString(", ")}")
                    val rememberedOwner = prefs.getString(LAST_OWNER_KEY, null)
                    if (!rememberedOwner.isNullOrBlank() && result.identifiedSenders.contains(rememberedOwner)) {
                        DebugLogManager.log("Lite setup auto owner match | owner=$rememberedOwner")
                        _state.update {
                            it.copy(
                                isLoading = false,
                                showSenderSelection = false,
                                identifiedSenders = result.identifiedSenders
                            )
                        }
                        onSenderSelected(rememberedOwner)
                    } else {
                        _state.update { it.copy(
                            isLoading = false,
                            showSenderSelection = true,
                            identifiedSenders = result.identifiedSenders
                        ) }
                    }
                }
                .onFailure { e ->
                    progressJob?.cancel()
                    stopForegroundService()
                    DebugLogManager.log("Lite setup import failed", e)
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun onSenderSelected(name: String) {
        if (_state.value.isLoading) {
            DebugLogManager.log("Lite setup sender selection ignored while already finalizing | owner=$name")
            return
        }
        _state.update {
            it.copy(
                showSenderSelection = false,
                isLoading = true,
                progressPercent = 0.96f,
                progressLabel = "Finalizzazione..."
            )
        }

        viewModelScope.launch {
            DebugLogManager.log("Lite setup sender selected | owner=$name")
            val finalizeResult = withContext(NonCancellable) {
                liteModeManager.finalizeOwnerSelection(name)
            }
            if (finalizeResult.isSuccess) {
                withContext(NonCancellable) {
                    analyticsRepository.setLiteMode(true)
                    analyticsRepository.notifyLiteImportDone()
                }
                prefs.edit().putString(LAST_OWNER_KEY, name).apply()
                DebugLogManager.log("Lite setup cache notify complete | owner=$name")
                DebugLogManager.log("Lite setup import complete | owner=$name")
                _state.update { it.copy(isLoading = false, isComplete = true) }
            } else {
                val error = finalizeResult.exceptionOrNull()
                DebugLogManager.log("Lite setup finalize failed", error)
                _state.update { it.copy(isLoading = false, error = error?.message) }
            }
        }
    }

    fun consumeImportComplete() {
        _state.update { state ->
            if (state.isComplete) state.copy(isComplete = false) else state
        }
    }

    private fun startForegroundService() {
        val intent = Intent(context, LiteImportService::class.java)
            .setAction(LiteImportService.ACTION_START)
        context.startForegroundService(intent)
    }

    private fun stopForegroundService() {
        context.stopService(Intent(context, LiteImportService::class.java))
    }
}

data class LiteSetupState(
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
    val selectedZipUri: Uri? = null,
    val identifiedSenders: List<String> = emptyList(),
    val showSenderSelection: Boolean = false,
    val progressPercent: Float = 0f,
    val progressLabel: String = ""
)

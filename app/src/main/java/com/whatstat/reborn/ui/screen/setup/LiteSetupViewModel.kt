package com.whatstat.reborn.ui.screen.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.lite.LiteModeManager
import com.whatstat.reborn.data.repository.WhatsAppRepository
import com.whatstat.reborn.data.repository.AnalyticsRepository
import com.whatstat.reborn.service.LiteImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LiteSetupViewModel @Inject constructor(
    private val liteModeManager: LiteModeManager,
    private val repository: WhatsAppRepository,
    private val analyticsRepository: AnalyticsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(LiteSetupState())
    val state: StateFlow<LiteSetupState> = _state.asStateFlow()

    private var progressJob: Job? = null

    fun onZipSelected(uri: Uri) {
        _state.update { it.copy(selectedZipUri = uri, error = null) }
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
                    _state.update { it.copy(
                        isLoading = false,
                        showSenderSelection = true,
                        identifiedSenders = result.identifiedSenders
                    ) }
                }
                .onFailure { e ->
                    progressJob?.cancel()
                    stopForegroundService()
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun onSenderSelected(name: String) {
        _state.update {
            it.copy(
                showSenderSelection = false,
                isLoading = true,
                progressPercent = 0.96f,
                progressLabel = "Finalizzazione..."
            )
        }

        viewModelScope.launch {
            liteModeManager.finalizeOwnerSelection(name)
                .onSuccess {
                    analyticsRepository.setLiteMode(true)
                    analyticsRepository.notifyLiteImportDone()
                    _state.update { it.copy(isLoading = false, isComplete = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
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

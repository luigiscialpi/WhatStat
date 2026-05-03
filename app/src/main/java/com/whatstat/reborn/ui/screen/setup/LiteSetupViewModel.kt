package com.whatstat.reborn.ui.screen.setup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.lite.LiteModeManager
import com.whatstat.reborn.data.lite.ImportResult
import com.whatstat.reborn.data.repository.WhatsAppRepository
import com.whatstat.reborn.data.repository.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LiteSetupState())
    val state: StateFlow<LiteSetupState> = _state.asStateFlow()

    fun onZipSelected(uri: Uri) {
        _state.update { it.copy(selectedZipUri = uri, error = null) }
        // Avvia il parsing preliminare per trovare i mittenti
        startProcessing(uri, "Guest") // Nome temporaneo, verrà sovrascritto
    }

    private fun startProcessing(uri: Uri, ownerName: String) {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            liteModeManager.importAndProcess(uri, ownerName)
                .onSuccess { result ->
                    _state.update { it.copy(
                        isLoading = false,
                        showSenderSelection = true,
                        identifiedSenders = result.identifiedSenders
                    ) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun onSenderSelected(name: String) {
        _state.update { it.copy(showSenderSelection = false, isLoading = true) }
        val uri = _state.value.selectedZipUri ?: return
        
        viewModelScope.launch {
            liteModeManager.importAndProcess(uri, name)
                .onSuccess {
                    // Notifica il repository principale che i dati sono pronti
                    analyticsRepository.setLiteMode(true)
                    analyticsRepository.notifyLiteImportDone()
                    _state.update { it.copy(isLoading = false, isComplete = true) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }
}

data class LiteSetupState(
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
    val selectedZipUri: Uri? = null,
    val identifiedSenders: List<String> = emptyList(),
    val showSenderSelection: Boolean = false
)

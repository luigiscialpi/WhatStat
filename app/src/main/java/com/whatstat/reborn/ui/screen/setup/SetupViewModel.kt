package com.whatstat.reborn.ui.screen.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.data.repository.AppErrorException
import com.whatstat.reborn.data.repository.SetupStep
import com.whatstat.reborn.data.repository.WhatsAppRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.net.Uri
import com.whatstat.reborn.data.root.FileExtractor
import com.whatstat.reborn.data.repository.GlobalStepStatus
import com.whatstat.reborn.service.SetupService
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collectLatest

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val repository: WhatsAppRepository,
    private val liteModeManager: com.whatstat.reborn.data.lite.LiteModeManager,
    private val analyticsRepository: com.whatstat.reborn.data.repository.AnalyticsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Se c'è una cache per la Lite Mode E il flag è ancora attivo (non si è appena fatto un reset),
            // salta il setup. Il flag viene azzerato da resetLiteMode() prima di tornare al Setup.
            if (analyticsRepository.isLiteMode.value) {
                _state.update { it.copy(isComplete = true, isCheckingCache = false) }
            }
            // Se il database root è già aperto (es. dopo rotazione schermo), skip del setup
            else if (repository.isDatabaseReady()) {
                _state.update { it.copy(isComplete = true, isCheckingCache = false) }
            } else {
                // Tenta di ripristinare la sessione root salvata
                repository.tryRestoreSession()
                _state.update { it.copy(isCheckingCache = false) }
            }
        }

        // Osserva lo stato globale nel repository (proveniente dal Service o dal repo direttamente)
        viewModelScope.launch {
            repository.setupState.collectLatest { repoState ->
                _state.update { it.copy(
                    isLoading = repoState.isLoading,
                    isComplete = repoState.isComplete,
                    error = repoState.error,
                    rootStatus = mapGlobalStatus(repoState.rootStatus),
                    extractionStatus = mapGlobalStatus(repoState.extractionStatus),
                    decryptionStatus = mapGlobalStatus(repoState.decryptionStatus),
                    contactsStatus = mapGlobalStatus(repoState.contactsStatus),
                    decryptionProgress = repoState.decryptionProgress
                ) }
            }
        }
    }

    private fun mapGlobalStatus(status: GlobalStepStatus): StepStatus {
        return when (status) {
            GlobalStepStatus.IDLE -> StepStatus.IDLE
            GlobalStepStatus.LOADING -> StepStatus.LOADING
            GlobalStepStatus.SUCCESS -> StepStatus.SUCCESS
            GlobalStepStatus.ERROR -> StepStatus.ERROR
        }
    }

    fun startSetup() {
        if (_state.value.isLoading) return
        repository.resetState()
        val intent = Intent(context, SetupService::class.java).apply {
            action = SetupService.ACTION_START_FULL
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun showManualMode() {
        _state.update { it.copy(isManualModeVisible = true) }
    }

    fun handleManualFileSelected(uri: Uri, fileDestName: String) {
        viewModelScope.launch {
            try {
                repository.copyFileFromUri(uri, fileDestName)
                _state.update { current ->
                    when (fileDestName) {
                        FileExtractor.KEY_DEST -> current.copy(hasManualKey = true)
                        FileExtractor.CRYPT14_DEST -> current.copy(hasManualCrypt14 = true)
                        FileExtractor.WA_DB_DEST -> current.copy(hasManualWaDb = true)
                        FileExtractor.DB_DECRYPTED_DEST -> current.copy(hasManualDbDecrypted = true)
                        else -> current
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Errore lettura file: ${e.message}") }
            }
        }
    }

    fun startManualSetup() {
        if (_state.value.isLoading) return
        repository.resetState()
        val intent = Intent(context, SetupService::class.java).apply {
            action = SetupService.ACTION_START_MANUAL
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun cancelSetup() {
        val intent = Intent(context, SetupService::class.java).apply {
            action = SetupService.ACTION_CANCEL
        }
        context.startService(intent)
    }
}

data class SetupState(
    val isCheckingCache: Boolean  = true, // Flag dedicato per la fase di avvio iniziale
    val isLoading: Boolean        = false,
    val isComplete: Boolean       = false,
    val error: String?            = null,
    val rootStatus: StepStatus    = StepStatus.IDLE,
    val extractionStatus: StepStatus = StepStatus.IDLE,
    val decryptionStatus: StepStatus = StepStatus.IDLE,
    val contactsStatus: StepStatus   = StepStatus.IDLE,
    val decryptionProgress: Float    = 0f,
    
    // UI State per la modalità manuale
    val isManualModeVisible: Boolean = false,
    val hasManualKey: Boolean        = false,
    val hasManualCrypt14: Boolean    = false,
    val hasManualWaDb: Boolean       = false,
    val hasManualDbDecrypted: Boolean = false
)

enum class StepStatus { IDLE, LOADING, SUCCESS, ERROR }

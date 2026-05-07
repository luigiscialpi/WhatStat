package com.whatstat.reborn.ui.screen.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.whatstat.reborn.data.lite.LiteDatabase

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val repository: WhatsAppRepository,
    private val analyticsRepository: com.whatstat.reborn.data.repository.AnalyticsRepository,
    private val liteDatabase: LiteDatabase,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Tenta di ripristinare la sessione root salvata
            repository.tryRestoreSession()
            
            // Osserva lo stato di LiteMode e RootDatabase
            analyticsRepository.isLiteMode.collectLatest { isLite ->
                val hasLiteDb = withContext(Dispatchers.IO) { liteDatabase.hasChatData() }
                val isRootReady = repository.isDatabaseReady()
                
                if (isLite) {
                    _state.update {
                        it.copy(
                            isComplete = true,
                            isCheckingCache = false,
                            hasLiteDb = hasLiteDb
                        )
                    }
                } else {
                    // Se non è pronto subito e stiamo ancora controllando la cache (avvio)
                    if (!isRootReady && _state.value.isCheckingCache) {
                        delay(500)
                    }
                    
                    val finalIsRootReady = repository.isDatabaseReady()
                    val autoNavigate = savedStateHandle.get<Boolean>("autoNavigate") ?: true
                    
                    _state.update {
                        it.copy(
                            isRootReady = finalIsRootReady,
                            // Naviga automaticamente solo se:
                            // 1. È il primo avvio (isCheckingCache == true)
                            // 2. Il flag autoNavigate è true
                            isComplete = if (it.isCheckingCache && autoNavigate) finalIsRootReady else it.isComplete,
                            isCheckingCache = false,
                            hasLiteDb = hasLiteDb
                        )
                    }
                }
            }
        }

        // Osserva lo stato globale nel repository (proveniente dal Service o dal repo direttamente)
        viewModelScope.launch {
            repository.setupState.collectLatest { repoState ->
                val autoNavigate = savedStateHandle.get<Boolean>("autoNavigate") ?: true
                _state.update { it.copy(
                    isLoading = repoState.isLoading,
                    // Sovrascrivi isComplete solo se autoNavigate è true o se stiamo resettando (isComplete false)
                    isComplete = if (autoNavigate || !repoState.isComplete) repoState.isComplete else it.isComplete,
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

    fun activateLiteMode() {
        analyticsRepository.setLiteMode(true)
        _state.update { it.copy(hasLiteDb = true, isComplete = true) }
    }

    fun resumeRootSession() {
        if (_state.value.isRootReady) {
            _state.update { it.copy(isComplete = true) }
        }
    }

    fun clearRootSession() {
        viewModelScope.launch {
            repository.cleanup()
            _state.update { it.copy(isRootReady = false, isComplete = false) }
        }
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
    val hasLiteDb: Boolean           = false,
    val isRootReady: Boolean         = false, // Sessione Root decifrata e pronta
    
    // UI State per la modalità manuale
    val isManualModeVisible: Boolean = false,
    val hasManualKey: Boolean        = false,
    val hasManualCrypt14: Boolean    = false,
    val hasManualWaDb: Boolean       = false,
    val hasManualDbDecrypted: Boolean = false
)

enum class StepStatus { IDLE, LOADING, SUCCESS, ERROR }

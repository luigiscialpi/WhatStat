package com.whatstat.ui.screen.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.data.lite.LiteDatabase
import com.whatstat.data.repository.GlobalStepStatus
import com.whatstat.data.repository.WhatsAppRepository
import com.whatstat.data.root.FileExtractor
import com.whatstat.service.SetupService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SetupViewModel
    @Inject
    constructor(
        private val repository: WhatsAppRepository,
        private val analyticsRepository: com.whatstat.data.repository.AnalyticsRepository,
        private val liteDatabase: LiteDatabase,
        private val savedStateHandle: androidx.lifecycle.SavedStateHandle,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _state = MutableStateFlow(SetupState())
        val state: StateFlow<SetupState> = _state.asStateFlow()

        init {
            val autoNavigate = savedStateHandle.get<Boolean>("autoNavigate") ?: true

            // Se torniamo con autoNavigate=false (es. back dalla Dashboard),
            // resetta lo stato del repository per evitare che un isComplete=true
            // stale venga propagato al nuovo SetupViewModel.
            if (!autoNavigate) {
                repository.resetState()
            }

            viewModelScope.launch {
                // Tenta sempre di ripristinare la sessione root: sia all'avvio normale
                // (autoNavigate=true) sia al ritorno da Dashboard (autoNavigate=false).
                // La card "Nuovo backup rilevato" deve restare visibile finché la
                // condizione è vera. L'auto-navigazione è bloccata separatamente
                // da shouldAutoNavigate nella LaunchedEffect di SetupScreen.
                repository.tryRestoreSession()

                // Osserva lo stato di LiteMode e RootDatabase
                analyticsRepository.isLiteMode.collectLatest { isLite ->
                    val hasLiteDb = withContext(Dispatchers.IO) { liteDatabase.hasChatData() }
                    val isRootReady = repository.isDatabaseReady()
                    val autoNav = savedStateHandle.get<Boolean>("autoNavigate") ?: true

                    if (isLite) {
                        _state.update {
                            it.copy(
                                // Auto-naviga verso Lite solo al primo avvio con autoNavigate=true
                                isComplete = it.isCheckingCache && autoNav,
                                isCheckingCache = false,
                                hasLiteDb = hasLiteDb,
                                isRootReady = isRootReady,
                            )
                        }
                    } else {
                        // Se non è pronto subito e stiamo ancora controllando la cache (avvio)
                        if (!isRootReady && _state.value.isCheckingCache) {
                            delay(500)
                        }

                        val finalIsRootReady = repository.isDatabaseReady()

                        _state.update {
                            it.copy(
                                isRootReady = finalIsRootReady,
                                isComplete = if (it.isCheckingCache && autoNav) finalIsRootReady else it.isComplete,
                                isCheckingCache = false,
                                hasLiteDb = hasLiteDb,
                            )
                        }
                    }
                }
            }

            // Osserva lo stato globale nel repository (proveniente dal Service o dal repo direttamente)
            viewModelScope.launch {
                repository.setupState.collectLatest { repoState ->
                    val autoNavigate = savedStateHandle.get<Boolean>("autoNavigate") ?: true
                    val isRootReady =
                        if (repoState.isComplete && !repoState.isLoading) {
                            repository.isDatabaseReady()
                        } else {
                            _state.value.isRootReady
                        }
                    _state.update {
                        it.copy(
                            isLoading = repoState.isLoading,
                            isRootReady = isRootReady,
                            isComplete = if (autoNavigate || !repoState.isComplete) repoState.isComplete else it.isComplete,
                            error = repoState.error,
                            rootStatus = mapGlobalStatus(repoState.rootStatus),
                            extractionStatus = mapGlobalStatus(repoState.extractionStatus),
                            decryptionStatus = mapGlobalStatus(repoState.decryptionStatus),
                            contactsStatus = mapGlobalStatus(repoState.contactsStatus),
                            decryptionProgress = repoState.decryptionProgress,
                            hasNewDbAvailable = repoState.hasNewDbAvailable,
                        )
                    }
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
            analyticsRepository.setLiteMode(false) // Assicura che il routing vada a Root
            repository.resetState()
            val intent =
                Intent(context, SetupService::class.java).apply {
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

        fun handleManualFileSelected(
            uri: Uri,
            fileDestName: String,
        ) {
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
            analyticsRepository.setLiteMode(false) // Assicura che il routing vada a Root
            repository.resetState()
            val intent =
                Intent(context, SetupService::class.java).apply {
                    action = SetupService.ACTION_START_MANUAL
                }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelSetup() {
            val intent =
                Intent(context, SetupService::class.java).apply {
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
                analyticsRepository.setLiteMode(false) // Assicura che il routing vada a Root
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
    // Flag dedicato per la fase di avvio iniziale
    val isCheckingCache: Boolean = true,
    val isLoading: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
    val rootStatus: StepStatus = StepStatus.IDLE,
    val extractionStatus: StepStatus = StepStatus.IDLE,
    val decryptionStatus: StepStatus = StepStatus.IDLE,
    val contactsStatus: StepStatus = StepStatus.IDLE,
    val decryptionProgress: Float = 0f,
    val hasLiteDb: Boolean = false,
    // Sessione Root decifrata e pronta
    val isRootReady: Boolean = false,
    // UI State per la modalità manuale
    val isManualModeVisible: Boolean = false,
    val hasManualKey: Boolean = false,
    val hasManualCrypt14: Boolean = false,
    val hasManualWaDb: Boolean = false,
    val hasManualDbDecrypted: Boolean = false,
    val hasNewDbAvailable: Boolean = false,
)

enum class StepStatus { IDLE, LOADING, SUCCESS, ERROR }

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

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val repository: WhatsAppRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SetupState())
    val state: StateFlow<SetupState> = _state.asStateFlow()

    init {
        // Se il database è già aperto (es. dopo rotazione schermo), skip del setup
        if (repository.isDatabaseReady()) {
            _state.update { it.copy(isComplete = true) }
        }
    }

    fun startSetup() {
        if (_state.value.isLoading) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    rootStatus = StepStatus.IDLE,
                    extractionStatus = StepStatus.IDLE,
                    decryptionStatus = StepStatus.IDLE,
                    contactsStatus = StepStatus.IDLE
                )
            }

            // Mostra lo step corrente come LOADING
            _state.update { it.copy(rootStatus = StepStatus.LOADING) }

            val result = repository.performSetup { step ->
                when (step) {
                    SetupStep.ROOT_CHECK -> _state.update {
                        it.copy(rootStatus = StepStatus.SUCCESS, extractionStatus = StepStatus.LOADING)
                    }
                    SetupStep.FILE_EXTRACTION -> _state.update {
                        it.copy(extractionStatus = StepStatus.SUCCESS, decryptionStatus = StepStatus.LOADING)
                    }
                    SetupStep.DECRYPTION -> _state.update {
                        it.copy(decryptionStatus = StepStatus.SUCCESS, contactsStatus = StepStatus.LOADING)
                    }
                    SetupStep.DATABASE_OPEN -> _state.update {
                        it.copy(contactsStatus = StepStatus.SUCCESS)
                    }
                }
            }

            result.fold(
                onSuccess = {
                    _state.update { it.copy(isLoading = false, isComplete = true) }
                },
                onFailure = { throwable ->
                    val errorMessage = when (throwable) {
                        is AppErrorException -> throwable.appError.userMessage
                        else -> throwable.message ?: "Errore sconosciuto"
                    }
                    // Marca lo step corrente in LOADING come ERROR
                    _state.update { current ->
                        current.copy(
                            isLoading = false,
                            error = errorMessage,
                            rootStatus = if (current.rootStatus == StepStatus.LOADING) StepStatus.ERROR else current.rootStatus,
                            extractionStatus = if (current.extractionStatus == StepStatus.LOADING) StepStatus.ERROR else current.extractionStatus,
                            decryptionStatus = if (current.decryptionStatus == StepStatus.LOADING) StepStatus.ERROR else current.decryptionStatus,
                            contactsStatus = if (current.contactsStatus == StepStatus.LOADING) StepStatus.ERROR else current.contactsStatus
                        )
                    }
                }
            )
        }
    }
}

data class SetupState(
    val isLoading: Boolean        = false,
    val isComplete: Boolean       = false,
    val error: String?            = null,
    val rootStatus: StepStatus    = StepStatus.IDLE,
    val extractionStatus: StepStatus = StepStatus.IDLE,
    val decryptionStatus: StepStatus = StepStatus.IDLE,
    val contactsStatus: StepStatus   = StepStatus.IDLE
)

enum class StepStatus { IDLE, LOADING, SUCCESS, ERROR }

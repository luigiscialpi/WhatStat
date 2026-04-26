package com.whatstat.reborn.ui.tutorial

import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whatstat.reborn.ui.tutorial.data.ScreenTutorialData
import com.whatstat.reborn.ui.tutorial.data.TutorialStep
import com.whatstat.reborn.ui.tutorial.data.TutorialCompletionState
import com.whatstat.reborn.ui.tutorial.data.TutorialDataStoreManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the tutorial system.
 */
data class TutorialUiState(
    val isTutorialActive: Boolean = false,
    val currentScreen: String? = null,
    val currentStepIndex: Int = 0,
    val steps: List<TutorialStep> = emptyList(),
    val isCompleted: Boolean = false,
    val targetBounds: Map<String, Rect> = emptyMap()
)

@HiltViewModel
class TutorialManager @Inject constructor(
    private val dataStoreManager: TutorialDataStoreManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(TutorialUiState())
    val uiState: StateFlow<TutorialUiState> = _uiState.asStateFlow()

    /**
     * Starts a tutorial for the given screen.
     */
    fun startTutorialForScreen(screen: String, stepsProvider: ScreenTutorialData) {
        viewModelScope.launch {
            val completionState = dataStoreManager.tutorialCompletionState.first()
            val lastStepIndex = completionState.getLastStepIndex(screen)
            val isCompleted = completionState.isScreenCompleted(screen)

            _uiState.value = TutorialUiState(
                isTutorialActive = true,
                currentScreen = screen,
                currentStepIndex = if (isCompleted) 0 else lastStepIndex,
                steps = stepsProvider.steps,
                isCompleted = isCompleted,
                targetBounds = emptyMap()
            )
        }
    }

    /**
     * Records the position of a target element.
     */
    fun setTargetBounds(key: String, bounds: Rect) {
        val current = _uiState.value
        if (current.isTutorialActive) {
            _uiState.value = current.copy(
                targetBounds = current.targetBounds + (key to bounds)
            )
        }
    }

    /**
     * Moves to the next step.
     */
    fun nextStep() {
        val current = _uiState.value
        if (current.isCompleted) return

        val nextIndex = current.currentStepIndex + 1
        if (nextIndex >= current.steps.size) {
            completeCurrentTutorial()
        } else {
            _uiState.value = current.copy(currentStepIndex = nextIndex)
            saveProgress(current.currentScreen ?: return, nextIndex)
        }
    }

    /**
     * Moves to the previous step.
     */
    fun previousStep() {
        val current = _uiState.value
        if (current.isCompleted) return

        val prevIndex = current.currentStepIndex - 1
        if (prevIndex >= 0) {
            _uiState.value = current.copy(currentStepIndex = prevIndex)
            saveProgress(current.currentScreen ?: return, prevIndex)
        }
    }

    /**
     * Closes the tutorial.
     */
    fun closeTutorial() {
        _uiState.value = TutorialUiState()
    }

    /**
     * Completes the current tutorial.
     */
    fun completeCurrentTutorial() {
        val screen = _uiState.value.currentScreen ?: return
        completeTutorial(screen)
    }

    /**
     * Completes the tutorial for a specific screen.
     */
    fun completeTutorial(screen: String) {
        viewModelScope.launch {
            dataStoreManager.markScreenCompleted(screen)
            _uiState.value = TutorialUiState(isTutorialActive = false)
        }
    }

    /**
     * Saves progress.
     */
    private fun saveProgress(screen: String, stepIndex: Int) {
        viewModelScope.launch {
            dataStoreManager.updateLastStep(screen, stepIndex)
        }
    }

    /**
     * Gets current step.
     */
    val currentStep: TutorialStep?
        get() = _uiState.value.steps.getOrNull(_uiState.value.currentStepIndex)

    val isTutorialActive: Boolean
        get() = _uiState.value.isTutorialActive

    val currentScreen: String?
        get() = _uiState.value.currentScreen

    fun getTargetBounds(key: String): Rect? = _uiState.value.targetBounds[key]
}

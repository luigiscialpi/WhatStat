package com.whatstat.reborn.ui.tutorial.data

import androidx.datastore.core.Serializer
import androidx.datastore.preferences.core.Preferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.io.InputStream
import java.io.OutputStream

/**
 * Data class representing the tutorial completion state.
 * Stored in DataStore and used to track which screens have been completed.
 *
 * @param completedScreens Set of screen names that the user has completed the tutorial for
 * @param lastStepReached Map of screen name to the last step index reached (for resuming)
 */
@Serializable
data class TutorialCompletionState(
    val completedScreens: Set<String> = emptySet(),
    val lastStepReached: Map<String, Int> = emptyMap()
) {
    fun isScreenCompleted(screen: String): Boolean = screen in completedScreens

    fun getLastStepIndex(screen: String): Int = lastStepReached[screen] ?: 0

    fun markStepCompleted(screen: String, stepIndex: Int): TutorialCompletionState {
        return copy(
            lastStepReached = lastStepReached + (screen to stepIndex)
        )
    }

    fun markScreenCompleted(screen: String): TutorialCompletionState {
        return copy(
            completedScreens = completedScreens + screen
        )
    }
}

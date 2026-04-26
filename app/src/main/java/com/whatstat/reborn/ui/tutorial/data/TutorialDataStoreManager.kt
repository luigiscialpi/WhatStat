package com.whatstat.reborn.ui.tutorial.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whatstat.reborn.ui.tutorial.data.TutorialCompletionState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tutorialDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "tutorial_settings"
)

@Singleton
class TutorialDataStoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.tutorialDataStore

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private val COMPLETION_STATE_KEY = stringPreferencesKey("completion_state")
    }

    /**
     * Flow emitting the current tutorial completion state.
     */
    val tutorialCompletionState: Flow<TutorialCompletionState> = dataStore.data
        .map { preferences ->
            val jsonString = preferences[COMPLETION_STATE_KEY]
            if (jsonString != null) {
                try {
                    json.decodeFromString<TutorialCompletionState>(jsonString)
                } catch (e: Exception) {
                    TutorialCompletionState()
                }
            } else {
                TutorialCompletionState()
            }
        }

    /**
     * Saves the tutorial completion state.
     */
    suspend fun saveCompletionState(state: TutorialCompletionState) {
        dataStore.edit { preferences ->
            val jsonString = json.encodeToString(state)
            preferences[COMPLETION_STATE_KEY] = jsonString
        }
    }

    /**
     * Updates the last step reached for a given screen.
     */
    suspend fun updateLastStep(screen: String, stepIndex: Int) {
        dataStore.edit { preferences ->
            val current = getCurrentState(preferences)
            val updated = current.copy(
                lastStepReached = current.lastStepReached + (screen to stepIndex)
            )
            preferences[COMPLETION_STATE_KEY] = json.encodeToString(updated)
        }
    }

    /**
     * Marks a screen as completed.
     */
    suspend fun markScreenCompleted(screen: String) {
        dataStore.edit { preferences ->
            val current = getCurrentState(preferences)
            val updated = current.copy(
                completedScreens = current.completedScreens + screen
            )
            preferences[COMPLETION_STATE_KEY] = json.encodeToString(updated)
        }
    }

    /**
     * Resets all tutorial progress.
     */
    suspend fun resetAll() {
        dataStore.edit { preferences ->
            preferences.remove(COMPLETION_STATE_KEY)
        }
    }

    private suspend fun getCurrentState(preferences: Preferences): TutorialCompletionState {
        val jsonString = preferences[COMPLETION_STATE_KEY]
        return if (jsonString != null) {
            try {
                json.decodeFromString<TutorialCompletionState>(jsonString)
            } catch (e: Exception) {
                TutorialCompletionState()
            }
        } else {
            TutorialCompletionState()
        }
    }
}


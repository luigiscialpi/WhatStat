package com.whatstats.data.store
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

@Singleton
class AppPreferencesStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val dataStore = context.appPreferencesDataStore

        companion object {
            private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
            private val LANGUAGE_KEY = stringPreferencesKey("app_language")
            private val TUTORIAL_SEEN_KEY = booleanPreferencesKey("tutorial_seen")
            private val APP_LAUNCH_COUNT_KEY = intPreferencesKey("app_launch_count")
            private val LAST_REVIEW_REQUEST_TIME_KEY = longPreferencesKey("last_review_request_time")
            private val REVIEW_PROMPT_SHOWN_COUNT_KEY = intPreferencesKey("review_prompt_shown_count")
            private val HAS_COMPLETED_REVIEW_FLOW_KEY = booleanPreferencesKey("has_completed_review_flow")
            private val HAS_SUCCESSFUL_ANALYSIS_KEY = booleanPreferencesKey("has_successful_analysis")
            private val LAST_FEEDBACK_SENT_TIME_KEY = longPreferencesKey("last_feedback_sent_time")
            private val FEEDBACK_FALLBACK_USER_ID_KEY = stringPreferencesKey("feedback_fallback_user_id")
        }

        val themeMode: Flow<ThemeMode> =
            dataStore.data.map { preferences ->
                val themeName = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
                try {
                    ThemeMode.valueOf(themeName)
                } catch (e: Exception) {
                    ThemeMode.SYSTEM
                }
            }

        suspend fun setThemeMode(mode: ThemeMode) {
            dataStore.edit { preferences ->
                preferences[THEME_MODE_KEY] = mode.name
            }
        }

        val language: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[LANGUAGE_KEY] ?: "system"
            }

        suspend fun setLanguage(langCode: String) {
            dataStore.edit { preferences ->
                preferences[LANGUAGE_KEY] = langCode
            }
        }

        val hasSeenTutorial: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[TUTORIAL_SEEN_KEY] ?: false
            }

        suspend fun setTutorialSeen(seen: Boolean = true) {
            dataStore.edit { preferences ->
                preferences[TUTORIAL_SEEN_KEY] = seen
            }
        }

        val appLaunchCount: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[APP_LAUNCH_COUNT_KEY] ?: 0
            }

        suspend fun incrementAppLaunchCount() {
            dataStore.edit { preferences ->
                val current = preferences[APP_LAUNCH_COUNT_KEY] ?: 0
                preferences[APP_LAUNCH_COUNT_KEY] = current + 1
            }
        }

        val lastReviewRequestTime: Flow<Long> =
            dataStore.data.map { preferences ->
                preferences[LAST_REVIEW_REQUEST_TIME_KEY] ?: 0L
            }

        val reviewPromptShownCount: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[REVIEW_PROMPT_SHOWN_COUNT_KEY] ?: 0
            }

        suspend fun recordReviewPromptShown(timestamp: Long) {
            dataStore.edit { preferences ->
                val current = preferences[REVIEW_PROMPT_SHOWN_COUNT_KEY] ?: 0
                preferences[REVIEW_PROMPT_SHOWN_COUNT_KEY] = current + 1
                preferences[LAST_REVIEW_REQUEST_TIME_KEY] = timestamp
            }
        }

        val hasCompletedReviewFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[HAS_COMPLETED_REVIEW_FLOW_KEY] ?: false
            }

        suspend fun markReviewFlowCompleted() {
            dataStore.edit { preferences ->
                preferences[HAS_COMPLETED_REVIEW_FLOW_KEY] = true
            }
        }

        val hasSuccessfulAnalysis: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[HAS_SUCCESSFUL_ANALYSIS_KEY] ?: false
            }

        suspend fun markAnalysisSuccessful() {
            dataStore.edit { preferences ->
                preferences[HAS_SUCCESSFUL_ANALYSIS_KEY] = true
            }
        }

        val lastFeedbackSentTime: Flow<Long> =
            dataStore.data.map { preferences ->
                preferences[LAST_FEEDBACK_SENT_TIME_KEY] ?: 0L
            }

        suspend fun recordFeedbackSent(timestamp: Long) {
            dataStore.edit { preferences ->
                preferences[LAST_FEEDBACK_SENT_TIME_KEY] = timestamp
            }
        }

        val feedbackFallbackUserId: Flow<String?> =
            dataStore.data.map { preferences ->
                preferences[FEEDBACK_FALLBACK_USER_ID_KEY]
            }

        suspend fun setFeedbackFallbackUserId(userId: String) {
            dataStore.edit { preferences ->
                preferences[FEEDBACK_FALLBACK_USER_ID_KEY] = userId
            }
        }
    }

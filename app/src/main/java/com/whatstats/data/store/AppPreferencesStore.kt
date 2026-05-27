package com.whatstats.data.store
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    }

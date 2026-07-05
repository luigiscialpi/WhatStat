package com.whatstats.telemetry

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private val Context.telemetryPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "telemetry_prefs")

@Singleton
class TelemetryPrefsStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val dataStore = context.telemetryPrefsDataStore

        companion object {
            private val DAILY_DATE_KEY = stringPreferencesKey("telemetry_daily_date")
            private val DAILY_COUNT_KEY = intPreferencesKey("telemetry_daily_count")
            private val INSTALLATION_ID_KEY = stringPreferencesKey("telemetry_installation_id")
        }

        suspend fun canTrackEvent(dailyCap: Int): Boolean {
            val today = LocalDate.now().toString()
            var canTrack = true
            dataStore.edit { preferences ->
                val storedDate = preferences[DAILY_DATE_KEY]
                val storedCount = preferences[DAILY_COUNT_KEY] ?: 0
                val currentCount = if (storedDate == today) storedCount else 0
                if (currentCount >= dailyCap) {
                    canTrack = false
                    return@edit
                }
                preferences[DAILY_DATE_KEY] = today
                preferences[DAILY_COUNT_KEY] = currentCount + 1
            }
            return canTrack
        }

        suspend fun getInstallationId(): String? = dataStore.data.first()[INSTALLATION_ID_KEY]

        suspend fun setInstallationId(installationId: String) {
            dataStore.edit { preferences ->
                preferences[INSTALLATION_ID_KEY] = installationId
            }
        }
    }

package com.whatstats.ads

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.rewardedUnlockDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "rewarded_unlock_store",
)

@Singleton
class RewardedUnlockStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val dataStore = context.rewardedUnlockDataStore

        data class UnlockState(
            val tabsUnlockUntilMs: Long,
            val predictionUnlockUntilMs: Long,
            val adsFreeUnlockUntilMs: Long,
        )

        private companion object {
            val KEY_TABS_UNLOCK_UNTIL_MS = longPreferencesKey("tabs_unlock_until_ms")
            val KEY_PREDICTION_UNLOCK_UNTIL_MS = longPreferencesKey("prediction_unlock_until_ms")
            val KEY_ADS_FREE_UNLOCK_UNTIL_MS = longPreferencesKey("ads_free_unlock_until_ms")
        }

        val unlockState: Flow<UnlockState> =
            dataStore.data.map { prefs ->
                UnlockState(
                    tabsUnlockUntilMs = prefs[KEY_TABS_UNLOCK_UNTIL_MS] ?: 0L,
                    predictionUnlockUntilMs = prefs[KEY_PREDICTION_UNLOCK_UNTIL_MS] ?: 0L,
                    adsFreeUnlockUntilMs = prefs[KEY_ADS_FREE_UNLOCK_UNTIL_MS] ?: 0L,
                )
            }

        suspend fun setTabsUnlockUntilMs(untilMs: Long) {
            dataStore.edit { prefs ->
                prefs[KEY_TABS_UNLOCK_UNTIL_MS] = untilMs
            }
        }

        suspend fun setPredictionUnlockUntilMs(untilMs: Long) {
            dataStore.edit { prefs ->
                prefs[KEY_PREDICTION_UNLOCK_UNTIL_MS] = untilMs
            }
        }

        suspend fun setAdsFreeUnlockUntilMs(untilMs: Long) {
            dataStore.edit { prefs ->
                prefs[KEY_ADS_FREE_UNLOCK_UNTIL_MS] = untilMs
            }
        }
    }

package com.whatstats.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.badgeUnlockDataStore: DataStore<Preferences> by preferencesDataStore(name = "badge_unlocks")
private val KEY_UNLOCKED_ROOT = stringSetPreferencesKey("unlocked_ids_root")
private val KEY_UNLOCKED_LITE = stringSetPreferencesKey("unlocked_ids_lite")
private val KEY_INITIALIZED_ROOT = booleanPreferencesKey("initialized_root")
private val KEY_INITIALIZED_LITE = booleanPreferencesKey("initialized_lite")

@Singleton
class BadgeUnlockStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        data class Snapshot(
            val initialized: Boolean,
            val unlockedIds: Set<String>,
        )

        private val dataStore = context.badgeUnlockDataStore

        suspend fun read(isLiteMode: Boolean): Snapshot {
            val prefs = dataStore.data.first()
            return Snapshot(
                initialized = prefs[initializedKey(isLiteMode)] ?: false,
                unlockedIds = prefs[unlockedKey(isLiteMode)] ?: emptySet(),
            )
        }

        suspend fun markUnlocked(
            isLiteMode: Boolean,
            ids: Set<String>,
        ) {
            dataStore.edit { prefs ->
                prefs[unlockedKey(isLiteMode)] = (prefs[unlockedKey(isLiteMode)] ?: emptySet()) + ids
                prefs[initializedKey(isLiteMode)] = true
            }
        }

        suspend fun clear(isLiteMode: Boolean) {
            dataStore.edit { prefs ->
                prefs.remove(unlockedKey(isLiteMode))
                prefs.remove(initializedKey(isLiteMode))
            }
        }

        private fun unlockedKey(isLiteMode: Boolean) = if (isLiteMode) KEY_UNLOCKED_LITE else KEY_UNLOCKED_ROOT

        private fun initializedKey(isLiteMode: Boolean) = if (isLiteMode) KEY_INITIALIZED_LITE else KEY_INITIALIZED_ROOT
    }

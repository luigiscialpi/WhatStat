package com.whatstat.billing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.proEntitlementDataStore: DataStore<Preferences> by preferencesDataStore(name = "pro_entitlement")

@Singleton
class ProEntitlementStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val dataStore = context.proEntitlementDataStore

        companion object {
            private val IS_PRO_KEY = booleanPreferencesKey("is_pro_cached")
            private val LAST_SYNC_MS_KEY = longPreferencesKey("last_purchase_check_ms")
        }

        val isPro: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[IS_PRO_KEY] ?: false
            }

        suspend fun setPro(isPro: Boolean) {
            val now = System.currentTimeMillis()
            dataStore.edit { preferences ->
                preferences[IS_PRO_KEY] = isPro
                preferences[LAST_SYNC_MS_KEY] = now
            }
        }
    }

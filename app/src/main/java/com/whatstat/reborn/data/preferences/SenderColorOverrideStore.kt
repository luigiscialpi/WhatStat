package com.whatstat.reborn.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.senderColorOverrideDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sender_color_overrides",
)

private val PREFS_KEY_MAP = stringPreferencesKey("override_map_v1")

/**
 * Persistenza delle scelte colore utente per mittente, per chat.
 * Chiave: [senderColorOverrideKey] → valore ARGB packed (Int).
 */
@Singleton
class SenderColorOverrideStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val dataStore = context.senderColorOverrideDataStore

        private val _overrides = MutableStateFlow<Map<String, Int>>(emptyMap())
        val overrides: StateFlow<Map<String, Int>> = _overrides.asStateFlow()

        init {
            scope.launch {
                dataStore.data
                    .map { prefs -> decodeMap(prefs[PREFS_KEY_MAP] ?: "{}") }
                    .distinctUntilChanged()
                    .collect { _overrides.value = it }
            }
        }

        suspend fun setOverride(
            key: String,
            argb: Int,
        ) {
            val next = _overrides.value.toMutableMap().apply { put(key, argb) }
            persist(next)
        }

        suspend fun removeOverride(key: String) {
            if (!_overrides.value.containsKey(key)) return
            val next = _overrides.value.toMutableMap().apply { remove(key) }
            persist(next)
        }

        private suspend fun persist(map: Map<String, Int>) {
            dataStore.edit { prefs ->
                prefs[PREFS_KEY_MAP] = encodeMap(map)
            }
        }

        private fun encodeMap(map: Map<String, Int>): String {
            val o = JSONObject()
            map.forEach { (k, v) -> o.put(k, v) }
            return o.toString()
        }

        private fun decodeMap(json: String): Map<String, Int> {
            return try {
                val o = JSONObject(json)
                buildMap {
                    o.keys().asSequence().forEach { k ->
                        put(k, o.getInt(k))
                    }
                }
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

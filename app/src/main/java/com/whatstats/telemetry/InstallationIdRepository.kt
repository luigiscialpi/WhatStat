package com.whatstats.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Punto unico per lettura/sync del Firebase Installation ID.
 * Usa cache locale per ridurre chiamate ripetute a Firebase Installations.
 */
@Singleton
class InstallationIdRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val telemetryPrefsStore: TelemetryPrefsStore,
    ) {
        private val isFirebaseAvailable: Boolean
            get() = FirebaseApp.getApps(context).isNotEmpty()

        suspend fun getCachedInstallationId(): String? = telemetryPrefsStore.getInstallationId()

        /**
         * Recupera l'ID da Firebase e aggiorna cache locale solo se cambia.
         * Se Firebase non e' disponibile o fallisce, ritorna il valore cached.
         */
        suspend fun syncInstallationId(): String? {
            val cachedId = telemetryPrefsStore.getInstallationId()?.takeIf { it.isNotBlank() }
            if (!isFirebaseAvailable) return cachedId

            val fetchedId =
                runCatching {
                    FirebaseInstallations.getInstance().id.await().takeIf { it.isNotBlank() }
                }.getOrNull()

            if (!fetchedId.isNullOrBlank() && fetchedId != cachedId) {
                telemetryPrefsStore.setInstallationId(fetchedId)
            }

            return fetchedId ?: cachedId
        }
    }

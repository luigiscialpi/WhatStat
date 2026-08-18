package com.whatstats.data.feedback

import android.content.Context
import com.google.firebase.installations.FirebaseInstallations
import com.whatstats.data.store.AppPreferencesStore
import com.whatstats.util.DebugLogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackUserIdProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val appPreferencesStore: AppPreferencesStore,
    ) {
        private var cachedUserId: String? = null

        suspend fun getUserId(): String {
            cachedUserId?.let { return it }

            return try {
                val fid = FirebaseInstallations.getInstance().id.await()
                val uid = "usr_${fid.take(16)}"
                cachedUserId = uid
                appPreferencesStore.setFeedbackFallbackUserId(uid)
                uid
            } catch (e: Exception) {
                DebugLogManager.log("FeedbackUserIdProvider | Errore recupero FID: ${e.message}")
                val savedFallback = appPreferencesStore.feedbackFallbackUserId.first()
                if (!savedFallback.isNullOrBlank()) {
                    cachedUserId = savedFallback
                    savedFallback
                } else {
                    val generated = "usr_" + UUID.randomUUID().toString().replace("-", "").take(16)
                    appPreferencesStore.setFeedbackFallbackUserId(generated)
                    cachedUserId = generated
                    generated
                }
            }
        }
    }

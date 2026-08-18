package com.whatstats.data.feedback

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.whatstats.BuildConfig
import com.whatstats.data.repository.AnalyticsRepository
import com.whatstats.data.store.AppPreferencesStore
import com.whatstats.util.DebugLogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepository
    @Inject
    constructor(
        private val firestore: FirebaseFirestore,
        private val userIdProvider: FeedbackUserIdProvider,
        private val appPreferencesStore: AppPreferencesStore,
        private val analyticsRepository: AnalyticsRepository,
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val COLLECTION_FEEDBACKS = "feedbacks"
            private const val RELEASE_COOLDOWN_MS = 60 * 60 * 1000L // 60 minuti
            private const val DEBUG_COOLDOWN_MS = 5 * 1000L // 5 secondi per testing rapido

            // Placeholder: configurare con l'URL effettivo del Cloudflare Worker dopo il deploy
            const val WORKER_NOTIFY_URL = "https://whatstat-feedback-worker.whatstat.workers.dev/notify"
        }

        private val cooldownMs: Long
            get() = if (BuildConfig.DEBUG) DEBUG_COOLDOWN_MS else RELEASE_COOLDOWN_MS

        suspend fun getRemainingCooldownSeconds(): Long {
            val lastSent = appPreferencesStore.lastFeedbackSentTime.first()
            val now = System.currentTimeMillis()
            val elapsed = now - lastSent
            val limit = cooldownMs
            return if (elapsed < limit) {
                ((limit - elapsed) / 1000L).coerceAtLeast(1L)
            } else {
                0L
            }
        }

        suspend fun sendFeedback(
            type: FeedbackType,
            message: String,
        ): Result<Unit> =
            withContext(Dispatchers.IO) {
                try {
                    val remaining = getRemainingCooldownSeconds()
                    if (remaining > 0) {
                        return@withContext Result.failure(
                            IllegalStateException("Cooldown active ($remaining s)"),
                        )
                    }

                    val cleanMessage = message.trim()
                    if (cleanMessage.isBlank()) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Message cannot be empty"),
                        )
                    }

                    val userId = userIdProvider.getUserId()
                    val feedbackId = "fb_${System.currentTimeMillis()}_${(100..999).random()}"

                    val pInfo =
                        try {
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        } catch (e: Exception) {
                            null
                        }

                    val versionName = pInfo?.versionName ?: BuildConfig.VERSION_NAME
                    val versionCode = pInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: BuildConfig.VERSION_CODE.toLong()
                    val androidVersion = Build.VERSION.SDK_INT
                    val userDeviceName =
                        try {
                            Settings.Global.getString(context.contentResolver, "device_name")
                        } catch (e: Exception) {
                            null
                        }
                    val manufacturer =
                        Build.MANUFACTURER.replaceFirstChar {
                            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
                        }
                    val model = Build.MODEL
                    val deviceModel =
                        if (!userDeviceName.isNullOrBlank() && !userDeviceName.equals(model, ignoreCase = true)) {
                            "$userDeviceName ($model)"
                        } else {
                            "$manufacturer $model".trim()
                        }
                    val mode = if (analyticsRepository.isLiteMode.value) "lite" else "root"
                    val createdAt = System.currentTimeMillis()
                    val fcmToken =
                        try {
                            FirebaseMessaging.getInstance().token.await()
                        } catch (e: Exception) {
                            null
                        }

                    val docData =
                        hashMapOf(
                            "id" to feedbackId,
                            "userId" to userId,
                            "type" to type.name,
                            "message" to cleanMessage,
                            "appVersion" to versionName,
                            "appVersionCode" to versionCode,
                            "androidVersion" to androidVersion,
                            "deviceModel" to deviceModel,
                            "mode" to mode,
                            "createdAt" to createdAt,
                            "status" to FeedbackStatus.PENDING.name,
                            "replyMessage" to null,
                            "repliedAt" to null,
                            "readByDevice" to false,
                            "telegramMessageId" to null,
                            "fcmToken" to fcmToken,
                        )

                    var firestoreSuccess = false
                    try {
                        withTimeout(5000L) {
                            firestore
                                .collection(COLLECTION_FEEDBACKS)
                                .document(feedbackId)
                                .set(docData)
                                .await()
                        }
                        firestoreSuccess = true
                    } catch (e: Exception) {
                        DebugLogManager.log("FeedbackRepository.sendFeedback | Firestore write error: ${e.message}")
                    }

                    // Notifica asincrona al Cloudflare Worker
                    CoroutineScope(Dispatchers.IO).launch {
                        notifyWorker(
                            feedbackId = feedbackId,
                            type = type.name,
                            message = cleanMessage,
                            userId = userId,
                            appVersion = "$versionName ($versionCode)",
                            deviceModel = deviceModel,
                            androidVersion = androidVersion,
                            mode = mode,
                            fcmToken = fcmToken,
                        )
                    }

                    appPreferencesStore.recordFeedbackSent(System.currentTimeMillis())
                    Result.success(Unit)
                } catch (e: Exception) {
                    DebugLogManager.log("FeedbackRepository.sendFeedback | Errore: ${e.message}")
                    Result.failure(e)
                }
            }

        fun observeUnreadReplies(): Flow<FeedbackItem?> =
            callbackFlow {
                val userId = userIdProvider.getUserId()
                val listener =
                    firestore
                        .collection(COLLECTION_FEEDBACKS)
                        .whereEqualTo("userId", userId)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                DebugLogManager.log("observeUnreadReplies | Error: ${error.message}")
                                trySend(null)
                                return@addSnapshotListener
                            }
                            val items =
                                snapshot?.documents?.mapNotNull { doc ->
                                    mapDocumentToFeedbackItem(doc.data)
                                } ?: emptyList()
                            val unread =
                                items.firstOrNull {
                                    it.status == FeedbackStatus.REPLIED && !it.readByDevice
                                }
                            trySend(unread)
                        }
                awaitClose { listener.remove() }
            }

        fun observeUserFeedbacks(): Flow<List<FeedbackItem>> =
            callbackFlow {
                val userId = userIdProvider.getUserId()
                val listener =
                    firestore
                        .collection(COLLECTION_FEEDBACKS)
                        .whereEqualTo("userId", userId)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                DebugLogManager.log("observeUserFeedbacks | Error: ${error.message}")
                                trySend(emptyList())
                                return@addSnapshotListener
                            }
                            val items =
                                snapshot?.documents?.mapNotNull { doc ->
                                    mapDocumentToFeedbackItem(doc.data)
                                }?.sortedByDescending { it.createdAt } ?: emptyList()
                            trySend(items)
                        }
                awaitClose { listener.remove() }
            }

        suspend fun markReplyAsRead(feedbackId: String) =
            withContext(Dispatchers.IO) {
                try {
                    firestore
                        .collection(COLLECTION_FEEDBACKS)
                        .document(feedbackId)
                        .update("readByDevice", true)
                        .await()
                } catch (e: Exception) {
                    DebugLogManager.log("markReplyAsRead | Error: ${e.message}")
                }
            }

        private fun mapDocumentToFeedbackItem(data: Map<String, Any?>?): FeedbackItem? {
            if (data == null) return null
            return try {
                FeedbackItem(
                    id = data["id"] as? String ?: "",
                    userId = data["userId"] as? String ?: "",
                    type =
                        try {
                            FeedbackType.valueOf(data["type"] as? String ?: FeedbackType.BUG.name)
                        } catch (e: Exception) {
                            FeedbackType.BUG
                        },
                    message = data["message"] as? String ?: "",
                    appVersion = data["appVersion"] as? String ?: "",
                    appVersionCode = (data["appVersionCode"] as? Number)?.toLong() ?: 0L,
                    androidVersion = (data["androidVersion"] as? Number)?.toInt() ?: 0,
                    deviceModel = data["deviceModel"] as? String ?: "",
                    mode = data["mode"] as? String ?: "",
                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
                    status =
                        try {
                            FeedbackStatus.valueOf(data["status"] as? String ?: FeedbackStatus.PENDING.name)
                        } catch (e: Exception) {
                            FeedbackStatus.PENDING
                        },
                    replyMessage = data["replyMessage"] as? String,
                    repliedAt = (data["repliedAt"] as? Number)?.toLong(),
                    readByDevice = data["readByDevice"] as? Boolean ?: false,
                    telegramMessageId = (data["telegramMessageId"] as? Number)?.toLong(),
                )
            } catch (e: Exception) {
                DebugLogManager.log("mapDocumentToFeedbackItem | Mapping error: ${e.message}")
                null
            }
        }

        private fun notifyWorker(
            feedbackId: String,
            type: String,
            message: String,
            userId: String,
            appVersion: String,
            deviceModel: String,
            androidVersion: Int,
            mode: String,
            fcmToken: String?,
        ) {
            try {
                val url = URL(WORKER_NOTIFY_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.doOutput = true

                val payload =
                    JSONObject().apply {
                        put("feedbackId", feedbackId)
                        put("type", type)
                        put("message", message)
                        put("userId", userId)
                        put("appVersion", appVersion)
                        put("deviceModel", deviceModel)
                        put("androidVersion", androidVersion)
                        put("mode", mode)
                        put("isDebug", BuildConfig.DEBUG)
                        put("fcmToken", fcmToken)
                    }

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(payload.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                DebugLogManager.log("notifyWorker | Response code: $responseCode")
                conn.disconnect()
            } catch (e: Exception) {
                DebugLogManager.log("notifyWorker | Error notifying worker: ${e.message}")
            }
        }
    }

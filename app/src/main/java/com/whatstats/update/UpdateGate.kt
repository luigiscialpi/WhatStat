package com.whatstats.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.whatstats.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateGate
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val KEY_FORCE_CONFIG = "force_update_config"
            private const val KEY_WARNING_CONFIG = "remote_warning_config"
            private const val PREFS_NAME = "remote_warning_prefs"
            private const val PREF_KEY_LAST_SEEN_VERSION = "remote_warning_last_seen_version"
            const val REQUEST_CODE = 1001
        }

        private val prefs: SharedPreferences by lazy {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        fun check(onForceUpdateRequired: (customMessage: String) -> Unit) {
            val remoteConfig =
                try {
                    FirebaseRemoteConfig.getInstance()
                } catch (_: Exception) {
                    return
                }

            val forceConfig = parseForceUpdateConfig(remoteConfig)
            if (!forceConfig.enabled || BuildConfig.VERSION_CODE >= forceConfig.min_supported_version_code) {
                return
            }

            onForceUpdateRequired(getLocalizedMessage(forceConfig.message))
        }

        fun startForceUpdate(activity: Activity) {
            val appUpdateManager = AppUpdateManagerFactory.create(context)
            appUpdateManager.appUpdateInfo
                .addOnSuccessListener { info ->
                    if (
                        info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                        info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                    ) {
                        try {
                            @Suppress("DEPRECATION")
                            appUpdateManager.startUpdateFlowForResult(
                                info,
                                AppUpdateType.IMMEDIATE,
                                activity,
                                REQUEST_CODE,
                            )
                        } catch (_: Exception) {
                            openPlayStore(activity)
                        }
                    } else {
                        openPlayStore(activity)
                    }
                }
                .addOnFailureListener { openPlayStore(activity) }
        }

        fun getRemoteWarning(): RemoteWarningPayload? {
            val remoteConfig =
                try {
                    FirebaseRemoteConfig.getInstance()
                } catch (_: Exception) {
                    return null
                }

            val warningConfig = parseRemoteWarningConfig(remoteConfig)
            if (!warningConfig.enabled) return null

            val showOncePerVersion = warningConfig.show_once_per_version
            val lastSeenVersion = prefs.getInt(PREF_KEY_LAST_SEEN_VERSION, -1)
            if (showOncePerVersion && lastSeenVersion == BuildConfig.VERSION_CODE) return null

            val title = getLocalizedMessage(warningConfig.title)
            val message = getLocalizedMessage(warningConfig.message)
            if (title.isBlank() && message.isBlank()) return null

            if (showOncePerVersion) {
                prefs.edit().putInt(PREF_KEY_LAST_SEEN_VERSION, BuildConfig.VERSION_CODE).apply()
            }

            return RemoteWarningPayload(
                title = title,
                message = message,
                primaryButtonLabel = getLocalizedMessage(warningConfig.primary_button),
                secondaryButtonLabel = getLocalizedMessage(warningConfig.secondary_button),
                primaryAction = parseAction(warningConfig.primary_action),
                secondaryAction = parseAction(warningConfig.secondary_action),
                actionUrl = warningConfig.action_url.trim(),
            )
        }

        fun executeWarningAction(
            activity: Activity,
            payload: RemoteWarningPayload,
            action: RemoteWarningAction,
        ) {
            when (action) {
                RemoteWarningAction.DISMISS -> Unit
                RemoteWarningAction.OPEN_PLAY_STORE -> openPlayStore(activity)
                RemoteWarningAction.OPEN_URL -> {
                    val url = payload.actionUrl
                    if (url.isBlank()) return
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (_: Exception) {
                        Unit
                    }
                }
            }
        }

        private fun parseForceUpdateConfig(remoteConfig: FirebaseRemoteConfig): ForceUpdateConfigPayload {
            return try {
                val json = remoteConfig.getString(KEY_FORCE_CONFIG)
                Json.decodeFromString<ForceUpdateConfigPayload>(json)
            } catch (e: Exception) {
                ForceUpdateConfigPayload()
            }
        }

        private fun parseRemoteWarningConfig(remoteConfig: FirebaseRemoteConfig): RemoteWarningConfigPayload {
            return try {
                val json = remoteConfig.getString(KEY_WARNING_CONFIG)
                Json.decodeFromString<RemoteWarningConfigPayload>(json)
            } catch (e: Exception) {
                RemoteWarningConfigPayload()
            }
        }

        private fun getLocalizedMessage(messages: Map<String, String>): String {
            val locale = context.resources.configuration.locales[0]
            val language = locale.language.lowercase()
            val country = locale.country.lowercase()

            val candidates =
                buildList {
                    if (language == "pt" && country == "br") add("pt_br")
                    add(language)
                    add("en")
                }

            for (suffix in candidates) {
                val value = messages[suffix]?.trim()
                if (!value.isNullOrBlank()) return value
            }
            return ""
        }

        private fun parseAction(raw: String): RemoteWarningAction =
            when (raw.trim().lowercase()) {
                "open_url" -> RemoteWarningAction.OPEN_URL
                "open_play_store" -> RemoteWarningAction.OPEN_PLAY_STORE
                else -> RemoteWarningAction.DISMISS
            }

        private fun openPlayStore(activity: Activity) {
            val packageName = "com.whatstats"
            try {
                activity.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$packageName"),
                    ),
                )
            } catch (_: Exception) {
                activity.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
                    ),
                )
            }
        }
    }

@Serializable
data class ForceUpdateConfigPayload(
    val enabled: Boolean = false,
    val min_supported_version_code: Long = 1,
    val message: Map<String, String> = emptyMap(),
)

@Serializable
data class RemoteWarningConfigPayload(
    val enabled: Boolean = false,
    val show_once_per_version: Boolean = true,
    val title: Map<String, String> = emptyMap(),
    val message: Map<String, String> = emptyMap(),
    val primary_button: Map<String, String> = emptyMap(),
    val secondary_button: Map<String, String> = emptyMap(),
    val primary_action: String = "dismiss",
    val secondary_action: String = "dismiss",
    val action_url: String = "",
)

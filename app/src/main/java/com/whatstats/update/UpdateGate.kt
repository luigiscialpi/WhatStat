package com.whatstats.update

import android.app.Activity
import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.whatstats.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateGate
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            private const val KEY_ENABLED = "force_update_enabled"
            private const val KEY_MIN_VERSION = "min_supported_version_code"
            private const val KEY_MSG_IT = "force_update_message_it"
            private const val KEY_MSG_EN = "force_update_message_en"
            const val REQUEST_CODE = 1001
        }

        fun check(
            activity: Activity,
            onPlayNotAvailable: (customMessage: String) -> Unit,
        ) {
            val remoteConfig =
                try {
                    FirebaseRemoteConfig.getInstance()
                } catch (_: Exception) {
                    return
                }

            val enabled = remoteConfig.getBoolean(KEY_ENABLED)
            val minVersionCode = remoteConfig.getLong(KEY_MIN_VERSION)

            if (!enabled || BuildConfig.VERSION_CODE >= minVersionCode) return

            val locale = context.resources.configuration.locales[0].language
            val msgKey = if (locale == "it") KEY_MSG_IT else KEY_MSG_EN
            val customMessage = remoteConfig.getString(msgKey)

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
                            onPlayNotAvailable(customMessage)
                        }
                    } else {
                        onPlayNotAvailable(customMessage)
                    }
                }
                .addOnFailureListener { onPlayNotAvailable(customMessage) }
        }
    }

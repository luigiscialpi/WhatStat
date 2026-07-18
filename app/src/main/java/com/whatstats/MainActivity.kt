package com.whatstats

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.whatstats.data.repository.WhatsAppRepository
import com.whatstats.data.store.ThemeMode
import com.whatstats.telemetry.TrackOverlayView
import com.whatstats.ui.MainViewModel
import com.whatstats.ui.navigation.appNavigation
import com.whatstats.ui.theme.WhatStatTheme
import com.whatstats.update.ForceUpdateState
import com.whatstats.update.RemoteWarningState
import com.whatstats.util.DebugLogManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val LANGUAGE_PREFS_NAME = "app_language_prefs"
        const val LANGUAGE_PREFS_KEY = "app_language"
    }

    @Inject lateinit var repository: WhatsAppRepository
    private lateinit var mainViewModel: MainViewModel
    private var lastHandledIncomingUri: String? = null
    private var lastHandledIncomingAtMs: Long = 0L
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun attachBaseContext(newBase: Context) {
        val lang =
            newBase
                .getSharedPreferences(LANGUAGE_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(LANGUAGE_PREFS_KEY, "system") ?: "system"
        val locale =
            when {
                lang == "system" ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        android.content.res.Resources.getSystem().configuration.locales[0]
                    } else {
                        @Suppress("DEPRECATION")
                        android.content.res.Resources.getSystem().configuration.locale
                    }
                else -> {
                    val normalizedTag = lang.replace('_', '-')
                    Locale.forLanguageTag(normalizedTag).takeIf { it.language.isNotBlank() }
                        ?: Locale.getDefault()
                }
            }
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        DebugLogManager.log("MainActivity.onCreate | action=${intent?.action ?: "-"} type=${intent?.type ?: "-"}")

        enableEdgeToEdge()

        // Gestisci l'intent PRIMA di setContent:
        // SetupViewModel.init legge isLiteMode durante la prima composizione,
        // quindi dobbiamo resettare lo stato Lite *prima* che la UI venga creata.
        intent?.let { handleIncomingIntent(it) }

        // Richiesta consenso UMP prima di setContent per minimizzare la latenza
        // (pattern standard Google: requestConsentInfoUpdate deve essere la prima
        // chiamata significativa nell'Activity, prima del layout).
        mainViewModel.requestAdsConsent(this)
        mainViewModel.initializeTelemetry()
        mainViewModel.trackAppOpen()
        if (savedInstanceState == null) {
            mainViewModel.incrementAppLaunchCount()
        }

        setContent {
            val themeMode by mainViewModel.themeMode.collectAsState()
            val darkTheme =
                when (themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }

            // Il locale e' gestito esclusivamente da attachBaseContext (SharedPreferences, sincrono).
            // Nessun override in setContent: evita race condition con DataStore asincrono.
            WhatStatTheme(darkTheme = darkTheme) {
                val forceUpdateState by mainViewModel.forceUpdateState.collectAsState()
                val remoteWarningState by mainViewModel.remoteWarningState.collectAsState()
                val telemetryReady by mainViewModel.telemetryReady.collectAsState()

                // Il Remote Config arriva in modo asincrono dopo il cold start.
                // Rivaluta force update e warning appena la config e' pronta.
                LaunchedEffect(telemetryReady) {
                    if (telemetryReady) {
                        mainViewModel.checkForUpdate()
                        mainViewModel.checkRemoteWarning()
                    }
                }
                if (forceUpdateState is ForceUpdateState.Required) {
                    val msg = (forceUpdateState as ForceUpdateState.Required).customMessage
                    TrackOverlayView("force_update")
                    AlertDialog(
                        onDismissRequest = {},
                        title = { Text(stringResource(R.string.force_update_dialog_title)) },
                        text = {
                            Text(
                                msg.ifBlank { stringResource(R.string.force_update_dialog_message_default) },
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = { mainViewModel.startForceUpdate(this@MainActivity) }) {
                                Text(stringResource(R.string.force_update_dialog_button))
                            }
                        },
                    )
                } else if (remoteWarningState is RemoteWarningState.Visible) {
                    val payload = (remoteWarningState as RemoteWarningState.Visible).payload
                    TrackOverlayView("remote_warning")
                    AlertDialog(
                        onDismissRequest = { mainViewModel.dismissRemoteWarning() },
                        title = {
                            val title = payload.title.ifBlank { stringResource(R.string.info_dialog_title) }
                            Text(title)
                        },
                        text = { Text(payload.message) },
                        confirmButton = {
                            val label = payload.primaryButtonLabel.ifBlank { stringResource(R.string.ok) }
                            TextButton(
                                onClick = {
                                    mainViewModel.executeRemoteWarningAction(this, payload.primaryAction)
                                },
                            ) {
                                Text(label)
                            }
                        },
                        dismissButton = {
                            if (payload.secondaryButtonLabel.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        mainViewModel.executeRemoteWarningAction(this, payload.secondaryAction)
                                    },
                                ) {
                                    Text(payload.secondaryButtonLabel)
                                }
                            }
                        },
                    )
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    appNavigation(
                        navController = navController,
                        repository = repository,
                        mainViewModel = mainViewModel,
                    )
                }
            }
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DebugLogManager.log("MainActivity.onNewIntent | action=${intent.action ?: "-"} type=${intent.type ?: "-"}")
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (::mainViewModel.isInitialized) {
            mainViewModel.checkForUpdate()
            mainViewModel.checkRemoteWarning()
        }
    }

    private fun handleIncomingIntent(intent: Intent) {
        val isSendOrView = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW
        if (!isSendOrView) return

        val uri =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
                ?: intent.data
                ?: intent.clipData?.getItemAt(0)?.uri
                ?: return

        DebugLogManager.log(
            "MainActivity.handleIncomingIntent | action=${intent.action} " +
                "type=${intent.type ?: "-"} uri=$uri isLite=${mainViewModel.isLiteMode.value}",
        )
        if (isLikelyZip(intent.type, uri)) {
            val uriString = uri.toString()
            val now = System.currentTimeMillis()
            if (lastHandledIncomingUri == uriString && (now - lastHandledIncomingAtMs) < 8000L) {
                DebugLogManager.log("MainActivity.handleIncomingIntent | duplicate ignored uri=$uriString")
                return
            }
            lastHandledIncomingUri = uriString
            lastHandledIncomingAtMs = now
            mainViewModel.setIncomingZipUri(uri.toString())
            // Pulisci l'intent per evitare re-delivery su ricreazione Activity
            intent.action = null
            intent.removeExtra(Intent.EXTRA_STREAM)
            intent.data = null
            intent.clipData = null
            DebugLogManager.log("MainActivity.handleIncomingIntent | accepted zip uri")
        } else {
            DebugLogManager.log("MainActivity.handleIncomingIntent | ignored non-zip uri")
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isLikelyZip(
        mimeType: String?,
        uri: Uri,
    ): Boolean {
        val type = mimeType?.lowercase().orEmpty()
        if (type.contains("zip")) return true

        val resolverType = contentResolver.getType(uri)?.lowercase().orEmpty()
        if (resolverType.contains("zip")) return true

        val path = uri.toString().lowercase()
        return path.endsWith(".zip") || path.contains(".zip?")
    }
}

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
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.whatstats.data.repository.WhatsAppRepository
import com.whatstats.data.store.ThemeMode
import com.whatstats.ui.MainViewModel
import com.whatstats.ui.navigation.appNavigation
import com.whatstats.ui.theme.WhatStatTheme
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
                        newBase.resources.configuration.locales[0]
                    } else {
                        @Suppress("DEPRECATION")
                        newBase.resources.configuration.locale
                    }
                lang.contains("_") -> {
                    val parts = lang.split("_")
                    Locale(parts[0], parts[1])
                }
                else -> Locale(lang)
            }
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

        setContent {
            val themeMode by mainViewModel.themeMode.collectAsState()
            val darkTheme =
                when (themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }

            val appLanguage by mainViewModel.appLanguage.collectAsState()
            val context = LocalContext.current
            val currentLocale =
                if (appLanguage == "system") {
                    Locale.getDefault()
                } else {
                    val parts = appLanguage.split("_")
                    if (parts.size == 2) {
                        Locale(parts[0], parts[1])
                    } else {
                        Locale(appLanguage)
                    }
                }

            val configuration = Configuration(context.resources.configuration)
            configuration.setLocale(currentLocale)
            val localizedContext = context.createConfigurationContext(configuration)

            // Usa un ContextWrapper per mantenere il riferimento all'Activity per Hilt,
            // altrimenti hiltViewModel() crasha trovando un ContextImpl.
            val activityContextWrapper =
                object : android.content.ContextWrapper(context) {
                    override fun getResources(): android.content.res.Resources {
                        return localizedContext.resources
                    }
                }

            CompositionLocalProvider(
                LocalContext provides activityContextWrapper,
                LocalConfiguration provides configuration,
            ) {
                WhatStatTheme(darkTheme = darkTheme) {
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
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DebugLogManager.log("MainActivity.onNewIntent | action=${intent.action ?: "-"} type=${intent.type ?: "-"}")
        handleIncomingIntent(intent)
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

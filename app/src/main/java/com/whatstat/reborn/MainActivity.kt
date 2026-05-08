package com.whatstat.reborn

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.whatstat.reborn.data.repository.WhatsAppRepository
import com.whatstat.reborn.ui.MainViewModel
import com.whatstat.reborn.ui.navigation.AppNavigation
import com.whatstat.reborn.ui.theme.WhatStatTheme
import com.whatstat.reborn.util.DebugLogManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var repository: WhatsAppRepository
    private lateinit var mainViewModel: MainViewModel
    private var lastHandledIncomingUri: String? = null
    private var lastHandledIncomingAtMs: Long = 0L
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        DebugLogManager.log("MainActivity.onCreate | action=${intent?.action ?: "-"} type=${intent?.type ?: "-"}")
        
        enableEdgeToEdge()

        // Gestisci l'intent PRIMA di setContent:
        // SetupViewModel.init legge isLiteMode durante la prima composizione,
        // quindi dobbiamo resettare lo stato Lite *prima* che la UI venga creata.
        intent?.let { handleIncomingIntent(it) }

        setContent {
            WhatStatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        repository = repository,
                        mainViewModel = mainViewModel
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

    private fun handleIncomingIntent(intent: Intent) {
        val isSendOrView = intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_VIEW
        if (!isSendOrView) return

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
            ?: intent.data
            ?: intent.clipData?.getItemAt(0)?.uri
            ?: return

        DebugLogManager.log("MainActivity.handleIncomingIntent | action=${intent.action} type=${intent.type ?: "-"} uri=$uri isLite=${mainViewModel.isLiteMode.value}")
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
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isLikelyZip(mimeType: String?, uri: Uri): Boolean {
        val type = mimeType?.lowercase().orEmpty()
        if (type.contains("zip")) return true

        val resolverType = contentResolver.getType(uri)?.lowercase().orEmpty()
        if (resolverType.contains("zip")) return true

        val path = uri.toString().lowercase()
        return path.endsWith(".zip") || path.contains(".zip?")
    }
}

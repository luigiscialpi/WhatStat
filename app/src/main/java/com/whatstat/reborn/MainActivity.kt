package com.whatstat.reborn

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.whatstat.reborn.data.repository.WhatsAppRepository
import com.whatstat.reborn.ui.MainViewModel
import com.whatstat.reborn.ui.navigation.AppNavigation
import com.whatstat.reborn.ui.theme.WhatStatTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var repository: WhatsAppRepository
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        enableEdgeToEdge()

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

        intent?.let { handleIncomingIntent(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            when (intent.type) {
                "application/zip", "application/x-zip-compressed" -> {
                    uri?.let {
                        mainViewModel.setIncomingZipUri(it.toString())
                    }
                }
            }
        }
    }
}

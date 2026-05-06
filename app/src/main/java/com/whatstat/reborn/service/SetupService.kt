package com.whatstat.reborn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.whatstat.reborn.MainActivity
import com.whatstat.reborn.R
import com.whatstat.reborn.data.repository.WhatsAppRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SetupService : Service() {

    @Inject
    lateinit var repository: WhatsAppRepository

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private var lastNotificationUpdateTime = 0L

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    companion object {
        const val CHANNEL_ID = "setup_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_FULL = "START_FULL"
        const val ACTION_START_MANUAL = "START_MANUAL"
        const val ACTION_CANCEL = "ACTION_CANCEL"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        val action = intent?.action
        when (action) {
            ACTION_START_FULL, ACTION_START_MANUAL -> {
                startForeground(NOTIFICATION_ID, createNotification(0f))
                startSetup(action == ACTION_START_MANUAL)
            }
            ACTION_CANCEL -> {
                cancelSetup()
            }
        }

        return START_NOT_STICKY
    }

    private fun cancelSetup() {
        repository.cancelOperation()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startSetup(isManual: Boolean) {
        serviceScope.launch {
            // Monitora il progresso per aggiornare la notifica
            val progressJob = launch {
                repository.setupState.collectLatest { state ->
                    if (state.isLoading) {
                        updateNotification(state.decryptionProgress)
                    }
                    if (state.isComplete || state.error != null) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }

            if (isManual) {
                repository.performManualSetup()
            } else {
                repository.performSetup()
            }
            
            progressJob.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Setup WhatsApp",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mostra l'avanzamento della decifratura"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(progress: Float): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val progressInt = (progress * 100).toInt()
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Decifratura Database")
            .setContentText(if (progressInt > 0) "Progresso: $progressInt%" else "Avvio in corso...")
            .setSmallIcon(R.drawable.ic_launcher) 
            .setOngoing(true)
            .setProgress(100, progressInt, progress == 0f)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(progress: Float) {
        val now = System.currentTimeMillis()
        if (now - lastNotificationUpdateTime >= 1000L || progress >= 1.0f) {
            notificationManager.notify(NOTIFICATION_ID, createNotification(progress))
            lastNotificationUpdateTime = now
        }
    }
}

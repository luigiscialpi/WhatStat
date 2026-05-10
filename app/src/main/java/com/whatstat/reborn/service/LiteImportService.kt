package com.whatstat.reborn.service

import android.app.NotificationChannel
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.whatstat.reborn.MainActivity
import com.whatstat.reborn.R
import com.whatstat.reborn.data.lite.LiteImportProgress
import com.whatstat.reborn.data.lite.LiteModeManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LiteImportService : Service() {

    @Inject
    lateinit var liteModeManager: LiteModeManager

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    companion object {
        const val ACTION_START = "ACTION_LITE_START"
        const val ACTION_STOP = "ACTION_LITE_STOP"
        // Usa lo stesso canale del SetupService root (già creato in App/SetupService)
        private const val CHANNEL_ID = SetupService.CHANNEL_ID
        private const val NOTIFICATION_ID = 1002
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification(LiteImportProgress(0f, "Avvio importazione...")))
                if (progressJob == null) {
                    observeProgress()
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
        serviceScope.cancel()
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun observeProgress() {
        progressJob = serviceScope.launch {
            liteModeManager.importProgress.collect { progress ->
                notificationManager.notify(NOTIFICATION_ID, buildNotification(progress))
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Setup WhatsApp",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mostra l'avanzamento dell'importazione Lite"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: LiteImportProgress): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        val progressInt = (progress.percent * 100).toInt()
        val isIndeterminate = progressInt == 0

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Importazione Chat Lite")
            .setContentText(if (progress.label.isNotEmpty()) "${progress.label} ($progressInt%)" else "Avvio...")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setProgress(100, progressInt, isIndeterminate)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

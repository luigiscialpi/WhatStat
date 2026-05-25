package com.whatstats.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.whatstats.MainActivity
import com.whatstats.R
import com.whatstats.data.analysis.LexicalAnalysisManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LexicalAnalysisService : Service() {
    @Inject
    lateinit var lexicalAnalysisManager: LexicalAnalysisManager

    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null

    companion object {
        const val ACTION_START = "ACTION_LEXICAL_START"
        const val ACTION_STOP = "ACTION_LEXICAL_STOP"
        private const val CHANNEL_ID = SetupService.CHANNEL_ID
        private const val NOTIFICATION_ID = 1003
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification(0, indeterminate = true))
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
        lexicalAnalysisManager.reset()
    }

    private fun observeProgress() {
        progressJob =
            serviceScope.launch {
                lexicalAnalysisManager.progress.collect { progress ->
                    if (progress < 0) return@collect
                    notificationManager.notify(NOTIFICATION_ID, buildNotification(progress, indeterminate = progress == 0))
                }
            }
    }

    private fun buildNotification(
        progress: Int,
        indeterminate: Boolean,
    ): Notification {
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Analisi lessicale")
            .setContentText(if (indeterminate) "Elaborazione in corso..." else "Elaborazione in corso... ($progress%)")
            .setSmallIcon(R.drawable.ic_launcher)
            .setOngoing(true)
            .setProgress(100, progress, indeterminate)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}

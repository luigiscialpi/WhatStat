package com.whatstats.ui.feedback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.whatstats.MainActivity
import com.whatstats.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackNotificationHelper
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        companion object {
            const val CHANNEL_ID = "feedback_reply_channel"
            private const val NOTIFICATION_ID = 9001
        }

        init {
            createChannel()
        }

        fun showReplyNotification() {
            val notificationManager = NotificationManagerCompat.from(context)
            if (!notificationManager.areNotificationsEnabled()) return

            val tapIntent =
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_OPEN_FEEDBACK, true)
                }
            val pendingIntent =
                PendingIntent.getActivity(
                    context,
                    NOTIFICATION_ID,
                    tapIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )

            val notification =
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.feedback_notif_title))
                    .setContentText(context.getString(R.string.feedback_notif_body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .build()

            try {
                notificationManager.notify(NOTIFICATION_ID, notification)
            } catch (e: SecurityException) {
                // Permesso notifiche non concesso: best-effort silenzioso
            }
        }

        private fun createChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.feedback_notif_channel_name),
                        NotificationManager.IMPORTANCE_HIGH,
                    ).apply {
                        description = context.getString(R.string.feedback_notif_channel_desc)
                    }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
        }
    }

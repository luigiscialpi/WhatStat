package com.whatstats.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.whatstats.data.store.AppPreferencesStore
import com.whatstats.domain.reminder.MonthlyReminderEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class MonthlyReminderWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val appPreferencesStore: AppPreferencesStore,
        private val notificationHelper: MonthlyReminderNotificationHelper,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val WORK_NAME = "monthly_reminder_work"

            fun schedule(context: Context) {
                val request =
                    PeriodicWorkRequestBuilder<MonthlyReminderWorker>(1, TimeUnit.DAYS)
                        .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }

            fun cancel(context: Context) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            }
        }

        override suspend fun doWork(): Result {
            val enabled = appPreferencesStore.monthlyReminderEnabled.first()
            if (!enabled) {
                cancel(applicationContext)
                return Result.success()
            }

            val now = System.currentTimeMillis()
            val lastActive = appPreferencesStore.lastAppActiveTime.first()
            if (lastActive <= 0L) {
                // Primo avvio o non ancora impostato: inizializza con timestamp corrente
                appPreferencesStore.recordAppActive(now)
                return Result.success()
            }

            val lastReminder = appPreferencesStore.lastReminderSentTime.first()
            if (MonthlyReminderEngine.shouldTriggerReminder(
                    isEnabled = enabled,
                    lastActiveTimeMs = lastActive,
                    lastReminderSentTimeMs = lastReminder,
                    currentTimeMs = now,
                )
            ) {
                notificationHelper.showReminderNotification()
                appPreferencesStore.recordReminderSent(now)
            }

            return Result.success()
        }
    }

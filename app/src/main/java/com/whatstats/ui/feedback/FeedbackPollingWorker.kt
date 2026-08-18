package com.whatstats.ui.feedback

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.whatstats.data.feedback.FeedbackRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class FeedbackPollingWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted workerParams: WorkerParameters,
        private val feedbackRepository: FeedbackRepository,
        private val notificationHelper: FeedbackNotificationHelper,
    ) : CoroutineWorker(context, workerParams) {
        companion object {
            private const val WORK_NAME = "feedback_polling"

            fun schedule(context: Context) {
                val request =
                    PeriodicWorkRequestBuilder<FeedbackPollingWorker>(15, TimeUnit.MINUTES)
                        .build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }

        override suspend fun doWork(): Result {
            val unreadReply = feedbackRepository.observeUnreadReplies().first()
            if (unreadReply != null) {
                notificationHelper.showReplyNotification()
            }
            return Result.success()
        }
    }

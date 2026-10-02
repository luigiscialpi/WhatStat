package com.whatstats

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.topjohnwu.superuser.Shell
import com.whatstats.ui.feedback.FeedbackPollingWorker
import com.whatstats.util.DebugLogManager
import com.whatstats.util.IntegrityChecker
import com.whatstats.worker.MonthlyReminderWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class App :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        DebugLogManager.init(this)
        IntegrityChecker.verifySignature(this)
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            DebugLogManager.log("Uncaught exception | thread=${thread.name}", throwable)
            previousHandler?.uncaughtException(thread, throwable)
        }

        // Configura la shell root persistente per tutta la sessione.
        // Verbose logging solo in build di debug.
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(
                    Shell.FLAG_REDIRECT_STDERR or
                        Shell.FLAG_MOUNT_MASTER,
                )
                .setTimeout(10),
        )

        // Avvia polling periodico risposte feedback (ogni 15 min, sopravvive a chiusura app)
        FeedbackPollingWorker.schedule(this)
        // Avvia controllo reminder mensile (ogni giorno, sopravvive a chiusura app)
        MonthlyReminderWorker.schedule(this)
    }
}

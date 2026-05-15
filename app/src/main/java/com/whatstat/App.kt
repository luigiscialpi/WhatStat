package com.whatstat

import android.app.Application
import com.topjohnwu.superuser.Shell
import com.whatstat.util.DebugLogManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DebugLogManager.init(this)
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
    }
}

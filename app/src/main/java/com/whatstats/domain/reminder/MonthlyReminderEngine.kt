package com.whatstats.domain.reminder

import java.util.concurrent.TimeUnit

object MonthlyReminderEngine {
    val DEFAULT_INACTIVITY_THRESHOLD_MS: Long = TimeUnit.DAYS.toMillis(30)

    fun shouldTriggerReminder(
        isEnabled: Boolean,
        lastActiveTimeMs: Long,
        lastReminderSentTimeMs: Long,
        currentTimeMs: Long,
        inactivityThresholdMs: Long = DEFAULT_INACTIVITY_THRESHOLD_MS,
    ): Boolean {
        if (!isEnabled) return false
        if (lastActiveTimeMs <= 0L) return false
        if (currentTimeMs <= lastActiveTimeMs) return false

        val timeSinceActive = currentTimeMs - lastActiveTimeMs
        if (timeSinceActive < inactivityThresholdMs) return false

        if (lastReminderSentTimeMs > 0L) {
            val timeSinceReminder = currentTimeMs - lastReminderSentTimeMs
            if (timeSinceReminder < inactivityThresholdMs) return false
        }

        return true
    }
}

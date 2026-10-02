package com.whatstats.domain.reminder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class MonthlyReminderEngineTest {
    private val thresholdMs = TimeUnit.DAYS.toMillis(30)
    private val now = 1_700_000_000_000L

    @Test
    fun shouldTriggerReminder_disabled_returnsFalse() {
        val result =
            MonthlyReminderEngine.shouldTriggerReminder(
                isEnabled = false,
                lastActiveTimeMs = now - TimeUnit.DAYS.toMillis(40),
                lastReminderSentTimeMs = 0L,
                currentTimeMs = now,
                inactivityThresholdMs = thresholdMs,
            )
        assertFalse(result)
    }

    @Test
    fun shouldTriggerReminder_uninitializedActiveTime_returnsFalse() {
        val result =
            MonthlyReminderEngine.shouldTriggerReminder(
                isEnabled = true,
                lastActiveTimeMs = 0L,
                lastReminderSentTimeMs = 0L,
                currentTimeMs = now,
                inactivityThresholdMs = thresholdMs,
            )
        assertFalse(result)
    }

    @Test
    fun shouldTriggerReminder_activeInFuture_returnsFalse() {
        val result =
            MonthlyReminderEngine.shouldTriggerReminder(
                isEnabled = true,
                lastActiveTimeMs = now + 1000L,
                lastReminderSentTimeMs = 0L,
                currentTimeMs = now,
                inactivityThresholdMs = thresholdMs,
            )
        assertFalse(result)
    }

    @Test
    fun shouldTriggerReminder_activeRecently_returnsFalse() {
        val result =
            MonthlyReminderEngine.shouldTriggerReminder(
                isEnabled = true,
                lastActiveTimeMs = now - TimeUnit.DAYS.toMillis(15),
                lastReminderSentTimeMs = 0L,
                currentTimeMs = now,
                inactivityThresholdMs = thresholdMs,
            )
        assertFalse(result)
    }

    @Test
    fun shouldTriggerReminder_active30DaysAgo_noPreviousReminder_returnsTrue() {
        val result =
            MonthlyReminderEngine.shouldTriggerReminder(
                isEnabled = true,
                lastActiveTimeMs = now - TimeUnit.DAYS.toMillis(30),
                lastReminderSentTimeMs = 0L,
                currentTimeMs = now,
                inactivityThresholdMs = thresholdMs,
            )
        assertTrue(result)
    }

    @Test
    fun shouldTriggerReminder_active45DaysAgo_reminderSentRecently_returnsFalse() {
        val result =
            MonthlyReminderEngine.shouldTriggerReminder(
                isEnabled = true,
                lastActiveTimeMs = now - TimeUnit.DAYS.toMillis(45),
                lastReminderSentTimeMs = now - TimeUnit.DAYS.toMillis(10),
                currentTimeMs = now,
                inactivityThresholdMs = thresholdMs,
            )
        assertFalse(result)
    }

    @Test
    fun shouldTriggerReminder_active65DaysAgo_reminderSent31DaysAgo_returnsTrue() {
        val result =
            MonthlyReminderEngine.shouldTriggerReminder(
                isEnabled = true,
                lastActiveTimeMs = now - TimeUnit.DAYS.toMillis(65),
                lastReminderSentTimeMs = now - TimeUnit.DAYS.toMillis(31),
                currentTimeMs = now,
                inactivityThresholdMs = thresholdMs,
            )
        assertTrue(result)
    }
}

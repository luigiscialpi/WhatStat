package com.whatstats.domain.engagement

import com.whatstats.domain.model.Highlight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightEngineTest {
    @Test
    fun noDataGivesNoCards() = assertTrue(HighlightEngine.build(globalStats()).isEmpty())

    @Test
    fun belowMinimumMessagesGivesNoCards() {
        assertTrue(HighlightEngine.build(globalStats(mapOf(10 to 99), longestStreak = 5)).isEmpty())
    }

    @Test
    fun countsOnlyMyMessages() {
        assertEquals(Highlight.MessagesSent(100), HighlightEngine.build(globalStats(mapOf(9 to 60, 21 to 40))).first())
    }

    @Test
    fun peakHourTiePicksEarliestHour() {
        assertTrue(Highlight.PeakHour(8) in HighlightEngine.build(globalStats(mapOf(8 to 50, 20 to 50))))
    }

    @Test
    fun topWeekdayUsesZeroForSunday() {
        assertTrue(
            Highlight.TopWeekday(0) in
                HighlightEngine.build(globalStats(mapOf(12 to 100), mapOf(0 to 70, 3 to 30))),
        )
    }

    @Test
    fun nightShareNeedsAtLeastFivePercent() {
        assertTrue(HighlightEngine.build(globalStats(mapOf(2 to 4, 12 to 96))).none { it is Highlight.NightShare })
        assertTrue(Highlight.NightShare(5) in HighlightEngine.build(globalStats(mapOf(2 to 5, 12 to 95))))
    }

    @Test
    fun extraHighlightsUseMyActivity() {
        val stats =
            globalStats(
                myByHour = mapOf(2 to 10, 8 to 20, 19 to 70),
                myByWeekday = mapOf(0 to 30, 2 to 10, 4 to 20, 6 to 40),
            )
        val result = HighlightEngine.build(stats)
        assertTrue(Highlight.PeakTimeBlock(18, 24) in result)
        assertTrue(Highlight.IntenseDay(6, 40) in result)
        assertTrue(Highlight.WeekendShare(70) in result)
        assertTrue(Highlight.ActivityPeriod(isEvening = true, percent = 80) in result)
    }
}

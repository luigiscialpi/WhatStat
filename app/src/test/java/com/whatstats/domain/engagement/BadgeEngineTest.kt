package com.whatstats.domain.engagement

import com.whatstats.domain.model.BadgeId
import com.whatstats.domain.model.BadgeProgress
import com.whatstats.domain.model.GlobalStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BadgeEngineTest {
    private fun evaluate(
        stats: GlobalStats,
        alreadyUnlocked: Set<String> = emptySet(),
    ): Map<BadgeId, BadgeProgress> = BadgeEngine.evaluate(stats, alreadyUnlocked).associateBy { it.id }

    @Test
    fun returnsEveryBadgeInCatalogOrder() {
        assertEquals(BadgeId.entries.toList(), evaluate(globalStats()).keys.toList())
    }

    @Test
    fun emptyStatsNeverUnlockAndNeverProduceNaN() {
        evaluate(globalStats()).values.forEach { badge ->
            assertFalse(badge.unlocked)
            assertFalse(badge.progress.isNaN())
        }
    }

    @Test
    fun chatterboxUnlocksExactlyAtThreshold() {
        assertFalse(evaluate(globalStats(mapOf(12 to 999))).getValue(BadgeId.CHATTERBOX_1K).unlocked)
        val exact = evaluate(globalStats(mapOf(12 to 1_000)))
        assertTrue(exact.getValue(BadgeId.CHATTERBOX_1K).unlocked)
        assertEquals(1f, exact.getValue(BadgeId.CHATTERBOX_1K).progress, 0f)
        assertFalse(exact.getValue(BadgeId.CHATTERBOX_10K).unlocked)
    }

    @Test
    fun nightOwlNeedsBothVolumeAndShare() {
        assertFalse(evaluate(globalStats(mapOf(1 to 50, 12 to 449))).getValue(BadgeId.NIGHT_OWL).unlocked)
        assertFalse(evaluate(globalStats(mapOf(1 to 49, 12 to 451))).getValue(BadgeId.NIGHT_OWL).unlocked)
        assertTrue(evaluate(globalStats(mapOf(1 to 50, 12 to 450))).getValue(BadgeId.NIGHT_OWL).unlocked)
    }

    @Test
    fun earlyBirdCountsOnlyFiveToSeven() {
        assertFalse(evaluate(globalStats(mapOf(4 to 100, 8 to 100, 12 to 300))).getValue(BadgeId.EARLY_BIRD).unlocked)
        assertTrue(evaluate(globalStats(mapOf(7 to 50, 12 to 450))).getValue(BadgeId.EARLY_BIRD).unlocked)
    }

    @Test
    fun marathonUsesLongestStreak() {
        val result = evaluate(globalStats(longestStreak = 100))
        assertTrue(result.getValue(BadgeId.MARATHON_30).unlocked)
        assertTrue(result.getValue(BadgeId.MARATHON_100).unlocked)
        assertFalse(result.getValue(BadgeId.MARATHON_365).unlocked)
    }

    @Test
    fun storedBadgeStaysUnlockedEvenIfStatsDrop() {
        val badge = evaluate(globalStats(), alreadyUnlocked = setOf(BadgeId.MARATHON_365.key)).getValue(BadgeId.MARATHON_365)
        assertTrue(badge.unlocked)
        assertEquals(1f, badge.progress, 0f)
    }
}

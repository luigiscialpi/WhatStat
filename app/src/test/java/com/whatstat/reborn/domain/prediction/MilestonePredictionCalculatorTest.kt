package com.whatstat.reborn.domain.prediction

import com.whatstat.reborn.domain.model.PredictionConfidence
import org.junit.Assert.*
import org.junit.Test

class MilestonePredictionCalculatorTest {

    @Test
    fun testGenerateTargets() {
        // currentCount = 0 -> 500, 1000
        assertEquals(listOf(500L, 1000L), MilestonePredictionCalculator.generateTargets(0))
        
        // currentCount = 499 -> 500, 1000
        assertEquals(listOf(500L, 1000L), MilestonePredictionCalculator.generateTargets(499))
        
        // currentCount = 500 -> 1000, 1500
        assertEquals(listOf(1000L, 1500L), MilestonePredictionCalculator.generateTargets(500))
        
        // currentCount = 999 -> 1000, 1500
        assertEquals(listOf(1000L, 1500L), MilestonePredictionCalculator.generateTargets(999))
        
        // currentCount = 1000 -> 2000, 5000
        assertEquals(listOf(2000L, 5000L), MilestonePredictionCalculator.generateTargets(1000))
        
        // currentCount = 42000 -> 50000, 100000
        assertEquals(listOf(50000L, 100000L), MilestonePredictionCalculator.generateTargets(42000))
        
        // currentCount = 100000 -> 200000, 500000
        assertEquals(listOf(200000L, 500000L), MilestonePredictionCalculator.generateTargets(100000))
    }

    @Test
    fun testBuildStateWithZeroRate() {
        val now = 1000000L
        val state = MilestonePredictionCalculator.buildState(
            currentCount = 900,
            rateCount = 0,
            rateStartTimestamp = 0,
            rateEndTimestamp = now,
            nowTimestamp = now
        )
        
        assertEquals(0f, state.dailyRate, 0.01f)
        assertEquals(PredictionConfidence.NONE, state.confidence)
        
        state.suggestedPredictions.forEach { 
            assertNull(it.estimatedDate)
            assertNull(it.daysRemaining)
            assertEquals(PredictionConfidence.NONE, it.confidence)
        }
    }

    @Test
    fun testBuildStateNormalRitmo() {
        val dayMs = 86400000L
        val now = dayMs * 10
        val start = 0L // 10 giorni fa
        
        // 100 messaggi in 10 giorni = 10 msg/giorno
        val state = MilestonePredictionCalculator.buildState(
            currentCount = 900,
            rateCount = 100,
            rateStartTimestamp = start,
            rateEndTimestamp = now,
            nowTimestamp = now
        )
        
        assertEquals(10f, state.dailyRate, 0.1f)
        // target sugerito per 900 e 1000
        val p1000 = state.suggestedPredictions.find { it.targetCount == 1000L }
        assertNotNull(p1000)
        // mancanti 100 / ritmo 10 = 10 giorni
        assertEquals(10L, p1000?.daysRemaining)
        assertEquals(now + 10 * dayMs, p1000?.estimatedDate)
    }

    @Test
    fun testBuildStateWithRecentFilter() {
        val dayMs = 86400000L
        val now = dayMs * 100
        val filterStart = now - 30 * dayMs
        
        // current 42000, rate 900 in 30 giorni = 30 msg/giorno
        val state = MilestonePredictionCalculator.buildState(
            currentCount = 42000,
            rateCount = 900,
            rateStartTimestamp = filterStart,
            rateEndTimestamp = now,
            nowTimestamp = now
        )
        
        assertEquals(30f, state.dailyRate, 0.1f)
        // Non deve proporre target < 42000
        state.suggestedPredictions.forEach { 
            assertTrue(it.targetCount > 42000)
        }
        assertEquals(50000L, state.suggestedPredictions[0].targetCount)
    }

    @Test
    fun testCalculateForCustomTargetValid() {
        val now = System.currentTimeMillis()
        val p = MilestonePredictionCalculator.calculateForTarget(
            currentCount = 42000,
            targetCount = 123456,
            dailyRate = 30f,
            confidence = PredictionConfidence.MEDIUM,
            nowTimestamp = now
        )
        
        assertNotNull(p.estimatedDate)
        assertTrue(p.estimatedDate!! > now)
        assertEquals(123456L, p.targetCount)
    }

    @Test
    fun testCalculateForTargetReached() {
        val p = MilestonePredictionCalculator.calculateForTarget(
            currentCount = 5000,
            targetCount = 4000,
            dailyRate = 10f,
            confidence = PredictionConfidence.MEDIUM
        )
        
        assertNull(p.estimatedDate)
        assertNull(p.daysRemaining)
        assertEquals(1f, p.progress, 0.01f)
        assertEquals(PredictionConfidence.NONE, p.confidence)
    }

    @Test
    fun testCalculateForTargetFarFuture() {
        val now = 1000L
        // ritmo basso, target lontano
        val p = MilestonePredictionCalculator.calculateForTarget(
            currentCount = 100,
            targetCount = 1000000,
            dailyRate = 0.01f,
            confidence = PredictionConfidence.LOW,
            nowTimestamp = now
        )
        
        // Deve comunque calcolare se non fa overflow Long
        assertNotNull(p.estimatedDate)
        assertTrue(p.daysRemaining!! > 365 * 10) // > 10 anni
    }

    @Test
    fun testProgressRange() {
        val pLow = MilestonePredictionCalculator.calculateForTarget(10, 100, 1f, PredictionConfidence.LOW)
        assertEquals(0.1f, pLow.progress, 0.01f)
        
        val pHigh = MilestonePredictionCalculator.calculateForTarget(150, 100, 1f, PredictionConfidence.LOW)
        assertEquals(1f, pHigh.progress, 0.01f)
        
        val pNegative = MilestonePredictionCalculator.calculateForTarget(-10, 100, 1f, PredictionConfidence.LOW)
        assertEquals(0f, pNegative.progress, 0.01f)
    }
}

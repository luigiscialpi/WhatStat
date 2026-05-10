package com.whatstat.reborn.domain.prediction

import com.whatstat.reborn.domain.model.MilestonePrediction
import com.whatstat.reborn.domain.model.MilestonePredictionState
import com.whatstat.reborn.domain.model.PredictionConfidence
import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.log10

object MilestonePredictionCalculator {
    private const val DAY_MS = 86_400_000L
    private const val DEFAULT_SUGGESTIONS = 2
    private const val CUSTOM_TARGET_MAX = 999_999_999L

    /**
     * Costruisce lo stato completo delle previsioni.
     */
    fun buildState(
        currentCount: Long,
        rateCount: Long,
        rateStartTimestamp: Long?,
        rateEndTimestamp: Long?,
        nowTimestamp: Long = System.currentTimeMillis(),
        maxSuggestions: Int = DEFAULT_SUGGESTIONS
    ): MilestonePredictionState {
        val safeCurrentCount = currentCount.coerceAtLeast(0L)
        val effectiveEnd = rateEndTimestamp ?: nowTimestamp
        val effectiveStart = rateStartTimestamp ?: nowTimestamp
        
        // Calcolo giorni del periodo (minimo 1 giorno per evitare divisione per zero)
        val rateDays = ((effectiveEnd - effectiveStart).toDouble() / DAY_MS).coerceAtLeast(1.0)
        
        // Calcolo ritmo giornaliero
        val dailyRate = rateCount.coerceAtLeast(0).toDouble() / rateDays
        
        // Calcolo confidenza
        val confidence = confidenceFor(rateCount.coerceAtLeast(0), rateDays, dailyRate)
        
        // Generazione target suggeriti
        val targets = generateTargets(safeCurrentCount, maxSuggestions)
        val suggestedPredictions = targets.map { target ->
            calculateForTarget(
                currentCount = safeCurrentCount,
                targetCount = target,
                dailyRate = dailyRate.toFloat(),
                confidence = confidence,
                nowTimestamp = nowTimestamp
            )
        }

        return MilestonePredictionState(
            currentCount = safeCurrentCount,
            dailyRate = dailyRate.toFloat(),
            confidence = confidence,
            suggestedPredictions = suggestedPredictions
        )
    }

    /**
     * Calcola la previsione per un singolo target specifico.
     */
    fun calculateForTarget(
        currentCount: Long,
        targetCount: Long,
        dailyRate: Float,
        confidence: PredictionConfidence,
        nowTimestamp: Long = System.currentTimeMillis()
    ): MilestonePrediction {
        // Se target gia raggiunto
        if (targetCount <= currentCount) {
            return MilestonePrediction(
                targetCount = targetCount,
                estimatedDate = null,
                daysRemaining = null,
                progress = 1f,
                dailyRate = dailyRate,
                confidence = PredictionConfidence.NONE
            )
        }

        val progress = (currentCount.toFloat() / targetCount.toFloat()).coerceIn(0f, 1f)

        // Se ritmo zero o negativo
        if (dailyRate <= 0f) {
            return MilestonePrediction(
                targetCount = targetCount,
                estimatedDate = null,
                daysRemaining = null,
                progress = progress,
                dailyRate = 0f,
                confidence = PredictionConfidence.NONE
            )
        }

        val remaining = targetCount - currentCount
        val days = ceil(remaining / dailyRate.toDouble()).toLong().coerceAtLeast(1L)

        // Controllo overflow data
        val maxSafeDays = (Long.MAX_VALUE - nowTimestamp) / DAY_MS
        val estimatedDate = if (days <= maxSafeDays) {
            nowTimestamp + days * DAY_MS
        } else {
            null
        }

        return MilestonePrediction(
            targetCount = targetCount,
            estimatedDate = estimatedDate,
            daysRemaining = days,
            progress = progress,
            dailyRate = dailyRate,
            confidence = confidence
        )
    }

    /**
     * Genera target "belli" basati sul conteggio corrente.
     */
    fun generateTargets(
        currentCount: Long,
        maxTargets: Int = DEFAULT_SUGGESTIONS
    ): List<Long> {
        val targets = mutableSetOf<Long>()
        
        if (currentCount < 1000) {
            // Step da 500 per conteggi bassi
            var next = 500L
            while (targets.size < maxTargets + 2 && next <= 10000) { // limite ragionevole per loop
                if (next > currentCount) targets.add(next)
                next += 500L
            }
        }
        
        // Generazione "nice numbers": 1, 2, 5 * 10^n
        val magnitudes = listOf(1, 2, 5)
        for (n in 3..9) { // da 10^3 (1.000) a 10^9
            val base = 10.0.pow(n.toDouble()).toLong()
            for (m in magnitudes) {
                val target = base * m / magnitudes[0] // Correzione logica per 1, 2, 5
                // Semplificato:
                val t = when(m) {
                    1 -> base
                    2 -> base * 2
                    5 -> base * 5
                    else -> base
                }
                if (t > currentCount) targets.add(t)
            }
        }

        return targets.toList()
            .filter { it > currentCount }
            .sorted()
            .take(maxTargets)
    }

    /**
     * Valuta la confidenza della previsione basata sulla quantita e durata dei dati.
     */
    private fun confidenceFor(rateCount: Long, rateDays: Double, dailyRate: Double): PredictionConfidence {
        if (dailyRate <= 0.0) return PredictionConfidence.NONE
        if (rateCount < 10 || rateDays < 7.0) return PredictionConfidence.LOW
        if (rateCount >= 100 && rateDays >= 30.0) return PredictionConfidence.HIGH
        return PredictionConfidence.MEDIUM
    }
}

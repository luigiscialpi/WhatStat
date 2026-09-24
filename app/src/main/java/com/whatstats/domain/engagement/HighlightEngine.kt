package com.whatstats.domain.engagement

import com.whatstats.domain.model.GlobalStats
import com.whatstats.domain.model.Highlight

object HighlightEngine {
    const val MIN_MY_MESSAGES = 100
    const val MIN_STREAK_DAYS = 2
    const val MIN_NIGHT_SHARE_PERCENT = 5
    val NIGHT_HOURS = 0..4

    fun build(stats: GlobalStats): List<Highlight> {
        val result = mutableListOf<Highlight>()
        val byHour = MyActivity.byHour(stats)
        val myTotal = byHour.sum()
        if (myTotal >= MIN_MY_MESSAGES) {
            result += Highlight.MessagesSent(myTotal)
            result += Highlight.PeakHour(byHour.indices.maxBy { byHour[it] })
            val byWeekday = MyActivity.byWeekday(stats)
            if (byWeekday.sum() > 0) {
                result += Highlight.TopWeekday(byWeekday.indices.maxBy { byWeekday[it] })
            }
            val night = NIGHT_HOURS.sumOf { byHour[it] }
            val nightPercent = (night.toLong() * 100 / myTotal).toInt()
            if (nightPercent >= MIN_NIGHT_SHARE_PERCENT) {
                result += Highlight.NightShare(nightPercent)
            }
        }
        if (stats.longestStreak >= MIN_STREAK_DAYS) {
            result += Highlight.RecordStreak(stats.longestStreak)
        }
        return result
    }
}

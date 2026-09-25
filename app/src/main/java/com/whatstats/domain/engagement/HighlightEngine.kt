package com.whatstats.domain.engagement

import com.whatstats.domain.model.GlobalStats
import com.whatstats.domain.model.Highlight

object HighlightEngine {
    const val MIN_MY_MESSAGES = 100
    const val MIN_NIGHT_SHARE_PERCENT = 5
    const val MIN_WEEKEND_SHARE_PERCENT = 20
    val NIGHT_HOURS = 0..4
    val TIME_BLOCKS = listOf(0..5, 6..11, 12..17, 18..23)

    fun build(stats: GlobalStats): List<Highlight> {
        val result = mutableListOf<Highlight>()
        val byHour = MyActivity.byHour(stats)
        val myTotal = byHour.sum()
        if (myTotal >= MIN_MY_MESSAGES) {
            result += Highlight.MessagesSent(myTotal)
            result += Highlight.PeakHour(byHour.indices.maxBy { byHour[it] })
            val byWeekday = MyActivity.byWeekday(stats)
            if (byWeekday.sum() > 0) {
                val topDay = byWeekday.indices.maxBy { byWeekday[it] }
                result += Highlight.TopWeekday(topDay)
                result += Highlight.IntenseDay(topDay, byWeekday[topDay])
                val weekend = byWeekday[0] + byWeekday[6]
                val weekendPercent = (weekend.toLong() * 100 / myTotal).toInt()
                if (weekendPercent >= MIN_WEEKEND_SHARE_PERCENT) {
                    result += Highlight.WeekendShare(weekendPercent)
                }
            }
            val night = NIGHT_HOURS.sumOf { byHour[it] }
            val nightPercent = (night.toLong() * 100 / myTotal).toInt()
            if (nightPercent >= MIN_NIGHT_SHARE_PERCENT) {
                result += Highlight.NightShare(nightPercent)
            }
            val dayMessages = (6..17).sumOf { byHour[it] }
            val eveningMessages = myTotal - dayMessages
            val eveningPercent = (eveningMessages.toLong() * 100 / myTotal).toInt()
            result +=
                Highlight.ActivityPeriod(
                    isEvening = eveningMessages > dayMessages,
                    percent = maxOf(eveningPercent, 100 - eveningPercent),
                )
            val peakBlock = TIME_BLOCKS.maxBy { range -> range.sumOf { byHour[it] } }
            result += Highlight.PeakTimeBlock(peakBlock.first, peakBlock.last + 1)
        }
        return result
    }
}

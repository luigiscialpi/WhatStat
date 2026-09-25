package com.whatstats.domain.engagement

import com.whatstats.domain.model.GlobalStats
import com.whatstats.domain.model.Highlight
import com.whatstats.domain.model.NamedReplyStats

object HighlightEngine {
    const val MIN_MY_MESSAGES = 100
    const val MIN_NIGHT_SHARE_PERCENT = 5
    const val MIN_WEEKEND_SHARE_PERCENT = 20
    const val MIN_PERSON_SAMPLES = 20
    const val MIN_START_SHARE_PERCENT = 50
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

    fun buildPersonHighlights(
        chats: List<NamedReplyStats>,
        includeReplyTimes: Boolean,
    ): List<Highlight> {
        val result = mutableListOf<Highlight>()
        val orderedChats = chats.sortedBy { it.name }
        if (includeReplyTimes) {
            orderedChats
                .filter { it.stats.theirReplyCount >= MIN_PERSON_SAMPLES }
                .minByOrNull { it.stats.theirReplySumMs / it.stats.theirReplyCount }
                ?.let {
                    result +=
                        Highlight.FastestReplier(
                            it.name,
                            it.stats.theirReplySumMs / it.stats.theirReplyCount,
                        )
                }
            orderedChats
                .filter { it.stats.myReplyCount >= MIN_PERSON_SAMPLES }
                .minByOrNull { it.stats.myReplySumMs / it.stats.myReplyCount }
                ?.let {
                    result +=
                        Highlight.YouReplyFastest(
                            it.name,
                            it.stats.myReplySumMs / it.stats.myReplyCount,
                        )
                }
        }
        orderedChats
            .filter { it.stats.iStarted + it.stats.theyStarted >= MIN_PERSON_SAMPLES }
            .maxByOrNull { it.stats.iStarted * 100 / (it.stats.iStarted + it.stats.theyStarted) }
            ?.let {
                val total = it.stats.iStarted + it.stats.theyStarted
                val percent = it.stats.iStarted * 100 / total
                if (percent >= MIN_START_SHARE_PERCENT) {
                    result += Highlight.YouStartMost(it.name, percent)
                }
            }
        return result
    }
}

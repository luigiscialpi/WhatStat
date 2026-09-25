package com.whatstats.domain.engagement

import com.whatstats.domain.model.BadgeId
import com.whatstats.domain.model.BadgeProgress
import com.whatstats.domain.model.ChatReplyStats
import com.whatstats.domain.model.GlobalStats

object BadgeEngine {
    const val MIN_MY_MESSAGES_FOR_TIME_SHARE = 500
    const val MIN_CONVERSATIONS_FOR_ICEBREAKER = 20
    const val MIN_REPLIES_FOR_QUICK_DRAW = 50
    private const val MAX_PARTIAL_PROGRESS = 0.99f
    val NIGHT_HOURS = 0..4
    val EARLY_HOURS = 5..7

    fun evaluate(
        stats: GlobalStats,
        oneToOne: List<ChatReplyStats> = emptyList(),
        isLiteMode: Boolean = false,
        alreadyUnlocked: Set<String>,
    ): List<BadgeProgress> {
        val byHour = MyActivity.byHour(stats)
        val myTotal = byHour.sum().toLong()
        val night = NIGHT_HOURS.sumOf { byHour[it] }.toLong()
        val early = EARLY_HOURS.sumOf { byHour[it] }.toLong()
        val streak = stats.longestStreak.toLong()
        val iStarted = oneToOne.sumOf { it.iStarted.toLong() }
        val conversations = oneToOne.sumOf { it.iStarted.toLong() + it.theyStarted }
        val myReplies = oneToOne.sumOf { it.myReplyCount.toLong() }
        val myFastReplies = oneToOne.sumOf { it.myFastReplyCount.toLong() }
        return BadgeId.entries.filter { !it.rootOnly || !isLiteMode }.map { id ->
            val score =
                when (id) {
                    BadgeId.NIGHT_OWL -> share(night, myTotal, id.target, MIN_MY_MESSAGES_FOR_TIME_SHARE)
                    BadgeId.EARLY_BIRD -> share(early, myTotal, id.target, MIN_MY_MESSAGES_FOR_TIME_SHARE)
                    BadgeId.CHATTERBOX_1K,
                    BadgeId.CHATTERBOX_10K,
                    BadgeId.CHATTERBOX_50K,
                    BadgeId.CHATTERBOX_100K,
                    -> allOf(Goal(myTotal, id.target.toLong()))
                    BadgeId.MARATHON_30,
                    BadgeId.MARATHON_100,
                    BadgeId.MARATHON_365,
                    -> allOf(Goal(streak, id.target.toLong()))
                    BadgeId.ICEBREAKER -> share(iStarted, conversations, id.target, MIN_CONVERSATIONS_FOR_ICEBREAKER)
                    BadgeId.QUICK_DRAW -> share(myFastReplies, myReplies, id.target, MIN_REPLIES_FOR_QUICK_DRAW)
                }
            val sticky = id.key in alreadyUnlocked
            BadgeProgress(id, if (sticky) 1f else score.progress, sticky || score.reached)
        }
    }

    private fun share(
        part: Long,
        total: Long,
        targetPercent: Int,
        minTotal: Int,
    ): Score = allOf(Goal(total, minTotal.toLong()), Goal(part * 100, total * targetPercent))

    private fun allOf(vararg goals: Goal): Score {
        val reached = goals.all { it.reached }
        val progress = if (reached) 1f else goals.minOf { it.partial }
        return Score(reached, progress)
    }

    private class Goal(val value: Long, val target: Long) {
        val reached: Boolean
            get() = value >= target

        val partial: Float
            get() = if (target <= 0L) 1f else (value.toFloat() / target).coerceIn(0f, MAX_PARTIAL_PROGRESS)
    }

    private class Score(val reached: Boolean, val progress: Float)
}

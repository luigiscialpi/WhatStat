package com.whatstats.domain.engagement

import com.whatstats.data.db.model.DayCount
import com.whatstats.data.db.model.HourlyCount
import com.whatstats.data.db.model.SenderStat
import com.whatstats.domain.model.ChatReplyStats
import com.whatstats.domain.model.GlobalStats

private const val OTHER_JID = "391234567890@s.whatsapp.net"
private const val OTHER_COUNT = 7

internal fun globalStats(
    myByHour: Map<Int, Int> = emptyMap(),
    myByWeekday: Map<Int, Int> = emptyMap(),
    longestStreak: Int = 0,
): GlobalStats =
    GlobalStats(
        totalMessages = 0,
        totalChats = 0,
        totalContacts = 0,
        totalMediaFiles = 0,
        oldestMessageDate = 0L,
        mostActiveChat = "",
        mostActiveHour = 0,
        mostActiveDayOfWeek = 0,
        averageMessagesPerDay = 0f,
        longestStreak = longestStreak,
        hourlyDistribution =
            myByHour.map { (hour, count) ->
                HourlyCount(hour = hour, count = count + OTHER_COUNT, senderBreakdown = breakdown(count))
            },
        weeklyDistribution =
            myByWeekday.map { (day, count) ->
                DayCount(dayOfWeek = day, count = count + OTHER_COUNT, senderBreakdown = breakdown(count))
            },
    )

private fun breakdown(myCount: Int): List<SenderStat> =
    listOf(
        SenderStat(jid = MyActivity.ME_JID, displayName = "You", count = myCount),
        SenderStat(jid = OTHER_JID, displayName = "Other", count = OTHER_COUNT),
    )

internal fun replyStats(
    chatId: Long = 1L,
    myReplyCount: Int = 0,
    myReplySumMs: Long = 0L,
    myFastReplyCount: Int = 0,
    theirReplyCount: Int = 0,
    theirReplySumMs: Long = 0L,
    iStarted: Int = 0,
    theyStarted: Int = 0,
): ChatReplyStats =
    ChatReplyStats(
        chatId = chatId,
        myReplyCount = myReplyCount,
        myReplySumMs = myReplySumMs,
        myFastReplyCount = myFastReplyCount,
        theirReplyCount = theirReplyCount,
        theirReplySumMs = theirReplySumMs,
        iStarted = iStarted,
        theyStarted = theyStarted,
    )

package com.whatstats.domain.engagement

import com.whatstats.domain.model.ChatReplyStats
import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyStatsAccumulatorTest {
    private fun accumulate(vararg rows: Triple<Long, Boolean, Long>): List<ChatReplyStats> {
        val accumulator = ReplyStatsAccumulator()
        rows.forEach { (chatId, fromMe, timestamp) -> accumulator.add(chatId, fromMe, timestamp) }
        return accumulator.finish()
    }

    @Test
    fun emptyInputGivesEmptyList() {
        assertEquals(emptyList<ChatReplyStats>(), accumulate())
    }

    @Test
    fun alternatingMessagesCountRepliesBothWays() {
        val result =
            accumulate(
                Triple(1L, false, 0L),
                Triple(1L, true, 60_000L),
                Triple(1L, false, 180_000L),
            ).single()
        assertEquals(1, result.myReplyCount)
        assertEquals(60_000L, result.myReplySumMs)
        assertEquals(1, result.myFastReplyCount)
        assertEquals(1, result.theirReplyCount)
        assertEquals(120_000L, result.theirReplySumMs)
        assertEquals(0, result.iStarted)
        assertEquals(1, result.theyStarted)
    }

    @Test
    fun consecutiveMessagesFromSameSenderAreNotReplies() {
        val result = accumulate(Triple(1L, true, 0L), Triple(1L, true, 1_000L)).single()
        assertEquals(0, result.myReplyCount)
        assertEquals(1, result.iStarted)
    }

    @Test
    fun gapOverThreeHoursStartsNewConversation() {
        val result =
            accumulate(
                Triple(1L, true, 0L),
                Triple(1L, false, 4 * 3_600_000L),
            ).single()
        assertEquals(1, result.theyStarted)
        assertEquals(1, result.iStarted)
        assertEquals(1, result.theirReplyCount)
        assertEquals(0, result.myReplyCount)
        assertEquals(0, result.myFastReplyCount)
    }

    @Test
    fun gapOfTwentyFourHoursOrMoreIsNotAReply() {
        val result = accumulate(Triple(1L, true, 0L), Triple(1L, false, 86_400_000L)).single()
        assertEquals(0, result.theirReplyCount)
    }

    @Test
    fun sameTimestampIsNotAReply() {
        val result = accumulate(Triple(1L, true, 60_000L), Triple(1L, false, 60_000L)).single()
        assertEquals(0, result.theirReplyCount)
    }

    @Test
    fun chatChangeResetsState() {
        val result = accumulate(Triple(1L, false, 0L), Triple(2L, true, 1_000L)).associateBy { it.chatId }
        assertEquals(setOf(1L, 2L), result.keys)
        assertEquals(0, result.getValue(2L).myReplyCount)
        assertEquals(1, result.getValue(2L).iStarted)
    }
}

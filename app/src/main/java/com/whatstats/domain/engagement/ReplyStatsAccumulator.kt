package com.whatstats.domain.engagement

import com.whatstats.domain.model.ChatReplyStats

/** Accumula metriche risposta in un singolo passaggio ordinato. */
class ReplyStatsAccumulator {
    private val results = mutableListOf<ChatReplyStats>()

    private var chatId = NO_CHAT
    private var hasPrevious = false
    private var previousFromMe = false
    private var previousTimestamp = 0L

    private var myReplyCount = 0
    private var myReplySumMs = 0L
    private var myFastReplyCount = 0
    private var theirReplyCount = 0
    private var theirReplySumMs = 0L
    private var iStarted = 0
    private var theyStarted = 0

    fun add(
        rowChatId: Long,
        fromMe: Boolean,
        timestamp: Long,
    ) {
        if (rowChatId != chatId) {
            flush()
            chatId = rowChatId
        }
        val gap = timestamp - previousTimestamp
        if (!hasPrevious || gap > CONVERSATION_GAP_MS) {
            if (fromMe) iStarted++ else theyStarted++
        }
        if (hasPrevious && fromMe != previousFromMe && gap > 0 && gap < MAX_REPLY_GAP_MS) {
            if (fromMe) {
                myReplyCount++
                myReplySumMs += gap
                if (gap < FAST_REPLY_MS) myFastReplyCount++
            } else {
                theirReplyCount++
                theirReplySumMs += gap
            }
        }
        hasPrevious = true
        previousFromMe = fromMe
        previousTimestamp = timestamp
    }

    fun finish(): List<ChatReplyStats> {
        flush()
        return results.toList()
    }

    private fun flush() {
        if (chatId != NO_CHAT) {
            results +=
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
        }
        chatId = NO_CHAT
        hasPrevious = false
        previousFromMe = false
        previousTimestamp = 0L
        myReplyCount = 0
        myReplySumMs = 0L
        myFastReplyCount = 0
        theirReplyCount = 0
        theirReplySumMs = 0L
        iStarted = 0
        theyStarted = 0
    }

    companion object {
        const val CONVERSATION_GAP_MS = 10_800_000L
        const val MAX_REPLY_GAP_MS = 86_400_000L
        const val FAST_REPLY_MS = 300_000L

        private const val NO_CHAT = Long.MIN_VALUE
    }
}

package com.whatstats.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatReplyStats(
    val chatId: Long,
    val myReplyCount: Int,
    val myReplySumMs: Long,
    val myFastReplyCount: Int,
    val theirReplyCount: Int,
    val theirReplySumMs: Long,
    val iStarted: Int,
    val theyStarted: Int,
)

data class NamedReplyStats(
    val name: String,
    val stats: ChatReplyStats,
)

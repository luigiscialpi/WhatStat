package com.whatstat.reborn.data.db.model

import kotlinx.serialization.Serializable

@Serializable
data class EmojiStat(
    val emoji: String,
    val count: Int,
)

@Serializable
data class UserEmojiStat(
    val jid: String,
    val displayName: String?,
    val totalEmojiCount: Int,
    val topEmoji: List<EmojiStat>,
)

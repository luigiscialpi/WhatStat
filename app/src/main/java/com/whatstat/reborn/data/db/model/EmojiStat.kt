package com.whatstat.reborn.data.db.model

data class EmojiStat(
    val emoji: String,
    val count: Int
)

data class UserEmojiStat(
    val jid: String,
    val displayName: String?,
    val totalEmojiCount: Int,
    val topEmoji: List<EmojiStat>
)
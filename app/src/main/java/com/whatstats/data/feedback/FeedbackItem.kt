package com.whatstats.data.feedback

enum class FeedbackType {
    BUG,
    FEATURE,
    OTHER,
}

enum class FeedbackStatus {
    PENDING,
    REPLIED,
}

data class FeedbackItem(
    val id: String = "",
    val userId: String = "",
    val type: FeedbackType = FeedbackType.BUG,
    val message: String = "",
    val appVersion: String = "",
    val appVersionCode: Long = 0L,
    val androidVersion: Int = 0,
    val deviceModel: String = "",
    val mode: String = "",
    val createdAt: Long = 0L,
    val status: FeedbackStatus = FeedbackStatus.PENDING,
    val replyMessage: String? = null,
    val repliedAt: Long? = null,
    val readByDevice: Boolean = false,
    val telegramMessageId: Long? = null,
)

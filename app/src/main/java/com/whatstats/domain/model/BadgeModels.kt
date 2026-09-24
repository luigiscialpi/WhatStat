package com.whatstats.domain.model

enum class BadgeId(
    val key: String,
    val target: Int,
) {
    NIGHT_OWL("night_owl", 10),
    EARLY_BIRD("early_bird", 10),
    CHATTERBOX_1K("chatterbox_1k", 1_000),
    CHATTERBOX_10K("chatterbox_10k", 10_000),
    CHATTERBOX_50K("chatterbox_50k", 50_000),
    CHATTERBOX_100K("chatterbox_100k", 100_000),
    MARATHON_30("marathon_30", 30),
    MARATHON_100("marathon_100", 100),
    MARATHON_365("marathon_365", 365),
}

data class BadgeProgress(
    val id: BadgeId,
    val progress: Float,
    val unlocked: Boolean,
)

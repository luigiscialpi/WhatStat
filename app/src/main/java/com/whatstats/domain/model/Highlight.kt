package com.whatstats.domain.model

sealed interface Highlight {
    val key: String

    data class MessagesSent(val count: Int) : Highlight {
        override val key = "messages_sent"
    }

    data class PeakHour(val hour: Int) : Highlight {
        override val key = "peak_hour"
    }

    data class TopWeekday(val dayOfWeek: Int) : Highlight {
        override val key = "top_weekday"
    }

    data class NightShare(val percent: Int) : Highlight {
        override val key = "night_share"
    }

    data class RecordStreak(val days: Int) : Highlight {
        override val key = "record_streak"
    }
}

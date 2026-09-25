package com.whatstats.domain.model

sealed interface Highlight {
    val key: String
    val shareable: Boolean

    data class MessagesSent(val count: Int) : Highlight {
        override val key = "messages_sent"
        override val shareable = true
    }

    data class PeakHour(val hour: Int) : Highlight {
        override val key = "peak_hour"
        override val shareable = true
    }

    data class TopWeekday(val dayOfWeek: Int) : Highlight {
        override val key = "top_weekday"
        override val shareable = true
    }

    data class NightShare(val percent: Int) : Highlight {
        override val key = "night_share"
        override val shareable = true
    }

    data class PeakTimeBlock(val startHour: Int, val endHour: Int) : Highlight {
        override val key = "peak_time_block"
        override val shareable = true
    }

    data class IntenseDay(val dayOfWeek: Int, val count: Int) : Highlight {
        override val key = "intense_day"
        override val shareable = true
    }

    data class WeekendShare(val percent: Int) : Highlight {
        override val key = "weekend_share"
        override val shareable = true
    }

    data class ActivityPeriod(val isEvening: Boolean, val percent: Int) : Highlight {
        override val key = "activity_period"
        override val shareable = true
    }

    data class FastestReplier(val name: String, val averageMs: Long) : Highlight {
        override val key = "fastest_replier"
        override val shareable = true
    }

    data class YouReplyFastest(val name: String, val averageMs: Long) : Highlight {
        override val key = "you_reply_fastest"
        override val shareable = true
    }

    data class YouStartMost(val name: String, val percent: Int) : Highlight {
        override val key = "you_start_most"
        override val shareable = true
    }
}

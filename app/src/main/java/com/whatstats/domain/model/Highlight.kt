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

    data class PeakTimeBlock(val startHour: Int, val endHour: Int) : Highlight {
        override val key = "peak_time_block"
    }

    data class IntenseDay(val dayOfWeek: Int, val count: Int) : Highlight {
        override val key = "intense_day"
    }

    data class WeekendShare(val percent: Int) : Highlight {
        override val key = "weekend_share"
    }

    data class ActivityPeriod(val isEvening: Boolean, val percent: Int) : Highlight {
        override val key = "activity_period"
    }
}

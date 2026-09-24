package com.whatstats.domain.engagement

import com.whatstats.domain.model.GlobalStats

object MyActivity {
    const val ME_JID = "me@s.whatsapp.net"

    fun byHour(stats: GlobalStats): IntArray {
        val result = IntArray(24)
        stats.hourlyDistribution.forEach { bucket ->
            if (bucket.hour in 0..23) {
                result[bucket.hour] += bucket.senderBreakdown.filter { it.jid == ME_JID }.sumOf { it.count }
            }
        }
        return result
    }

    fun byWeekday(stats: GlobalStats): IntArray {
        val result = IntArray(7)
        stats.weeklyDistribution.forEach { bucket ->
            if (bucket.dayOfWeek in 0..6) {
                result[bucket.dayOfWeek] += bucket.senderBreakdown.filter { it.jid == ME_JID }.sumOf { it.count }
            }
        }
        return result
    }
}

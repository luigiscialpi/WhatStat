package com.whatstats.telemetry

import com.google.firebase.remoteconfig.FirebaseRemoteConfig

enum class TrackingMode {
    FULL,
    MINIMAL,
    OFF,
}

data class TelemetryRemoteConfig(
    val trackingEnabledGlobal: Boolean = true,
    val trackingMode: TrackingMode = TrackingMode.FULL,
    val allowedEvents: Set<String> = emptySet(),
    val blockedEvents: Set<String> = emptySet(),
    val samplingPercent: Int = 100,
    val dailyEventCapPerUser: Int = 400,
    val configTtlMinutes: Long = 1,
) {
    companion object {
        fun defaults() = TelemetryRemoteConfig()

        fun from(remoteConfig: FirebaseRemoteConfig) =
            TelemetryRemoteConfig(
                trackingEnabledGlobal = remoteConfig.getBoolean("tracking_enabled_global"),
                trackingMode =
                    when (remoteConfig.getString("tracking_mode").trim().lowercase()) {
                        "minimal" -> TrackingMode.MINIMAL
                        "off" -> TrackingMode.OFF
                        else -> TrackingMode.FULL
                    },
                allowedEvents = remoteConfig.getString("tracking_allowed_events_csv").splitCsv(),
                blockedEvents = remoteConfig.getString("tracking_blocked_events_csv").splitCsv(),
                samplingPercent =
                    remoteConfig.getLong("tracking_sampling_percent").toInt().coerceIn(0, 100),
                dailyEventCapPerUser =
                    remoteConfig.getLong("tracking_daily_event_cap_per_user").toInt()
                        .coerceAtLeast(0),
                configTtlMinutes = remoteConfig.getLong("tracking_config_ttl_minutes").coerceAtLeast(0L),
            )

        private fun String.splitCsv(): Set<String> =
            split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
    }
}

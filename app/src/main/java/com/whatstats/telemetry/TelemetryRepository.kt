package com.whatstats.telemetry

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.whatstats.BuildConfig
import com.whatstats.util.DebugLogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelemetryRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val prefsStore: TelemetryPrefsStore,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val random = SecureRandom()
        private var appOpenTracked = false
        private var lastScreenViewKey: String? = null

        private val isFirebaseAvailable: Boolean
            get() = FirebaseApp.getApps(context).isNotEmpty()

        private val _remoteConfig = MutableStateFlow(TelemetryRemoteConfig.defaults())
        val remoteConfig: StateFlow<TelemetryRemoteConfig> = _remoteConfig.asStateFlow()

        private val _isReady = MutableStateFlow(false)
        val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

        private val firebaseAnalytics: FirebaseAnalytics?
            get() = if (isFirebaseAvailable) FirebaseAnalytics.getInstance(context) else null

        fun initialize() {
            if (!isFirebaseAvailable) {
                debugTelemetry("init skip: firebase unavailable")
                return
            }
            firebaseAnalytics?.setAnalyticsCollectionEnabled(true)
            firebaseAnalytics?.setUserProperty("app_version", BuildConfig.VERSION_NAME)
            firebaseAnalytics?.setUserProperty("app_version_code", BuildConfig.VERSION_CODE.toString())
            firebaseAnalytics?.setUserProperty("build_type", if (BuildConfig.DEBUG) "debug" else "release")
            debugTelemetry("init ok: analytics enabled")
            refreshRemoteConfig()
        }

        fun setMode(mode: String) {
            if (!isFirebaseAvailable) return
            if (mode == "root" || mode == "lite") {
                firebaseAnalytics?.setUserProperty("mode", mode)
            }
        }

        fun trackAppOpen() {
            if (appOpenTracked) return
            appOpenTracked = true
            logIfAllowed(
                eventName = "app_opened",
                extra =
                    bundleOf(
                        "version_code" to BuildConfig.VERSION_CODE.toString(),
                        "build_type" to if (BuildConfig.DEBUG) "debug" else "release",
                    ),
            )
        }

        fun trackScreenView(
            screenName: String,
            graphName: String,
        ) {
            val screenKey = "$graphName|$screenName"
            if (screenKey == lastScreenViewKey) return
            lastScreenViewKey = screenKey
            setMode(graphName)
            logIfAllowed(
                eventName = "screen_view_custom",
                extra =
                    bundleOf(
                        "ws_screen_name" to screenName,
                        "ws_graph_name" to graphName,
                        "ws_mode" to graphName,
                        "screen_name" to screenName,
                        "graph_name" to graphName,
                        "mode_root_or_lite" to graphName,
                    ),
            )
        }

        fun trackClick(
            screenName: String,
            ctaName: String,
            section: String,
        ) {
            logIfAllowed(
                eventName = "cta_click",
                extra =
                    bundleOf(
                        "ws_screen_name" to screenName,
                        "screen_name" to screenName,
                        "cta_name" to ctaName,
                        "section" to section,
                    ),
            )
        }

        fun trackAnalysisEvent(
            eventName: String,
            flowType: String,
            source: String,
        ) {
            logIfAllowed(
                eventName = eventName,
                extra =
                    bundleOf(
                        "flow_type" to flowType,
                        "source" to source,
                    ),
            )
        }

        fun trackScreenTime(
            screenName: String,
            stayMs: Long,
        ) {
            logIfAllowed(
                eventName = "screen_time",
                extra =
                    bundleOf(
                        "ws_screen_name" to screenName,
                        "screen_name" to screenName,
                        "stay_ms_bucket" to bucketDuration(stayMs),
                    ),
            )
        }

        fun refreshRemoteConfig() {
            if (!isFirebaseAvailable) {
                _isReady.value = false
                return
            }
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val ttlSeconds =
                if (BuildConfig.DEBUG) {
                    0L
                } else {
                    (_remoteConfig.value.configTtlMinutes.takeIf { it > 0 } ?: 240L) * 60L
                }
            remoteConfig.setConfigSettingsAsync(
                remoteConfigSettings {
                    minimumFetchIntervalInSeconds = ttlSeconds
                },
            )
            remoteConfig.setDefaultsAsync(
                mapOf(
                    "tracking_enabled_global" to true,
                    "tracking_mode" to "full",
                    "tracking_allowed_events_csv" to "",
                    "tracking_blocked_events_csv" to "",
                    "tracking_sampling_percent" to 100,
                    "tracking_daily_event_cap_per_user" to 200,
                    "tracking_config_ttl_minutes" to 240,
                    "force_update_enabled" to false,
                    "min_supported_version_code" to 1L,
                    "force_update_message_it" to "",
                    "force_update_message_en" to "",
                ),
            )
            remoteConfig.fetchAndActivate()
                .addOnCompleteListener {
                    val parsed = TelemetryRemoteConfig.from(remoteConfig)
                    _remoteConfig.value = parsed
                    firebaseAnalytics?.setAnalyticsCollectionEnabled(
                        parsed.trackingEnabledGlobal && parsed.trackingMode != TrackingMode.OFF,
                    )
                    debugTelemetry(
                        "rc active: enabled=${parsed.trackingEnabledGlobal} mode=${parsed.trackingMode} " +
                            "sampling=${parsed.samplingPercent} cap=${parsed.dailyEventCapPerUser} " +
                            "allow=${parsed.allowedEvents} block=${parsed.blockedEvents}",
                    )
                    _isReady.value = true
                }
                .addOnFailureListener {
                    debugTelemetry("rc fetch failed")
                    _isReady.value = false
                }
        }

        private fun logIfAllowed(
            eventName: String,
            extra: Bundle = Bundle(),
        ) {
            val config = _remoteConfig.value
            if (!isFirebaseAvailable) {
                debugTelemetry("drop $eventName: firebase unavailable")
                return
            }
            if (!isEventAllowed(eventName, config)) {
                debugTelemetry("drop $eventName: blocked by config")
                return
            }
            if (!passesSampling(config.samplingPercent)) {
                debugTelemetry("drop $eventName: sampling=${config.samplingPercent}")
                return
            }

            scope.launch {
                if (!prefsStore.canTrackEvent(config.dailyEventCapPerUser)) {
                    debugTelemetry("drop $eventName: cap=${config.dailyEventCapPerUser}")
                    return@launch
                }
                firebaseAnalytics?.logEvent(eventName, extra)
                debugTelemetry("sent $eventName")
            }
        }

        private fun debugTelemetry(message: String) {
            if (!BuildConfig.DEBUG) return
            DebugLogManager.log("Telemetry | $message")
        }

        private fun isEventAllowed(
            eventName: String,
            config: TelemetryRemoteConfig,
        ): Boolean {
            val normalized = eventName.trim().lowercase()
            if (!config.trackingEnabledGlobal) return false
            if (config.trackingMode == TrackingMode.OFF) return false
            if (config.trackingMode == TrackingMode.MINIMAL && normalized !in minimalEvents) return false
            if (normalized in config.blockedEvents) return false
            if (config.allowedEvents.isNotEmpty() && normalized !in config.allowedEvents) return false
            return true
        }

        private fun passesSampling(percent: Int): Boolean {
            if (percent >= 100) return true
            if (percent <= 0) return false
            return random.nextInt(100) < percent
        }

        private fun bucketDuration(valueMs: Long): String =
            when {
                valueMs < 1_000L -> "lt_1s"
                valueMs < 5_000L -> "1_5s"
                valueMs < 15_000L -> "5_15s"
                valueMs < 60_000L -> "15_60s"
                else -> "gt_60s"
            }

        private fun bundleOf(vararg entries: Pair<String, String>): Bundle =
            Bundle().apply {
                entries.forEach { (key, value) -> putString(key, value) }
            }

        private val minimalEvents =
            setOf(
                "app_opened",
                "screen_view_custom",
                "analysis_started",
                "analysis_completed",
            )
    }

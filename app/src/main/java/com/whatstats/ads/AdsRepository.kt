package com.whatstats.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.whatstats.BuildConfig
import com.whatstats.util.DebugLogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: android.content.Context,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val consentInformation = UserMessagingPlatform.getConsentInformation(context)

        private var isMobileAdsInitialized = false
        private var isConsentRequestInFlight = false
        private var interstitialAd: InterstitialAd? = null
        private var lastInterstitialShownAtMs: Long = 0L
        private var easterEggActive = false

        private val _uiState = MutableStateFlow(AdsUiState())
        val uiState: StateFlow<AdsUiState> = _uiState.asStateFlow()

        private companion object {
            const val INTERSTITIAL_COOLDOWN_MS = 12 * 60 * 1000L

            val isTestBuild: Boolean
                get() = BuildConfig.ADMOB_APP_ID == "ca-app-pub-3940256099942544~3347511713"
        }

        private val canRequestAds: Boolean
            get() = isTestBuild || consentInformation.canRequestAds()

        init {
            if (isTestBuild || consentInformation.canRequestAds()) {
                initializeMobileAdsIfPossible()
            }
        }

        fun requestConsent(activity: Activity) {
            if (isConsentRequestInFlight) return
            isConsentRequestInFlight = true
            DebugLogManager.log(
                "AdsRepository.requestConsent | " +
                    "consentStatus=${consentInformation.consentStatus} " +
                    "isConsentFormAvailable=${consentInformation.isConsentFormAvailable} " +
                    "canRequestAds=${consentInformation.canRequestAds()} " +
                    "isTestBuild=$isTestBuild",
            )
            val requestParameters =
                ConsentRequestParameters
                    .Builder()
                    .apply {
                        if (isTestBuild) {
                            setConsentDebugSettings(
                                ConsentDebugSettings
                                    .Builder(activity)
                                    .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_DISABLED)
                                    .build(),
                            )
                        }
                    }.build()

            consentInformation.requestConsentInfoUpdate(
                activity,
                requestParameters,
                {
                    isConsentRequestInFlight = false
                    publishState()
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                        if (formError != null) {
                            DebugLogManager.log(
                                "Ads consent form error | code=${formError.errorCode} msg=${formError.message}",
                            )
                        }
                        publishState()
                        initializeMobileAdsIfPossible()
                    }
                },
                { requestError ->
                    isConsentRequestInFlight = false
                    DebugLogManager.log(
                        "Ads consent update error | code=${requestError.errorCode} msg=${requestError.message}",
                    )
                    publishState(message = requestError.message)
                    initializeMobileAdsIfPossible()
                },
            )
        }

        fun showPrivacyOptions(activity: Activity) {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                if (formError != null) {
                    DebugLogManager.log(
                        "Ads privacy options error | code=${formError.errorCode} msg=${formError.message}",
                    )
                    publishState(message = formError.message)
                } else {
                    publishState()
                    initializeMobileAdsIfPossible()
                }
            }
        }

        fun clearMessage() {
            _uiState.update { it.copy(message = null) }
        }

        fun activateEasterEgg() {
            easterEggActive = true
            interstitialAd = null
            publishState()
        }

        fun tryShowInterstitialAfterLongOperation(
            activity: Activity,
            source: String,
        ): Boolean {
            if (easterEggActive || !isMobileAdsInitialized || !canRequestAds) return false
            val now = System.currentTimeMillis()
            if ((now - lastInterstitialShownAtMs) < INTERSTITIAL_COOLDOWN_MS) {
                preloadInterstitialIfPossible()
                return false
            }

            val readyAd =
                interstitialAd ?: run {
                    preloadInterstitialIfPossible()
                    return false
                }

            interstitialAd = null
            readyAd.fullScreenContentCallback =
                object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        lastInterstitialShownAtMs = System.currentTimeMillis()
                        preloadInterstitialIfPossible()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        DebugLogManager.log(
                            "Interstitial failed to show | code=${adError.code} msg=${adError.message} source=$source",
                        )
                        preloadInterstitialIfPossible()
                    }
                }

            readyAd.show(activity)
            return true
        }

        private fun initializeMobileAdsIfPossible() {
            if (isMobileAdsInitialized) return
            if (!canRequestAds) return

            if (isTestBuild) {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                        .build(),
                )
            }
            MobileAds.initialize(context) {
                if (!isMobileAdsInitialized) {
                    isMobileAdsInitialized = true
                    preloadInterstitialIfPossible()
                    publishState()
                }
            }
            isMobileAdsInitialized = true
            preloadInterstitialIfPossible()
            publishState()
        }

        private fun preloadInterstitialIfPossible() {
            if (!isMobileAdsInitialized || !canRequestAds) return
            if (interstitialAd != null) return

            InterstitialAd.load(
                context,
                BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        interstitialAd = null
                        DebugLogManager.log(
                            "Interstitial load failed | code=${loadAdError.code} msg=${loadAdError.message}",
                        )
                    }
                },
            )
        }

        private fun publishState(message: String? = null) {
            val canRequestAdsValue = canRequestAds
            val isPrivacyOptionsRequired =
                consentInformation.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
            val areAdsEnabled = canRequestAdsValue && isMobileAdsInitialized && !easterEggActive

            _uiState.update { current ->
                current.copy(
                    areAdsEnabled = areAdsEnabled,
                    canRequestAds = canRequestAdsValue,
                    isPrivacyOptionsRequired = isPrivacyOptionsRequired,
                    isMobileAdsInitialized = isMobileAdsInitialized,
                    shouldShowBanner = areAdsEnabled,
                    message = message ?: current.message,
                )
            }
        }
    }

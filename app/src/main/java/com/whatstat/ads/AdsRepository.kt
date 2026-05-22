package com.whatstat.ads

import android.app.Activity
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.whatstat.BuildConfig
import com.whatstat.billing.BillingRepository
import com.whatstat.util.DebugLogManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdsRepository
    @Inject
    constructor(
        @ApplicationContext private val context: android.content.Context,
        billingRepository: BillingRepository,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val consentInformation = UserMessagingPlatform.getConsentInformation(context)

        private var isMobileAdsInitialized = false
        private var isPro = false
        private var interstitialAd: InterstitialAd? = null
        private var lastInterstitialShownAtMs: Long = 0L

        private val _uiState = MutableStateFlow(AdsUiState())
        val uiState: StateFlow<AdsUiState> = _uiState.asStateFlow()

        private companion object {
            const val INTERSTITIAL_COOLDOWN_MS = 12 * 60 * 1000L
        }

        init {
            scope.launch {
                billingRepository.uiState.collect { billingState ->
                    isPro = billingState.isPro
                    if (isPro) {
                        interstitialAd = null
                    } else {
                        preloadInterstitialIfPossible()
                    }
                    publishState()
                }
            }
        }

        fun requestConsent(activity: Activity) {
            val requestParameters =
                ConsentRequestParameters
                    .Builder()
                    .build()

            consentInformation.requestConsentInfoUpdate(
                activity,
                requestParameters,
                {
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

        fun tryShowInterstitialAfterLongOperation(
            activity: Activity,
            source: String,
        ): Boolean {
            if (isPro || !isMobileAdsInitialized || !consentInformation.canRequestAds()) return false
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
            if (!consentInformation.canRequestAds()) return

            MobileAds.initialize(context) {}
            isMobileAdsInitialized = true
            preloadInterstitialIfPossible()
            publishState()
        }

        private fun preloadInterstitialIfPossible() {
            if (isPro) return
            if (!isMobileAdsInitialized || !consentInformation.canRequestAds()) return
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
            val canRequestAds = consentInformation.canRequestAds()
            val isPrivacyOptionsRequired =
                consentInformation.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
            val areAdsEnabled = canRequestAds && isMobileAdsInitialized && !isPro

            _uiState.update { current ->
                current.copy(
                    areAdsEnabled = areAdsEnabled,
                    canRequestAds = canRequestAds,
                    isPrivacyOptionsRequired = isPrivacyOptionsRequired,
                    isMobileAdsInitialized = isMobileAdsInitialized,
                    shouldShowBanner = areAdsEnabled,
                    message = message ?: current.message,
                )
            }
        }
    }

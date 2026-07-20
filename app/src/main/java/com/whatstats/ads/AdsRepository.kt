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
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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
import kotlinx.coroutines.delay
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
        private val rewardedUnlockStore: RewardedUnlockStore,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val consentInformation = UserMessagingPlatform.getConsentInformation(context)

        private var isMobileAdsInitialized = false
        private var isConsentRequestInFlight = false
        private var interstitialAd: InterstitialAd? = null
        private var interstitialLoadedAtMs: Long = 0L
        private var rewardedAd: RewardedAd? = null
        private var rewardedAdLoadedAtMs: Long = 0L
        private var lastInterstitialShownAtMs: Long = 0L
        private var easterEggActive = false
        private var rewardedTabsUnlockedUntilMs: Long = 0L
        private var rewardedPredictionUnlockedUntilMs: Long = 0L
        private var rewardedAdsFreeUnlockedUntilMs: Long = 0L

        private val _uiState = MutableStateFlow(AdsUiState())
        val uiState: StateFlow<AdsUiState> = _uiState.asStateFlow()

        private companion object {
            const val INTERSTITIAL_COOLDOWN_MS = 12 * 60 * 1000L
            const val REWARDED_UNLOCK_DURATION_MS = 2 * 60 * 60 * 1000L

            // Policy Google: non riusare ad precaricati piu' vecchi di un'ora.
            const val PRELOADED_AD_MAX_AGE_MS = 60 * 60 * 1000L

            val isTestBuild: Boolean
                get() = BuildConfig.ADMOB_APP_ID == "ca-app-pub-3940256099942544~3347511713"
        }

        private val canRequestAds: Boolean
            get() = isTestBuild || consentInformation.canRequestAds()

        init {
            scope.launch {
                rewardedUnlockStore.unlockState.collect { persistedState ->
                    rewardedTabsUnlockedUntilMs = persistedState.tabsUnlockUntilMs
                    rewardedPredictionUnlockedUntilMs = persistedState.predictionUnlockUntilMs
                    rewardedAdsFreeUnlockedUntilMs = persistedState.adsFreeUnlockUntilMs
                    maybeExpireRewardedUnlocks()
                    publishState()
                    preloadRewardedAdIfPossible()
                    if (isRewardedTabsUnlockedNow()) {
                        scheduleTabsUnlockExpiryUpdate()
                    }
                    if (isRewardedPredictionUnlockedNow()) {
                        schedulePredictionUnlockExpiryUpdate()
                    }
                    if (isRewardedAdsFreeUnlockedNow()) {
                        scheduleAdsFreeUnlockExpiryUpdate()
                    }
                }
            }

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
            rewardedAd = null
            publishState()
        }

        fun refreshRewardedInsightsLockState() {
            publishState()
        }

        fun requestRewardedTabsUnlock(
            activity: Activity,
            source: String,
            onResult: (Boolean) -> Unit,
        ) {
            requestRewardedUnlock(
                activity = activity,
                source = source,
                isAlreadyUnlocked = { isRewardedTabsUnlockedNow() },
                applyReward = {
                    rewardedTabsUnlockedUntilMs = System.currentTimeMillis() + REWARDED_UNLOCK_DURATION_MS
                    scope.launch {
                        rewardedUnlockStore.setTabsUnlockUntilMs(rewardedTabsUnlockedUntilMs)
                    }
                    scheduleTabsUnlockExpiryUpdate()
                },
                onResult = onResult,
            )
        }

        fun requestRewardedPredictionUnlock(
            activity: Activity,
            source: String,
            onResult: (Boolean) -> Unit,
        ) {
            requestRewardedUnlock(
                activity = activity,
                source = source,
                isAlreadyUnlocked = { isRewardedPredictionUnlockedNow() },
                applyReward = {
                    rewardedPredictionUnlockedUntilMs = System.currentTimeMillis() + REWARDED_UNLOCK_DURATION_MS
                    scope.launch {
                        rewardedUnlockStore.setPredictionUnlockUntilMs(rewardedPredictionUnlockedUntilMs)
                    }
                    schedulePredictionUnlockExpiryUpdate()
                },
                onResult = onResult,
            )
        }

        fun requestRewardedAdsFreeUnlock(
            activity: Activity,
            source: String,
            onResult: (Boolean) -> Unit,
        ) {
            requestRewardedUnlock(
                activity = activity,
                source = source,
                isAlreadyUnlocked = { isRewardedAdsFreeUnlockedNow() },
                applyReward = {
                    rewardedAdsFreeUnlockedUntilMs = System.currentTimeMillis() + REWARDED_UNLOCK_DURATION_MS
                    scope.launch {
                        rewardedUnlockStore.setAdsFreeUnlockUntilMs(rewardedAdsFreeUnlockedUntilMs)
                    }
                    scheduleAdsFreeUnlockExpiryUpdate()
                },
                onResult = onResult,
            )
        }

        private fun requestRewardedUnlock(
            activity: Activity,
            source: String,
            isAlreadyUnlocked: () -> Boolean,
            applyReward: () -> Unit,
            onResult: (Boolean) -> Unit,
        ) {
            if (isAlreadyUnlocked()) {
                publishState()
                onResult(true)
                return
            }

            if (!isMobileAdsInitialized || !canRequestAds) {
                onResult(false)
                return
            }

            if (isRewardedAdExpired()) {
                rewardedAd = null
            }

            val readyAd =
                rewardedAd ?: run {
                    preloadRewardedAdIfPossible()
                    onResult(false)
                    return
                }

            rewardedAd = null
            var rewardEarned = false
            var callbackSent = false

            readyAd.fullScreenContentCallback =
                object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        if (!callbackSent) {
                            callbackSent = true
                            onResult(rewardEarned)
                        }
                        preloadRewardedAdIfPossible()
                        publishState()
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        DebugLogManager.log(
                            "Rewarded ad failed to show | code=${adError.code} msg=${adError.message} source=$source",
                        )
                        if (!callbackSent) {
                            callbackSent = true
                            onResult(false)
                        }
                        preloadRewardedAdIfPossible()
                        publishState()
                    }
                }

            readyAd.show(activity) {
                rewardEarned = true
                applyReward()
                publishState()
            }
        }

        fun tryShowInterstitialAfterLongOperation(
            activity: Activity,
            source: String,
        ): Boolean {
            if (easterEggActive || !isMobileAdsInitialized || !canRequestAds) return false
            if (isRewardedAdsFreeUnlockedNow()) return false
            val now = System.currentTimeMillis()
            if ((now - lastInterstitialShownAtMs) < INTERSTITIAL_COOLDOWN_MS) {
                preloadInterstitialIfPossible()
                return false
            }

            if (isInterstitialExpired()) {
                interstitialAd = null
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
                    preloadRewardedAdIfPossible()
                    publishState()
                }
            }
            isMobileAdsInitialized = true
            preloadInterstitialIfPossible()
            preloadRewardedAdIfPossible()
            publishState()
        }

        private fun isInterstitialExpired(): Boolean {
            val ad = interstitialAd ?: return false
            return (System.currentTimeMillis() - interstitialLoadedAtMs) >= PRELOADED_AD_MAX_AGE_MS
        }

        private fun isRewardedAdExpired(): Boolean {
            val ad = rewardedAd ?: return false
            return (System.currentTimeMillis() - rewardedAdLoadedAtMs) >= PRELOADED_AD_MAX_AGE_MS
        }

        private fun preloadInterstitialIfPossible() {
            if (!isMobileAdsInitialized || !canRequestAds) return
            if (isInterstitialExpired()) {
                interstitialAd = null
            }
            if (interstitialAd != null) return

            InterstitialAd.load(
                context,
                BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        interstitialLoadedAtMs = System.currentTimeMillis()
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

        private fun preloadRewardedAdIfPossible() {
            maybeExpireRewardedUnlocks()
            if (!isMobileAdsInitialized || !canRequestAds) return
            if (easterEggActive ||
                (isRewardedTabsUnlockedNow() && isRewardedPredictionUnlockedNow() && isRewardedAdsFreeUnlockedNow())
            ) {
                return
            }
            if (isRewardedAdExpired()) {
                rewardedAd = null
            }
            if (rewardedAd != null) return

            RewardedAd.load(
                context,
                BuildConfig.ADMOB_REWARDED_UNIT_ID,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        rewardedAdLoadedAtMs = System.currentTimeMillis()
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        rewardedAd = null
                        DebugLogManager.log(
                            "Rewarded ad load failed | code=${loadAdError.code} msg=${loadAdError.message}",
                        )
                    }
                },
            )
        }

        private fun isRewardedTabsUnlockedNow(): Boolean {
            if (easterEggActive) return true
            return rewardedTabsUnlockedUntilMs > System.currentTimeMillis()
        }

        private fun isRewardedPredictionUnlockedNow(): Boolean {
            if (easterEggActive) return true
            return rewardedPredictionUnlockedUntilMs > System.currentTimeMillis()
        }

        private fun isRewardedAdsFreeUnlockedNow(): Boolean {
            if (easterEggActive) return true
            return rewardedAdsFreeUnlockedUntilMs > System.currentTimeMillis()
        }

        private fun maybeExpireRewardedUnlocks() {
            if (easterEggActive) return
            val now = System.currentTimeMillis()
            if (rewardedTabsUnlockedUntilMs > 0L && rewardedTabsUnlockedUntilMs <= now) {
                rewardedTabsUnlockedUntilMs = 0L
                scope.launch {
                    rewardedUnlockStore.setTabsUnlockUntilMs(0L)
                }
            }
            if (rewardedPredictionUnlockedUntilMs > 0L && rewardedPredictionUnlockedUntilMs <= now) {
                rewardedPredictionUnlockedUntilMs = 0L
                scope.launch {
                    rewardedUnlockStore.setPredictionUnlockUntilMs(0L)
                }
            }
            if (rewardedAdsFreeUnlockedUntilMs > 0L && rewardedAdsFreeUnlockedUntilMs <= now) {
                rewardedAdsFreeUnlockedUntilMs = 0L
                scope.launch {
                    rewardedUnlockStore.setAdsFreeUnlockUntilMs(0L)
                }
            }
        }

        private fun scheduleTabsUnlockExpiryUpdate() {
            val unlockUntil = rewardedTabsUnlockedUntilMs
            if (unlockUntil <= 0L) return

            scope.launch {
                val remainingMs = unlockUntil - System.currentTimeMillis()
                if (remainingMs > 0L) {
                    delay(remainingMs)
                }
                if (!easterEggActive && rewardedTabsUnlockedUntilMs <= System.currentTimeMillis()) {
                    rewardedTabsUnlockedUntilMs = 0L
                    scope.launch {
                        rewardedUnlockStore.setTabsUnlockUntilMs(0L)
                    }
                    publishState()
                    preloadRewardedAdIfPossible()
                }
            }
        }

        private fun schedulePredictionUnlockExpiryUpdate() {
            val unlockUntil = rewardedPredictionUnlockedUntilMs
            if (unlockUntil <= 0L) return

            scope.launch {
                val remainingMs = unlockUntil - System.currentTimeMillis()
                if (remainingMs > 0L) {
                    delay(remainingMs)
                }
                if (!easterEggActive && rewardedPredictionUnlockedUntilMs <= System.currentTimeMillis()) {
                    rewardedPredictionUnlockedUntilMs = 0L
                    scope.launch {
                        rewardedUnlockStore.setPredictionUnlockUntilMs(0L)
                    }
                    publishState()
                    preloadRewardedAdIfPossible()
                }
            }
        }

        private fun scheduleAdsFreeUnlockExpiryUpdate() {
            val unlockUntil = rewardedAdsFreeUnlockedUntilMs
            if (unlockUntil <= 0L) return

            scope.launch {
                val remainingMs = unlockUntil - System.currentTimeMillis()
                if (remainingMs > 0L) {
                    delay(remainingMs)
                }
                if (!easterEggActive && rewardedAdsFreeUnlockedUntilMs <= System.currentTimeMillis()) {
                    rewardedAdsFreeUnlockedUntilMs = 0L
                    scope.launch {
                        rewardedUnlockStore.setAdsFreeUnlockUntilMs(0L)
                    }
                    publishState()
                    preloadInterstitialIfPossible()
                    preloadRewardedAdIfPossible()
                }
            }
        }

        private fun publishState(message: String? = null) {
            maybeExpireRewardedUnlocks()
            val canRequestAdsValue = canRequestAds
            val isPrivacyOptionsRequired =
                consentInformation.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
            val rewardedAdsFreeUnlocked = isRewardedAdsFreeUnlockedNow()
            val areAdsEnabled = canRequestAdsValue && isMobileAdsInitialized && !easterEggActive && !rewardedAdsFreeUnlocked

            _uiState.update { current ->
                current.copy(
                    areAdsEnabled = areAdsEnabled,
                    canRequestAds = canRequestAdsValue,
                    isPrivacyOptionsRequired = isPrivacyOptionsRequired,
                    isMobileAdsInitialized = isMobileAdsInitialized,
                    shouldShowBanner = areAdsEnabled,
                    message = message ?: current.message,
                    easterEggActive = easterEggActive,
                    rewardedTabsUnlocked = isRewardedTabsUnlockedNow(),
                    rewardedPredictionUnlocked = isRewardedPredictionUnlockedNow(),
                    rewardedAdsFreeUnlocked = rewardedAdsFreeUnlocked,
                    rewardedAdsFreeUnlockedUntilMs = rewardedAdsFreeUnlockedUntilMs,
                )
            }
        }
    }

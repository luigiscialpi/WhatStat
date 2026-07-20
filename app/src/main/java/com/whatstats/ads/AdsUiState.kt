package com.whatstats.ads

data class AdsUiState(
    val areAdsEnabled: Boolean = false,
    val canRequestAds: Boolean = false,
    val isPrivacyOptionsRequired: Boolean = false,
    val isMobileAdsInitialized: Boolean = false,
    val shouldShowBanner: Boolean = false,
    val message: String? = null,
    val easterEggActive: Boolean = false,
    val rewardedTabsUnlocked: Boolean = false,
    val rewardedPredictionUnlocked: Boolean = false,
    val rewardedAdsFreeUnlocked: Boolean = false,
    val rewardedAdsFreeUnlockedUntilMs: Long = 0L,
)

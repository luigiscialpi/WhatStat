package com.whatstats.billing

data class ProBillingUiState(
    val isPro: Boolean = false,
    val isProcessing: Boolean = false,
    val isBillingReady: Boolean = false,
    val productPrice: String? = null,
    val message: String? = null,
)

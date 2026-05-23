package com.whatstat.billing

import android.app.Activity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.whatstat.BuildConfig
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
class BillingRepository
    @Inject
    constructor(
        @ApplicationContext private val context: android.content.Context,
        private val entitlementStore: ProEntitlementStore,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val onReadyQueue = mutableListOf<() -> Unit>()

        private var billingClient: BillingClient? = null
        private var isConnecting = false
        private var productDetails: ProductDetails? = null

        private val _uiState = MutableStateFlow(ProBillingUiState())
        val uiState: StateFlow<ProBillingUiState> = _uiState.asStateFlow()

        private var sessionProUnlocked = false
        private val purchaseListener =
            PurchasesUpdatedListener { billingResult, purchases ->
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        if (purchases.isNullOrEmpty()) {
                            updateMessage("Acquisto completato, ma nessun prodotto ricevuto.")
                            _uiState.update { it.copy(isProcessing = false) }
                        } else {
                            handlePurchases(purchases)
                        }
                    }
                    BillingClient.BillingResponseCode.USER_CANCELED -> {
                        updateMessage("Acquisto annullato.")
                        _uiState.update { it.copy(isProcessing = false) }
                    }
                    else -> {
                        updateMessage("Acquisto non riuscito (${billingResult.responseCode}).")
                        _uiState.update { it.copy(isProcessing = false) }
                    }
                }
            }

        init {
            scope.launch {
                entitlementStore.isPro.collect { cachedIsPro ->
                    if (!sessionProUnlocked) {
                        _uiState.update { current ->
                            current.copy(isPro = cachedIsPro)
                        }
                    }
                }
            }
            start()
        }

        fun start() {
            connectIfNeeded {
                refreshProductDetails()
                refreshPurchases()
            }
        }

        fun launchPurchase(activity: Activity) {
            _uiState.update { it.copy(isProcessing = true, message = null) }
            connectIfNeeded {
                val details = productDetails
                if (details == null) {
                    _uiState.update { it.copy(isProcessing = false) }
                    updateMessage("Prodotto Pro non disponibile. Riprova tra poco.")
                    refreshProductDetails()
                    return@connectIfNeeded
                }

                val productParams =
                    BillingFlowParams.ProductDetailsParams
                        .newBuilder()
                        .setProductDetails(details)
                        .build()

                val flowParams =
                    BillingFlowParams
                        .newBuilder()
                        .setProductDetailsParamsList(listOf(productParams))
                        .build()

                val result = requireNotNull(billingClient).launchBillingFlow(activity, flowParams)
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    _uiState.update { it.copy(isProcessing = false) }
                    updateMessage("Impossibile avviare acquisto (${result.responseCode}).")
                }
            }
        }

        fun restorePurchases() {
            _uiState.update { it.copy(isProcessing = true, message = null) }
            connectIfNeeded {
                refreshPurchases()
            }
        }

        fun clearMessage() {
            _uiState.update { it.copy(message = null) }
        }

        private fun connectIfNeeded(onReady: () -> Unit) {
            val client = billingClient ?: createClient().also { billingClient = it }

            if (client.isReady) {
                _uiState.update { it.copy(isBillingReady = true) }
                onReady()
                return
            }

            onReadyQueue.add(onReady)
            if (isConnecting) return
            isConnecting = true

            client.startConnection(
                object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        isConnecting = false
                        val connected = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                        _uiState.update { it.copy(isBillingReady = connected) }
                        if (!connected) {
                            _uiState.update { it.copy(isProcessing = false) }
                            updateMessage("Billing non disponibile (${billingResult.responseCode}).")
                            onReadyQueue.clear()
                            return
                        }

                        val callbacks = onReadyQueue.toList()
                        onReadyQueue.clear()
                        callbacks.forEach { it.invoke() }
                    }

                    override fun onBillingServiceDisconnected() {
                        _uiState.update { it.copy(isBillingReady = false) }
                    }
                },
            )
        }

        private fun createClient(): BillingClient {
            return BillingClient
                .newBuilder(context)
                .setListener(purchaseListener)
                .enablePendingPurchases(
                    PendingPurchasesParams
                        .newBuilder()
                        .enableOneTimeProducts()
                        .build(),
                )
                .enableAutoServiceReconnection()
                .build()
        }

        private fun refreshProductDetails() {
            val client = billingClient ?: return
            if (!client.isReady) return

            val product =
                QueryProductDetailsParams
                    .Product
                    .newBuilder()
                    .setProductId(BuildConfig.PRO_PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()

            val params =
                QueryProductDetailsParams
                    .newBuilder()
                    .setProductList(listOf(product))
                    .build()

            client.queryProductDetailsAsync(params) { billingResult, queryResult ->
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    updateMessage("Errore caricamento prezzo Pro (${billingResult.responseCode}).")
                    return@queryProductDetailsAsync
                }

                val details = queryResult.productDetailsList.firstOrNull()
                productDetails = details
                val price = details?.oneTimePurchaseOfferDetails?.formattedPrice
                _uiState.update { it.copy(productPrice = price) }
            }
        }

        private fun refreshPurchases() {
            val client = billingClient ?: return
            if (!client.isReady) return

            val params =
                QueryPurchasesParams
                    .newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()

            client.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    _uiState.update { it.copy(isProcessing = false) }
                    updateMessage("Errore ripristino acquisti (${billingResult.responseCode}).")
                    return@queryPurchasesAsync
                }
                handlePurchases(purchases)
            }
        }

        private fun handlePurchases(purchases: List<Purchase>) {
            scope.launch {
                val matchingPurchases =
                    purchases.filter { purchase ->
                        purchase.products.contains(BuildConfig.PRO_PRODUCT_ID)
                    }

                matchingPurchases.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
                        acknowledgePurchase(purchase)
                    }
                }

                val hasPro =
                    matchingPurchases.any { purchase ->
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                    }

                entitlementStore.setPro(hasPro)
                _uiState.update { it.copy(isProcessing = false, isPro = hasPro) }
                if (hasPro) {
                    updateMessage("Pro attivata con successo.")
                } else {
                    updateMessage("Nessun acquisto Pro attivo.")
                }
            }
        }

        private fun acknowledgePurchase(purchase: Purchase) {
            val client = billingClient ?: return
            if (!client.isReady) return

            val params =
                AcknowledgePurchaseParams
                    .newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()

            client.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    DebugLogManager.log(
                        "Billing acknowledge failed | code=${result.responseCode} product=${purchase.products.joinToString()}",
                    )
                }
            }
        }

        fun unlockProForSession() {
            sessionProUnlocked = true
            _uiState.update { it.copy(isPro = true) }
        }

        private fun updateMessage(message: String) {
            _uiState.update { it.copy(message = message) }
            DebugLogManager.log("Billing | $message")
        }
    }

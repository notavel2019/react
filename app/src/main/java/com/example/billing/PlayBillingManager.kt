package com.example.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlayBillingSnapshot(
    val isConnected: Boolean = false,
    val isLoading: Boolean = true,
    val isSubscribed: Boolean = false,
    val isPendingPayment: Boolean = false,
    val hasPlayFreeTrialOffer: Boolean = false,
    val formattedAnnualPrice: String = PlayBillingManager.DEFAULT_PRICE_BRL,
    val billingMessage: String? = null,
    val isBillingUnavailable: Boolean = false
)

/**
 * Google Play Billing Library (v9) manager for React Studio's annual subscription.
 *
 * Play Console Configuration:
 * - Product ID (Subscription): [PRODUCT_ID_ANNUAL] ("react_studio_annual")
 * - Base Plan ID: "annual-plan" (1 year, R$ 20,00 / ano)
 * - Free Trial Offer ID / Tag: "trial-4d" (4 days free trial, then R$ 20,00 / ano)
 */
class PlayBillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID_ANNUAL = "react_studio_annual"
        const val OFFER_TAG_FREE_TRIAL = "trial-4d"
        const val DEFAULT_PRICE_BRL = "R$ 20,00"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _snapshot = MutableStateFlow(PlayBillingSnapshot())
    val snapshot: StateFlow<PlayBillingSnapshot> = _snapshot.asStateFlow()

    @Volatile
    private var annualProductDetails: ProductDetails? = null

    private val billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .enableAutoServiceReconnection()
        .build()

    fun startConnection() {
        if (billingClient.isReady) {
            refreshAll()
            return
        }
        _snapshot.update { it.copy(isLoading = true) }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                when (billingResult.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        _snapshot.update {
                            it.copy(
                                isConnected = true,
                                isBillingUnavailable = false
                            )
                        }
                        refreshAll()
                    }
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
                    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
                    BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED -> {
                        _snapshot.update {
                            it.copy(
                                isConnected = false,
                                isLoading = false,
                                isBillingUnavailable = true
                            )
                        }
                    }
                    else -> {
                        _snapshot.update {
                            it.copy(
                                isConnected = false,
                                isLoading = false
                            )
                        }
                    }
                }
            }

            override fun onBillingServiceDisconnected() {
                _snapshot.update { it.copy(isConnected = false) }
            }
        })
    }

    fun refreshAll() {
        if (!billingClient.isReady) {
            startConnection()
            return
        }
        queryAnnualProductDetails()
        queryActiveSubscriptions(notifyIfEmpty = false)
    }

    fun restorePurchases() {
        if (!billingClient.isReady) {
            _snapshot.update {
                it.copy(billingMessage = "Conectando à Google Play Store para restaurar compras...")
            }
            startConnection()
            return
        }
        _snapshot.update { it.copy(isLoading = true, billingMessage = null) }
        queryActiveSubscriptions(notifyIfEmpty = true)
    }

    fun clearBillingMessage() {
        _snapshot.update { it.copy(billingMessage = null) }
    }

    fun launchSubscriptionPurchase(activity: Activity): Boolean {
        if (!billingClient.isReady) {
            _snapshot.update {
                it.copy(billingMessage = "Google Play indisponível no momento. Verifique sua conta Play Store.")
            }
            startConnection()
            return false
        }

        val details = annualProductDetails
        if (details == null) {
            queryAnnualProductDetails()
            _snapshot.update {
                it.copy(
                    billingMessage = "Carregando plano anual (${DEFAULT_PRICE_BRL}/ano) na Google Play. Tente novamente em instantes."
                )
            }
            return false
        }

        val selectedOffer = selectBestSubscriptionOffer(details)
        if (selectedOffer == null) {
            _snapshot.update {
                it.copy(billingMessage = "Nenhuma oferta ativa encontrada para o plano anual na Google Play.")
            }
            return false
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(selectedOffer.offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _snapshot.update {
                it.copy(billingMessage = mapBillingError(result))
            }
            return false
        }
        return true
    }

    fun openSubscriptionManagement(targetContext: Context) {
        val packageName = Uri.encode(targetContext.packageName)
        val sku = Uri.encode(PRODUCT_ID_ANNUAL)
        val url = "https://play.google.com/store/account/subscriptions?sku=$sku&package=$packageName"
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            targetContext.startActivity(intent)
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                processPurchases(purchases.orEmpty(), notifyIfEmpty = false)
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _snapshot.update {
                    it.copy(billingMessage = "Assinatura cancelada antes da confirmação.")
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryActiveSubscriptions(notifyIfEmpty = false)
                _snapshot.update {
                    it.copy(billingMessage = "Você já possui a assinatura anual ativa!")
                }
            }
            else -> {
                _snapshot.update {
                    it.copy(billingMessage = mapBillingError(billingResult))
                }
            }
        }
    }

    private fun queryAnnualProductDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID_ANNUAL)
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = queryResult.productDetailsList.firstOrNull {
                    it.productId == PRODUCT_ID_ANNUAL
                }
                annualProductDetails = details
                if (details != null) {
                    val bestOffer = selectBestSubscriptionOffer(details)
                    val phases = bestOffer?.pricingPhases?.pricingPhaseList.orEmpty()
                    val hasFreeTrial = phases.any { it.priceAmountMicros == 0L }
                    val recurringPhase = phases.lastOrNull { it.priceAmountMicros > 0L }
                        ?: phases.lastOrNull()
                    val formattedPrice = recurringPhase?.formattedPrice?.takeIf { it.isNotBlank() }
                        ?: DEFAULT_PRICE_BRL

                    _snapshot.update {
                        it.copy(
                            hasPlayFreeTrialOffer = hasFreeTrial,
                            formattedAnnualPrice = formattedPrice
                        )
                    }
                }
            }
        }
    }

    private fun queryActiveSubscriptions(notifyIfEmpty: Boolean) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases, notifyIfEmpty = notifyIfEmpty)
            } else {
                _snapshot.update {
                    it.copy(
                        isLoading = false,
                        billingMessage = if (notifyIfEmpty) mapBillingError(billingResult) else it.billingMessage
                    )
                }
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>, notifyIfEmpty: Boolean) {
        val annualPurchases = purchases.filter { it.products.contains(PRODUCT_ID_ANNUAL) }
        val activePurchase = annualPurchases.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        val pendingPurchase = annualPurchases.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PENDING
        }

        if (activePurchase != null) {
            if (!activePurchase.isAcknowledged) {
                acknowledgePurchase(activePurchase)
            }
            _snapshot.update {
                it.copy(
                    isLoading = false,
                    isSubscribed = true,
                    isPendingPayment = false,
                    billingMessage = if (notifyIfEmpty) "Assinatura anual restaurada com sucesso!" else it.billingMessage
                )
            }
        } else if (pendingPurchase != null) {
            // Never grant PRO entitlement while purchase is still PENDING
            _snapshot.update {
                it.copy(
                    isLoading = false,
                    isSubscribed = false,
                    isPendingPayment = true,
                    billingMessage = "Pagamento pendente de confirmação na Google Play."
                )
            }
        } else {
            _snapshot.update {
                it.copy(
                    isLoading = false,
                    isSubscribed = false,
                    isPendingPayment = false,
                    billingMessage = if (notifyIfEmpty) {
                        "Nenhuma assinatura ativa encontrada nesta conta Google."
                    } else {
                        it.billingMessage
                    }
                )
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        scope.launch {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { /* Acknowledged within 3-day Play window */ }
        }
    }

    private fun selectBestSubscriptionOffer(
        details: ProductDetails
    ): ProductDetails.SubscriptionOfferDetails? {
        val offers = details.subscriptionOfferDetails.orEmpty()
        if (offers.isEmpty()) return null

        // Prefer the 4-day free trial offer if the user is eligible on Google Play
        val explicitTrial = offers.firstOrNull { offer ->
            offer.offerTags.contains(OFFER_TAG_FREE_TRIAL) ||
                offer.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        }
        return explicitTrial ?: offers.first()
    }

    private fun mapBillingError(result: BillingResult): String {
        return when (result.responseCode) {
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
                "Google Play Billing indisponível neste dispositivo."
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.NETWORK_ERROR ->
                "Sem conexão com a Google Play. Verifique sua internet."
            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
                "Plano anual ainda não publicado na Google Play Console."
            else ->
                "Não foi possível concluir a compra (${result.responseCode})."
        }
    }

    fun release() {
        runCatching { billingClient.endConnection() }
        scope.cancel()
    }
}

package com.whatstats.ui.component.ads

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.setPadding
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import com.whatstats.BuildConfig
import com.whatstats.util.DebugLogManager
import android.graphics.Color as AndroidColor

@Composable
@Suppress("FunctionName")
fun NativeAdCard(
    modifier: Modifier = Modifier,
    adUnitId: String = BuildConfig.ADMOB_NATIVE_UNIT_ID,
) {
    val context = LocalContext.current
    var nativeAd by remember(adUnitId) { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(context, adUnitId) {
        val adLoader =
            AdLoader
                .Builder(context, adUnitId)
                .forNativeAd { loadedAd ->
                    nativeAd?.destroy()
                    nativeAd = loadedAd
                }.withNativeAdOptions(
                    NativeAdOptions
                        .Builder()
                        .build(),
                ).withAdListener(
                    object : AdListener() {
                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            DebugLogManager.log(
                                "Native ad load failed | code=${loadAdError.code} msg=${loadAdError.message}",
                            )
                        }
                    },
                ).build()

        adLoader.loadAd(AdRequest.Builder().build())

        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    val loadedAd = nativeAd

    if (loadedAd == null) {
        // Placeholder: riserva spazio mentre l'ad carica, evita layout shift
        Box(
            modifier =
                modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFFEEEEEE)),
        )
        return
    }

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { createNativeAdView(it) },
        update = { adView ->
            bindNativeAd(adView, loadedAd)
        },
    )
}

private data class NativeAdAssets(
    val headlineText: TextView,
    val bodyText: TextView,
    val ctaButton: Button,
    val mediaAssetView: MediaView,
)

private fun createNativeAdView(context: Context): NativeAdView {
    val adView = NativeAdView(context)
    val headlineText = TextView(context)
    val bodyText = TextView(context)
    val ctaButton = Button(context)
    val mediaAssetView = MediaView(context)

    val paddingPx =
        TypedValue
            .applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f,
                context.resources.displayMetrics,
            ).toInt()

    val rootLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(paddingPx)
            setBackgroundColor(AndroidColor.parseColor("#EAF3FF"))
        }

    val adLabel =
        TextView(context).apply {
            text = "Ad"
            setTextColor(AndroidColor.parseColor("#0D47A1"))
            textSize = 12f
        }

    headlineText.apply {
        setTextColor(AndroidColor.parseColor("#111111"))
        textSize = 16f
    }

    bodyText.apply {
        setTextColor(AndroidColor.parseColor("#333333"))
        textSize = 14f
    }

    mediaAssetView.layoutParams =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )

    ctaButton.apply {
        isAllCaps = false
        setBackgroundColor(AndroidColor.parseColor("#1565C0"))
        setTextColor(AndroidColor.WHITE)
        textSize = 14f
    }

    rootLayout.addView(adLabel)
    rootLayout.addView(headlineText)
    rootLayout.addView(bodyText)
    rootLayout.addView(mediaAssetView)
    rootLayout.addView(ctaButton)
    adView.addView(
        rootLayout,
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ),
    )

    adView.setHeadlineView(headlineText)
    adView.setBodyView(bodyText)
    adView.setMediaView(mediaAssetView)
    adView.setCallToActionView(ctaButton)
    adView.tag = NativeAdAssets(headlineText, bodyText, ctaButton, mediaAssetView)
    return adView
}

private fun bindNativeAd(
    adView: NativeAdView,
    nativeAd: NativeAd,
) {
    val assets = adView.tag as? NativeAdAssets ?: return
    assets.headlineText.text = nativeAd.headline.orEmpty()
    assets.bodyText.text = nativeAd.body.orEmpty()
    val callToAction = nativeAd.callToAction.orEmpty()
    assets.ctaButton.text = callToAction
    assets.ctaButton.visibility = if (callToAction.isBlank()) View.GONE else View.VISIBLE
    assets.mediaAssetView.mediaContent = nativeAd.mediaContent
    adView.setNativeAd(nativeAd)
}

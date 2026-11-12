package com.gcl.gamechiefads.sdk

import android.app.Activity
import com.gcl.gamechiefads.FullScreenAd
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback

/** Loads one interstitial for one unit id. Call from the main thread. Never throws. */
internal object InterstitialSource {
    suspend fun load(unitId: String): FullScreenAd? =
        awaitAd<InterstitialAd> { cb -> InterstitialAd.load(AdRequest.Builder(unitId).build(), cb) }
            ?.let(::SdkInterstitial)
}

private class SdkInterstitial(private val ad: InterstitialAd) : FullScreenAd {
    override val unitId: String get() = ad.adUnitId

    override fun show(activity: Activity, listener: FullScreenAd.Listener) {
        try {
            ad.adEventCallback = object : InterstitialAdEventCallback {
                override fun onAdShowedFullScreenContent() = MainThread.run { listener.onShown() }
                override fun onAdDismissedFullScreenContent() = MainThread.run { listener.onDismissed() }
                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                    MainThread.run { listener.onFailed(fullScreenContentError.message) }
            }
            ad.show(activity)
        } catch (t: Throwable) {
            listener.onFailed(t.message ?: "show failed")
        }
    }

    override fun destroy() { runCatching { ad.destroy() } }
}

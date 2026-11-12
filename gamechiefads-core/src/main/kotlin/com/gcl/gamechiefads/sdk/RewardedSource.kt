package com.gcl.gamechiefads.sdk

import android.app.Activity
import com.gcl.gamechiefads.FullScreenAd
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback

/** Loads one rewarded ad for one unit id. Call from the main thread. Never throws. */
internal object RewardedSource {
    suspend fun load(unitId: String): FullScreenAd? =
        awaitAd<RewardedAd> { cb -> RewardedAd.load(AdRequest.Builder(unitId).build(), cb) }
            ?.let(::SdkRewarded)
}

private class SdkRewarded(private val ad: RewardedAd) : FullScreenAd {
    override val unitId: String get() = ad.adUnitId

    override fun show(activity: Activity, listener: FullScreenAd.Listener) {
        try {
            ad.adEventCallback = object : RewardedAdEventCallback {
                override fun onAdShowedFullScreenContent() = MainThread.run { listener.onShown() }
                override fun onAdDismissedFullScreenContent() = MainThread.run { listener.onDismissed() }
                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                    MainThread.run { listener.onFailed(fullScreenContentError.message) }
            }
            ad.show(activity) { item -> MainThread.run { listener.onRewarded(item.amount, item.type) } }
        } catch (t: Throwable) {
            listener.onFailed(t.message ?: "show failed")
        }
    }

    override fun destroy() { runCatching { ad.destroy() } }
}

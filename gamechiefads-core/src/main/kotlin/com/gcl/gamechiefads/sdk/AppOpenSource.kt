package com.gcl.gamechiefads.sdk

import android.app.Activity
import com.gcl.gamechiefads.FullScreenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError

/** Loads one app-open ad for one unit id. Call from the main thread. Never throws. */
internal object AppOpenSource {
    suspend fun load(unitId: String): FullScreenAd? =
        awaitAd<AppOpenAd> { cb -> AppOpenAd.load(AdRequest.Builder(unitId).build(), cb) }
            ?.let(::SdkAppOpen)
}

private class SdkAppOpen(private val ad: AppOpenAd) : FullScreenAd {
    override val unitId: String get() = ad.adUnitId

    override fun show(activity: Activity, listener: FullScreenAd.Listener) {
        try {
            ad.adEventCallback = object : AppOpenAdEventCallback {
                override fun onAdShowedFullScreenContent() = listener.onShown()
                override fun onAdDismissedFullScreenContent() = listener.onDismissed()
                override fun onAdFailedToShowFullScreenContent(fullScreenContentError: FullScreenContentError) =
                    listener.onFailed(fullScreenContentError.message)
            }
            ad.show(activity)
        } catch (t: Throwable) {
            listener.onFailed(t.message ?: "show failed")
        }
    }

    override fun destroy() { runCatching { ad.destroy() } }
}

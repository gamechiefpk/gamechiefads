package com.gcl.gamechiefads.sdk

import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import kotlinx.coroutines.CancellationException

/** Loads one banner into a fresh [AdView]. Call from the main thread. Never throws; null = no ad. */
internal object BannerSource {

    /** Large anchored adaptive banner sized to [widthDp]. */
    fun adaptiveSize(context: Context, widthDp: Int): AdSize =
        AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)

    suspend fun load(context: Context, unitId: String, size: AdSize): AdView? {
        val view = try { AdView(context) } catch (t: Throwable) { return null }
        val loaded = try {
            awaitAd<BannerAd> { cb -> view.loadAd(BannerAdRequest.Builder(unitId, size).build(), cb) }
        } catch (e: CancellationException) {
            runCatching { view.destroy() }
            throw e
        }
        if (loaded == null) { runCatching { view.destroy() }; return null }
        return view
    }
}

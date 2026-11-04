package com.gcl.gamechiefads.sdk

import android.content.Context
import android.view.View
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import kotlinx.coroutines.CancellationException

/** A loaded banner: the view to add to a layout and the way to free it. No SDK type leaks out. */
internal class LoadedBanner(val view: View, private val release: () -> Unit) {
    fun destroy() { runCatching { release() } }
}

/** Loads one large anchored adaptive banner into a fresh AdView. Main thread. Never throws; null = no ad. */
internal object BannerSource {

    suspend fun load(context: Context, unitId: String, widthDp: Int): LoadedBanner? {
        val view = try { AdView(context) } catch (t: Throwable) { return null }
        val size: AdSize = try {
            AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
        } catch (t: Throwable) {
            runCatching { view.destroy() }
            return null
        }
        val loaded = try {
            awaitAd<BannerAd> { cb -> view.loadAd(BannerAdRequest.Builder(unitId, size).build(), cb) }
        } catch (e: CancellationException) {
            runCatching { view.destroy() }
            throw e
        }
        if (loaded == null) {
            runCatching { view.destroy() }
            return null
        }
        return LoadedBanner(view) { view.destroy() }
    }
}

package com.gcl.gamechiefads.sdk

import android.content.Context
import android.view.View
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import android.os.Bundle
import com.gcl.gamechiefads.BannerType
import kotlinx.coroutines.CancellationException

/** A loaded banner: the view to add to a layout and the way to free it. No SDK type leaks out. */
internal class LoadedBanner(val view: View, private val release: () -> Unit) {
    fun destroy() { runCatching { release() } }
}

/** Loads one banner of the requested [BannerType] into a fresh AdView. Main thread. Never throws; null = no ad. */
internal object BannerSource {

    suspend fun load(context: Context, unitId: String, widthDp: Int, type: BannerType): LoadedBanner? {
        val view = try { AdView(context) } catch (t: Throwable) { return null }
        val size: AdSize = try {
            sizeFor(context, widthDp, type)
        } catch (t: Throwable) {
            runCatching { view.destroy() }
            return null
        }
        val loaded = try {
            awaitAd<BannerAd> { cb -> view.loadAd(request(unitId, size, type), cb) }
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

    private fun sizeFor(context: Context, widthDp: Int, type: BannerType): AdSize = when (type) {
        BannerType.ADAPTIVE, BannerType.COLLAPSIBLE_BOTTOM, BannerType.COLLAPSIBLE_TOP ->
            AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp)
        BannerType.INLINE_ADAPTIVE -> AdSize.getCurrentOrientationInlineAdaptiveBannerAdSize(context, widthDp)
        BannerType.BANNER -> AdSize.BANNER
        BannerType.LARGE_BANNER -> AdSize.LARGE_BANNER
        BannerType.MEDIUM_RECTANGLE -> AdSize.MEDIUM_RECTANGLE
        BannerType.FULL_BANNER -> AdSize.FULL_BANNER
        BannerType.LEADERBOARD -> AdSize.LEADERBOARD
    }

    /** Collapsible banners are an extras key on the request ("collapsible" = "top" | "bottom"). */
    private fun request(unitId: String, size: AdSize, type: BannerType): BannerAdRequest {
        val b = BannerAdRequest.Builder(unitId, size)
        when (type) {
            BannerType.COLLAPSIBLE_BOTTOM -> b.setGoogleExtrasBundle(Bundle().apply { putString("collapsible", "bottom") })
            BannerType.COLLAPSIBLE_TOP -> b.setGoogleExtrasBundle(Bundle().apply { putString("collapsible", "top") })
            else -> Unit
        }
        return b.build()
    }
}

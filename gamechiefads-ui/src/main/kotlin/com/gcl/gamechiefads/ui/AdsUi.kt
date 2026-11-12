package com.gcl.gamechiefads.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import com.gcl.gamechiefads.AdPlacement
import com.gcl.gamechiefads.BannerListener
import com.gcl.gamechiefads.BannerType
import com.gcl.gamechiefads.GamechiefAds

/**
 * Code-only banner for apps that already have a FrameLayout where the banner goes (no XML needed):
 *
 * ```java
 * AdsUi.banner(findViewById(R.id.bannerContainer), AdIds.BANNER_HOME);
 * ```
 * The banner type (adaptive, collapsible, medium rectangle...) comes from the placement's config. A shimmer fills the
 * container while loading and is removed on success AND on failure; the container is hidden when there is no ad
 * (also for premium); the banner is destroyed when the container leaves the window. Calling it again on the same
 * container reuses what is there.
 */
object AdsUi {
    private const val SHIMMER_TAG = "gads_banner_shimmer"

    @JvmStatic
    @JvmOverloads
    fun banner(container: FrameLayout, placement: AdPlacement, listener: BannerListener? = null) {
        val type = GamechiefAds.bannerTypeOf(placement)
        if (container.childCount == 0 && container.findViewWithTag<android.view.View>(SHIMMER_TAG) == null) {
            val dp = container.resources.displayMetrics.density
            val shimmer = ShimmerView(container.context).apply { tag = SHIMMER_TAG }
            container.addView(shimmer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (placeholderDp(type) * dp).toInt()))
        }
        GamechiefAds.banner(container, placement, object : BannerListener {
            override fun onLoaded() {
                removeShimmer(container)
                listener?.onLoaded()
            }

            override fun onFailed(reason: String) {
                removeShimmer(container) // also on failure: never leave a shimmer behind
                listener?.onFailed(reason)
            }
        })
    }

    /** Splash banner (always LARGE_BANNER, 320x100) into [container], with a shimmer while it loads. */
    @JvmStatic
    @JvmOverloads
    fun bannerForSplash(container: FrameLayout, listener: BannerListener? = null) {
        if (container.childCount == 0 && container.findViewWithTag<android.view.View>(SHIMMER_TAG) == null) {
            val dp = container.resources.displayMetrics.density
            val shimmer = ShimmerView(container.context).apply { tag = SHIMMER_TAG }
            container.addView(shimmer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (100 * dp).toInt()))
        }
        GamechiefAds.bannerForSplash(container, object : BannerListener {
            override fun onLoaded() {
                removeShimmer(container)
                listener?.onLoaded()
            }

            override fun onFailed(reason: String) {
                removeShimmer(container)
                listener?.onFailed(reason)
            }
        })
    }

    private fun removeShimmer(container: FrameLayout) {
        container.findViewWithTag<android.view.View>(SHIMMER_TAG)?.let { container.removeView(it) }
    }

    private fun placeholderDp(type: BannerType): Int = when (type) {
        BannerType.MEDIUM_RECTANGLE -> 250
        BannerType.LARGE_BANNER -> 100
        BannerType.LEADERBOARD, BannerType.FULL_BANNER -> 60
        BannerType.BANNER -> 50
        else -> 60
    }
}

package com.gcl.gamechiefads.ui

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.gcl.gamechiefads.BannerPlaceholder
import com.gcl.gamechiefads.BannerType

/**
 * The shimmer shown in a banner FrameLayout while the ad loads. The core module finds this class by name, so every
 * GamechiefAds.loadXxxBannerAd call gets a shimmer automatically when the ui module is in the app.
 */
class ShimmerBannerPlaceholder : BannerPlaceholder {

    override fun show(container: FrameLayout, type: BannerType) {
        if (container.childCount != 0) return
        val dp = container.resources.displayMetrics.density
        val shimmer = ShimmerView(container.context).apply { tag = TAG }
        container.addView(shimmer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (heightDp(type) * dp).toInt()))
    }

    override fun remove(container: FrameLayout) {
        container.findViewWithTag<View>(TAG)?.let { container.removeView(it) }
    }

    private fun heightDp(type: BannerType): Int = when (type) {
        BannerType.MEDIUM_RECTANGLE -> 250
        BannerType.LARGE_BANNER -> 100
        BannerType.LEADERBOARD, BannerType.FULL_BANNER -> 60
        BannerType.BANNER -> 50
        else -> 60
    }

    private companion object {
        const val TAG = "gads_banner_shimmer"
    }
}

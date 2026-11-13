package com.gcl.gamechiefads

import android.widget.FrameLayout

/**
 * What a banner container shows while the ad loads (the ui module's shimmer). Used automatically when the ui module is
 * on the classpath; without it the container just stays empty until the ad arrives.
 */
interface BannerPlaceholder {
    /** Put a placeholder sized for [type] into [container] if it is empty. */
    fun show(container: FrameLayout, type: BannerType)

    /** Take it out again (ad loaded or failed). */
    fun remove(container: FrameLayout)
}

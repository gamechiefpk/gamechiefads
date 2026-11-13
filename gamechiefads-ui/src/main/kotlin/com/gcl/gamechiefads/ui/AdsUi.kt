package com.gcl.gamechiefads.ui

import android.app.Activity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.gcl.gamechiefads.AdPlacement
import com.gcl.gamechiefads.InterstitialListener
import com.gcl.gamechiefads.Placements

/**
 * Native ads (they need the ui module). Banners, interstitials and rewarded ads are all in `GamechiefAds`.
 *
 * ```java
 * AdsUi.loadIntroNativeAd(findViewById(R.id.nativeContainer));          // small native at the bottom, with floors
 * AdsUi.loadFullScreenNativeAd(this, listener);                          // full-screen native, like an interstitial
 * ```
 */
object AdsUi {

    /**
     * Small native ad for the intro screens: fills [container] (any FrameLayout, no XML needed). Units from
     * `introNativeUnits(...)` with floors. A shimmer shows while it loads and the ad hides itself when there is none.
     */
    @JvmStatic
    @JvmOverloads
    fun loadIntroNativeAd(container: FrameLayout, listener: NativeAdTemplateView.Listener? = null) {
        container.removeAllViews()
        val view = NativeAdTemplateView(container.context).apply {
            size = NativeSize.SMALL
            autoLoad = false
            this.listener = listener
        }
        container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        view.load(Placements.INTRO_NATIVE)
    }

    /**
     * Full-screen native ad that behaves like an interstitial (close button after a short countdown). Unit from
     * `fullScreenNativeUnit(...)`, one request, no floors. [listener] may be null; onComplete runs once.
     */
    @JvmStatic
    @JvmOverloads
    fun loadFullScreenNativeAd(
        activity: Activity,
        listener: InterstitialListener? = null,
        options: FullScreenNativeAd.Options = FullScreenNativeAd.Options(),
    ) = FullScreenNativeAd.show(activity, AdPlacement.of(Placements.FULLSCREEN_NATIVE), listener, options)
}

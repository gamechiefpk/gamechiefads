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
    fun loadIntroNativeAd(container: FrameLayout, listener: NativeAdTemplateView.Listener? = null) =
        loadNativeAd(container, Placements.INTRO_NATIVE, NativeSize.SMALL, NativeStyle.DEFAULT, listener)

    /**
     * Native ad for the language selection screen: fills [container], SMALL_MEDIA size (thumbnail left, text and button right). Units from
     * `languageNativeUnits(...)` with floors. Remote Config `{"placements":{"language_native":{"nativeSize":"medium"}}}` changes the size.
     */
    @JvmStatic
    @JvmOverloads
    fun loadLanguageNativeAd(container: FrameLayout, listener: NativeAdTemplateView.Listener? = null) =
        loadNativeAd(container, Placements.LANGUAGE_NATIVE, NativeSize.SMALL_MEDIA, NativeStyle.DEFAULT, listener)

    /**
     * Any native ad in any FrameLayout, no XML of yours: the library inflates the layout for [size], shows a shimmer
     * while loading, hides the container when there is no ad, and destroys the ad with the screen.
     * [placement] is the name of a native spot ([Placements.INTRO_NATIVE], [Placements.LANGUAGE_NATIVE] or your own `nativeAd("name").units(...)`).
     * Without [size] you get a small card (SMALL_MEDIA on the language screen): big sizes are only shown when you ask for them or Remote Config does.
     *
     * ```java
     * AdsUi.loadNativeAd(frame, "language_native", NativeSize.MEDIUM);
     * ```
     * A second call on the same container replaces the first ad.
     */
    @JvmStatic
    @JvmOverloads
    fun loadNativeAd(
        container: FrameLayout,
        placement: String,
        size: NativeSize? = null,
        style: NativeStyle = NativeStyle.DEFAULT,
        listener: NativeAdTemplateView.Listener? = null,
    ) {
        val size = size ?: NativeSize.defaultFor(placement) // no size given: a small card (SMALL_MEDIA on the language screen)
        container.removeAllViews()
        val view = NativeAdTemplateView(container.context).apply {
            this.size = size
            this.style = style
            autoLoad = false
            this.listener = listener
        }
        val height = if (size == NativeSize.FULL) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        container.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
        view.load(placement)
    }

    /** Same, with a placement object. */
    @JvmStatic
    @JvmOverloads
    fun loadNativeAd(
        container: FrameLayout,
        placement: AdPlacement,
        size: NativeSize? = null,
        style: NativeStyle = NativeStyle.DEFAULT,
        listener: NativeAdTemplateView.Listener? = null,
    ) = loadNativeAd(container, placement.name, size, style, listener)

    /** Used by the layout tag `gads_native:<placement>[:<size>]` (found by name from the core module). Size is a [NativeSize] name, any case. */
    @JvmStatic
    fun loadNativeAd(container: FrameLayout, placement: String, sizeName: String?) =
        loadNativeAd(container, placement, NativeSize.fromName(sizeName), NativeStyle.DEFAULT, null)

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

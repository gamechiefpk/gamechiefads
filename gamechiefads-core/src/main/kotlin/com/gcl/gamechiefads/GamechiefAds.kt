package com.gcl.gamechiefads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.widget.FrameLayout

/**
 * The whole public surface. Delegates only; every call is safe before init() (it simply shows no ad).
 * Call [init] once from Application.onCreate (main thread).
 */
object GamechiefAds {
    @Volatile private var runtime: AdsRuntime? = null

    @JvmStatic
    @Synchronized
    fun init(app: Application, config: AdsConfig) {
        if (runtime == null) runtime = AdsRuntime(app, config)
    }

    /** Debug trace log (logcat tag "GamechiefAds"). On by default in debuggable apps, ignored in release builds. */
    @JvmStatic
    fun setDebugLogging(enabled: Boolean) {
        runtime?.setLogging(enabled)
    }

    /** Wire from your billing library's premium callback. Premium = no ads of any kind. */
    @JvmStatic
    fun setPremium(premium: Boolean) {
        runtime?.state?.setPremium(premium)
    }

    /** Optional: the Firebase Remote Config string for AdsConfig.remoteConfigKey. Bad JSON is ignored. */
    @JvmStatic
    fun applyRemoteOverrides(json: String?) {
        runtime?.applyRemote(json)
    }

    /** Replaces the "loading ad" overlay shown by on-demand interstitials. Null = no overlay. */
    @JvmStatic
    fun setLoaderUi(ui: AdLoaderUi?) {
        runtime?.loaderUi = ui
    }

    /** Consent -> init -> waterfall -> timeout. [callback] runs once, with or without an ad. */
    @JvmStatic
    fun prepareSplash(activity: Activity, callback: SplashCallback) {
        val r = runtime
        if (r == null) callback.onReady(NoSplashAds) else r.splash.prepare(activity, callback)
    }

    /** On-demand interstitial (default flow): load, then show. [listener] may be null. */
    @JvmStatic
    fun showInterstitial(activity: Activity, placement: String, listener: InterstitialListener?) {
        val r = runtime
        if (r == null) ListenerDispatcher(listener).onFinished(false, "not initialized")
        else r.interstitial.showOnDemand(activity, placement, listener)
    }

    /** Opt-in: request one interstitial for later. No request if one is ready or loading. */
    @JvmStatic
    fun preloadInterstitial(placement: String) {
        runtime?.interstitial?.preload(placement)
    }

    @JvmStatic
    fun isInterstitialReady(placement: String): Boolean = runtime?.interstitial?.isReady(placement) ?: false

    /** Shows a preloaded interstitial. False = nothing started (config.fallbackToLoad can change that). */
    @JvmStatic
    fun showPreloadedInterstitial(activity: Activity, placement: String, listener: InterstitialListener?): Boolean =
        runtime?.interstitial?.showPreloaded(activity, placement, listener) ?: false

    /** Skip the next app-open ad (call before launching a purchase flow, permission dialog, etc.). */
    @JvmStatic
    fun pauseAppOpenOnce() {
        runtime?.pauseAppOpenOnce()
    }

    // ---- typed placements: pass your AdPlacement constants instead of strings (see AdPlacement) ----

    @JvmStatic
    fun showInterstitial(activity: Activity, placement: AdPlacement, listener: InterstitialListener?) =
        showInterstitial(activity, placement.name, listener)

    @JvmStatic
    fun preloadInterstitial(placement: AdPlacement) = preloadInterstitial(placement.name)

    @JvmStatic
    fun isInterstitialReady(placement: AdPlacement): Boolean = isInterstitialReady(placement.name)

    @JvmStatic
    fun showPreloadedInterstitial(activity: Activity, placement: AdPlacement, listener: InterstitialListener?): Boolean =
        showPreloadedInterstitial(activity, placement.name, listener)

    /**
     * Claims the single full-screen ad slot for something you show yourself (the ui module's full-screen native ad).
     * Null = not allowed now (premium, disabled, another ad showing). Call release() when your screen closes.
     */
    @JvmStatic
    fun openFullScreenSlot(activity: Activity, placement: AdPlacement): FullScreenSlot? =
        runtime?.slots?.open(activity, placement.name)

    /** Loads a banner of the placement's [BannerType] into [container]; the container is hidden if there is no ad. */
    @JvmStatic
    fun banner(container: FrameLayout, placement: String) = banner(container, placement, null)

    @JvmStatic
    fun banner(container: FrameLayout, placement: AdPlacement) = banner(container, placement.name, null)

    @JvmStatic
    fun banner(container: FrameLayout, placement: AdPlacement, listener: BannerListener?) =
        banner(container, placement.name, listener)

    /** Same, with an optional result callback (the ui module's BannerAdView uses it for its shimmer). */
    @JvmStatic
    fun banner(container: FrameLayout, placement: String, listener: BannerListener?) {
        val r = runtime
        if (r == null) {
            container.visibility = android.view.View.GONE
            listener?.onFailed("not initialized")
        } else {
            r.banner.attach(container, placement, listener)
        }
    }

    /**
     * Banner for the splash screen: always a LARGE_BANNER (320x100), loaded from the placement
     * [Placements.SPLASH_BANNER] (declare it with `.banner(AdPlacement.SPLASH_BANNER).units(...)`; flooring, units,
     * premium and Remote Config work like for any banner). Hidden when there is no ad.
     */
    @JvmStatic
    @JvmOverloads
    fun bannerForSplash(container: FrameLayout, listener: BannerListener? = null) {
        val r = runtime
        if (r == null) {
            container.visibility = android.view.View.GONE
            listener?.onFailed("not initialized")
        } else {
            r.banner.attach(container, Placements.SPLASH_BANNER, listener, BannerType.LARGE_BANNER)
        }
    }

    /** The configured [BannerType] of a banner placement (ADAPTIVE if unknown). The ui module sizes its shimmer with it. */
    @JvmStatic
    fun bannerTypeOf(placement: AdPlacement): BannerType =
        runtime?.config?.placement(placement.name)?.bannerType ?: BannerType.ADAPTIVE

    @JvmStatic
    fun loadNative(context: Context, placement: AdPlacement, callback: NativeLoadCallback): AdRequestHandle =
        loadNative(context, placement.name, callback)

    /** Low-level native load, used by the ui module's NativeAdTemplateView. */
    @JvmStatic
    fun loadNative(context: Context, placement: String, callback: NativeLoadCallback): AdRequestHandle {
        val r = runtime
        if (r == null) {
            callback.onFailed("not initialized")
            return AdRequestHandle { }
        }
        return r.native.load(context, placement, callback)
    }

    private object NoSplashAds : SplashAds {
        override fun hasAd() = false
        override fun show(activity: Activity, onDone: Runnable) = onDone.run()
    }
}

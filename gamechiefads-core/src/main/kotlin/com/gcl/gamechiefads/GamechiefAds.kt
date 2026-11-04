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

    /** Loads an adaptive banner into [container]; the container is hidden if there is no ad. */
    @JvmStatic
    fun banner(container: FrameLayout, placement: String) = banner(container, placement, null)

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

package com.gcl.gamechiefads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.View
import android.widget.FrameLayout

/**
 * The whole public surface, one static object you can call from every Activity.
 *
 * 1. In your Application: `GamechiefAds.init(this, AdsConfig.builder().xxxUnits(...)....build())` (all ad units go there).
 * 2. In your screens: `GamechiefAds.loadXxxAd(...)`. Every method is named `load<What>Ad`.
 *
 * Every call is safe before init() and never throws: no ad is simply "no ad" and your callback still runs.
 */
object GamechiefAds {
    @Volatile private var runtime: AdsRuntime? = null

    // =====================================================================================================
    // SETUP
    // =====================================================================================================

    /** Call once from Application.onCreate. All your ad units are in [config]. */
    @JvmStatic
    @Synchronized
    fun init(app: Application, config: AdsConfig) {
        if (runtime == null) runtime = AdsRuntime(app, config)
    }

    /** Premium users see no ads. Call it from your billing library's callback. */
    @JvmStatic
    fun setPremium(premium: Boolean) {
        runtime?.state?.setPremium(premium)
    }

    /** Optional: the Firebase Remote Config string (JSON). Changes units, gaps, on/off... without an app update. Bad JSON is ignored. */
    @JvmStatic
    fun applyRemoteConfig(json: String?) {
        runtime?.applyRemote(json)
    }

    /** Debug trace in logcat (tag "GamechiefAds"). On by default in debuggable apps, always off in release builds. */
    @JvmStatic
    fun setDebugLogging(enabled: Boolean) {
        runtime?.setLogging(enabled)
    }

    /**
     * The "Ad is loading, please wait" dialog shown while an ad loads at runtime. It is always shown (a default one is
     * built in); use this only to replace it with your own. Null = back to the default.
     */
    @JvmStatic
    fun setLoadingDialog(ui: AdLoaderUi?) {
        runtime?.loaderUi = ui
    }

    /** Changes the texts of the loading dialog at any time (e.g. after a language change). Null = default text. */
    @JvmStatic
    fun setLoadingText(title: String?, message: String?) {
        runtime?.setLoaderText(title, message)
    }

    /** Skip the next app-open ad (call it right before a purchase flow, permission dialog, camera...). */
    @JvmStatic
    fun skipNextAppOpenAd() {
        runtime?.pauseAppOpenOnce()
    }

    // =====================================================================================================
    // SPLASH. Loads the ad (consent -> init -> floors -> timeout). [callback] runs once, with or without an ad:
    // call ads.show(activity, onDone) and open your next screen in onDone (it runs exactly once).
    // =====================================================================================================

    /** Splash app-open ad (units from splashAppOpenUnits). */
    @JvmStatic
    fun loadSplashAppOpenAd(activity: Activity, callback: SplashCallback) =
        splash(activity, callback, Placements.SPLASH, false)

    /** Two splash app-open ads shown together (second ad from splashAppOpenDoubleUnits, or the same units). */
    @JvmStatic
    fun loadSplashAppOpenDoubleAd(activity: Activity, callback: SplashCallback) =
        splash(activity, callback, Placements.SPLASH, true)

    /** Splash interstitial ad (units from splashInterstitialUnits). */
    @JvmStatic
    fun loadSplashInterstitialAd(activity: Activity, callback: SplashCallback) =
        splash(activity, callback, Placements.SPLASH_INTERSTITIAL, false)

    /** Two splash interstitials shown together (second from splashInterstitialDoubleUnits, or the same units). */
    @JvmStatic
    fun loadSplashInterstitialDoubleAd(activity: Activity, callback: SplashCallback) =
        splash(activity, callback, Placements.SPLASH_INTERSTITIAL, true)

    private fun splash(activity: Activity, callback: SplashCallback, name: String, double: Boolean) {
        val r = runtime
        if (r == null) callback.onReady(NoSplashAds) else r.splash.prepare(activity, callback, name, double)
    }

    // =====================================================================================================
    // BANNERS. Give a FrameLayout, the library fills it. It hides itself when there is no ad.
    // =====================================================================================================

    /** Large banner (320x100) for the splash screen (units from splashBannerUnits). */
    @JvmStatic
    @JvmOverloads
    fun loadSplashBannerAd(container: FrameLayout, listener: BannerListener? = null) =
        banner(container, Placements.SPLASH_BANNER, listener)

    /** Banner for all normal screens (units from bannerUnits). */
    @JvmStatic
    @JvmOverloads
    fun loadBannerAd(container: FrameLayout, listener: BannerListener? = null) =
        banner(container, Placements.BANNER, listener)

    /** Banner for the exit screen (units from exitBannerUnits). */
    @JvmStatic
    @JvmOverloads
    fun loadExitBannerAd(container: FrameLayout, listener: BannerListener? = null) =
        banner(container, Placements.EXIT_BANNER, listener)

    /**
     * Full control: your own ad unit and [BannerType] (size and kind: ADAPTIVE, COLLAPSIBLE_BOTTOM, MEDIUM_RECTANGLE,
     * LARGE_BANNER...). One unit, no floors, nothing needed in the config.
     */
    @JvmStatic
    @JvmOverloads
    fun loadCustomBannerAd(container: FrameLayout, adUnitId: String, type: BannerType, listener: BannerListener? = null) {
        val r = runtime
        if (r == null) failBanner(container, listener) else r.banner.attachUnit(container, adUnitId, type, listener)
    }

    /** A banner of one of your own custom placements (see [AdsConfig.Builder.banner]). */
    @JvmStatic
    fun loadBannerAd(container: FrameLayout, placement: AdPlacement, listener: BannerListener?) =
        banner(container, placement.name, listener)

    private fun banner(container: FrameLayout, name: String, listener: BannerListener?) {
        val r = runtime
        if (r == null) failBanner(container, listener)
        else r.banner.attach(container, name, listener, if (name == Placements.SPLASH_BANNER) BannerType.LARGE_BANNER else null)
    }

    private fun failBanner(container: FrameLayout, listener: BannerListener?) {
        container.visibility = View.GONE
        listener?.onFailed("not initialized")
    }

    /** The configured [BannerType] of a banner slot or placement (ADAPTIVE if unknown). The ui module sizes its shimmer with it. */
    @JvmStatic
    fun bannerTypeOf(placement: String): BannerType =
        if (placement == Placements.SPLASH_BANNER) BannerType.LARGE_BANNER
        else runtime?.config?.placement(placement)?.bannerType ?: BannerType.ADAPTIVE

    // =====================================================================================================
    // RANDOM INTERSTITIAL. Call it on a click. The library decides if an ad is due (min gap, every N calls, not premium,
    // not while another ad is showing, not on excluded screens). When it is due the loading dialog is shown while the ad
    // loads (always), then the ad. [listener] may be null; onComplete runs once whether or not an ad was shown.
    // =====================================================================================================

    /** One interstitial (units from randomInterstitialUnits). */
    @JvmStatic
    fun loadRandomInterstitialAd(activity: Activity, listener: InterstitialListener?) {
        val r = runtime
        if (r == null) ListenerDispatcher(listener).onFinished(false, "not initialized")
        else r.interstitial.showRandom(activity, listener, false)
    }

    /** Two interstitials shown together (second from randomInterstitialDoubleUnits, or the same units). */
    @JvmStatic
    fun loadRandomInterstitialDoubleAd(activity: Activity, listener: InterstitialListener?) {
        val r = runtime
        if (r == null) ListenerDispatcher(listener).onFinished(false, "not initialized")
        else r.interstitial.showRandom(activity, listener, true)
    }

    // =====================================================================================================
    // REWARDED
    // =====================================================================================================

    /** Rewarded ad (units from rewardedUnits). Give the reward ONLY in [RewardListener.onRewardEarned]. */
    @JvmStatic
    fun loadRewardedAd(activity: Activity, listener: RewardListener?) =
        loadRewardedAd(activity, Placements.REWARDED, listener)

    /** A rewarded ad of one of your own custom placements. */
    @JvmStatic
    fun loadRewardedAd(activity: Activity, placement: AdPlacement, listener: RewardListener?) =
        loadRewardedAd(activity, placement.name, listener)

    private fun loadRewardedAd(activity: Activity, name: String, listener: RewardListener?) {
        val r = runtime
        if (r == null) {
            listener?.onNotAvailable("not initialized")
            listener?.onComplete(false)
        } else {
            r.rewarded.show(activity, name, listener)
        }
    }

    // =====================================================================================================
    // CUSTOM SPOTS (optional). For extra interstitials you name yourself, e.g. "after_save".
    // Declare them in the config with AdsConfig.Builder.interstitial(...).
    // =====================================================================================================

    /** Interstitial of a custom placement: loading dialog, floors, show. No pacing: it shows whenever you call it. */
    @JvmStatic
    fun loadInterstitialAd(activity: Activity, placement: AdPlacement, listener: InterstitialListener?) {
        val r = runtime
        if (r == null) ListenerDispatcher(listener).onFinished(false, "not initialized")
        else r.interstitial.showOnDemand(activity, placement.name, listener)
    }

    /** Opt-in: request one interstitial of a custom placement now, to show it later. */
    @JvmStatic
    fun preloadInterstitial(placement: AdPlacement) {
        runtime?.interstitial?.preload(placement.name)
    }

    @JvmStatic
    fun isInterstitialReady(placement: AdPlacement): Boolean = runtime?.interstitial?.isReady(placement.name) ?: false

    /** Shows the preloaded interstitial. False = nothing started (nothing was ready). */
    @JvmStatic
    fun showPreloadedInterstitial(activity: Activity, placement: AdPlacement, listener: InterstitialListener?): Boolean =
        runtime?.interstitial?.showPreloaded(activity, placement.name, listener) ?: false

    // =====================================================================================================
    // Used by the ui module (native ads, full-screen native ad)
    // =====================================================================================================

    /** Claims the single full-screen ad slot for something you show yourself. Null = not allowed now. Call release() when done. */
    @JvmStatic
    fun openFullScreenSlot(activity: Activity, placement: String): FullScreenSlot? =
        runtime?.slots?.open(activity, placement)

    /** Low-level native load, used by the ui module's native views. */
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

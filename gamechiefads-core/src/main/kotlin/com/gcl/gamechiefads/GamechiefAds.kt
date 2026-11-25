package com.gcl.gamechiefads

import android.app.Activity
import android.util.Log
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

    /**
     * The setup checker: the usual integration mistakes (missing AdMob app id in the manifest, the old play-services-ads still in the app,
     * Google test unit ids in a release build, missing units...), in plain words, each starting with [ERROR], [WARN] or [INFO]. Empty = all good.
     * Debug builds also print it in logcat at start. Call it yourself any time, for example from a QA screen.
     */
    @JvmStatic
    fun checkSetup(): List<String> = runtime?.checkSetup() ?: listOf("[ERROR] GamechiefAds.init(app, config) was not called yet (do it in Application.onCreate).")

    /**
     * DEBUG BUILDS ONLY: opens a bottom sheet with the live library log, the setup check, the current rules and buttons to test every ad.
     * Call it from any screen (a long press, a hidden button...). In a release build it does nothing and returns false.
     * Needs the gamechiefads-ui module (included in the one-line dependency).
     */
    @JvmStatic
    fun showDebugPanel(activity: Activity): Boolean {
        val r = runtime ?: return false
        if (!r.isDebug) { Log.w("GamechiefAds", "showDebugPanel ignored: release build"); return false }
        return runCatching {
            Class.forName("com.gcl.gamechiefads.ui.DebugSheet").getMethod("show", Activity::class.java).invoke(null, activity)
            true
        }.getOrElse { Log.w("GamechiefAds", "debug panel needs the gamechiefads-ui module: $it"); false }
    }

    /** DEBUG BUILDS ONLY: opens the full-screen QA screen (the same tools as the debug panel). Does nothing in a release build and returns false. */
    @JvmStatic
    fun openQaScreen(activity: Activity): Boolean {
        val r = runtime ?: return false
        if (!r.isDebug) { Log.w("GamechiefAds", "openQaScreen ignored: release build"); return false }
        return runCatching {
            activity.startActivity(android.content.Intent(activity, Class.forName("com.gcl.gamechiefads.ui.QaActivity")))
            true
        }.getOrElse { Log.w("GamechiefAds", "QA screen needs the gamechiefads-ui module: $it"); false }
    }

    /** The current rules as readable lines (units, switches, doubles, sizes), for QA screens. */
    @JvmStatic
    fun debugConfig(): List<String> = runtime?.describeConfig() ?: listOf("GamechiefAds.init(app, config) was not called yet")

    @JvmStatic
    fun isPremium(): Boolean = runtime?.state?.premium?.value ?: false

    /** Debug builds only: is this a debuggable app (the debug panel is available)? */
    @JvmStatic
    fun isDebugBuild(): Boolean = runtime?.isDebug ?: false

    /**
     * QA test mode: every ad request uses Google TEST unit ids, in any build (also release). Put it behind your debug or QA menu so testers can
     * click ads without risking your account. Off by default and not remembered: call it again after a restart.
     */
    @JvmStatic
    fun setTestMode(enabled: Boolean) {
        runtime?.testMode = enabled
        Log.w("GamechiefAds", if (enabled) "TEST MODE ON: all ads use Google test unit ids" else "test mode off")
    }

    @JvmStatic
    fun isTestMode(): Boolean = runtime?.testMode ?: false

    /** Opens Google's Ad Inspector (requests and responses of this device) for QA. [onClosed] may be null. Does nothing if the SDK is not ready. */
    @JvmStatic
    @JvmOverloads
    fun openAdInspector(onClosed: Runnable? = null) {
        val r = runtime
        if (r == null) onClosed?.run() else r.openAdInspector(onClosed)
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

    /**
     * Splash app-open ad (units from splashAppOpenUnits). A second ad is shown with it ("double") ONLY when the double switch is on:
     * Remote Config `{"placements":{"splash":{"double":true}}}` or `.doubleAd(true)` in the config. OFF by default.
     */
    @JvmStatic
    fun loadSplashAppOpenAd(activity: Activity, callback: SplashCallback) =
        splash(activity, callback, Placements.SPLASH, null)

    /** Same, asking for the double in code (true = also load and show the second ad: one request to splashAppOpenDoubleUnit, no floors). The Remote Config switch still turns the double on when this is false. */
    @JvmStatic
    fun loadSplashAppOpenAd(activity: Activity, double: Boolean, callback: SplashCallback) =
        splash(activity, callback, Placements.SPLASH, double)

    /** Splash interstitial ad (units from splashInterstitialUnits). Double: the same switch, on the `splash_interstitial` slot. OFF by default. */
    @JvmStatic
    fun loadSplashInterstitialAd(activity: Activity, callback: SplashCallback) =
        splash(activity, callback, Placements.SPLASH_INTERSTITIAL, null)

    /** Same, choosing single or double for this call (true = also the second ad: one request to splashInterstitialDoubleUnit, no floors). */
    @JvmStatic
    fun loadSplashInterstitialAd(activity: Activity, double: Boolean, callback: SplashCallback) =
        splash(activity, callback, Placements.SPLASH_INTERSTITIAL, double)

    @Deprecated("Use loadSplashAppOpenAd(activity, true, callback), or turn the double switch on in the config or Remote Config.", ReplaceWith("loadSplashAppOpenAd(activity, true, callback)"))
    @JvmStatic
    fun loadSplashAppOpenDoubleAd(activity: Activity, callback: SplashCallback) =
        splash(activity, callback, Placements.SPLASH, true)

    @Deprecated("Use loadSplashInterstitialAd(activity, true, callback), or turn the double switch on in the config or Remote Config.", ReplaceWith("loadSplashInterstitialAd(activity, true, callback)"))
    @JvmStatic
    fun loadSplashInterstitialDoubleAd(activity: Activity, callback: SplashCallback) =
        splash(activity, callback, Placements.SPLASH_INTERSTITIAL, true)

    /** [double]: what the code asked for; the slot switch (Remote Config) can turn the double on whatever the code says. */
    private fun splash(activity: Activity, callback: SplashCallback, name: String, double: Boolean?) {
        val r = runtime
        if (r == null) callback.onReady(NoSplashAds)
        else r.splash.prepare(activity, callback, name, DoubleSwitch.resolve(r.config.placement(name), double))
    }

    // =====================================================================================================
    // BANNERS. Give a FrameLayout, the library fills it. It hides itself when there is no ad.
    // [isEnableFlooring] = true (default): walk the floors (HF, MF, NORMAL). false: ONE request, to the LAST unit, so the
    // banner shows as fast as possible (use it on screens the user leaves quickly, e.g. one button click away).
    // =====================================================================================================

    /** Large banner (320x100) for the splash screen. One unit (splashBannerUnit), never floors. */
    @JvmStatic
    @JvmOverloads
    fun loadSplashBannerAd(container: FrameLayout, listener: BannerListener? = null) =
        banner(container, Placements.SPLASH_BANNER, listener, false)

    /** Banner for all normal screens (units from bannerUnits), with floors. */
    @JvmStatic
    @JvmOverloads
    fun loadBannerAd(container: FrameLayout, listener: BannerListener? = null) =
        banner(container, Placements.BANNER, listener, true)

    /** Same, choosing floors on or off for this call. */
    @JvmStatic
    @JvmOverloads
    fun loadBannerAd(container: FrameLayout, isEnableFlooring: Boolean, listener: BannerListener? = null) =
        banner(container, Placements.BANNER, listener, isEnableFlooring)

    /**
     * Same, with the banner type chosen for this call only (for example [BannerType.COLLAPSIBLE_BOTTOM] on one screen).
     * The default type of all banners is set in the config: `.defaultBannerType(...)`.
     */
    @JvmStatic
    @JvmOverloads
    fun loadBannerAd(container: FrameLayout, type: BannerType, isEnableFlooring: Boolean = true, listener: BannerListener? = null) =
        banner(container, Placements.BANNER, listener, isEnableFlooring, type)

    /** Banner for the exit screen (units from exitBannerUnits), with floors. */
    @JvmStatic
    @JvmOverloads
    fun loadExitBannerAd(container: FrameLayout, listener: BannerListener? = null) =
        banner(container, Placements.EXIT_BANNER, listener, true)

    /** Same, choosing floors on or off for this call. */
    @JvmStatic
    @JvmOverloads
    fun loadExitBannerAd(container: FrameLayout, isEnableFlooring: Boolean, listener: BannerListener? = null) =
        banner(container, Placements.EXIT_BANNER, listener, isEnableFlooring)

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
        banner(container, placement.name, listener, true)

    private fun banner(container: FrameLayout, name: String, listener: BannerListener?, flooring: Boolean, type: BannerType? = null) {
        val r = runtime
        if (r == null) failBanner(container, listener)
        else r.banner.attach(container, name, listener, if (name == Placements.SPLASH_BANNER) BannerType.LARGE_BANNER else type, flooring)
    }

    private fun failBanner(container: FrameLayout, listener: BannerListener?) {
        container.visibility = View.GONE
        listener?.onFailed("not initialized")
    }

    /** Native size name set remotely for a native placement (`"nativeSize"` in the rules), or null. The ui module applies it. */
    @JvmStatic
    fun nativeSizeOf(placement: String): String? = runtime?.config?.placement(placement)?.nativeSize

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

    /**
     * The random interstitial (units from randomInterstitialUnits). A second ad is shown with it ("double") ONLY when the double switch is on:
     * Remote Config `{"placements":{"random":{"double":true}}}` or `.doubleAd(true)` in the config. OFF by default.
     */
    @JvmStatic
    fun loadRandomInterstitialAd(activity: Activity, listener: InterstitialListener?) = randomInterstitial(activity, listener, null)

    /** Same, choosing single or double for this call (true = also the second ad: one request to randomInterstitialDoubleUnit, no floors). */
    @JvmStatic
    fun loadRandomInterstitialAd(activity: Activity, double: Boolean, listener: InterstitialListener?) = randomInterstitial(activity, listener, double)

    @Deprecated("Use loadRandomInterstitialAd(activity, true, listener), or turn the double switch on in the config or Remote Config.", ReplaceWith("loadRandomInterstitialAd(activity, true, listener)"))
    @JvmStatic
    fun loadRandomInterstitialDoubleAd(activity: Activity, listener: InterstitialListener?) = randomInterstitial(activity, listener, true)

    private fun randomInterstitial(activity: Activity, listener: InterstitialListener?, double: Boolean?) {
        val r = runtime
        if (r == null) ListenerDispatcher(listener).onFinished(false, "not initialized")
        else r.interstitial.showRandom(activity, listener, DoubleSwitch.resolve(r.config.placement(Placements.RANDOM), double))
    }

    /**
     * Switch the AUTOMATIC random interstitial (the one shown over activity transitions) on or off at any time, e.g. off while
     * the user is in a purchase flow or a video and on again afterwards. Screens opened while it is off are not counted. Your
     * button calls (loadRandomInterstitialAd) are not affected. On by default.
     */
    @JvmStatic
    fun setAutoRandomInterstitialEnabled(enabled: Boolean) {
        runtime?.state?.setAutoRandom(enabled)
    }

    @JvmStatic
    fun isAutoRandomInterstitialEnabled(): Boolean = runtime?.state?.autoRandom?.value ?: false

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
    // REMOTE CONFIG (built in: Firebase Remote Config when the remoteconfig module is in the app, which the one-line
    // dependency includes). The ads rules come from one key (default "gamechief_ads_config") by themselves. These
    // methods are for YOUR OWN values: add them in the config with remoteDefault(...), read them anywhere.
    // =====================================================================================================

    /** A remote text value. [fallback] is returned if the key has no value anywhere (also before init). */
    @JvmStatic
    fun getRemoteString(key: String, fallback: String): String = runtime?.remote?.string(key, fallback) ?: fallback

    @JvmStatic
    fun getRemoteBoolean(key: String, fallback: Boolean): Boolean = runtime?.remote?.boolean(key, fallback) ?: fallback

    @JvmStatic
    fun getRemoteLong(key: String, fallback: Long): Long = runtime?.remote?.long(key, fallback) ?: fallback

    @JvmStatic
    fun getRemoteDouble(key: String, fallback: Double): Double = runtime?.remote?.double(key, fallback) ?: fallback

    /** Adds your own keys and their defaults after init (String, Boolean, Int, Long or Double values). Same as remoteDefault(...) in the config. */
    @JvmStatic
    fun addRemoteDefaults(values: Map<String, Any>) {
        runtime?.remote?.addDefaults(values)
    }

    /** Fetches fresh values now (the app also fetches by itself at start, at most every remoteFetchIntervalSeconds). [listener] may be null. */
    @JvmStatic
    fun fetchRemoteConfig(listener: RemoteFetchListener?) {
        val r = runtime
        if (r == null) listener?.onResult(false)
        else r.remote.fetch { ok ->
            if (ok) r.applyRemoteRules()
            listener?.onResult(ok)
        }
    }

    /** Called after every successful fetch or real-time update: re-read your values here. Null to remove. */
    @JvmStatic
    fun setRemoteConfigListener(listener: Runnable?) {
        runtime?.remote?.setListener(listener)
    }

    /** Like [setRemoteConfigListener] but for many callers (a screen adds it when it opens and removes it when it closes). */
    @JvmStatic
    fun addRemoteChangeListener(listener: Runnable) {
        runtime?.remote?.addListener(listener)
    }

    @JvmStatic
    fun removeRemoteChangeListener(listener: Runnable) {
        runtime?.remote?.removeListener(listener)
    }

    /** True if a Remote Config source (Firebase) is running. False = getters only return your defaults and fallbacks. */
    @JvmStatic
    fun isRemoteConfigAvailable(): Boolean = runtime?.remote?.available ?: false

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

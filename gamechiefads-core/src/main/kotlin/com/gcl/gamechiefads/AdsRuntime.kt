package com.gcl.gamechiefads

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ProcessLifecycleOwner
import com.gcl.gamechiefads.sdk.AppOpenSource
import com.gcl.gamechiefads.sdk.BannerSource
import com.gcl.gamechiefads.sdk.InterstitialSource
import com.gcl.gamechiefads.sdk.NativeSource
import com.gcl.gamechiefads.sdk.RewardedSource
import com.gcl.gamechiefads.sdk.SdkInit
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Builds and wires every object once. Only wiring here, no logic. Must be created on the main thread. */
internal class AdsRuntime(private val app: Application, private val baseConfig: AdsConfig) {
    @Volatile private var base = baseConfig
    @Volatile var config = baseConfig
        private set
    @Volatile var loaderUi: AdLoaderUi? = null

    /**
     * The loading dialog is compulsory for every ad that loads at runtime. When the app set none, the ui module's
     * default dialog is used (found by name, so the core module needs no UI code). Null only if the ui module is missing.
     */
    /** The ui module's shimmer for banners, found by name like the loading dialog. */
    private val shimmer: BannerPlaceholder? by lazy {
        runCatching {
            Class.forName("com.gcl.gamechiefads.ui.ShimmerBannerPlaceholder").getConstructor().newInstance() as BannerPlaceholder
        }.getOrNull()
    }

    private val defaultLoader: AdLoaderUi? by lazy {
        runCatching {
            Class.forName("com.gcl.gamechiefads.ui.DefaultLoaderDialog")
                .getConstructor(CharSequence::class.java).newInstance(null as CharSequence?) as AdLoaderUi
        }.onFailure { AdLog.d("init") { "no default loading dialog (add the gamechiefads-ui module): $it" } }.getOrNull()
    }

    /** Remote values for the app (your own keys) and the remote ads rules. Firebase comes from the remoteconfig module, found by name. */
    val remote = RemoteValues(
        baseConfig.remoteSource ?: runCatching {
            Class.forName("com.gcl.gamechiefads.remoteconfig.FirebaseRemoteConfigSource").getConstructor().newInstance() as RemoteConfigSource
        }.onFailure { AdLog.d("remote") { "no Firebase Remote Config (add the remoteconfig module and the google-services setup): $it" } }.getOrNull(),
    )

    val state = AdState()
    /** QA test mode: Google test unit ids for every ad request, whatever the build. Off by default. */
    @Volatile var testMode = false
    private val debuggable = AdUnits.isDebuggable(app)
    init {
        AdLog.enabled = debuggable
        AdLog.d("init") { "GamechiefAds ready: placements=${baseConfig.placements.keys}, appId=${baseConfig.appId ?: "(test)"}, test ids=$debuggable" }
    }
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
    /** After a pass with no ad the same placement waits a few seconds: taps or foregrounds cannot make a request loop. */
    private val noFill = NoFillCooldown(clock) { config.noFillRetrySeconds }
    /** "No ad available, please try again in a few seconds", shown inside the loading dialog (a toast only if the dialog cannot). */
    private val noAd = NoAdNotice({ config }, { loaderUi ?: defaultLoader }) { msg -> Toast.makeText(app, msg, Toast.LENGTH_SHORT).show() }
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate +
            CoroutineExceptionHandler { _, t -> Log.w(TAG, "ad flow error (ignored)", t) },
    )

    private val tracker = ScreenTracker(state)
    private val consent = Consent()
    private val sdkInit = SdkInit(app)
    private val gate = AdsGate(
        consent = { a: Activity -> consent.gather(a, config.appId) },
        init = { sdkInit.ensure(config.appId ?: AdUnits.TEST_APP_ID, config.initTimeoutMs) },
        clock = clock,
    )
    private val pipeline = AdLoadPipeline({ config }, gate::ready, AdUnits.isDebuggable(app), FloorPolicy(clock)) { testMode }
    private val shower = FullScreenShower(state, clock, scope)

    val rewarded = RewardedController(state, { config }, pipeline, shower, tracker, scope, clock, { loaderUi ?: defaultLoader }, RewardedSource::load, noFill, noAd)

    val interstitial = InterstitialController(
        state, { config }, AdPool(clock = clock), pipeline, shower, tracker, scope, clock,
        { loaderUi ?: defaultLoader }, InterstitialSource::load, noFill, noAd,
    )
    private val appOpen = AppOpenController(
        state, { config }, AdPool(ttlMs = APP_OPEN_TTL_MS, clock = clock), pipeline, shower, tracker, scope, clock,
        AppOpenSource::load, noFill,
    )
    private val random = RandomInterstitialTrigger(state, { config }, tracker, interstitial, scope, clock)
    val splash = SplashFlow(state, { config }, pipeline, shower, tracker, scope, AppOpenSource::load, InterstitialSource::load, clock)
    val banner = BannerController(state, { config }, pipeline, tracker, scope, BannerSource::load, { shimmer })
    /** The ui module's native loader, found by name like the loading dialog (null without the ui module). */
    private val nativeLoader: ((android.widget.FrameLayout, String, String?) -> Unit)? by lazy {
        runCatching {
            val m = Class.forName("com.gcl.gamechiefads.ui.AdsUi")
                .getMethod("loadNativeAd", android.widget.FrameLayout::class.java, String::class.java, String::class.java)
            val f: (android.widget.FrameLayout, String, String?) -> Unit = { c, p, s -> m.invoke(null, c, p, s) }
            f
        }.getOrNull()
    }
    private val tagBanners = TagBanners(banner, { config }) { c, p, s -> nativeLoader?.invoke(c, p, s) }
    val slots = FullScreenSlots(state, { config }, tracker, clock)
    val native = NativeController(state, { config }, pipeline, tracker, scope, NativeSource::load)

    init {
        tracker.onScreen = { activity ->
            appOpen.onScreenResumed(activity)
            random.onScreenResumed(activity)
        }
        // tagged banner containers: every screen instance once, also screens restored after a restart (not only "new" ones)
        tracker.onResumed = { activity -> tagBanners.onScreen(activity) }
        app.registerActivityLifecycleCallbacks(tracker)
        ProcessLifecycleOwner.get().lifecycle.addObserver(ProcessForegroundObserver(appOpen::onForeground))
        if (debuggable) startShake(app)
        scope.launch { state.premium.drop(1).filter { it }.collect { banner.hideAll() } }

        // remote values: defaults, last known values right away, then a fetch; the ads rules (one JSON key) are re-applied each time
        startSharedFile(app)
        remote.addDefaults(baseConfig.remoteDefaults)
        applyRemoteRules()
        remote.start(baseConfig.remoteFetchIntervalSeconds) { applyRemoteRules() }
        if (debuggable) runCatching { logSetup() }
    }

    // ---- remote rules in layers, lowest first: your config < shared file (https) < Firebase Remote Config < applyRemoteConfig(json)
    @Volatile private var urlJson: String? = null
    @Volatile private var firebaseJson: String? = null
    @Volatile private var manualJson: String? = null

    /** Firebase Remote Config: the rules key changed or was fetched. */
    fun applyRemoteRules() {
        firebaseJson = base.remoteConfigKey?.let { key -> remote.string(key, "").takeIf { it.isNotBlank() } }
        recompute()
    }

    /** Your own call: GamechiefAds.applyRemoteConfig(json). Null clears this layer only. */
    fun applyRemote(json: String?) {
        manualJson = json?.takeIf { it.isNotBlank() }
        recompute()
    }

    private fun recompute() {
        var c = base
        for (layer in listOf(urlJson, firebaseJson)) c = RemoteOverrides.apply(c, layer)
        c = FlatRemote.apply(c) { k -> remote.string(k, "").takeIf { it.isNotBlank() } }   // the simple one-value Firebase keys
        c = RemoteOverrides.apply(c, manualJson)
        config = c
        AdLog.d("remote") { "rules applied (shared file=${urlJson != null}, firebase=${firebaseJson != null}, manual=${manualJson != null})" }
    }

    /** The shared rules file: last good copy at once, then a fetch in the background (never on the main thread). */
    private fun startSharedFile(app: Application) {
        val url = baseConfig.remoteConfigUrl ?: return
        val file = RemoteUrlConfig(
            url, baseConfig.remoteConfigUrlIntervalSeconds, { System.currentTimeMillis() },
            RemoteUrlAndroid.store(app), RemoteUrlAndroid.http,
        )
        urlJson = file.cached()
        if (urlJson != null) recompute()
        scope.launch {
            val fresh = withContext(Dispatchers.IO) { runCatching { file.refresh() }.getOrNull() }
            if (fresh != null) {
                urlJson = fresh
                recompute()
            }
        }
    }

    /** Logging can only be on for debuggable apps. */
    fun setLogging(on: Boolean) { AdLog.enabled = on && debuggable }
    /** Changes the default loader texts now; survives later Remote Config overrides unless they set the same keys. */
    fun setLoaderText(title: String?, message: String?) {
        base = base.copy(loaderTitle = title, loaderMessage = message)
        recompute()
    }

    /** The current rules as readable lines, for the debug panel. */
    fun describeConfig(): List<String> {
        val c = config
        val out = ArrayList<String>()
        out += "ads enabled=${c.enabled}  premium=${state.premium.value}  test mode=$testMode  debuggable=$debuggable"
        out += "app id=${c.appId ?: "(Google test app id)"}  rules: shared file=${urlJson != null}, firebase=${firebaseJson != null}, manual=${manualJson != null}"
        out += "quiet after app open=${c.quietSecondsAfterAppOpen}s  no-fill wait=${c.noFillRetrySeconds}s  tag banners=${c.tagBanners}"
        for ((name, p) in c.placements) {
            val extras = buildString {
                if (p.secondUnit != null) append("  second=${p.secondUnit}")
                append("  double=${p.wantsDouble()}")
                if (p.doubleRemote != null) append("(firebase=${p.doubleRemote})")
                if (p.nativeSize != null) append("  nativeSize=${p.nativeSize}")
                if (p.format == AdFormat.BANNER) append("  type=${p.bannerType}")
                if (!p.flooring) append("  no floors")
            }
            out += "$name: ${if (p.enabled) "ON " else "OFF"} ${p.units}$extras"
        }
        return out
    }

    val isDebug: Boolean get() = debuggable

    /** The setup checker (see [SetupChecker]): the usual integration mistakes, in plain words. */
    fun checkSetup(): List<String> {
        fun has(cls: String) = runCatching { Class.forName(cls) }.isSuccess
        val meta = runCatching {
            app.packageManager.getApplicationInfo(app.packageName, android.content.pm.PackageManager.GET_META_DATA).metaData?.containsKey("com.google.android.gms.ads.APPLICATION_ID") == true
        }.getOrDefault(true)
        return SetupChecker.check(
            config,
            SetupChecker.Env(
                debuggable = debuggable, hasAdMobAppIdInManifest = meta,
                legacyPlayServicesAdsPresent = legacyAdsInManifest(),
                uiModulePresent = has("com.gcl.gamechiefads.ui.DefaultLoaderDialog"),
                firebaseModulePresent = has("com.gcl.gamechiefads.remoteconfig.FirebaseRemoteConfigSource"),
            ),
        )
    }

    /**
     * The OLD play-services-ads declares com.google.android.gms.ads.AdActivity in the merged manifest. (Checking for a class is wrong: the Next-Gen
     * SDK ships its own class named com.google.android.gms.ads.MobileAds.)
     */
    private fun legacyAdsInManifest(): Boolean = runCatching {
        app.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.GET_ACTIVITIES)
            .activities?.any { it.name == "com.google.android.gms.ads.AdActivity" } == true
    }.getOrDefault(false)

    /** DEBUG BUILDS ONLY: shake the phone while the app is in front and the debug panel opens. No sensor is ever registered in a release build. */
    private fun startShake(app: Application) {
        val shake = ShakeWatcher(app, { config.debugShake && remote.boolean(ShakeWatcher.KEY, true) }) {
            tracker.current?.let { GamechiefAds.showDebugPanel(it) }
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onStart(owner: androidx.lifecycle.LifecycleOwner) = shake.start()
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) = shake.stop()
        })
    }

    /** Debug builds: say it in logcat (warnings) once at start. */
    private fun logSetup() {
        val issues = checkSetup()
        if (issues.isEmpty()) Log.i(TAG, "setup check: nothing to report")
        else issues.forEach { if (it.startsWith("[INFO]")) Log.i(TAG, "setup: $it") else Log.w(TAG, "setup: $it") }
    }

    fun openAdInspector(onClosed: Runnable?) = com.gcl.gamechiefads.sdk.AdInspector.open { onClosed?.run() }

    fun pauseAppOpenOnce() = appOpen.pauseOnce()

    private companion object {
        const val TAG = "GamechiefAds"
        const val APP_OPEN_TTL_MS = 4 * 60 * 60 * 1000L - 5 * 60 * 1000L
    }
}

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
internal class AdsRuntime(app: Application, private val baseConfig: AdsConfig) {
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
    private val pipeline = AdLoadPipeline({ config }, gate::ready, AdUnits.isDebuggable(app), FloorPolicy(clock))
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
    val slots = FullScreenSlots(state, { config }, tracker, clock)
    val native = NativeController(state, { config }, pipeline, tracker, scope, NativeSource::load)

    init {
        tracker.onScreen = { activity ->
            appOpen.onScreenResumed(activity)
            random.onScreenResumed(activity)
        }
        app.registerActivityLifecycleCallbacks(tracker)
        ProcessLifecycleOwner.get().lifecycle.addObserver(ProcessForegroundObserver(appOpen::onForeground))
        scope.launch { state.premium.drop(1).filter { it }.collect { banner.hideAll() } }

        // remote values: defaults, last known values right away, then a fetch; the ads rules (one JSON key) are re-applied each time
        startSharedFile(app)
        remote.addDefaults(baseConfig.remoteDefaults)
        applyRemoteRules()
        remote.start(baseConfig.remoteFetchIntervalSeconds) { applyRemoteRules() }
    }

    // ---- remote rules in layers, lowest first: your config < shared file (https) < Firebase Remote Config < applyRemoteConfig(json)
    @Volatile private var urlJson: String? = null
    @Volatile private var firebaseJson: String? = null
    @Volatile private var manualJson: String? = null

    /** Firebase Remote Config: the rules key changed or was fetched. */
    fun applyRemoteRules() {
        val key = base.remoteConfigKey ?: return
        firebaseJson = remote.string(key, "").takeIf { it.isNotBlank() }
        recompute()
    }

    /** Your own call: GamechiefAds.applyRemoteConfig(json). Null clears this layer only. */
    fun applyRemote(json: String?) {
        manualJson = json?.takeIf { it.isNotBlank() }
        recompute()
    }

    private fun recompute() {
        var c = base
        for (layer in listOf(urlJson, firebaseJson, manualJson)) c = RemoteOverrides.apply(c, layer)
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

    fun pauseAppOpenOnce() = appOpen.pauseOnce()

    private companion object {
        const val TAG = "GamechiefAds"
        const val APP_OPEN_TTL_MS = 4 * 60 * 60 * 1000L - 5 * 60 * 1000L
    }
}

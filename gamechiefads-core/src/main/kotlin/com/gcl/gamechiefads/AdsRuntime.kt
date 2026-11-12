package com.gcl.gamechiefads

import android.app.Activity
import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import com.gcl.gamechiefads.sdk.AppOpenSource
import com.gcl.gamechiefads.sdk.BannerSource
import com.gcl.gamechiefads.sdk.InterstitialSource
import com.gcl.gamechiefads.sdk.NativeSource
import com.gcl.gamechiefads.sdk.SdkInit
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/** Builds and wires every object once. Only wiring here, no logic. Must be created on the main thread. */
internal class AdsRuntime(app: Application, baseConfig: AdsConfig) {
    @Volatile private var base = baseConfig
    @Volatile var config = baseConfig
        private set
    @Volatile var loaderUi: AdLoaderUi? = null

    val state = AdState()
    private val debuggable = AdUnits.isDebuggable(app)
    init {
        AdLog.enabled = debuggable
        AdLog.d("init") { "GamechiefAds ready: placements=${baseConfig.placements.keys}, appId=${baseConfig.appId ?: "(test)"}, test ids=$debuggable" }
    }
    private val clock: () -> Long = { SystemClock.elapsedRealtime() }
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

    val interstitial = InterstitialController(
        state, { config }, AdPool(clock = clock), pipeline, shower, tracker, scope, clock,
        { loaderUi }, InterstitialSource::load,
    )
    private val appOpen = AppOpenController(
        state, { config }, AdPool(ttlMs = APP_OPEN_TTL_MS, clock = clock), pipeline, shower, tracker, scope, clock,
        AppOpenSource::load,
    )
    private val random = RandomInterstitialTrigger(state, { config }, tracker, interstitial, scope, clock)
    val splash = SplashFlow(state, { config }, pipeline, shower, tracker, scope, AppOpenSource::load, InterstitialSource::load)
    val banner = BannerController(state, { config }, pipeline, tracker, scope, BannerSource::load)
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
    }

    fun applyRemote(json: String?) {
        config = RemoteOverrides.apply(base, json)
        AdLog.d("remote") { "overrides applied (${json?.length ?: 0} chars)" }
    }

    /** Logging can only be on for debuggable apps. */
    fun setLogging(on: Boolean) { AdLog.enabled = on && debuggable }
    fun pauseAppOpenOnce() = appOpen.pauseOnce()

    private companion object {
        const val TAG = "GamechiefAds"
        const val APP_OPEN_TTL_MS = 4 * 60 * 60 * 1000L - 5 * 60 * 1000L
    }
}

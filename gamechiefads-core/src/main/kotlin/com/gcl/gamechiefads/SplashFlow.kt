package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Splash: consent -> init -> waterfall -> timeout. The callback runs exactly once, inside
 * config.splashTimeoutMs, with or without an ad (premium, consent denied, offline, no fill all
 * give "no ad"). If the optional `splash_second` placement is enabled (Remote Config can switch it)
 * both ads are requested together; the second is waited for at most config.doubleSecondWaitMs after
 * the first arrived, and both are shown back to back as one break.
 */
internal class SplashFlow(
    private val state: AdState,
    private val config: () -> AdsConfig,
    private val pipeline: AdLoadPipeline,
    private val shower: FullScreenShower,
    private val screens: ScreenHost,
    private val scope: CoroutineScope,
    /** Loads an app-open ad (the splash default). */
    private val source: suspend (String) -> FullScreenAd?,
    /** Loads an interstitial, used for the splash_interstitial slot. */
    private val interstitialSource: suspend (String) -> FullScreenAd? = source,
) {
    private suspend fun loadOne(p: PlacementConfig, activity: Activity): FullScreenAd? {
        val src = if (p.format == AdFormat.INTERSTITIAL) interstitialSource else source
        return pipeline.load(p, activity, src) { it.destroy() }
    }

    /** [name] = which splash slot (splash or splash_interstitial). [double] = also load and show the second ad (its own units). */
    fun prepare(activity: Activity, callback: SplashCallback, name: String = Placements.SPLASH, double: Boolean = false) {
        val ads = SplashAdsImpl(state, shower, screens)
        scope.launch {
            AdLog.d("splash") { "prepare start $name double=$double (timeout ${config().splashTimeoutMs}ms, premium=${state.premium.value})" }
            try {
                val cfg = config()
                val firstP = cfg.placement(name)?.takeIf { it.enabled && cfg.enabled }
                if (firstP != null && !state.premium.value) {
                    val secondP = if (double) firstP.copy(units = firstP.secondUnits.ifEmpty { firstP.units }) else null
                    withTimeoutOrNull(cfg.splashTimeoutMs) {
                        coroutineScope {
                            val d1 = async { loadOne(firstP, activity) }
                            // second ad (double): its own units with its own floors, requested at the same time
                            val d2 = secondP?.let { sp -> async { loadOne(sp, activity) } }
                            val a = d1.await()
                            if (a != null) {
                                ads.first = a
                                if (d2 != null) {
                                    val b = withTimeoutOrNull(cfg.doubleSecondWaitMs) { d2.await() }
                                    if (b != null) ads.second = b
                                    else if (d2.isCompleted && !d2.isCancelled) ads.second = d2.getCompleted()
                                    else d2.cancel()
                                }
                            } else if (d2 != null) {
                                ads.first = d2.await() // nothing from the splash units: use the second ad alone
                            }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) { AdLog.d("splash") { "error, failing open: $t" } }
            if (state.premium.value) ads.dispose()
            AdLog.d("splash") { "onReady: first=${ads.first != null}, second=${ads.second != null}" }
            callback.onReady(ads)
        }
    }
}

package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Splash: consent -> init -> waterfall -> timeout. The callback runs exactly once, inside
 * config.splashTimeoutMs, with or without an ad (premium, consent denied, offline, no fill all
 * give "no ad"). The optional second ad loads in the background and never delays the first.
 */
internal class SplashFlow(
    private val state: AdState,
    private val config: () -> AdsConfig,
    private val pipeline: AdLoadPipeline,
    private val shower: FullScreenShower,
    private val screens: ScreenHost,
    private val scope: CoroutineScope,
    private val source: suspend (String) -> FullScreenAd?,
) {
    fun prepare(activity: Activity, callback: SplashCallback) {
        val ads = SplashAdsImpl(state, shower, screens, scope)
        scope.launch {
            try {
                val cfg = config()
                val first = cfg.placement(Placements.SPLASH)?.takeIf { it.enabled }
                if (first != null && !state.premium.value) {
                    val second = cfg.placement(Placements.SPLASH_SECOND)?.takeIf { it.enabled }
                    withTimeoutOrNull(cfg.splashTimeoutMs) {
                        if (second != null) {
                            ads.secondJob = scope.launch {
                                try {
                                    ads.second = pipeline.load(second, activity, source)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (t: Throwable) { /* second ad is optional */ }
                            }
                        }
                        ads.first = pipeline.load(first, activity, source)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) { /* fail open: no ad */ }
            if (state.premium.value) ads.dispose()
            callback.onReady(ads)
        }
    }
}

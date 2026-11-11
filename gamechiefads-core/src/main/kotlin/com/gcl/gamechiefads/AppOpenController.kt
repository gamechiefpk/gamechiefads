package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * App-open ad when the app returns to the foreground (called by [ProcessForegroundObserver]).
 * One cached ad (pool, long life). Shows only if the policy allows: premium, ad showing, cooldown
 * (the placement's minGapSeconds) and skip list (its excluded screens). Fetches for next time on the
 * first normal screen, when empty, and after each show. [pauseOnce] skips the next foreground
 * (purchase flow, permission dialog...).
 */
internal class AppOpenController(
    private val state: AdState,
    private val config: () -> AdsConfig,
    private val pool: AdPool,
    private val pipeline: AdLoadPipeline,
    private val shower: FullScreenShower,
    private val screens: ScreenHost,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val source: suspend (String) -> FullScreenAd?,
) {
    @Volatile private var pausedOnce = false
    private var prefetchStarted = false

    fun pauseOnce() {
        pausedOnce = true
        AdLog.d("appopen") { "pauseOnce set" }
    }

    /** First screen that is not in the skip list (i.e. not the splash): prefetch once, no earlier. */
    fun onScreenResumed(activity: Activity) {
        if (prefetchStarted) return
        val p = enabledPlacement() ?: return
        if (activity.javaClass.name in p.excludedScreens) return
        prefetchStarted = true
        AdLog.d("appopen") { "first normal screen ${activity.javaClass.simpleName}: prefetch" }
        fetch()
    }

    fun onForeground() {
        val p = enabledPlacement() ?: return
        if (pausedOnce) {
            pausedOnce = false
            AdLog.d("appopen") { "foreground: skipped (pauseAppOpenOnce)" }
            return
        }
        val activity = screens.current ?: run { AdLog.d("appopen") { "foreground: no current screen" }; return }
        val decision = AdPolicy.evaluate(p, state.context(clock(), activity.javaClass.name))
        AdLog.d("appopen") { "foreground on ${activity.javaClass.simpleName}: $decision, ready=${pool.isReady(Placements.APP_OPEN)}" }
        if (decision == Decision.DENY_EXCLUDED_SCREEN) return
        if (decision == Decision.ALLOW && screens.isUsable(activity)) {
            val ad = pool.take(Placements.APP_OPEN)
            if (ad != null) {
                val second = if (p.doubleAd) pool.take(Placements.APP_OPEN_SECOND) else null
                AdLog.d("appopen") { "showing ${if (second != null) "2 ads (double)" else "1 ad"}" }
                shower.showAll(activity, listOfNotNull(ad, second), object : FullScreenShower.Events {
                    override fun onShown() {}
                    override fun onFinished(shown: Boolean, reason: String?) = fetch()
                })
                return
            }
        }
        fetch()
    }

    private fun enabledPlacement(): PlacementConfig? =
        config().placement(Placements.APP_OPEN)?.takeIf { it.enabled && !state.premium.value }

    private fun fetch() {
        val p = enabledPlacement() ?: return
        if (p.doubleAd) {
            scope.launch {
                try {
                    pool.fill(Placements.APP_OPEN_SECOND) { pipeline.load(p, screens.current, source) { it.destroy() } }
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) { /* fail open */ }
            }
        }
        scope.launch {
            try {
                val ready = pool.fill(Placements.APP_OPEN) { pipeline.load(p, screens.current, source) { it.destroy() } }
                AdLog.d("appopen") { "fetch finished, ready=$ready" }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) { /* fail open */ }
        }
    }
}

package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The "random" interstitial: reacts to a resumed screen, applies the full [AdPolicy]
 * (every N screens, min gap, excluded screens, premium, ad showing) and starts the interstitial.
 * A new screen cancels a trigger that is still settling. No call is needed in any Activity.
 */
internal class RandomInterstitialTrigger(
    private val state: AdState,
    private val config: () -> AdsConfig,
    private val screens: ScreenHost,
    private val interstitial: InterstitialController,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val settleMs: Long = SETTLE_MS,
) {
    private var pending: Job? = null

    fun onScreenResumed(activity: Activity) {
        pending?.cancel()
        pending = scope.launch {
            delay(settleMs) // let the screen finish its transition before covering it
            val p = config().placement(Placements.RANDOM)?.takeIf { it.everyNScreens > 0 && config().enabled } ?: return@launch // automatic only when everyNScreens is set
            if (!screens.isUsable(activity)) return@launch
            val ctx = state.context(clock(), activity.javaClass.name)
            val decision = AdPolicy.evaluate(p, ctx)
            AdLog.d("random") { "${activity.javaClass.simpleName}: $decision (screens=${ctx.screensSinceAd}/${p.everyNScreens}, lastShown=${ctx.lastShownAtMs?.let { "${(ctx.nowMs - it) / 1000}s ago" } ?: "never"}, gap=${p.minGapSeconds}s)" }
            if (decision != Decision.ALLOW) return@launch
            if (p.mode == AdMode.PRELOAD) {
                // preload mode: show only what is ready; otherwise ask for one for the next time
                if (!interstitial.showPreloaded(activity, Placements.RANDOM, null)) interstitial.preload(Placements.RANDOM)
            } else {
                interstitial.showOnDemand(activity, Placements.RANDOM, null)
            }
        }
    }

    companion object {
        const val SETTLE_MS = 400L
    }
}

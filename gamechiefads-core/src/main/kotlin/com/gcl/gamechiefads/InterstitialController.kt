package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Interstitial flows. ON_DEMAND (default): claim the slot, loader UI, waterfall, show.
 * PRELOADED (opt-in): fill the pool on request, show what is ready. At most one ad is ever in flight:
 * the "ad showing" flag is claimed before any load starts, so a second call is refused (no double ads).
 */
internal class InterstitialController(
    private val state: AdState,
    private val config: () -> AdsConfig,
    private val pool: AdPool,
    private val pipeline: AdLoadPipeline,
    private val shower: FullScreenShower,
    private val screens: ScreenHost,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val loaderUi: () -> AdLoaderUi?,
    private val source: suspend (String) -> FullScreenAd?,
) {

    /** Default path: load now, then show. [listener] may be null. */
    fun showOnDemand(activity: Activity, name: String, listener: InterstitialListener?) {
        val events = ListenerDispatcher(listener)
        val p = allowed(activity, name, events) ?: return
        state.setAdShowing(true) // claim the slot before loading
        scope.launch {
            var handedOver = false
            val ui = loaderUi()
            try {
                runCatching { ui?.show(activity) }
                val ad = try {
                    pipeline.load(p, activity, source)
                } finally {
                    runCatching { ui?.dismiss() }
                }
                when {
                    ad == null -> events.onFinished(false, "no fill")
                    state.premium.value -> { ad.destroy(); events.onFinished(false, "premium") }
                    !screens.isUsable(activity) -> { ad.destroy(); events.onFinished(false, "screen gone") }
                    else -> {
                        handedOver = true
                        shower.show(activity, ad, refillAfter(name, events))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                events.onFinished(false, t.message ?: "error")
            } finally {
                if (!handedOver) state.setAdShowing(false)
            }
        }
    }

    /** Opt-in: make sure one ad is ready for [name]. No request if one is ready or loading. */
    fun preload(name: String) {
        val p = config().placement(name) ?: return
        if (!p.enabled || state.premium.value) return
        scope.launch {
            try {
                pool.fill(name) { pipeline.load(p, screens.current, source) }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) { /* fail open */ }
        }
    }

    fun isReady(name: String): Boolean = pool.isReady(name)

    /**
     * Shows a ready ad. False = nothing was started. With fallbackToLoad the on-demand path
     * is started instead and true is returned.
     */
    fun showPreloaded(activity: Activity, name: String, listener: InterstitialListener?): Boolean {
        val p = config().placement(name) ?: return false
        if (AdPolicy.evaluateExplicit(p, state.context(clock(), activity.javaClass.name)) != Decision.ALLOW) return false
        val ad = pool.take(name)
        if (ad == null) {
            if (!config().fallbackToLoad) return false
            showOnDemand(activity, name, listener)
            return true
        }
        val events = ListenerDispatcher(listener)
        if (!screens.isUsable(activity)) {
            ad.destroy()
            return false
        }
        shower.show(activity, ad, refillAfter(name, events))
        return true
    }

    private fun allowed(activity: Activity, name: String, events: ListenerDispatcher): PlacementConfig? {
        val p = config().placement(name)
        if (p == null) {
            events.onFinished(false, "unknown placement: $name")
            return null
        }
        val decision = AdPolicy.evaluateExplicit(p, state.context(clock(), activity.javaClass.name))
        if (decision != Decision.ALLOW) {
            events.onFinished(false, decision.name)
            return null
        }
        return p
    }

    /** Auto-refill is opt-in (config.autoRefill, default off) and only for placements in PRELOAD mode. */
    private fun refillAfter(name: String, inner: ListenerDispatcher) = object : FullScreenShower.Events {
        override fun onShown() = inner.onShown()
        override fun onFinished(shown: Boolean, reason: String?) {
            inner.onFinished(shown, reason)
            val c = config()
            if (c.autoRefill && c.placement(name)?.mode == AdMode.PRELOAD) preload(name)
        }
    }
}

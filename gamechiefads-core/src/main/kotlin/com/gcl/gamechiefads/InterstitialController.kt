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
        AdLog.d("interstitial") { "$name: showOnDemand requested from ${activity.javaClass.simpleName}" }
        val p = allowed(activity, name, events) ?: return
        state.setAdShowing(true) // claim the slot before loading
        scope.launch {
            var handedOver = false
            val ui = loaderUi()
            try {
                runCatching { ui?.show(activity, LoaderInfo(name, p.loaderTitle, p.loaderMessage)) }
                val ads: List<FullScreenAd> = try {
                    loadAds(p, activity)
                } finally {
                    runCatching { ui?.dismiss() }
                }
                when {
                    ads.isEmpty() -> { AdLog.d("interstitial") { "$name: no fill -> not shown" }; events.onFinished(false, "no fill") }
                    state.premium.value -> { AdLog.d("interstitial") { "$name: became premium while loading -> dropped" }; ads.forEach { it.destroy() }; events.onFinished(false, "premium") }
                    !screens.isUsable(activity) -> { AdLog.d("interstitial") { "$name: screen gone while loading -> dropped" }; ads.forEach { it.destroy() }; events.onFinished(false, "screen gone") }
                    else -> {
                        AdLog.d("interstitial") { "$name: showing ${ads.size} ad(s)" }
                        handedOver = true
                        shower.showAll(activity, ads, refillAfter(name, events))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                AdLog.d("interstitial") { "$name: error $t" }
                events.onFinished(false, t.message ?: "error")
            } finally {
                if (!handedOver) state.setAdShowing(false)
            }
        }
    }

    /** One ad, or two for a "double" placement (loaded together, shown back to back). */
    private suspend fun loadAds(p: PlacementConfig, activity: Activity): List<FullScreenAd> {
        if (!p.doubleAd) return listOfNotNull(pipeline.load(p, activity, source) { it.destroy() })
        val pair = pipeline.loadPair(p, activity, source, config().doubleSecondWaitMs) { it.destroy() }
        return listOfNotNull(pair?.first, pair?.second)
    }

    /** Opt-in: make sure one ad is ready for [name]. No request if one is ready or loading. */
    fun preload(name: String) {
        val p = config().placement(name) ?: run { AdLog.d("interstitial") { "$name: preload ignored, unknown placement" }; return }
        if (!p.enabled || state.premium.value) {
            AdLog.d("interstitial") { "$name: preload ignored (enabled=${p.enabled}, premium=${state.premium.value})" }
            return
        }
        scope.launch {
            try {
                AdLog.d("interstitial") { "$name: preload requested" }
                val ready = pool.fill(name) { pipeline.load(p, screens.current, source) }
                AdLog.d("interstitial") { "$name: preload finished, ready=$ready" }
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
        val decision = AdPolicy.evaluateExplicit(p, state.context(clock(), activity.javaClass.name))
        if (decision != Decision.ALLOW) {
            AdLog.d("interstitial") { "$name: showPreloaded denied: $decision" }
            return false
        }
        val ad = pool.take(name)
        if (ad == null) {
            if (!config().fallbackToLoad) {
                AdLog.d("interstitial") { "$name: showPreloaded: nothing ready (fallbackToLoad off)" }
                return false
            }
            AdLog.d("interstitial") { "$name: showPreloaded: nothing ready -> falling back to on-demand" }
            showOnDemand(activity, name, listener)
            return true
        }
        val events = ListenerDispatcher(listener)
        if (!screens.isUsable(activity)) {
            ad.destroy()
            return false
        }
        AdLog.d("interstitial") { "$name: showing preloaded ad" }
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
            AdLog.d("interstitial") { "$name: denied: $decision" }
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
            if (c.autoRefill && c.placement(name)?.mode == AdMode.PRELOAD) {
                AdLog.d("interstitial") { "$name: auto-refill" }
                preload(name)
            }
        }
    }
}

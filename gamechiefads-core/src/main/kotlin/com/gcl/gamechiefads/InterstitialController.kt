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
    /** After a pass with no fill the placement waits a few seconds (no request loops). Null = no wait. */
    private val cooldown: NoFillCooldown? = null,
    /** Tells the user "no ad available" inside the loading dialog (toast if the dialog cannot). Null = no message. */
    private val noAd: NoAdNotice? = null,
) {

    /** Default path: load now, then show. [listener] may be null. */
    fun showOnDemand(activity: Activity, name: String, listener: InterstitialListener?, double: Boolean? = null) {
        val events = ListenerDispatcher(listener)
        AdLog.d("interstitial") { "$name: showOnDemand requested from ${activity.javaClass.simpleName} (double=$double)" }
        val p = allowed(activity, name, events)?.let { it.copy(doubleAd = it.wantsDouble(double)) } ?: return
        runOnDemand(activity, name, p, events)
    }

    /**
     * The automatic random interstitial, called by [RandomInterstitialTrigger] over a new screen after the policy said yes.
     * Same loader dialog, floors and double (the placement's doubleAd) as a click. Silent while a "no fill" wait runs:
     * an ad the user did not ask for never pops a "no ad available" message. Restarts the click counter when it shows.
     */
    fun showAutoRandom(activity: Activity) {
        val p = config().placement(Placements.RANDOM)?.takeIf { config().enabled } ?: return
        if ((cooldown?.remainingMs(Placements.RANDOM) ?: 0L) > 0) {
            AdLog.d("random") { "automatic: no fill a moment ago, skipped quietly" }
            return
        }
        val events = ListenerDispatcher(object : InterstitialListener {
            override fun onShown() { callsSinceAd = 0 }
        })
        runOnDemand(activity, Placements.RANDOM, p.copy(doubleAd = p.wantsDouble()), events)
    }

    private var callsSinceAd = 0

    /**
     * "Random" interstitial for clicks: the app calls this on a click, the library decides (premium, ad showing, excluded
     * screen, min gap, every N calls). Not allowed = nothing happens and the listener gets onNotShown + onComplete(false).
     * Allowed = loader dialog, floors, show (one ad, or two for [double]). The counter restarts only when an ad was shown.
     */
    fun showRandom(activity: Activity, listener: InterstitialListener?, double: Boolean) {
        val events = ListenerDispatcher(object : InterstitialListener {
            override fun onShown() { callsSinceAd = 0; listener?.onShown() }
            override fun onClosed() { listener?.onClosed() }
            override fun onNotShown(reason: String) { listener?.onNotShown(reason) }
            override fun onComplete(wasShown: Boolean) { listener?.onComplete(wasShown) }
        })
        val p = config().placement(Placements.RANDOM)?.takeIf { config().enabled }
        if (p == null) {
            events.onFinished(false, "random interstitial is not configured or ads are off")
            return
        }
        callsSinceAd++
        val decision = AdPolicy.evaluateClick(p, state.context(clock(), activity.javaClass.name, config().quietSecondsAfterAppOpen * 1000L), callsSinceAd)
        AdLog.d("random") { "click on ${activity.javaClass.simpleName}: $decision (call $callsSinceAd/${p.everyNCalls}, gap=${p.minGapSeconds}s, double=$double)" }
        if (decision != Decision.ALLOW) {
            events.onFinished(false, decision.name)
            return
        }
        runOnDemand(activity, Placements.RANDOM, p.copy(doubleAd = double), events)
    }

    private fun runOnDemand(activity: Activity, name: String, p: PlacementConfig, events: ListenerDispatcher) {
        val info = LoaderInfo(name, p.loaderTitle ?: config().loaderTitle, p.loaderMessage ?: config().loaderMessage)
        val wait = cooldown?.remainingMs(name) ?: 0L
        if (wait > 0) {
            AdLog.d("interstitial") { "$name: no fill a moment ago, not requesting again for ${wait}ms" }
            state.setAdShowing(true) // the message is on screen: no other ad meanwhile
            scope.launch {
                try { noAd?.show(activity, info) } finally { state.setAdShowing(false) }
                events.onFinished(false, "no ad available, retry in ${(wait + 999) / 1000}s")
            }
            return
        }
        state.setAdShowing(true) // claim the slot before loading
        scope.launch {
            var handedOver = false
            val ui = loaderUi()
            try {
                runCatching { ui?.show(activity, info) }
                val ads: List<FullScreenAd> = try {
                    loadAds(p, activity)
                } catch (t: Throwable) {
                    runCatching { ui?.dismiss() }
                    throw t
                }
                // The loader stays up after the ad is LOADED: on a slow connection the SDK can take seconds to put it on
                // screen, and an empty gap there looks like "no ad, then an ad out of nowhere". It goes when the ad shows.
                if (ads.isNotEmpty() && (state.premium.value || !screens.isUsable(activity))) runCatching { ui?.dismiss() }
                when {
                    ads.isEmpty() -> {
                        AdLog.d("interstitial") { "$name: no fill -> not shown, no new pass for a few seconds" }
                        cooldown?.recordFail(name)
                        if (noAd != null) noAd.show(activity, info) else runCatching { ui?.dismiss() }
                        events.onFinished(false, "no fill")
                    }
                    state.premium.value -> { AdLog.d("interstitial") { "$name: became premium while loading -> dropped" }; ads.forEach { it.destroy() }; events.onFinished(false, "premium") }
                    !screens.isUsable(activity) -> { AdLog.d("interstitial") { "$name: screen gone while loading -> dropped" }; ads.forEach { it.destroy() }; events.onFinished(false, "screen gone") }
                    else -> {
                        AdLog.d("interstitial") { "$name: showing ${ads.size} ad(s)" }
                        cooldown?.recordOk(name)
                        handedOver = true
                        val inner = refillAfter(name, events)
                        var loaderUp = true
                        fun hideLoader() { if (loaderUp) { loaderUp = false; runCatching { ui?.dismiss() } } }
                        shower.showAll(activity, ads, object : FullScreenShower.Events by inner {
                            override fun onShown() { hideLoader(); inner.onShown() }
                            override fun onFinished(shown: Boolean, reason: String?) { hideLoader(); inner.onFinished(shown, reason) }
                        })
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
        val pair = pipeline.loadPair(p, activity, source) { it.destroy() }
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
        val decision = AdPolicy.evaluateExplicit(p, state.context(clock(), activity.javaClass.name, config().quietSecondsAfterAppOpen * 1000L))
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
        val decision = AdPolicy.evaluateExplicit(p, state.context(clock(), activity.javaClass.name, config().quietSecondsAfterAppOpen * 1000L))
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

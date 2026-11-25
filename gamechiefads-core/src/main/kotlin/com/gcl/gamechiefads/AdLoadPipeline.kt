package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The one way to load any ad: SDK ready? -> resolve unit ids (test ids in debug, disabled units removed,
 * recently missing floors skipped) -> waterfall (sequential or parallel).
 */
internal class AdLoadPipeline(
    private val config: () -> AdsConfig,
    private val ready: suspend (Activity?) -> Boolean,
    private val debug: Boolean,
    private val floors: FloorPolicy = FloorPolicy { System.currentTimeMillis() },
    /** QA test mode: Google test unit ids for every request, in any build (GamechiefAds.setTestMode). */
    private val forceTestIds: () -> Boolean = { false },
) {
    suspend fun <T : Any> load(
        p: PlacementConfig,
        activity: Activity?,
        source: suspend (String) -> T?,
        discard: (T) -> Unit = {},
    ): T? {
        val t0 = System.nanoTime()
        if (!ready(activity)) {
            AdLog.d("pipeline") { "${p.name}: SDK not ready (consent denied, no activity, or init failed) -> no ad" }
            return null
        }
        val useTestIds = forceTestIds() || (debug && config().testIdsInDebug)
        val all = AdUnits.resolve(p, useTestIds)
        val units = floors.active(p, all)
        AdLog.d("pipeline") {
            "${p.name}: ${p.waterfall} waterfall, ${units.size}/${all.size} unit(s), tier ${p.tierTimeoutMs}ms, " +
                "budget ${config().overallBudgetMs}ms${if (useTestIds) " (test ids)" else ""}"
        }
        val hit = Waterfall.run(
            units = units,
            timeoutFor = { u -> p.unitTimeouts[u] ?: p.tierTimeoutMs },
            overallBudgetMs = config().overallBudgetMs,
            mode = p.waterfall,
            discard = discard,
            onTier = { unit, ok -> floors.record(p, unit, ok) },
        ) { unit ->
            source(unit).also { AdLog.d("pipeline") { "${p.name}: $unit -> ${if (it != null) "loaded" else "no ad"}" } }
        }
        AdLog.d("pipeline") { "${p.name}: ${if (hit != null) "ad ready" else "no ad"} after ${(System.nanoTime() - t0) / 1_000_000}ms" }
        return hit
    }

    /**
     * Two ads for a "double" show, requested at the same time. The second is optional: after the first has
     * arrived the second is used only if it is already there (no waiting). Null = no ad at all.
     */
    suspend fun <T : Any> loadPair(
        p: PlacementConfig,
        activity: Activity?,
        source: suspend (String) -> T?,
        discard: (T) -> Unit,
    ): Pair<T, T?>? = coroutineScope {
        val d1 = async { load(p, activity, source, discard) }
        // the second ad of a double: the same request as the first, started together
        val p2 = p.forSecondAd()
        val d2 = async { load(p2, activity, source, discard) }
        val a = d1.await()
        if (a == null) {
            val only = d2.await() ?: return@coroutineScope null
            return@coroutineScope Pair(only, null)
        }
        val b = if (d2.isCompleted && !d2.isCancelled) d2.getCompleted() else { d2.cancel(); null }
        AdLog.d("pipeline") { "${p.name}: double ${if (b != null) "ready (2 ads)" else "second ad missing, showing one"}" }
        Pair(a, b)
    }
}

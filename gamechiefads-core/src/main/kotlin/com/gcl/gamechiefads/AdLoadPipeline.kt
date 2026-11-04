package com.gcl.gamechiefads

import android.app.Activity

/** The one way to load any ad: SDK ready? -> resolve unit ids (test ids in debug) -> waterfall. */
internal class AdLoadPipeline(
    private val config: () -> AdsConfig,
    private val ready: suspend (Activity?) -> Boolean,
    private val debug: Boolean,
) {
    suspend fun <T : Any> load(p: PlacementConfig, activity: Activity?, source: suspend (String) -> T?): T? {
        val t0 = System.nanoTime()
        if (!ready(activity)) {
            AdLog.d("pipeline") { "${p.name}: SDK not ready (consent denied, no activity, or init failed) -> no ad" }
            return null
        }
        val units = AdUnits.resolve(p, debug)
        AdLog.d("pipeline") { "${p.name}: waterfall start, ${units.size} unit(s), tier ${p.tierTimeoutMs}ms, budget ${config().overallBudgetMs}ms${if (debug) " (test ids)" else ""}" }
        val hit = Waterfall.run(units, p.tierTimeoutMs, config().overallBudgetMs) { unit ->
            source(unit).also { AdLog.d("pipeline") { "${p.name}: $unit -> ${if (it != null) "loaded" else "no ad"}" } }
        }
        AdLog.d("pipeline") { "${p.name}: ${if (hit != null) "ad ready" else "no ad"} after ${(System.nanoTime() - t0) / 1_000_000}ms" }
        return hit
    }
}

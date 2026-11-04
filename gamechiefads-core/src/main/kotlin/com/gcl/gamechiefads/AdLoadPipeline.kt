package com.gcl.gamechiefads

import android.app.Activity

/** The one way to load any ad: SDK ready? -> resolve unit ids (test ids in debug) -> waterfall. */
internal class AdLoadPipeline(
    private val config: () -> AdsConfig,
    private val ready: suspend (Activity?) -> Boolean,
    private val debug: Boolean,
) {
    suspend fun <T : Any> load(p: PlacementConfig, activity: Activity?, source: suspend (String) -> T?): T? {
        if (!ready(activity)) {
            AdLog.d("${p.name}: SDK not ready (consent denied, no activity, or init failed)")
            return null
        }
        val units = AdUnits.resolve(p, debug)
        val hit = Waterfall.run(units, p.tierTimeoutMs, config().overallBudgetMs) { unit ->
            source(unit).also { AdLog.d("${p.name}: $unit -> ${if (it != null) "loaded" else "no ad"}") }
        }
        if (hit == null) AdLog.d("${p.name}: waterfall finished without an ad (${units.size} units)")
        return hit
    }
}

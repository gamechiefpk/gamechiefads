package com.gcl.gamechiefads

import android.app.Activity

/** The one way to load any ad: SDK ready? -> resolve unit ids (test ids in debug) -> waterfall. */
internal class AdLoadPipeline(
    private val config: () -> AdsConfig,
    private val ready: suspend (Activity?) -> Boolean,
    private val debug: Boolean,
) {
    suspend fun <T : Any> load(p: PlacementConfig, activity: Activity?, source: suspend (String) -> T?): T? {
        if (!ready(activity)) return null
        return Waterfall.run(AdUnits.resolve(p, debug), p.tierTimeoutMs, config().overallBudgetMs, source)
    }
}

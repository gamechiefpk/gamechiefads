package com.gcl.gamechiefads

import android.content.Context
import android.content.pm.ApplicationInfo

/** Google's published test ids and the rule "debug builds always use test ids". */
internal object AdUnits {
    const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_APP_OPEN = "ca-app-pub-3940256099942544/9257395921"
    const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    const val TEST_NATIVE = "ca-app-pub-3940256099942544/2247696110"
    const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    fun testUnit(format: AdFormat): String = when (format) {
        AdFormat.INTERSTITIAL -> TEST_INTERSTITIAL
        AdFormat.APP_OPEN -> TEST_APP_OPEN
        AdFormat.BANNER -> TEST_BANNER
        AdFormat.NATIVE -> TEST_NATIVE
        AdFormat.REWARDED -> TEST_REWARDED
    }

    /** Debug: one test unit (no point walking a waterfall of test ids). Release: the configured units. */
    fun resolve(p: PlacementConfig, debug: Boolean): List<String> =
        if (debug) listOf(testUnit(p.format)) else applyFlooring(p, p.units.filter { it !in p.disabledUnits })

    /** Flooring off = a single request to the last (lowest floor) unit. */
    private fun applyFlooring(p: PlacementConfig, units: List<String>): List<String> =
        if (!p.flooring && units.size > 1) listOf(units.last()) else units

    fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
}

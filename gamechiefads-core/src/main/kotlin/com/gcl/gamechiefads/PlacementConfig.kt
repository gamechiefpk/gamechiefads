package com.gcl.gamechiefads

enum class AdFormat { INTERSTITIAL, APP_OPEN, BANNER, NATIVE }

/** ON_DEMAND = load then show (default). PRELOAD = show a ready ad from the pool. */
enum class AdMode { ON_DEMAND, PRELOAD }

/** Plain data for one named placement. Built through [AdsConfig.Builder]; never mutated. */
data class PlacementConfig(
    val name: String,
    val format: AdFormat,
    val units: List<String> = emptyList(),
    val mode: AdMode = AdMode.ON_DEMAND,
    /** Show only after this many screens since the last ad. 0 = no screen rule. */
    val everyNScreens: Int = 0,
    /** Minimum seconds since the last ad of any kind. 0 = no gap rule. */
    val minGapSeconds: Int = 0,
    /** Fully qualified Activity class names where this placement must not trigger. */
    val excludedScreens: Set<String> = emptySet(),
    val tierTimeoutMs: Long = DEFAULT_TIER_TIMEOUT_MS,
    val enabled: Boolean = true,
) {
    companion object {
        const val DEFAULT_TIER_TIMEOUT_MS = 3_000L
    }
}

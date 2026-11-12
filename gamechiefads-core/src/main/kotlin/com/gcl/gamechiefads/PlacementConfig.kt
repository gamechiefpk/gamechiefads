package com.gcl.gamechiefads

enum class AdFormat { INTERSTITIAL, APP_OPEN, BANNER, NATIVE }

/** ON_DEMAND = load then show (default). PRELOAD = show a ready ad from the pool. */
enum class AdMode { ON_DEMAND, PRELOAD }

/** Plain data for one named placement. Built through [AdsConfig.Builder]; never mutated. */
data class PlacementConfig(
    val name: String,
    val format: AdFormat,
    /** Ordered ad units, the "floors": highest floor first. */
    val units: List<String> = emptyList(),
    val mode: AdMode = AdMode.ON_DEMAND,
    /** Show only after this many screens since the last ad. 0 = no screen rule. */
    val everyNScreens: Int = 0,
    /** Minimum seconds since the last ad of any kind. 0 = no gap rule. */
    val minGapSeconds: Int = 0,
    /** Fully qualified Activity class names where this placement must not trigger. */
    val excludedScreens: Set<String> = emptySet(),
    /** Default timeout for one unit. See [unitTimeouts] to give a single unit its own. */
    val tierTimeoutMs: Long = DEFAULT_TIER_TIMEOUT_MS,
    val enabled: Boolean = true,

    // ---- floor (waterfall) control ----
    /**
     * true (default): all [units] are used as floors (high floor first). false: no floors, ONE request to a single
     * unit, the LAST of [units] (the lowest floor, normally the one that always fills).
     */
    val flooring: Boolean = true,
    /** One-by-one (default) or all at once. */
    val waterfall: WaterfallMode = WaterfallMode.SEQUENTIAL,
    /** Per-unit timeout override, in ms. Units not listed use [tierTimeoutMs]. */
    val unitTimeouts: Map<String, Long> = emptyMap(),
    /** Units that stay in [units] but are not requested (switch a floor off without losing its id). */
    val disabledUnits: Set<String> = emptySet(),
    /** A unit that missed this many times in a row is skipped for [skipForSeconds]. 0 = never skip. */
    val skipAfterFails: Int = 0,
    val skipForSeconds: Int = DEFAULT_SKIP_SECONDS,

    // ---- format options ----
    /** Interstitial and app-open: show two ads back to back as if they were one (see docs). */
    val doubleAd: Boolean = false,
    /** Banner only. */
    val bannerType: BannerType = BannerType.ADAPTIVE,
    /** Text of the loading overlay for this placement; null = the loader's own text. */
    val loaderTitle: String? = null,
    val loaderMessage: String? = null,
) {
    companion object {
        const val DEFAULT_TIER_TIMEOUT_MS = 3_000L
        const val DEFAULT_SKIP_SECONDS = 60
    }
}

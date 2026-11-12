package com.gcl.gamechiefads

import android.content.Context
import androidx.annotation.StringRes

/** Whole-library configuration as plain data. Java-friendly via [builder]. */
data class AdsConfig(
    val placements: Map<String, PlacementConfig>,
    /** AdMob app id ("ca-app-pub-...~..."), passed to the SDK init and UMP. Null = Google's test app id. */
    val appId: String? = null,
    /** Firebase Remote Config key holding the JSON read by [RemoteOverrides]. */
    val remoteConfigKey: String? = null,
    /** If a preloaded ad is not ready, load on demand instead of returning false. */
    val fallbackToLoad: Boolean = false,
    /** Request a new ad after a preloaded one was shown. Off: no requests the app did not ask for. */
    val autoRefill: Boolean = false,
    /** Upper bound for one whole waterfall, whatever the tier count. */
    val overallBudgetMs: Long = DEFAULT_OVERALL_BUDGET_MS,
    /** Hard limit for prepareSplash: consent + init + waterfall together. */
    val splashTimeoutMs: Long = DEFAULT_SPLASH_TIMEOUT_MS,
    /** How long one ad request waits for the SDK to finish its (one-time) init. A late init is kept, not restarted. */
    val initTimeoutMs: Long = DEFAULT_INIT_TIMEOUT_MS,
    /** Double ads: after the first ad is ready, how long to wait for the second before showing just one. */
    val doubleSecondWaitMs: Long = DEFAULT_DOUBLE_WAIT_MS,
    /** Debug builds replace your unit ids by Google test ids (default). false = use the configured units even in debug. */
    val testIdsInDebug: Boolean = true,
    /** Default texts of the loading overlay for every placement without its own. Null = the loader's own text. */
    val loaderTitle: String? = null,
    val loaderMessage: String? = null,
) {
    fun placement(name: String): PlacementConfig? = placements[name]

    companion object {
        const val DEFAULT_OVERALL_BUDGET_MS = 8_000L
        const val DEFAULT_SPLASH_TIMEOUT_MS = 10_000L
        const val DEFAULT_INIT_TIMEOUT_MS = 8_000L
        const val DEFAULT_DOUBLE_WAIT_MS = 2_500L

        @JvmStatic
        fun builder(): Builder = Builder()
    }

    /** Fluent builder: start a placement with interstitial()/appOpen()/..., then chain its options. */
    class Builder internal constructor() {
        private val done = LinkedHashMap<String, PlacementConfig>()
        private var current: PlacementConfig? = null
        private var appId: String? = null
        private var remoteConfigKey: String? = null
        private var fallbackToLoad = false
        private var autoRefill = false
        private var overallBudgetMs = DEFAULT_OVERALL_BUDGET_MS
        private var splashTimeoutMs = DEFAULT_SPLASH_TIMEOUT_MS
        private var initTimeoutMs = DEFAULT_INIT_TIMEOUT_MS
        private var doubleSecondWaitMs = DEFAULT_DOUBLE_WAIT_MS
        private var testIdsInDebug = true
        private var loaderTitle: String? = null
        private var loaderMessage: String? = null

        fun interstitial(name: String) = start(name, AdFormat.INTERSTITIAL)
        fun appOpen(name: String) = start(name, AdFormat.APP_OPEN)
        fun banner(name: String) = start(name, AdFormat.BANNER)
        fun nativeAd(name: String) = start(name, AdFormat.NATIVE)
        fun rewarded(name: String) = start(name, AdFormat.REWARDED)

        fun interstitial(placement: AdPlacement) = interstitial(placement.name)
        fun appOpen(placement: AdPlacement) = appOpen(placement.name)
        fun banner(placement: AdPlacement) = banner(placement.name)
        fun nativeAd(placement: AdPlacement) = nativeAd(placement.name)
        fun rewarded(placement: AdPlacement) = rewarded(placement.name)

        fun units(vararg ids: String) = edit { it.copy(units = ids.filter(String::isNotBlank)) }
        /** Same as [units] but from string resources, e.g. unitsRes(app, R.string.HF, R.string.MF). */
        fun unitsRes(context: Context, @StringRes vararg ids: Int) =
            edit { it.copy(units = ids.map { id -> context.getString(id) }.filter(String::isNotBlank)) }

        fun mode(mode: AdMode) = edit { it.copy(mode = mode) }
        fun everyNScreens(n: Int) = edit { it.copy(everyNScreens = n.coerceAtLeast(0)) }
        fun minGapSeconds(s: Int) = edit { it.copy(minGapSeconds = s.coerceAtLeast(0)) }
        fun excludeScreens(vararg screens: Class<*>) =
            edit { it.copy(excludedScreens = it.excludedScreens + screens.map { c -> c.name }) }
        fun tierTimeoutMs(ms: Long) = edit { it.copy(tierTimeoutMs = ms.coerceAtLeast(1)) }
        fun enabled(enabled: Boolean) = edit { it.copy(enabled = enabled) }

        // ---- floor (waterfall) control
        /** false = no floors: one request to the last unit only. true (default) = walk all units, high floor first. */
        fun flooring(enabled: Boolean) = edit { it.copy(flooring = enabled) }
        /** SEQUENTIAL (default, one unit at a time) or PARALLEL (all at once, first ad wins). */
        fun waterfallMode(mode: WaterfallMode) = edit { it.copy(waterfall = mode) }
        /** Gives one unit its own timeout, e.g. a short one for the high floor that rarely fills. */
        fun unitTimeoutMs(unit: String, ms: Long) =
            edit { it.copy(unitTimeouts = it.unitTimeouts + (unit to ms.coerceAtLeast(1))) }
        /** Keeps [unit] in the list but does not request it. */
        fun disableUnit(unit: String) = edit { it.copy(disabledUnits = it.disabledUnits + unit) }
        /** A unit that misses [fails] times in a row is skipped for [seconds]. */
        fun skipUnitAfterFails(fails: Int, seconds: Int) =
            edit { it.copy(skipAfterFails = fails.coerceAtLeast(0), skipForSeconds = seconds.coerceAtLeast(1)) }

        // ---- format options
        /** Interstitial / app-open: two ads back to back as one break. Also switchable by Remote Config. */
        fun doubleAd(value: Boolean) = edit { it.copy(doubleAd = value) }
        /** Rewarded: true (default) = premium users get the reward with no ad. false = they get nothing. */
        fun premiumGetsReward(value: Boolean) = edit { it.copy(premiumGetsReward = value) }
        fun bannerType(type: BannerType) = edit { it.copy(bannerType = type) }
        /** Texts of the loading overlay for this placement (null = the loader's own text). */
        fun loaderText(title: String?, message: String?) = edit { it.copy(loaderTitle = title, loaderMessage = message) }

        /** Loading overlay texts for ALL placements (a placement's own loaderText wins). Can also change at runtime. */
        fun defaultLoaderText(title: String?, message: String?) = apply { loaderTitle = title; loaderMessage = message }

        fun appId(id: String?) = apply { appId = id }
        fun remoteConfigKey(key: String?) = apply { remoteConfigKey = key }
        fun fallbackToLoad(value: Boolean) = apply { fallbackToLoad = value }
        fun autoRefill(value: Boolean) = apply { autoRefill = value }
        fun overallBudgetMs(ms: Long) = apply { overallBudgetMs = ms.coerceAtLeast(1) }

        fun splashTimeoutMs(ms: Long) = apply { splashTimeoutMs = ms.coerceAtLeast(1) }
        fun initTimeoutMs(ms: Long) = apply { initTimeoutMs = ms.coerceAtLeast(1) }
        fun doubleSecondWaitMs(ms: Long) = apply { doubleSecondWaitMs = ms.coerceAtLeast(0) }

        /** Set false to try your real or custom unit ids (e.g. floors) in a debug build. Release builds always use your units. */
        fun testIdsInDebug(value: Boolean) = apply { testIdsInDebug = value }

        fun build(): AdsConfig {
            commit()
            return AdsConfig(done.toMap(), appId, remoteConfigKey, fallbackToLoad, autoRefill, overallBudgetMs, splashTimeoutMs, initTimeoutMs, doubleSecondWaitMs, testIdsInDebug, loaderTitle, loaderMessage)
        }

        private fun start(name: String, format: AdFormat) = apply {
            require(name.isNotBlank()) { "Placement name must not be blank" }
            commit()
            require(name !in done) { "Duplicate placement: $name" }
            current = PlacementConfig(name, format)
        }

        private fun edit(change: (PlacementConfig) -> PlacementConfig) = apply {
            val c = checkNotNull(current) { "Call interstitial()/appOpen()/banner()/nativeAd() before setting options" }
            current = change(c)
        }

        private fun commit() {
            current?.let { done[it.name] = it }
            current = null
        }
    }
}

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
) {
    fun placement(name: String): PlacementConfig? = placements[name]

    companion object {
        const val DEFAULT_OVERALL_BUDGET_MS = 8_000L
        const val DEFAULT_SPLASH_TIMEOUT_MS = 10_000L

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

        fun interstitial(name: String) = start(name, AdFormat.INTERSTITIAL)
        fun appOpen(name: String) = start(name, AdFormat.APP_OPEN)
        fun banner(name: String) = start(name, AdFormat.BANNER)
        fun nativeAd(name: String) = start(name, AdFormat.NATIVE)

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

        fun appId(id: String?) = apply { appId = id }
        fun remoteConfigKey(key: String?) = apply { remoteConfigKey = key }
        fun fallbackToLoad(value: Boolean) = apply { fallbackToLoad = value }
        fun autoRefill(value: Boolean) = apply { autoRefill = value }
        fun overallBudgetMs(ms: Long) = apply { overallBudgetMs = ms.coerceAtLeast(1) }

        fun splashTimeoutMs(ms: Long) = apply { splashTimeoutMs = ms.coerceAtLeast(1) }

        fun build(): AdsConfig {
            commit()
            return AdsConfig(done.toMap(), appId, remoteConfigKey, fallbackToLoad, autoRefill, overallBudgetMs, splashTimeoutMs)
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

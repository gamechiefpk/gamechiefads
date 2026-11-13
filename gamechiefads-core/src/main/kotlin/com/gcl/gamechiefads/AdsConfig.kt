package com.gcl.gamechiefads

import android.content.Context
import androidx.annotation.StringRes

/** Whole-library configuration as plain data. Java-friendly via [builder]. */
data class AdsConfig(
    val placements: Map<String, PlacementConfig>,
    /** AdMob app id ("ca-app-pub-...~..."), passed to the SDK init and UMP. Null = Google's test app id. */
    val appId: String? = null,
    /** Firebase Remote Config key holding the JSON read by [RemoteOverrides]. */
    val remoteConfigKey: String? = DEFAULT_REMOTE_KEY,
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
    /** false = every ad off (Remote Config {"enabled":false}). */
    val enabled: Boolean = true,
    /** Your own Remote Config values and their defaults (read anywhere with GamechiefAds.getRemoteString/Boolean/Long/Double). */
    val remoteDefaults: Map<String, Any> = emptyMap(),
    /** How often the app may fetch Remote Config from the server. Lower it only while testing (the server throttles). */
    val remoteFetchIntervalSeconds: Long = DEFAULT_REMOTE_INTERVAL_SECONDS,
    /** Your own source of remote values. Null (default) = Firebase Remote Config when the remoteconfig module is in the app. */
    val remoteSource: RemoteConfigSource? = null,
) {
    fun placement(name: String): PlacementConfig? = placements[name]

    companion object {
        const val DEFAULT_REMOTE_KEY = "gamechief_ads_config"
        const val DEFAULT_REMOTE_INTERVAL_SECONDS = 3_600L
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
        private var remoteConfigKey: String? = DEFAULT_REMOTE_KEY
        private val remoteDefaults = LinkedHashMap<String, Any>()
        private var remoteFetchIntervalSeconds = DEFAULT_REMOTE_INTERVAL_SECONDS
        private var remoteSource: RemoteConfigSource? = null
        private var fallbackToLoad = false
        private var autoRefill = false
        private var overallBudgetMs = DEFAULT_OVERALL_BUDGET_MS
        private var splashTimeoutMs = DEFAULT_SPLASH_TIMEOUT_MS
        private var initTimeoutMs = DEFAULT_INIT_TIMEOUT_MS
        private var doubleSecondWaitMs = DEFAULT_DOUBLE_WAIT_MS
        private var testIdsInDebug = true
        private var loaderTitle: String? = null
        private var loaderMessage: String? = null
        private val pendingSecond = LinkedHashMap<String, String>()

        // ======== THE SIMPLE WAY: give every ad unit here, one method per slot ========
        // "Units" with several ids = floors: the first id is tried first (high floor), then the next, then the next.
        // After a slot method you can chain options for that slot, e.g. .bannerUnits(A, B).bannerType(BannerType.ADAPTIVE).

        /** Splash app-open ad (call GamechiefAds.loadSplashAppOpenAd). Floors: HF, MF, NORMAL. */
        fun splashAppOpenUnits(vararg ids: String) = slot(Placements.SPLASH, AdFormat.APP_OPEN, ids)
        /** The ONE unit of the SECOND splash app-open ad (loadSplashAppOpenDoubleAd): one request, no floors. Paste the same id as the first ad if you like. Not given = the last first-ad unit. */
        fun splashAppOpenDoubleUnit(id: String) = second(Placements.SPLASH, id)
        /** Splash interstitial ad, for apps that show an interstitial on the splash (GamechiefAds.loadSplashInterstitialAd). */
        fun splashInterstitialUnits(vararg ids: String) = slot(Placements.SPLASH_INTERSTITIAL, AdFormat.INTERSTITIAL, ids)
        fun splashInterstitialDoubleUnit(id: String) = second(Placements.SPLASH_INTERSTITIAL, id)
        /** Large banner on the splash screen (GamechiefAds.loadSplashBannerAd). */
        fun splashBannerUnits(vararg ids: String) = slot(Placements.SPLASH_BANNER, AdFormat.BANNER, ids).bannerType(BannerType.LARGE_BANNER)
        /** App-open ad when the user returns to the app: ONE unit, no floors. Automatic, nothing to call. */
        fun appOpenUnit(id: String) = slot(Placements.APP_OPEN, AdFormat.APP_OPEN, arrayOf(id))
        /** Banner for all normal screens (GamechiefAds.loadBannerAd). */
        fun bannerUnits(vararg ids: String) = slot(Placements.BANNER, AdFormat.BANNER, ids)
        /** Banner for the exit screen (GamechiefAds.loadExitBannerAd). A medium rectangle unless you chain .bannerType(...). */
        fun exitBannerUnits(vararg ids: String) = slot(Placements.EXIT_BANNER, AdFormat.BANNER, ids).bannerType(BannerType.MEDIUM_RECTANGLE)
        /** Interstitial for random clicks (GamechiefAds.loadRandomInterstitialAd). Chain .minGapSeconds(30).everyNCalls(3) to pace it. */
        fun randomInterstitialUnits(vararg ids: String) = slot(Placements.RANDOM, AdFormat.INTERSTITIAL, ids)
        /** The ONE unit of the SECOND random interstitial (loadRandomInterstitialDoubleAd): one request, no floors. */
        fun randomInterstitialDoubleUnit(id: String) = second(Placements.RANDOM, id)
        /** Small native ad at the bottom of the intro screens (AdsUi.loadIntroNativeAd). Floors. */
        fun introNativeUnits(vararg ids: String) = slot(Placements.INTRO_NATIVE, AdFormat.NATIVE, ids)
        /** Full-screen native ad like an interstitial (AdsUi.loadFullScreenNativeAd): ONE unit, no floors. */
        fun fullScreenNativeUnit(id: String) = slot(Placements.FULLSCREEN_NATIVE, AdFormat.NATIVE, arrayOf(id))
        /** Rewarded ad (GamechiefAds.loadRewardedAd). Floors. */
        fun rewardedUnits(vararg ids: String) = slot(Placements.REWARDED, AdFormat.REWARDED, ids)

        /** Random interstitial: show only on every N-th call (default 1 = every call that passes the min gap). */
        fun everyNCalls(n: Int) = edit { it.copy(everyNCalls = n.coerceAtLeast(1)) }

        // ======== ADVANCED: your own extra spots, called by name ========

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
        /** The Remote Config key that holds the ads JSON (default "gamechief_ads_config"). Null = do not read ads rules remotely. */
        fun remoteConfigKey(key: String?) = apply { remoteConfigKey = key }
        /** Adds one of YOUR OWN Remote Config values with its default (String, Boolean, Int, Long or Double). Read it with GamechiefAds.getRemoteXxx. */
        fun remoteDefault(key: String, value: Any) = apply { if (key.isNotBlank() && (value is String || value is Boolean || value is Number)) remoteDefaults[key] = value }
        /** Optional: use your own source of remote values instead of Firebase Remote Config. */
        fun remoteConfigSource(source: RemoteConfigSource?) = apply { remoteSource = source }
        fun remoteDefaults(values: Map<String, Any>) = apply { values.forEach { (k, v) -> remoteDefault(k, v) } }
        /** Minimum seconds between server fetches (default 3600). Use a small value only while testing. */
        fun remoteFetchIntervalSeconds(seconds: Long) = apply { remoteFetchIntervalSeconds = seconds.coerceAtLeast(0) }
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
            pendingSecond.forEach { (name, id) ->
                val p = done[name] ?: throw IllegalStateException("Double units for \"$name\" need the normal units of that slot too")
                done[name] = p.copy(secondUnit = id)
            }
            return AdsConfig(
                placements = done.toMap(), appId = appId, remoteConfigKey = remoteConfigKey, fallbackToLoad = fallbackToLoad,
                autoRefill = autoRefill, overallBudgetMs = overallBudgetMs, splashTimeoutMs = splashTimeoutMs,
                initTimeoutMs = initTimeoutMs, doubleSecondWaitMs = doubleSecondWaitMs, testIdsInDebug = testIdsInDebug,
                loaderTitle = loaderTitle, loaderMessage = loaderMessage,
                remoteDefaults = remoteDefaults.toMap(), remoteFetchIntervalSeconds = remoteFetchIntervalSeconds, remoteSource = remoteSource,
            )
        }

        private fun slot(name: String, format: AdFormat, ids: Array<out String>) =
            start(name, format).units(*ids)

        private fun second(name: String, id: String) = apply {
            pendingSecond[name] = id
        }

        private fun start(name: String, format: AdFormat) = apply {
            require(name.isNotBlank()) { "Placement name must not be blank" }
            commit()
            require(name !in done) { "Duplicate placement: $name" }
            current = PlacementConfig(name, format)
        }

        private fun edit(change: (PlacementConfig) -> PlacementConfig) = apply {
            val c = checkNotNull(current) { "Call a slot method (bannerUnits(...), randomInterstitialUnits(...), ...) before setting options" }
            current = change(c)
        }

        private fun commit() {
            current?.let { done[it.name] = it }
            current = null
        }
    }
}

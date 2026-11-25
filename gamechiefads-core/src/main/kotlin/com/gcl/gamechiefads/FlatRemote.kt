package com.gcl.gamechiefads

/**
 * The simple Firebase Remote Config keys, one value per parameter (no JSON needed). A key that is missing or blank is "not set":
 * the app's own settings stay. Applied on top of the shared file and the JSON rules key; GamechiefAds.applyRemoteConfig(json) still wins.
 *
 *  splashDoubleEnable (bool)          double ad on the splash (app open and interstitial)
 *  doubleInterstitialAdEnable (bool)  double random interstitial
 *  randomClickInterstitialAd (bool)   random interstitial on or off
 *  randomClickAdInterval (number)     every N-th call / new screen
 *  randomClickTimeGap (number)        seconds between two random interstitials
 *  randomClickAdAutoEnable (bool)     the automatic random interstitial over new screens
 *  bannerAdType (text)                type of the normal banner
 *  rewardedAdEnable (bool)            rewarded ads on or off
 *  langaugeSelectionNativeType (text) size of the language screen native (also read when spelled languageSelectionNativeType)
 *
 * Keys read when the ad is asked for (not config): splashType, splashBannerAd, homescrBannerType, interstitialOnPremiumDismiss,
 * introScrBigNativeEnable, noOfBigNativeAdsInIntro.
 */
internal object FlatRemote {
    const val SPLASH_DOUBLE = "splashDoubleEnable"
    const val DOUBLE_INTERSTITIAL = "doubleInterstitialAdEnable"
    const val RANDOM_ENABLE = "randomClickInterstitialAd"
    const val RANDOM_INTERVAL = "randomClickAdInterval"
    const val RANDOM_GAP = "randomClickTimeGap"
    const val RANDOM_AUTO = "randomClickAdAutoEnable"
    const val BANNER_TYPE = "bannerAdType"
    const val REWARDED_ENABLE = "rewardedAdEnable"
    const val LANGUAGE_TYPE = "langaugeSelectionNativeType"
    const val LANGUAGE_TYPE_FIXED = "languageSelectionNativeType"

    /** Keys the library reads at call time. */
    const val SPLASH_TYPE = "splashType"
    const val SPLASH_BANNER = "splashBannerAd"
    const val HOME_BANNER_TYPE = "homescrBannerType"
    const val PREMIUM_DISMISS_INTERSTITIAL = "interstitialOnPremiumDismiss"
    const val INTRO_BIG_ENABLE = "introScrBigNativeEnable"
    const val INTRO_BIG_COUNT = "noOfBigNativeAdsInIntro"

    /** "smallMedia", "small-media", "Small Media" -> "small_media". */
    fun normalize(s: String?): String =
        s.orEmpty().trim().replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").replace('-', '_').replace(' ', '_').lowercase()

    /** A banner type by any spelling; "collapsible" alone = COLLAPSIBLE_BOTTOM. Null when unknown. */
    fun bannerType(s: String?): BannerType? {
        val n = normalize(s)
        if (n.isEmpty()) return null
        if (n == "collapsible") return BannerType.COLLAPSIBLE_BOTTOM
        return BannerType.entries.firstOrNull { it.name.equals(n, ignoreCase = true) }
    }

    fun bool(s: String?): Boolean? = when (s?.trim()?.lowercase()) { "true", "1" -> true; "false", "0" -> false; else -> null }

    fun apply(base: AdsConfig, get: (String) -> String?): AdsConfig {
        fun v(k: String): String? = get(k)?.trim()?.takeIf { it.isNotEmpty() }
        var c = base
        fun edit(name: String, f: (PlacementConfig) -> PlacementConfig) {
            val p = c.placements[name] ?: return
            c = c.copy(placements = c.placements + (name to f(p)))
        }
        bool(v(SPLASH_DOUBLE))?.let { b -> edit(Placements.SPLASH) { it.copy(doubleRemote = b) }; edit(Placements.SPLASH_INTERSTITIAL) { it.copy(doubleRemote = b) } }
        bool(v(DOUBLE_INTERSTITIAL))?.let { b -> edit(Placements.RANDOM) { it.copy(doubleRemote = b) } }
        bool(v(RANDOM_ENABLE))?.let { b -> edit(Placements.RANDOM) { it.copy(enabled = b) } }
        v(RANDOM_INTERVAL)?.toDoubleOrNull()?.toInt()?.takeIf { it >= 0 }?.let { n ->
            edit(Placements.RANDOM) { it.copy(everyNScreens = n, everyNCalls = n.coerceAtLeast(1)) }
        }
        v(RANDOM_GAP)?.toDoubleOrNull()?.toInt()?.takeIf { it >= 0 }?.let { n -> edit(Placements.RANDOM) { it.copy(minGapSeconds = n) } }
        bool(v(RANDOM_AUTO))?.let { b -> edit(Placements.RANDOM) { it.copy(autoShow = b) } }
        bannerType(v(BANNER_TYPE))?.let { t -> edit(Placements.BANNER) { it.copy(bannerType = t) } }
        bool(v(REWARDED_ENABLE))?.let { b -> edit(Placements.REWARDED) { it.copy(enabled = b) } }
        (v(LANGUAGE_TYPE_FIXED) ?: v(LANGUAGE_TYPE))?.let { raw ->
            val n = normalize(raw)
            if (n.isNotEmpty() && n.length <= 20) edit(Placements.LANGUAGE_NATIVE) { it.copy(nativeSize = n) }
        }
        return c
    }
}

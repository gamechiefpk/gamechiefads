package com.gcl.gamechiefads

/**
 * A named ad spot. Declare your spots once, in upper case, and pass the constants around instead of strings:
 *
 * ```java
 * public final class AdIds {
 *     public static final AdPlacement BANNER_HOME = AdPlacement.of("banner_home");
 *     public static final AdPlacement AFTER_SAVE  = AdPlacement.of("after_save");
 * }
 * GamechiefAds.loadInterstitialAd(this, AdIds.AFTER_SAVE, null);
 * ```
 */
class AdPlacement private constructor(val name: String) {

    override fun equals(other: Any?) = other is AdPlacement && other.name == name
    override fun hashCode() = name.hashCode()
    override fun toString() = name

    companion object {
        @JvmStatic
        fun of(name: String): AdPlacement {
            require(name.isNotBlank()) { "Placement name must not be blank" }
            return AdPlacement(name)
        }

        @JvmField val RANDOM = AdPlacement(Placements.RANDOM)
        @JvmField val APP_OPEN = AdPlacement(Placements.APP_OPEN)
        @JvmField val SPLASH = AdPlacement(Placements.SPLASH)
        @JvmField val SPLASH_BANNER = AdPlacement(Placements.SPLASH_BANNER)
    }
}

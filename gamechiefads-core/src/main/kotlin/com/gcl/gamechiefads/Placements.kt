package com.gcl.gamechiefads

/**
 * The fixed ad slots of the library. You do not use these names in normal code: you give the ad units in
 * [AdsConfig.Builder] (splashAppOpenUnits, bannerUnits, ...) and call GamechiefAds.loadXxxAd(). Remote Config uses the names.
 */
object Placements {
    /** Splash app-open ad (units with floors). The double version uses its second units. */
    const val SPLASH = "splash"

    /** Splash interstitial ad, for apps that show an interstitial on the splash instead of an app-open ad. */
    const val SPLASH_INTERSTITIAL = "splash_interstitial"

    /** Large banner (320x100) shown on the splash screen. */
    const val SPLASH_BANNER = "splash_banner"

    /** App-open ad when the user returns to the app: ONE unit, no floors. */
    const val APP_OPEN = "app_open"

    /** Banner for all normal screens. */
    const val BANNER = "banner"

    /** Banner for the exit screen or exit dialog. */
    const val EXIT_BANNER = "exit_banner"

    /** Interstitial shown on "random" clicks (paced by gap and every-N-calls). The double version uses its second units. */
    const val RANDOM = "random"

    /** Small native ad at the bottom of the intro screens (units with floors). */
    const val INTRO_NATIVE = "intro_native"

    /** Full-screen native ad that behaves like an interstitial: ONE unit, no floors. */
    const val FULLSCREEN_NATIVE = "fullscreen_native"

    /** Rewarded ad (units with floors). */
    const val REWARDED = "rewarded"

    /** Pool key for the second app-open ad of a double break. Not a configurable placement. */
    internal const val APP_OPEN_SECOND = "app_open_second"
}

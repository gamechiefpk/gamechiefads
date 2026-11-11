package com.gcl.gamechiefads

/** Placement names the library reacts to by itself. Everything else is called by name from the app. */
object Placements {
    /** Interstitial shown automatically by the screen rule (every N screens, min gap, excluded screens). */
    const val RANDOM = "random"

    /** Process-lifecycle app-open ad. */
    const val APP_OPEN = "app_open"

    /** First splash app-open ad. */
    const val SPLASH = "splash"

    /** Optional second splash app-open ad, shown only after the first one closed. */
    const val SPLASH_SECOND = "splash_second"

    /** Pool key for the second app-open ad of a double break. Not a configurable placement. */
    internal const val APP_OPEN_SECOND = "app_open_second"
}

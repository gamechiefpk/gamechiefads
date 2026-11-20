package com.gcl.gamechiefads

/** Banner flavours, chosen per placement (`AdsConfig.Builder.bannerType`) or by Remote Config (`"bannerType"`). */
enum class BannerType {
    /** Anchored adaptive banner (default): full width, a standard height picked by Google for the width (about 50 to 90 dp). Collapsible banners use the same size. */
    ADAPTIVE,

    /** Large anchored adaptive banner: full width and TALLER than [ADAPTIVE] (about 100 to 150 dp), more room for the ad. */
    ADAPTIVE_LARGE,

    /** Inline adaptive: for banners inside scrolling content. Height is decided at load time. */
    INLINE_ADAPTIVE,

    /** Collapsible banner, expands towards the top of the screen. Put it at the bottom of the screen. Google demand only. */
    COLLAPSIBLE_BOTTOM,

    /** Collapsible banner, expands towards the bottom of the screen. Put it at the top of the screen. Google demand only. */
    COLLAPSIBLE_TOP,

    /** 320x50 */
    BANNER,

    /** 320x100 */
    LARGE_BANNER,

    /** 300x250 */
    MEDIUM_RECTANGLE,

    /** 468x60 */
    FULL_BANNER,

    /** 728x90 */
    LEADERBOARD,
}

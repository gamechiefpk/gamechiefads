package com.gcl.gamechiefads

/** Banner flavours, chosen per placement (`AdsConfig.Builder.bannerType`) or by Remote Config (`"bannerType"`). */
enum class BannerType {
    /** Large anchored adaptive banner (default): full width, height picked by Google (50 to 150 dp). */
    ADAPTIVE,

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

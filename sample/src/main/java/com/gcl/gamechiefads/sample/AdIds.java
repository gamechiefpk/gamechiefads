package com.gcl.gamechiefads.sample;

import com.gcl.gamechiefads.AdPlacement;

/** All ad spots of the app in one place, upper case. Pass these constants instead of strings. */
final class AdIds {
    private AdIds() {
    }

    // interstitial
    static final AdPlacement MANUAL = AdPlacement.of("manual");
    static final AdPlacement PRELOADED = AdPlacement.of("preloaded");
    static final AdPlacement DOUBLE_INTERSTITIAL = AdPlacement.of("double_interstitial");
    static final AdPlacement FLOORS = AdPlacement.of("floors");

    // banner (one placement per banner type)
    static final AdPlacement BANNER_ADAPTIVE = AdPlacement.of("banner_adaptive");
    static final AdPlacement BANNER_COLLAPSIBLE = AdPlacement.of("banner_collapsible");
    static final AdPlacement BANNER_RECT = AdPlacement.of("banner_rect");
    static final AdPlacement BANNER_LARGE = AdPlacement.of("banner_large");
    static final AdPlacement BANNER_STANDARD = AdPlacement.of("banner_standard");
    static final AdPlacement BANNER_INLINE = AdPlacement.of("banner_inline");

    // native
    static final AdPlacement NATIVE_MAIN = AdPlacement.of("native_main");
    static final AdPlacement NATIVE_FULLSCREEN = AdPlacement.of("native_fullscreen");
}

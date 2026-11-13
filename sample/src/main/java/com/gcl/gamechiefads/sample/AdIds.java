package com.gcl.gamechiefads.sample;

import com.gcl.gamechiefads.AdPlacement;

/** Only for EXTRA custom spots. The standard slots (splash, banner, random...) need no constants. */
final class AdIds {
    private AdIds() {
    }

    static final AdPlacement MANUAL = AdPlacement.of("manual");
    static final AdPlacement PRELOADED = AdPlacement.of("preloaded");
    static final AdPlacement NATIVE_MAIN = AdPlacement.of("native_main");
}

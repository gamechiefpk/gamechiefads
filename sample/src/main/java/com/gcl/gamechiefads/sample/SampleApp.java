package com.gcl.gamechiefads.sample;

import android.app.Application;

import com.gcl.gamechiefads.AdPlacement;
import com.gcl.gamechiefads.AdsConfig;
import com.gcl.gamechiefads.BannerType;
import com.gcl.gamechiefads.GamechiefAds;
import com.gcl.gamechiefads.Placements;
import com.gcl.gamechiefads.ui.DefaultLoaderDialog;

public class SampleApp extends Application {
    /** An id that never fills: stands in for a high floor that misses. */
    static final String NO_FILL_UNIT = "ca-app-pub-3940256099942544/0000000000";

    @Override
    public void onCreate() {
        super.onCreate();

        // An id that never fills: stands in for a "high floor" that misses, to show the floor controls.
        final String HIGH_FLOOR_NO_FILL = NO_FILL_UNIT;

        // 3000 ms per tier is fine on real devices; the emulator is slow, so the sample uses 10000
        AdsConfig config = AdsConfig.builder()
                .appId(getString(R.string.admob_app_id))
                .testIdsInDebug(false) // lets the FLOORS demo use its own ids in debug (all ids here are Google test ids)

                // shown automatically every 2 screens, at most every 15 s, never on the splash, never on back presses.
                .interstitial(Placements.RANDOM)
                .unitsRes(this, R.string.unit_interstitial).tierTimeoutMs(10000)
                .everyNScreens(2).minGapSeconds(15)
                .excludeScreens(SplashActivity.class)

                // called by name from the app. loaderText = personalised text of the loading overlay.
                .interstitial(AdIds.MANUAL).unitsRes(this, R.string.unit_interstitial).tierTimeoutMs(10000)
                .loaderText("Ad is loading", "Please wait, it will open in a moment")
                .interstitial(AdIds.PRELOADED).unitsRes(this, R.string.unit_interstitial).tierTimeoutMs(10000)
                // two ads back to back, like one ad break (also switchable by Remote Config: "double": true)
                .interstitial(AdIds.DOUBLE_INTERSTITIAL).unitsRes(this, R.string.unit_interstitial).tierTimeoutMs(10000)
                .doubleAd(true)
                // floors: HF never fills, gets a short own timeout and is skipped after 2 misses for 60 s
                .interstitial(AdIds.FLOORS)
                .units(HIGH_FLOOR_NO_FILL, getString(R.string.unit_interstitial), getString(R.string.unit_interstitial))
                .tierTimeoutMs(10000).unitTimeoutMs(HIGH_FLOOR_NO_FILL, 1500).skipUnitAfterFails(2, 60)

                // splash: an app-open ad by default (use .interstitial(Placements.SPLASH) for an interstitial instead).
                // Floors: the first unit never fills, so flooring ON shows a miss before the hit; flooring OFF asks only the last unit.
                .appOpen(Placements.SPLASH)
                .units(HIGH_FLOOR_NO_FILL, getString(R.string.unit_app_open), getString(R.string.unit_app_open))
                .tierTimeoutMs(10000).unitTimeoutMs(HIGH_FLOOR_NO_FILL, 1500)
                .flooring(true).doubleAd(true)
                // large banner shown on the splash screen with GamechiefAds.bannerForSplash / AdsUi.bannerForSplash
                .banner(AdPlacement.SPLASH_BANNER).unitsRes(this, R.string.unit_banner).tierTimeoutMs(10000)
                .appOpen(Placements.APP_OPEN).unitsRes(this, R.string.unit_app_open).tierTimeoutMs(10000)
                .minGapSeconds(10).excludeScreens(SplashActivity.class)

                // one banner placement per banner type
                .banner(AdIds.BANNER_ADAPTIVE).unitsRes(this, R.string.unit_banner).tierTimeoutMs(10000)
                .bannerType(BannerType.ADAPTIVE)
                .banner(AdIds.BANNER_COLLAPSIBLE).unitsRes(this, R.string.unit_banner).tierTimeoutMs(10000)
                .bannerType(BannerType.COLLAPSIBLE_BOTTOM)
                .banner(AdIds.BANNER_RECT).unitsRes(this, R.string.unit_banner).tierTimeoutMs(10000)
                .bannerType(BannerType.MEDIUM_RECTANGLE)
                .banner(AdIds.BANNER_LARGE).unitsRes(this, R.string.unit_banner).tierTimeoutMs(10000)
                .bannerType(BannerType.LARGE_BANNER)
                .banner(AdIds.BANNER_STANDARD).unitsRes(this, R.string.unit_banner).tierTimeoutMs(10000)
                .bannerType(BannerType.BANNER)
                .banner(AdIds.BANNER_INLINE).unitsRes(this, R.string.unit_banner).tierTimeoutMs(10000)
                .bannerType(BannerType.INLINE_ADAPTIVE)

                .rewarded(AdIds.REWARDED).unitsRes(this, R.string.unit_rewarded).tierTimeoutMs(10000)
                .nativeAd(AdIds.NATIVE_MAIN).unitsRes(this, R.string.unit_native).tierTimeoutMs(10000)
                .nativeAd(AdIds.NATIVE_FULLSCREEN).unitsRes(this, R.string.unit_native).tierTimeoutMs(10000)
                .initTimeoutMs(15000)      // emulators can need 10 s+ for the first SDK init; phones need ~1-2 s
                .splashTimeoutMs(25000)
                .overallBudgetMs(15000)
                .build();

        GamechiefAds.init(this, config);
        GamechiefAds.setLoaderUi(DefaultLoaderDialog.builder()
                .title("Ad is loading")
                .message("Please wait…")
                .tips("Tip: ads help keep this app free", "Tip: premium removes all ads")
                .build());
    }
}

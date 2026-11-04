package com.gcl.gamechiefads.sample;

import android.app.Application;

import com.gcl.gamechiefads.AdsConfig;
import com.gcl.gamechiefads.GamechiefAds;
import com.gcl.gamechiefads.Placements;
import com.gcl.gamechiefads.ui.DefaultLoaderDialog;

public class SampleApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // 3000 ms per tier is fine on real devices; the emulator is slow, so the sample uses 6000
        AdsConfig config = AdsConfig.builder()
                .appId(getString(R.string.admob_app_id))

                // shown automatically every 2 screens, at most every 15 s, never on the splash. No call in activities.
                .interstitial(Placements.RANDOM)
                .unitsRes(this, R.string.unit_interstitial).tierTimeoutMs(6000)
                .everyNScreens(2).minGapSeconds(15)
                .excludeScreens(SplashActivity.class)

                // called by name from the app
                .interstitial("manual").unitsRes(this, R.string.unit_interstitial).tierTimeoutMs(6000)
                .interstitial("preloaded").unitsRes(this, R.string.unit_interstitial).tierTimeoutMs(6000)

                .appOpen(Placements.SPLASH).unitsRes(this, R.string.unit_app_open).tierTimeoutMs(6000)
                .appOpen(Placements.SPLASH_SECOND).unitsRes(this, R.string.unit_app_open).tierTimeoutMs(6000)
                .appOpen(Placements.APP_OPEN).unitsRes(this, R.string.unit_app_open).tierTimeoutMs(6000)
                .minGapSeconds(10).excludeScreens(SplashActivity.class)

                .banner("banner_main").unitsRes(this, R.string.unit_banner).tierTimeoutMs(6000)
                .nativeAd("native_main").unitsRes(this, R.string.unit_native).tierTimeoutMs(6000)
                .splashTimeoutMs(15000)
                .overallBudgetMs(10000)
                .build();

        GamechiefAds.init(this, config);
        GamechiefAds.setLoaderUi(new DefaultLoaderDialog());
    }
}

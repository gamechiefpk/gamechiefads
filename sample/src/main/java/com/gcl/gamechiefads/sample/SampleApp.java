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

        AdsConfig config = AdsConfig.builder()
                .appId(getString(R.string.admob_app_id))

                // shown automatically every 2 screens, at most every 15 s, never on the splash. No call in activities.
                .interstitial(Placements.RANDOM)
                .unitsRes(this, R.string.unit_interstitial)
                .everyNScreens(2).minGapSeconds(15)
                .excludeScreens(SplashActivity.class)

                // called by name from the app
                .interstitial("manual").unitsRes(this, R.string.unit_interstitial)
                .interstitial("preloaded").unitsRes(this, R.string.unit_interstitial)

                .appOpen(Placements.SPLASH).unitsRes(this, R.string.unit_app_open).tierTimeoutMs(3000)
                .appOpen(Placements.SPLASH_SECOND).unitsRes(this, R.string.unit_app_open)
                .appOpen(Placements.APP_OPEN).unitsRes(this, R.string.unit_app_open)
                .minGapSeconds(10).excludeScreens(SplashActivity.class)

                .banner("banner_main").unitsRes(this, R.string.unit_banner)
                .nativeAd("native_main").unitsRes(this, R.string.unit_native)
                .build();

        GamechiefAds.init(this, config);
        GamechiefAds.setLoaderUi(new DefaultLoaderDialog());
    }
}

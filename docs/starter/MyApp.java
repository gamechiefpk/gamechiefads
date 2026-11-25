package com.yourcompany.yourapp;

import android.app.Application;

import com.gcl.gamechiefads.AdsConfig;
import com.gcl.gamechiefads.GamechiefAds;

/** Step 1: ALL your ad units, in one place. Delete the lines of ads you do not use. Debug builds use Google test ads by themselves. */
public class MyApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        GamechiefAds.init(this, AdsConfig.builder()
                .appId("ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY")          // your AdMob app id (the same as in AndroidManifest.xml)

                // splash: several ids = floors (the first is asked first). The "double" ad is OFF until Firebase turns it on.
                .splashBannerUnit("ca-app-pub-XXXXXXXXXXXXXXXX/1111111111")
                .splashAppOpenUnits("HIGH_FLOOR_ID", "MEDIUM_FLOOR_ID", "NORMAL_ID")
                .splashAppOpenDoubleUnit("SECOND_AD_ID")                   // only used when the double switch is on in Firebase

                .appOpenUnit("APP_OPEN_ID")                                // when the user comes back to the app (automatic)

                // natives
                .languageNativeUnits("HIGH_FLOOR_ID", "NORMAL_ID")         // language screen (small media)
                .introNativeUnits("HIGH_FLOOR_ID", "MEDIUM_FLOOR_ID", "ALL_ID")   // small native on each intro slide (small media), WITH floors
                .introSlideNativeUnit("INTRO_BIG_ID")                      // big (full-screen) native pages between the slides: ONE unit, NO floors; off until Firebase noOfBigNativeAdInIntro > 0

                // banners
                .bannerUnits("HIGH_FLOOR_ID", "MEDIUM_FLOOR_ID", "NORMAL_ID")   // every screen
                .exitBannerUnits("EXIT_BANNER_ID")                         // exit dialog

                // interstitial: automatic over every 3rd new screen, and on your buttons
                .randomInterstitialUnits("HIGH_FLOOR_ID", "MEDIUM_FLOOR_ID", "NORMAL_ID")
                .randomInterstitialDoubleUnit("SECOND_AD_ID")              // only used when the double switch is on in Firebase

                .rewardedUnits("HIGH_FLOOR_ID", "NORMAL_ID")
                .build());

        // Premium users see no ads: call it from your billing code
        // GamechiefAds.setPremium(isPremium);
    }
}

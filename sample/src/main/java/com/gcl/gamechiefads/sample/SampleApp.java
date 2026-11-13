package com.gcl.gamechiefads.sample;

import android.app.Application;

import com.gcl.gamechiefads.AdsConfig;
import com.gcl.gamechiefads.GamechiefAds;
import com.gcl.gamechiefads.ui.DefaultLoaderDialog;

/**
 * ALL ad units of the app, in one place. Here every id is a Google TEST id; in your app use your own ids.
 * "Units" with several ids are floors: the first id is tried first (high floor), then the next, then the next.
 */
public class SampleApp extends Application {
    /** An id that never fills: stands in for a high floor that misses, so you can see the floors in the trace. */
    static final String NO_FILL_UNIT = "ca-app-pub-3940256099942544/0000000000";

    @Override
    public void onCreate() {
        super.onCreate();

        final String interstitial = getString(R.string.unit_interstitial);
        final String appOpen = getString(R.string.unit_app_open);
        final String banner = getString(R.string.unit_banner);
        final String nativeUnit = getString(R.string.unit_native);

        // 3000 ms per unit is fine on real devices; an emulator is slow, so the sample uses 10000.
        AdsConfig config = AdsConfig.builder()
                .appId(getString(R.string.admob_app_id))
                .testIdsInDebug(false) // lets this sample show its own (test) ids and the floors, even in a debug build

                // ---- splash ----
                .splashAppOpenUnits(NO_FILL_UNIT, appOpen, appOpen).tierTimeoutMs(10000).unitTimeoutMs(NO_FILL_UNIT, 1500)
                .splashAppOpenDoubleUnit(appOpen)                     // the SECOND ad of a double: ONE unit, no floors
                .splashInterstitialUnits(NO_FILL_UNIT, interstitial, interstitial).tierTimeoutMs(10000).unitTimeoutMs(NO_FILL_UNIT, 1500)
                .splashInterstitialDoubleUnit(interstitial)
                .splashBannerUnits(banner).tierTimeoutMs(10000)           // always a 320x100 large banner

                // ---- app open when the user returns to the app: ONE unit, no floors ----
                .appOpenUnit(appOpen).minGapSeconds(10).excludeScreens(SplashActivity.class)

                // ---- banners ----
                .bannerUnits(banner).tierTimeoutMs(10000)                  // all normal screens
                .exitBannerUnits(banner).tierTimeoutMs(10000)              // exit screen (a medium rectangle by default)

                // ---- interstitial on random clicks (with floors), double has its own units ----
                .randomInterstitialUnits(NO_FILL_UNIT, interstitial, interstitial).tierTimeoutMs(10000).unitTimeoutMs(NO_FILL_UNIT, 1500)
                    .minGapSeconds(15).everyNCalls(2)
                .randomInterstitialDoubleUnit(interstitial)

                // ---- native ----
                .introNativeUnits(NO_FILL_UNIT, nativeUnit, nativeUnit).tierTimeoutMs(10000).unitTimeoutMs(NO_FILL_UNIT, 1500) // small, floors
                .fullScreenNativeUnit(nativeUnit)                          // one unit, no floors

                // ---- rewarded ----
                .rewardedUnits(getString(R.string.unit_rewarded)).tierTimeoutMs(10000)

                // ---- optional extra spots you name yourself (here: the demo screens) ----
                .interstitial(AdIds.MANUAL).units(interstitial).tierTimeoutMs(10000)
                .interstitial(AdIds.PRELOADED).units(interstitial).tierTimeoutMs(10000)
                .nativeAd(AdIds.NATIVE_MAIN).units(nativeUnit).tierTimeoutMs(10000)

                .initTimeoutMs(15000)      // emulators can need 10 s+ for the first SDK init; phones need ~1-2 s
                .splashTimeoutMs(25000)
                .overallBudgetMs(15000)
                .build();

        GamechiefAds.init(this, config);

        // Optional: the "Ad is loading, please wait" dialog is built in. This only changes its look and tips.
        GamechiefAds.setLoadingDialog(DefaultLoaderDialog.builder()
                .title("Ad is loading")
                .message("Please wait…")
                .tips("Tip: ads help keep this app free", "Tip: premium removes all ads")
                .build());
    }
}

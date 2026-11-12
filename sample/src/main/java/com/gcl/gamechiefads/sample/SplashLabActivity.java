package com.gcl.gamechiefads.sample;

import android.widget.Button;

import com.gcl.gamechiefads.BannerListener;
import com.gcl.gamechiefads.GamechiefAds;
import com.gcl.gamechiefads.SplashAds;
import com.gcl.gamechiefads.ui.AdsUi;

/**
 * Splash lab: choose the splash ad type (app open or interstitial), flooring on/off and double on/off, then run
 * prepareSplash right here (no need to restart the app). The choice is applied like Remote Config would apply it.
 * Floors: the first unit never fills. Flooring ON = you see it miss, then the next unit hit. OFF = one request.
 */
public class SplashLabActivity extends DemoActivity {
    private boolean interstitial;
    private boolean flooring = true;
    private boolean doubleAd = true;
    private boolean running;
    private Button typeBtn;
    private Button flooringBtn;
    private Button doubleBtn;

    @Override
    protected String screenTitle() {
        return "Splash lab";
    }

    @Override
    protected void build() {
        note("Pick the options, then run. The first floor never fills (see the log), the splash banner is a LARGE_BANNER.");
        typeBtn = button("", () -> {
            interstitial = !interstitial;
            refresh();
        });
        flooringBtn = button("", () -> {
            flooring = !flooring;
            refresh();
        });
        doubleBtn = button("", () -> {
            doubleAd = !doubleAd;
            refresh();
        });
        button("Run splash", this::run);
        button("Splash banner only (bannerForSplash)", this::banner);
        refresh();
    }

    private void refresh() {
        typeBtn.setText("Splash ad: " + (interstitial ? "INTERSTITIAL" : "APP OPEN") + " (tap to switch)");
        flooringBtn.setText("Flooring: " + (flooring ? "ON (HF -> MF -> normal)" : "OFF (one request, lowest floor)"));
        doubleBtn.setText("Double ad: " + (doubleAd ? "ON (two, back to back)" : "OFF (one)"));
    }

    private void banner() {
        bottomBar().removeAllViews();
        android.widget.FrameLayout f = new android.widget.FrameLayout(this);
        bottomBar().addView(f, new android.widget.FrameLayout.LayoutParams(-1, -2));
        say("splash banner: loading");
        AdsUi.bannerForSplash(f, new BannerListener() {
            @Override
            public void onLoaded() {
                say("splash banner: loaded");
            }

            @Override
            public void onFailed(String reason) {
                say("splash banner: failed (" + reason + ")");
            }
        });
    }

    private void run() {
        if (running) {
            say("splash already running");
            return;
        }
        running = true;
        String units = interstitial
                ? "[\"" + SampleApp.NO_FILL_UNIT + "\",\"" + getString(R.string.unit_interstitial) + "\",\"" + getString(R.string.unit_interstitial) + "\"]"
                : "[\"" + SampleApp.NO_FILL_UNIT + "\",\"" + getString(R.string.unit_app_open) + "\",\"" + getString(R.string.unit_app_open) + "\"]";
        String json = "{\"placements\":{\"splash\":{\"type\":\"" + (interstitial ? "interstitial" : "app_open")
                + "\",\"units\":" + units + ",\"flooring\":" + flooring + ",\"double\":" + doubleAd + "}}}";
        GamechiefAds.applyRemoteOverrides(json);
        say("splash: " + (interstitial ? "interstitial" : "app open") + ", flooring=" + flooring + ", double=" + doubleAd);
        banner();
        long t0 = System.currentTimeMillis();
        GamechiefAds.prepareSplash(this, (SplashAds ads) -> {
            say("splash ready in " + (System.currentTimeMillis() - t0) + " ms, hasAd=" + ads.hasAd());
            if (!ads.hasAd()) {
                running = false;
                return;
            }
            ads.show(this, () -> {
                say("splash finished: onDone ran (navigate here, once)");
                running = false;
            });
        });
    }
}

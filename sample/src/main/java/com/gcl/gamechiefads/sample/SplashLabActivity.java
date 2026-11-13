package com.gcl.gamechiefads.sample;

import android.widget.Button;
import android.widget.FrameLayout;

import com.gcl.gamechiefads.BannerListener;
import com.gcl.gamechiefads.GamechiefAds;
import com.gcl.gamechiefads.SplashAds;
import com.gcl.gamechiefads.SplashCallback;

/**
 * Splash lab: pick the splash ad type (app open or interstitial), flooring on/off and single/double, then run it right
 * here. Each choice is just a different method: loadSplashAppOpenAd, loadSplashAppOpenDoubleAd,
 * loadSplashInterstitialAd, loadSplashInterstitialDoubleAd. Flooring is a Remote Config switch on the slot.
 * The first floor never fills: flooring ON shows it miss, then the next unit hit; OFF = one request to the last unit.
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
        note("Pick the options, then run. The splash banner is a LARGE_BANNER.");
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
        button("loadSplashBannerAd() only", this::banner);
        refresh();
    }

    private void refresh() {
        typeBtn.setText("Splash ad: " + (interstitial ? "INTERSTITIAL" : "APP OPEN") + " (tap to switch)");
        flooringBtn.setText("Flooring: " + (flooring ? "ON (HF -> MF -> normal)" : "OFF (one request, lowest floor)"));
        doubleBtn.setText("Double ad: " + (doubleAd ? "ON (two, together)" : "OFF (one)"));
    }

    private void banner() {
        bottomBar().removeAllViews();
        FrameLayout f = new FrameLayout(this);
        bottomBar().addView(f, new FrameLayout.LayoutParams(-1, -2));
        say("splash banner: loading");
        GamechiefAds.loadSplashBannerAd(f, new BannerListener() {
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
        String slot = interstitial ? "splash_interstitial" : "splash";
        GamechiefAds.applyRemoteConfig("{\"placements\":{\"" + slot + "\":{\"flooring\":" + flooring + "}}}");
        say((interstitial ? "interstitial" : "app open") + ", flooring=" + flooring + ", double=" + doubleAd);
        banner();
        long t0 = System.currentTimeMillis();
        SplashCallback callback = (SplashAds ads) -> {
            say("splash ready in " + (System.currentTimeMillis() - t0) + " ms, hasAd=" + ads.hasAd());
            if (!ads.hasAd()) {
                running = false;
                return;
            }
            ads.show(this, () -> {
                say("splash finished: onDone ran (open your next screen here, once)");
                running = false;
            });
        };
        if (interstitial) {
            if (doubleAd) GamechiefAds.loadSplashInterstitialDoubleAd(this, callback);
            else GamechiefAds.loadSplashInterstitialAd(this, callback);
        } else {
            if (doubleAd) GamechiefAds.loadSplashAppOpenDoubleAd(this, callback);
            else GamechiefAds.loadSplashAppOpenAd(this, callback);
        }
    }
}

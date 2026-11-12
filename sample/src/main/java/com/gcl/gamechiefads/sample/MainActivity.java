package com.gcl.gamechiefads.sample;

import android.content.Intent;
import android.widget.Button;

import com.gcl.gamechiefads.GamechiefAds;

/** Hub: one button per ad type, plus premium and remote-override switches. */
public class MainActivity extends DemoActivity {
    private boolean premium;

    @Override
    protected String screenTitle() {
        return "GamechiefAds demo";
    }

    @Override
    protected void build() {
        note("Google test ads only. Open a screen per ad type. The dark box below is the event log of every screen.");

        button("Interstitials: on-demand, preload, double tap", () -> open(InterstitialActivity.class));
        button("Banner", () -> open(BannerActivity.class));
        button("Native: all 6 sizes + style", () -> open(NativeActivity.class));
        button("App-open (leave and return)", () -> open(AppOpenActivity.class));
        button("Random interstitial (screen counter)", () -> open(SecondActivity.class));
        button("Library trace (logcat GamechiefAds)", () -> open(TraceActivity.class));
        button("Splash lab: app open / interstitial, flooring, double", () -> open(SplashLabActivity.class));
        button("Run the splash again", () -> open(SplashActivity.class));

        note("Switches");
        Button p = button("Premium: OFF (tap to turn on)", () -> { });
        p.setOnClickListener(v -> {
            premium = !premium;
            GamechiefAds.setPremium(premium);
            p.setText("Premium: " + (premium ? "ON, no ads at all" : "OFF") + " (tap to toggle)");
            say("premium=" + premium);
        });

        button("Remote override: random every screen, no gap", () -> {
            GamechiefAds.applyRemoteOverrides("{\"placements\":{\"random\":{\"every\":1,\"gap\":0}}}");
            say("remote: random every=1 gap=0");
        });
        button("Remote override: random every 3 screens, gap 30 s", () -> {
            GamechiefAds.applyRemoteOverrides("{\"placements\":{\"random\":{\"every\":3,\"gap\":30}}}");
            say("remote: random every=3 gap=30");
        });
        button("Remote override: double ON (manual + app-open)", () -> {
            GamechiefAds.applyRemoteOverrides("{\"placements\":{\"manual\":{\"double\":true},\"app_open\":{\"double\":true}}}");
            say("remote: manual + app_open double=true");
        });
        button("Remote override: random DOUBLE, every screen, no gap", () -> {
            GamechiefAds.applyRemoteOverrides("{\"placements\":{\"random\":{\"every\":1,\"gap\":0,\"double\":true}}}");
            say("remote: random double=true every=1 gap=0 (open Second screen)");
        });
        button("Remote override: floors run in PARALLEL", () -> {
            GamechiefAds.applyRemoteOverrides("{\"placements\":{\"floors\":{\"waterfall\":\"parallel\"}}}");
            say("remote: floors waterfall=parallel");
        });
        button("Remote override: banners as medium rectangle", () -> {
            GamechiefAds.applyRemoteOverrides("{\"placements\":{\"banner_adaptive\":{\"bannerType\":\"MEDIUM_RECTANGLE\"}}}");
            say("remote: banner_adaptive -> MEDIUM_RECTANGLE (open Banners again)");
        });
        button("Remote override: ALL ads off", () -> {
            GamechiefAds.applyRemoteOverrides("{\"enabled\":false}");
            say("remote: enabled=false");
        });
        button("Remote override: back to app defaults", () -> {
            GamechiefAds.applyRemoteOverrides(null);
            say("remote: cleared");
        });
        button("Clear log", () -> {
            DemoLog.clear();
            recreate();
        });
    }

    private void open(Class<?> c) {
        startActivity(new Intent(this, c));
    }
}

package com.gcl.gamechiefads.sample;

import android.content.Intent;
import android.widget.Button;

import com.gcl.gamechiefads.GamechiefAds;

/** Hub: one button per ad type, plus premium and Remote Config switches. */
public class MainActivity extends DemoActivity {
    private boolean premium;

    @Override
    protected String screenTitle() {
        return "GamechiefAds demo";
    }

    @Override
    protected void build() {
        note("Google test ads only. Open a screen per ad type. The dark box below is the event log of every screen.");

        button("Splash lab: app open / interstitial, flooring, double", () -> open(SplashLabActivity.class));
        button("Random click interstitial (single and double)", () -> open(SecondActivity.class));
        button("Interstitials: random, custom spot, preload", () -> open(InterstitialActivity.class));
        button("Rewarded ad (coins)", () -> open(RewardedActivity.class));
        button("Remote Config: your own values", () -> open(RemoteConfigActivity.class));
        button("Banners: ready-made and custom", () -> open(BannerActivity.class));
        button("Native: intro, full screen, 6 sizes", () -> open(NativeActivity.class));
        button("App-open (leave and return)", () -> open(AppOpenActivity.class));
        button("Library trace (logcat GamechiefAds)", () -> open(TraceActivity.class));
        button("Run the real splash again", () -> open(SplashActivity.class));

        note("Switches");
        Button p = button("Premium: OFF (tap to turn on)", () -> { });
        p.setOnClickListener(v -> {
            premium = !premium;
            GamechiefAds.setPremium(premium);
            p.setText("Premium: " + (premium ? "ON, no ads at all" : "OFF") + " (tap to toggle)");
            say("premium=" + premium);
        });

        note("Remote Config (what you would read from Firebase and pass to applyRemoteConfig)");
        button("Random: every call, no gap", () -> {
            GamechiefAds.applyRemoteConfig("{\"placements\":{\"random\":{\"everyNCalls\":1,\"gap\":0}}}");
            say("remote: random everyNCalls=1 gap=0");
        });
        button("Random: every 3rd call, gap 30 s", () -> {
            GamechiefAds.applyRemoteConfig("{\"placements\":{\"random\":{\"everyNCalls\":3,\"gap\":30}}}");
            say("remote: random everyNCalls=3 gap=30");
        });
        button("Random: floors in PARALLEL", () -> {
            GamechiefAds.applyRemoteConfig("{\"placements\":{\"random\":{\"waterfall\":\"parallel\"}}}");
            say("remote: random waterfall=parallel");
        });
        button("Banner slot as medium rectangle", () -> {
            GamechiefAds.applyRemoteConfig("{\"placements\":{\"banner\":{\"bannerType\":\"MEDIUM_RECTANGLE\"}}}");
            say("remote: banner -> MEDIUM_RECTANGLE (open Banners again)");
        });
        button("Loading text from Remote Config", () -> {
            GamechiefAds.applyRemoteConfig("{\"loaderTitle\":\"Loading ad\",\"loaderMessage\":\"Almost there, please wait\"}");
            say("remote: loader text changed");
        });
        button("ALL ads off", () -> {
            GamechiefAds.applyRemoteConfig("{\"enabled\":false}");
            say("remote: enabled=false");
        });
        button("Back to app defaults", () -> {
            GamechiefAds.applyRemoteConfig(null);
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

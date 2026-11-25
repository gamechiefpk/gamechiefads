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
        button("Banners from layout tags (no ad code)", () -> open(TagBannerActivity.class));
        button("RecyclerView: ONE native ad, Firebase switch (NativeAdAdapter)", () -> open(ScrollAdapterActivity.class));
        button("RecyclerView: several native ads (NativeListAds)", () -> open(RecyclerNativeActivity.class));
        button("Native: intro, full screen, 6 sizes", () -> open(NativeActivity.class));
        button("App-open (leave and return)", () -> open(AppOpenActivity.class));
        button("Intro slider (IntroPager) with Firebase count = 0", () -> introWithAds(0));
        button("Intro slider with Firebase count = 1", () -> introWithAds(1));
        button("Intro slider with Firebase count = 2", () -> introWithAds(2));
        button("Intro slider: ads that NEVER fill (must stay clean)", () -> introNoFill());
        button("Firebase: debugShakeEnabled = FALSE (shake does nothing)", () -> {
            DemoRemoteSource.INSTANCE.publish("debugShakeEnabled", false);
            say("Firebase debugShakeEnabled = false: shaking the phone no longer opens the panel");
        });
        button("Firebase: debugShakeEnabled = TRUE (shake opens the panel)", () -> {
            DemoRemoteSource.INSTANCE.publish("debugShakeEnabled", true);
            say("Firebase debugShakeEnabled = true: shake the phone to open the debug panel");
        });
        button("DEBUG PANEL (bottom sheet, debug builds only)", () -> GamechiefAds.showDebugPanel(this));
        button("QA screen (debug builds only)", () -> GamechiefAds.openQaScreen(this));
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

        note("Firebase simple keys (flat values, like in the console)");
        flat("splashDoubleEnable", true); flat("splashDoubleEnable", false);
        flat("doubleInterstitialAdEnable", true); flat("doubleInterstitialAdEnable", false);
        flat("randomClickInterstitialAd", false); flat("randomClickInterstitialAd", true);
        flat("randomClickAdInterval", 1L); flat("randomClickTimeGap", 0L);
        flat("rewardedAdEnable", false); flat("rewardedAdEnable", true);
        flat("bannerAdType", "collapsible"); flat("bannerAdType", "adaptive");
        flat("langaugeSelectionNativeType", "smallMedia"); flat("splashType", "interstitial");
        flat("splashBannerAd", false); flat("interstitialOnPremiumDismiss", true);
        button("loadSplashAd (follows Firebase splashType)", () -> GamechiefAds.loadSplashAd(this, new com.gcl.gamechiefads.SplashCallback() {
            @Override public void onReady(com.gcl.gamechiefads.SplashAds ads) { say("splash ad ready: " + ads.hasAd()); ads.show(MainActivity.this, () -> say("splash done")); }
        }));
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

    /** Both intro ad units never fill: the slider must show only the slides, no placeholder, no empty box, no stuck page. */
    private void introNoFill() {
        String bad = "ca-app-pub-3940256099942544/0000000000";
        GamechiefAds.applyRemoteConfig("{\"placements\":{\"intro_native\":{\"units\":[\"" + bad + "\"]},\"intro_slide_native\":{\"units\":[\"" + bad + "\"]}}}");
        say("intro ad units set to a unit that never fills (use \"Back to app defaults\" to undo)");
        introWithAds(2);
    }

    /** Pretends you set the Firebase values introScrBigNativeEnable and noOfBigNativeAdsInIntro, then opens the intro slider. */
    private void introWithAds(long count) {
        DemoRemoteSource.INSTANCE.publish("introScrBigNativeEnable", count > 0);
        DemoRemoteSource.INSTANCE.publish("noOfBigNativeAdsInIntro", count);
        say("Firebase introScrBigNativeEnable = " + (count > 0) + ", noOfBigNativeAdsInIntro = " + count);
        open(IntroPagerActivity.class);
    }

    private void flat(String key, Object value) {
        button("Firebase " + key + " = " + value, () -> {
            DemoRemoteSource.INSTANCE.publish(key, value);
            say("Firebase " + key + " = " + value);
        });
    }

    private void open(Class<?> c) {
        startActivity(new Intent(this, c));
    }
}

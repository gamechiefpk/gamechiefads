package com.gcl.gamechiefads.sample;

import com.gcl.gamechiefads.GamechiefAds;

public class AppOpenActivity extends DemoActivity {
    @Override
    protected String screenTitle() {
        return "App-open";
    }

    @Override
    protected void build() {
        note("1. Wait a few seconds so one app-open ad is fetched.\n"
                + "2. Press Home, then open the app again: the ad shows (cooldown 10 s, never on the splash).\n"
                + "3. Tap the button below, press Home and return: that one return is skipped.");
        button("Firebase: app_open double = TRUE (two ads on return)", () -> {
            GamechiefAds.applyRemoteConfig("{\"placements\":{\"app_open\":{\"double\":true}}}");
            say("rules: app_open double = true. Wait for the fetch, Home, come back: two ads");
        });
        button("Firebase: app_open double = FALSE", () -> {
            GamechiefAds.applyRemoteConfig("{\"placements\":{\"app_open\":{\"double\":false}}}");
            say("rules: app_open double = false: a single ad on return");
        });
        button("Skip the next return (skipNextAppOpenAd)", () -> {
            GamechiefAds.skipNextAppOpenAd();
            say("app-open paused once: the next return shows no ad");
        });
    }
}

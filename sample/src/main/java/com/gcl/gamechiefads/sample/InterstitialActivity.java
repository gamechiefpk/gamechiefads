package com.gcl.gamechiefads.sample;

import com.gcl.gamechiefads.GamechiefAds;

public class InterstitialActivity extends DemoActivity {
    @Override
    protected String screenTitle() {
        return "Interstitials";
    }

    @Override
    protected void build() {
        note("On-demand (default): loader overlay, waterfall, load, then show.");
        button("Show (on-demand)", () -> GamechiefAds.showInterstitial(this, "manual", listener("on-demand")));
        button("Show with a null listener (must not crash)", () -> {
            GamechiefAds.showInterstitial(this, "manual", null);
            say("null-listener call made");
        });
        button("Double tap test: two calls at once", () -> {
            say("calling twice; the second must be refused (DENY_AD_SHOWING)");
            GamechiefAds.showInterstitial(this, "manual", listener("call 1"));
            GamechiefAds.showInterstitial(this, "manual", listener("call 2"));
        });

        note("Preloaded (opt-in): nothing is requested until you ask, and nothing refills by itself.");
        button("1. Preload", () -> {
            GamechiefAds.preloadInterstitial("preloaded");
            say("preload requested");
        });
        button("2. Is ready?", () -> say("ready=" + GamechiefAds.isInterstitialReady("preloaded")));
        button("3. Show preloaded", () -> {
            boolean started = GamechiefAds.showPreloadedInterstitial(this, "preloaded", listener("preloaded"));
            say("showPreloaded started=" + started + (started ? "" : " (nothing ready)"));
        });
    }
}

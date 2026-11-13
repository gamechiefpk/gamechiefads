package com.gcl.gamechiefads.sample;

import com.gcl.gamechiefads.GamechiefAds;

public class InterstitialActivity extends DemoActivity {
    @Override
    protected String screenTitle() {
        return "Interstitials";
    }

    @Override
    protected void build() {
        note("Random click interstitial: call it on any click. The library decides (min gap 15 s, every 2nd call). "
                + "When due, the \"Ad is loading, please wait\" dialog shows while the ad loads. The first floor never fills.");
        button("loadRandomInterstitialAd()", () ->
                GamechiefAds.loadRandomInterstitialAd(this, listener("random")));
        button("loadRandomInterstitialDoubleAd()  (two ads together)", () ->
                GamechiefAds.loadRandomInterstitialDoubleAd(this, listener("random double")));
        button("Null listener (must not crash)", () -> {
            GamechiefAds.loadRandomInterstitialAd(this, null);
            say("null-listener call made");
        });
        button("Two calls at once (the second is refused)", () -> {
            say("calling twice; the second must be refused (DENY_AD_SHOWING)");
            GamechiefAds.loadRandomInterstitialAd(this, listener("call 1"));
            GamechiefAds.loadRandomInterstitialAd(this, listener("call 2"));
        });

        note("Loading dialog preview (4 s). It is always shown when an ad loads at runtime.");
        button("Preview the loading dialog", () -> {
            com.gcl.gamechiefads.ui.DefaultLoaderDialog d = com.gcl.gamechiefads.ui.DefaultLoaderDialog.builder()
                    .title("Ad is loading").message("Please wait…")
                    .tips("Tip: ads help keep this app free", "Tip: premium removes all ads").build();
            d.show(this, new com.gcl.gamechiefads.LoaderInfo("preview", null, null));
            content.postDelayed(d::dismiss, 4000);
        });

        note("Your own extra spot (here \"manual\"): shows whenever you call it, no pacing.");
        button("loadInterstitialAd(MANUAL)", () -> GamechiefAds.loadInterstitialAd(this, AdIds.MANUAL, listener("manual")));

        note("Preloaded (opt-in): nothing is requested until you ask, and nothing refills by itself.");
        button("1. Preload", () -> {
            GamechiefAds.preloadInterstitial(AdIds.PRELOADED);
            say("preload requested");
        });
        button("2. Is ready?", () -> say("ready=" + GamechiefAds.isInterstitialReady(AdIds.PRELOADED)));
        button("3. Show preloaded", () -> {
            boolean started = GamechiefAds.showPreloadedInterstitial(this, AdIds.PRELOADED, listener("preloaded"));
            say("showPreloaded started=" + started + (started ? "" : " (nothing ready)"));
        });
    }
}

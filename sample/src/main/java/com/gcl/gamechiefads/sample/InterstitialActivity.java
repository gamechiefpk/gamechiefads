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
        button("Show (on-demand)", () -> GamechiefAds.showInterstitial(this, AdIds.MANUAL, listener("on-demand")));
        button("Show with a null listener (must not crash)", () -> {
            GamechiefAds.showInterstitial(this, AdIds.MANUAL, null);
            say("null-listener call made");
        });
        button("Double tap test: two calls at once", () -> {
            say("calling twice; the second must be refused (DENY_AD_SHOWING)");
            GamechiefAds.showInterstitial(this, AdIds.MANUAL, listener("call 1"));
            GamechiefAds.showInterstitial(this, AdIds.MANUAL, listener("call 2"));
        });

        button("Preview the loading overlay (4 s)", () -> {
            com.gcl.gamechiefads.ui.DefaultLoaderDialog d = com.gcl.gamechiefads.ui.DefaultLoaderDialog.builder()
                    .title("Ad is loading").message("Please wait…")
                    .tips("Tip: ads help keep this app free", "Tip: premium removes all ads").build();
            d.show(this, new com.gcl.gamechiefads.LoaderInfo("preview", null, null));
            content.postDelayed(d::dismiss, 4000);
        });

        note("Double: two ads load together and play back to back, like one ad break. "
                + "Remote Config can switch this on or off per placement (see the hub).");
        button("Show double interstitial", () -> GamechiefAds.showInterstitial(this, AdIds.DOUBLE_INTERSTITIAL, listener("double")));

        note("Floors: the high floor never fills (own 1.5 s timeout), is skipped after 2 misses for 60 s, then retried. "
                + "Tap 3 times and watch the Library trace.");
        button("Load with floors (HF, MF, NORMAL)", () -> GamechiefAds.showInterstitial(this, AdIds.FLOORS, listener("floors")));

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

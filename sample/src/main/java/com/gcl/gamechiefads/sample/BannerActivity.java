package com.gcl.gamechiefads.sample;

import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.gcl.gamechiefads.BannerListener;
import com.gcl.gamechiefads.BannerType;
import com.gcl.gamechiefads.GamechiefAds;

/**
 * No XML for banners: the app only has a FrameLayout and the library fills it. A shimmer shows while it loads (when the
 * ui module is in the app). One banner at a time, so a slow emulator is not asked for six ads at once.
 */
public class BannerActivity extends DemoActivity {
    private static final String UNIT = "ca-app-pub-3940256099942544/9214589741"; // Google test banner
    private LinearLayout host;

    @Override
    protected String screenTitle() {
        return "Banners";
    }

    @Override
    protected void build() {
        note("Ready-made slots (units come from the config):");
        button("loadBannerAd()  (all screens)", () -> show("banner", f -> GamechiefAds.loadBannerAd(f, bannerListener("banner"))));
        button("loadExitBannerAd()  (medium rectangle)", () -> show("exit banner", f -> GamechiefAds.loadExitBannerAd(f, bannerListener("exit banner"))));
        button("loadSplashBannerAd()  (large banner)", () -> show("splash banner", f -> GamechiefAds.loadSplashBannerAd(f, bannerListener("splash banner"))));

        note("Full control: your own ad unit, size and type, one call:");
        button("Custom: adaptive", () -> custom(BannerType.ADAPTIVE));
        button("Custom: medium rectangle 300x250", () -> custom(BannerType.MEDIUM_RECTANGLE));
        button("Custom: large banner 320x100", () -> custom(BannerType.LARGE_BANNER));
        button("Custom: standard banner 320x50", () -> custom(BannerType.BANNER));
        button("Custom: inline adaptive", () -> custom(BannerType.INLINE_ADAPTIVE));
        button("Custom: collapsible (pinned to the bottom)", () -> {
            clear();
            FrameLayout f = new FrameLayout(this);
            bottomBar().addView(f, new FrameLayout.LayoutParams(-1, -2));
            say("custom collapsible: loading at the bottom");
            GamechiefAds.loadCustomBannerAd(f, UNIT, BannerType.COLLAPSIBLE_BOTTOM, bannerListener("collapsible"));
        });

        host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        content.addView(host, new LinearLayout.LayoutParams(-1, -2));
        note("Collapsible: tap the arrow to expand it (Google demand only). Turn Premium on in the hub and come back: every banner must be gone.");
    }

    private void custom(BannerType type) {
        show("custom " + type, f -> GamechiefAds.loadCustomBannerAd(f, UNIT, type, bannerListener("custom " + type)));
    }

    private interface Loader {
        void load(FrameLayout frame);
    }

    private void show(String name, Loader loader) {
        clear();
        FrameLayout f = new FrameLayout(this);
        host.addView(f, new LinearLayout.LayoutParams(-1, -2));
        say(name + ": loading");
        loader.load(f);
    }

    /** Dropping the old container is all it takes: the library destroys the banner when its view leaves the window. */
    private void clear() {
        host.removeAllViews();
        bottomBar().removeAllViews();
    }

    private BannerListener bannerListener(String name) {
        return new BannerListener() {
            @Override
            public void onLoaded() {
                say(name + ": loaded");
            }

            @Override
            public void onFailed(String reason) {
                say(name + ": failed (" + reason + ")");
            }
        };
    }
}

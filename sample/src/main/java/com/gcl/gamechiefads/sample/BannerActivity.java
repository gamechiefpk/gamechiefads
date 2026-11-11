package com.gcl.gamechiefads.sample;

import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.gcl.gamechiefads.AdPlacement;
import com.gcl.gamechiefads.BannerListener;
import com.gcl.gamechiefads.ui.AdsUi;

/**
 * No XML for the banners: the app only has a FrameLayout, and AdsUi.banner(frame, placement) loads into it.
 * The banner TYPE is not chosen here but in the placement config (see SampleApp). One banner at a time, so a slow
 * emulator is not asked for six ads at once.
 */
public class BannerActivity extends DemoActivity {
    private LinearLayout host; // where the normal banners go
    private FrameLayout current;

    @Override
    protected String screenTitle() {
        return "Banners";
    }

    @Override
    protected void build() {
        note("Pick a type. Each one is a FrameLayout loaded from code; the type comes from the placement config.");
        button("Adaptive (anchored)", () -> inline(AdIds.BANNER_ADAPTIVE, "adaptive"));
        button("Medium rectangle 300x250", () -> inline(AdIds.BANNER_RECT, "medium rectangle"));
        button("Large banner 320x100", () -> inline(AdIds.BANNER_LARGE, "large banner"));
        button("Standard banner 320x50", () -> inline(AdIds.BANNER_STANDARD, "standard"));
        button("Inline adaptive", () -> inline(AdIds.BANNER_INLINE, "inline adaptive"));
        button("Collapsible (pinned to the bottom)", this::collapsible);

        host = new LinearLayout(this);
        host.setOrientation(LinearLayout.VERTICAL);
        content.addView(host, new LinearLayout.LayoutParams(-1, -2));
        note("Collapsible: tap the arrow on the banner to expand it. It works with Google demand only; mediated ads "
                + "show as a normal banner. Turn Premium on in the hub and come back: every banner must be gone.");
    }

    private void inline(AdPlacement placement, String name) {
        clear();
        current = new FrameLayout(this);
        host.addView(current, new LinearLayout.LayoutParams(-1, -2));
        say("banner " + name + ": loading");
        AdsUi.banner(current, placement, bannerListener(name));
    }

    private void collapsible() {
        clear();
        current = new FrameLayout(this);
        bottomBar().addView(current, new FrameLayout.LayoutParams(-1, -2));
        say("banner collapsible: loading at the bottom");
        AdsUi.banner(current, AdIds.BANNER_COLLAPSIBLE, bannerListener("collapsible"));
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
                say("banner " + name + ": loaded");
            }

            @Override
            public void onFailed(String reason) {
                say("banner " + name + ": failed (" + reason + ")");
            }
        };
    }
}

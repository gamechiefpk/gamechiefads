package com.gcl.gamechiefads.sample;

import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gcl.gamechiefads.AdPlacement;
import com.gcl.gamechiefads.BannerListener;
import com.gcl.gamechiefads.ui.AdsUi;

/**
 * No XML for the banners: the app only has FrameLayouts, and AdsUi.banner(frame, placement) loads into them.
 * The banner TYPE is not chosen here but in the placement config (see SampleApp).
 */
public class BannerActivity extends DemoActivity {
    @Override
    protected String screenTitle() {
        return "Banners";
    }

    @Override
    protected void build() {
        note("One FrameLayout per banner, loaded from code. The type comes from the placement config.");
        add("Adaptive (anchored)", AdIds.BANNER_ADAPTIVE);
        add("Medium rectangle 300x250", AdIds.BANNER_RECT);
        add("Large banner 320x100", AdIds.BANNER_LARGE);
        add("Standard banner 320x50", AdIds.BANNER_STANDARD);
        add("Inline adaptive", AdIds.BANNER_INLINE);

        // collapsible banners belong at the screen edge: this one sits pinned at the bottom of the screen
        FrameLayout bottom = bottomBar();
        say("collapsible banner: bottom of screen");
        AdsUi.banner(bottom, AdIds.BANNER_COLLAPSIBLE, bannerListener("collapsible"));
        note("Collapsible (bottom of the screen): tap the arrow on the banner to expand it. "
                + "Collapsible works with Google demand only; mediated ads show as a normal banner.");
        note("Turn Premium on in the hub and come back: all banners must be gone.");
    }

    private void add(String label, AdPlacement placement) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setPadding(0, 24, 0, 6);
        content.addView(t);
        FrameLayout frame = new FrameLayout(this);
        content.addView(frame, new LinearLayout.LayoutParams(-1, -2));
        AdsUi.banner(frame, placement, bannerListener(label));
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

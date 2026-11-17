package com.gcl.gamechiefads.sample;

import android.widget.FrameLayout;

/**
 * NO ad code here. The two FrameLayouts only carry a tag (in a real app: android:tag="gads_banner" in the layout XML) and the
 * library fills them when this screen opens: floors, shimmer, premium hiding and cleanup included.
 */
public class TagBannerActivity extends DemoActivity {
    @Override
    protected String screenTitle() {
        return "Tag banners";
    }

    @Override
    protected void build() {
        note("This screen has no ad code. A container tagged gads_banner (floors) and one tagged gads_banner_fast (no floors) are filled by the library by itself. Tags: gads_banner, gads_banner_fast, gads_exit_banner, gads_exit_banner_fast, gads_splash_banner.");
        content.addView(tagged("gads_banner"), new FrameLayout.LayoutParams(-1, -2));
        content.addView(tagged("gads_banner_fast"), new FrameLayout.LayoutParams(-1, -2));
    }

    /** Same as android:tag="..." on a FrameLayout in XML. */
    private FrameLayout tagged(String tag) {
        FrameLayout f = new FrameLayout(this);
        f.setTag(tag);
        return f;
    }
}

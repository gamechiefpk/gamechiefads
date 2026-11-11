package com.gcl.gamechiefads.sample;

import com.gcl.gamechiefads.BannerListener;
import com.gcl.gamechiefads.ui.BannerAdView;

/** The banner is declared in res/layout/screen_banner.xml: no Java ad code is needed for it to work. */
public class BannerActivity extends DemoActivity {
    @Override
    protected String screenTitle() {
        return "Banner";
    }

    @Override
    protected int layoutRes() {
        return R.layout.screen_banner;
    }

    @Override
    protected void build() {
        BannerAdView banner = findViewById(R.id.banner);
        banner.setListener(new BannerListener() {
            @Override
            public void onLoaded() {
                say("banner: loaded");
            }

            @Override
            public void onFailed(String reason) {
                say("banner: failed (" + reason + "), view hidden");
            }
        });
        button("Reload banner", () -> {
            say("banner: reload");
            banner.load("banner_main");
        });
        note("Turn Premium on in the hub and come back: the banner must be gone.");
    }
}

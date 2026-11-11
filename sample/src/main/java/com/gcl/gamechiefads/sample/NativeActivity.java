package com.gcl.gamechiefads.sample;

import android.graphics.Color;

import com.gcl.gamechiefads.ui.FullScreenNativeAd;
import com.gcl.gamechiefads.ui.NativeAdTemplateView;
import com.gcl.gamechiefads.ui.NativeStyle;

/** Six native templates declared in res/layout/screen_native.xml (gads_size), each loading its own ad. */
public class NativeActivity extends DemoActivity {
    private static final int[] IDS = {R.id.n_tiny, R.id.n_small, R.id.n_small_media, R.id.n_medium, R.id.n_large, R.id.n_full};
    private static final String[] NAMES = {"tiny", "small", "small_media", "medium", "large", "full"};
    private boolean dark;

    @Override
    protected String screenTitle() {
        return "Native ads";
    }

    @Override
    protected int layoutRes() {
        return R.layout.screen_native;
    }

    @Override
    protected void build() {
        for (int i = 0; i < IDS.length; i++) {
            String name = NAMES[i];
            NativeAdTemplateView v = findViewById(IDS[i]);
            v.setListener(new NativeAdTemplateView.Listener() {
                @Override
                public void onLoaded() {
                    say("native " + name + ": loaded");
                }

                @Override
                public void onFailed(String reason) {
                    say("native " + name + ": failed (" + reason + ")");
                }
            });
        }
        button("Full-screen native (like an interstitial)", () ->
                FullScreenNativeAd.show(this, AdIds.NATIVE_FULLSCREEN, listener("native-fs")));
        button("Reload all", () -> {
            for (int id : IDS) ((NativeAdTemplateView) findViewById(id)).load(AdIds.NATIVE_MAIN);
            say("native: reload all");
        });
        button("Switch style (default / dark)", () -> {
            dark = !dark;
            NativeStyle style = dark
                    ? NativeStyle.builder().background(0xFF1E1E1E).headlineColor(Color.WHITE).bodyColor(0xFFBBBBBB)
                    .secondaryColor(0xFF999999).button(0xFFFFB300, Color.BLACK).cornerRadiusDp(20).build()
                    : NativeStyle.DEFAULT;
            for (int id : IDS) ((NativeAdTemplateView) findViewById(id)).setStyle(style);
            say("native style: " + (dark ? "dark" : "default"));
        });
    }
}

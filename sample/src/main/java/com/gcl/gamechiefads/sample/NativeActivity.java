package com.gcl.gamechiefads.sample;

import android.graphics.Color;
import android.widget.FrameLayout;

import com.gcl.gamechiefads.ui.AdsUi;
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
        // one ad at a time: each template starts loading when the previous one finished (a slow emulator or network
        // does not like six requests at once). In a real app a list normally shows one or two native ads.
        for (int i = 0; i < IDS.length; i++) {
            final int index = i;
            NativeAdTemplateView v = findViewById(IDS[i]);
            v.setListener(new NativeAdTemplateView.Listener() {
                @Override
                public void onLoaded() {
                    say("native " + NAMES[index] + ": loaded");
                    loadFrom(index + 1);
                }

                @Override
                public void onFailed(String reason) {
                    say("native " + NAMES[index] + ": failed (" + reason + ")");
                    loadFrom(index + 1);
                }
            });
        }
        loadFrom(0);
        FrameLayout intro = new FrameLayout(this);
        content.addView(intro, new android.widget.LinearLayout.LayoutParams(-1, -2));
        button("AdsUi.loadIntroNativeAd()  (small, with floors)", () -> {
            say("intro native: loading");
            AdsUi.loadIntroNativeAd(intro, new NativeAdTemplateView.Listener() {
                @Override public void onLoaded() { say("intro native: loaded"); }
                @Override public void onFailed(String reason) { say("intro native: failed (" + reason + ")"); }
            });
        });
        button("AdsUi.loadFullScreenNativeAd()  (like an interstitial, 1 unit)", () ->
                AdsUi.loadFullScreenNativeAd(this, listener("native-fs")));
        button("Reload all (one after another)", () -> {
            say("native: reload all");
            loadFrom(0);
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

    private void loadFrom(int index) {
        if (index >= IDS.length) return;
        ((NativeAdTemplateView) findViewById(IDS[index])).load(AdIds.NATIVE_MAIN);
    }
}

package com.gcl.gamechiefads.sample;

import android.content.Intent;

/**
 * No ad code here on purpose: the library counts every new screen by itself (ActivityLifecycleCallbacks)
 * and shows the "random" interstitial when its rule says so (sample: every 2 screens, 15 s apart).
 */
public class SecondActivity extends DemoActivity {
    @Override
    protected String screenTitle() {
        return "Random interstitial";
    }

    @Override
    protected void build() {
        int depth = getIntent().getIntExtra("depth", 1);
        note("Screen #" + depth + " of this chain. This class contains no ad code.\n"
                + "Keep opening screens: every 2nd screen, and at least 15 s after the last ad, an interstitial appears by "
                + "itself. The splash is excluded. Use the hub's remote override to try 'every screen'.");
        button("Open another screen", () -> startActivity(new Intent(this, SecondActivity.class).putExtra("depth", depth + 1)));
        button("Back", this::finish);
    }
}

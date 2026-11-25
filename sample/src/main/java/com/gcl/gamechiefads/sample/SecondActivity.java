package com.gcl.gamechiefads.sample;

import android.content.Intent;

import com.gcl.gamechiefads.GamechiefAds;

/** Click counter: every click calls the random interstitial and the library decides if an ad is due. */
public class SecondActivity extends DemoActivity {
    @Override
    protected String screenTitle() {
        return "Random clicks";
    }

    @Override
    protected void build() {
        int depth = getIntent().getIntExtra("depth", 1);
        note("Screen #" + depth + ". AUTOMATIC: open new screens with \"Open another screen\": every 2nd new screen shows an ad by itself. "
                + "ON A BUTTON: tap \"Click\": the same ad on every 2nd click, "
                + "at least 15 s after the last ad. Not due = the log says why and nothing happens.");
        android.widget.Button auto = button("", () -> { });
        auto.setText("Automatic ad: " + (GamechiefAds.isAutoRandomInterstitialEnabled() ? "ON" : "OFF") + " (tap to toggle)");
        auto.setOnClickListener(v -> {
            GamechiefAds.setAutoRandomInterstitialEnabled(!GamechiefAds.isAutoRandomInterstitialEnabled());
            auto.setText("Automatic ad: " + (GamechiefAds.isAutoRandomInterstitialEnabled() ? "ON" : "OFF") + " (tap to toggle)");
            say("automatic random ad: " + (GamechiefAds.isAutoRandomInterstitialEnabled() ? "ON" : "OFF"));
        });
        button("Click (random interstitial)", () -> GamechiefAds.loadRandomInterstitialAd(this, listener("click")));
        button("Click (random DOUBLE interstitial)", () -> GamechiefAds.loadRandomInterstitialAd(this, true, listener("click double")));
        button("Open another screen", () -> startActivity(new Intent(this, SecondActivity.class).putExtra("depth", depth + 1)));
        button("Back", this::finish);
    }
}

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
        note("Screen #" + depth + ". Tap \"Click\" again and again: the sample asks for an ad on every 2nd click, "
                + "at least 15 s after the last ad. Not due = the log says why and nothing happens.");
        button("Click (random interstitial)", () -> GamechiefAds.loadRandomInterstitialAd(this, listener("click")));
        button("Click (random DOUBLE interstitial)", () -> GamechiefAds.loadRandomInterstitialDoubleAd(this, listener("click double")));
        button("Open another screen", () -> startActivity(new Intent(this, SecondActivity.class).putExtra("depth", depth + 1)));
        button("Back", this::finish);
    }
}

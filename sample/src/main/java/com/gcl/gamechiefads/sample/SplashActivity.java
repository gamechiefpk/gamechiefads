package com.gcl.gamechiefads.sample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import com.gcl.gamechiefads.GamechiefAds;
import com.gcl.gamechiefads.SplashAds;

public class SplashActivity extends Activity {
    private boolean navigated;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        TextView status = findViewById(R.id.status);
        Button start = findViewById(R.id.start);

        // consent -> init -> waterfall -> timeout. Called once, with or without an ad.
        GamechiefAds.prepareSplash(this, (SplashAds ads) -> {
            status.setText(ads.hasAd() ? "Ad ready" : "No ad (premium, offline, consent denied or timeout)");
            start.setVisibility(android.view.View.VISIBLE);
            start.setOnClickListener(v -> {
                start.setEnabled(false);
                // first ad, then the second only after the first closed; onDone runs once
                ads.show(this, this::goHome);
            });
        });
    }

    private void goHome() {
        if (navigated) return;
        navigated = true;
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}

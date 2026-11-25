package com.yourcompany.yourapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.gcl.gamechiefads.GamechiefAds;

/** Step 2: the splash. A large banner under the logo and one full-screen ad, then the next screen. */
public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        GamechiefAds.loadSplashBannerAd(findViewById(R.id.splashBanner));

        // Open your next screen ONLY inside onDone: it runs once, with or without an ad (offline, premium, no fill, time limit).
        GamechiefAds.loadSplashAppOpenAd(this, ads ->
                ads.show(this, () -> startActivity(new Intent(this, LanguageActivity.class))));
    }
}

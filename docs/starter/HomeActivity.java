package com.yourcompany.yourapp;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.gcl.gamechiefads.GamechiefAds;
import com.gcl.gamechiefads.RewardListener;

/**
 * Step 5: the home screen.
 * - The banner needs NO code: the FrameLayout in activity_home.xml carries android:tag="gads_banner".
 * - The random interstitial is automatic over every 3rd new screen; the button below is an extra spot.
 * - The exit dialog shows the exit banner.
 */
public class HomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // an extra click spot (the library decides if it is time: gap, every N calls, premium...). null = no listener needed.
        findViewById(R.id.extraButton).setOnClickListener(v -> GamechiefAds.loadRandomInterstitialAd(this, null));

        findViewById(R.id.rewardButton).setOnClickListener(v -> GamechiefAds.loadRewardedAd(this, new RewardListener() {
            @Override public void onRewardEarned(int amount, String type) { /* give the reward ONLY here */ }
        }));
    }

    @Override
    public void onBackPressed() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_exit, null);
        GamechiefAds.loadExitBannerAd((FrameLayout) v.findViewById(R.id.exitBanner));
        new AlertDialog.Builder(this).setView(v)
                .setPositiveButton("Exit", (d, w) -> finish())
                .setNegativeButton("Stay", null)
                .show();
    }
}

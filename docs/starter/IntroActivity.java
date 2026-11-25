package com.yourcompany.yourapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.gcl.gamechiefads.ui.AdsUi;

/** Step 4: an intro screen with a small-media native ad (one call per intro screen). */
public class IntroActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        AdsUi.loadIntroNativeAd(findViewById(R.id.nativeFrame));

        findViewById(R.id.nextButton).setOnClickListener(v -> startActivity(new Intent(this, HomeActivity.class)));
    }
}

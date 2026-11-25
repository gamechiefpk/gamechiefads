package com.yourcompany.yourapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.gcl.gamechiefads.ui.AdsUi;

/** Step 3: the language selection screen with a small-media native ad. */
public class LanguageActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language);

        AdsUi.loadLanguageNativeAd(findViewById(R.id.nativeFrame));     // that is all: shimmer, no-ad hiding and cleanup are built in

        findViewById(R.id.continueButton).setOnClickListener(v -> startActivity(new Intent(this, IntroActivity.class)));
    }
}

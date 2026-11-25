package com.yourcompany.yourapp;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.gcl.gamechiefads.ui.IntroPager;

/**
 * Step 4: the intro slider. You give only the page layouts. The library adds the dots, the "Get started" button on the last page and, when Firebase
 * says so (value noOfBigNativeAdInIntro, default 0 = none), full-screen native ad pages between the slides. A full-screen ad page has a close
 * button that appears after 3 seconds; tapping it moves to the next slide. Nothing else to do in code.
 */
public class IntroActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intro);

        IntroPager pager = findViewById(R.id.introPager);
        pager.setPages(R.layout.intro_page_1, R.layout.intro_page_2, R.layout.intro_page_3);
        pager.setOnFinished(() -> {
            startActivity(new Intent(this, HomeActivity.class));
            finish();
        });
    }
}

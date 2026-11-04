package com.gcl.gamechiefads.sample;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import com.gcl.gamechiefads.GamechiefAds;
import com.gcl.gamechiefads.InterstitialListener;
import com.gcl.gamechiefads.ui.NativeAdTemplateView;
import com.gcl.gamechiefads.ui.NativeSize;

public class MainActivity extends Activity {
    private TextView log;
    private boolean premium;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        log = findViewById(R.id.log);

        // 1) on-demand (default): loader overlay, waterfall, load then show. Listener may be null.
        findViewById(R.id.btn_show).setOnClickListener(v ->
                GamechiefAds.showInterstitial(this, "manual", listener("on-demand")));

        // 2) opt-in preloading
        findViewById(R.id.btn_preload).setOnClickListener(v -> {
            GamechiefAds.preloadInterstitial("preloaded");
            say("preload requested");
        });
        findViewById(R.id.btn_show_preloaded).setOnClickListener(v -> {
            boolean started = GamechiefAds.showPreloadedInterstitial(this, "preloaded", listener("preloaded"));
            say("ready=" + GamechiefAds.isInterstitialReady("preloaded") + " started=" + started);
        });

        // 3) premium switch: no ads of any kind while on
        findViewById(R.id.btn_premium).setOnClickListener(v -> {
            premium = !premium;
            GamechiefAds.setPremium(premium);
            say("premium=" + premium);
        });

        // 4) screens count towards the random interstitial; no ad code in SecondActivity at all
        findViewById(R.id.btn_second).setOnClickListener(v -> startActivity(new Intent(this, SecondActivity.class)));

        // 5) native ad in every size
        NativeAdTemplateView nativeView = findViewById(R.id.native_view);
        Spinner sizes = findViewById(R.id.size_spinner);
        sizes.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, NativeSize.values()));
        NativeSize initial = NativeSize.MEDIUM;
        String requested = getIntent().getStringExtra("size");
        if (requested != null) {
            try {
                initial = NativeSize.valueOf(requested);
            } catch (IllegalArgumentException ignored) {
            }
        }
        sizes.setSelection(initial.ordinal());
        sizes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                nativeView.setSize(NativeSize.values()[position]);
                nativeView.load("native_main");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private InterstitialListener listener(String name) {
        return new InterstitialListener() {
            @Override
            public void onShown() {
                say(name + ": shown");
            }

            @Override
            public void onNotShown(String reason) {
                say(name + ": not shown (" + reason + ")");
            }

            @Override
            public void onComplete(boolean wasShown) {
                say(name + ": complete shown=" + wasShown);
            }
        };
    }

    private void say(String s) {
        android.util.Log.d("SampleLog", s);
        log.append(s + "\n");
    }
}

package com.gcl.gamechiefads.sample;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** No ad code here on purpose: ScreenTracker counts this screen and may show the random interstitial. */
public class SecondActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView t = new TextView(this);
        t.setText("Second screen. Go back and forth: every 2nd screen (and 15 s apart) an interstitial appears by itself.");
        t.setPadding(48, 96, 48, 48);
        setContentView(t);
    }
}

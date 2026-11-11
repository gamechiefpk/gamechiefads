package com.gcl.gamechiefads.sample;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.gcl.gamechiefads.InterstitialListener;

/**
 * Base for the demo screens: a scrolling content area, and an event log pinned at the bottom.
 * Subclasses add their controls in build(). There is NO ad code in this class.
 */
abstract class DemoActivity extends Activity {
    protected LinearLayout content;
    private TextView log;
    private ScrollView logScroll;

    protected abstract String screenTitle();

    protected abstract void build();

    /** Set to inflate an XML layout into the content area (before build() runs). */
    protected int layoutRes() {
        return 0;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float dp = getResources().getDisplayMetrics().density;

        LinearLayout outer = new LinearLayout(this);
        outer.setOrientation(LinearLayout.VERTICAL);

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) (16 * dp), (int) (24 * dp), (int) (16 * dp), (int) (16 * dp));
        scroll.addView(content);
        outer.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        log = new TextView(this);
        log.setTypeface(Typeface.MONOSPACE);
        log.setTextSize(11);
        log.setTextColor(Color.WHITE);
        log.setPadding((int) (8 * dp), (int) (4 * dp), (int) (8 * dp), (int) (4 * dp));
        log.setText(DemoLog.all().isEmpty() ? "event log\n" : DemoLog.all());
        logScroll = new ScrollView(this);
        logScroll.setBackgroundColor(0xFF263238);
        logScroll.addView(log);
        outer.addView(logScroll, new LinearLayout.LayoutParams(-1, (int) (150 * dp)));
        setContentView(outer);

        title(screenTitle());
        if (layoutRes() != 0) getLayoutInflater().inflate(layoutRes(), content, true);
        build();
        logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
    }

    protected void title(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(22);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        content.addView(v);
    }

    protected void note(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(13);
        v.setPadding(0, 8, 0, 12);
        content.addView(v);
    }

    protected Button button(String text, Runnable onClick) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(v -> onClick.run());
        content.addView(b, new LinearLayout.LayoutParams(-1, -2));
        return b;
    }

    protected void say(String s) {
        String line = DemoLog.add(s);
        if (log != null) {
            log.append(line);
            logScroll.post(() -> logScroll.fullScroll(View.FOCUS_DOWN));
        }
    }

    /** Logs every callback of an interstitial request, under [name]. */
    protected InterstitialListener listener(String name) {
        return new InterstitialListener() {
            @Override
            public void onShown() {
                say(name + ": shown");
            }

            @Override
            public void onClosed() {
                say(name + ": closed");
            }

            @Override
            public void onNotShown(String reason) {
                say(name + ": NOT shown (" + reason + ")");
            }

            @Override
            public void onComplete(boolean wasShown) {
                say(name + ": complete, wasShown=" + wasShown);
            }
        };
    }
}

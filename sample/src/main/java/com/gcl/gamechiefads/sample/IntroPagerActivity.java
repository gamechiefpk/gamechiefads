package com.gcl.gamechiefads.sample;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.gcl.gamechiefads.ui.IntroPager;

import java.util.ArrayList;
import java.util.List;

/**
 * The intro slider with the ads built in: only the pages are yours. Full-screen native pages between them come from the Firebase value
 * noOfBigNativeAdsInIntro (set it with the buttons on the hub; 0 = none). Swipe through, tap the close button of an ad page (it appears after 3 s),
 * and "Get started" shows on the last page.
 */
public class IntroPagerActivity extends Activity {
    private static final int[] COLORS = {0xFF3F51B5, 0xFF009688, 0xFFFF7043, 0xFF7E57C2};
    private static final String[] TITLES = {"Welcome", "Find things fast", "Stay in sync", "Ready?"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        IntroPager pager = new IntroPager(this);
        List<IntroPager.PageFactory> pages = new ArrayList<>();
        for (int i = 0; i < COLORS.length; i++) {
            final int index = i;
            pages.add(parent -> page(index));
        }
        pager.setPageFactories(pages);                    // with XML files: pager.setPages(R.layout.intro_1, R.layout.intro_2, ...)
        pager.setOnPageBound((index, view) -> { });       // wire the buttons of a page here if it has any
        pager.setOnFinished(() -> {
            Toast.makeText(this, "Get started tapped: open your home screen here", Toast.LENGTH_SHORT).show();
            finish();
        });
        // QA: how many ad requests this intro has sent (it must stay put while you swipe)
        android.widget.FrameLayout root = new android.widget.FrameLayout(this);
        root.addView(pager, new android.widget.FrameLayout.LayoutParams(-1, -1));
        counter = new TextView(this);
        counter.setTextColor(Color.WHITE);
        counter.setBackgroundColor(0x88000000);
        counter.setTextSize(11);
        counter.setPadding(12, 4, 12, 4);
        root.addView(counter, new android.widget.FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START));
        setContentView(root);
        introPager = pager;
        handler.post(tick);
    }

    private IntroPager introPager;
    private TextView counter;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            counter.setText(" ad requests: " + introPager.getAdRequestCount() + " ");
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }

    private View page(int i) {
        TextView t = new TextView(this);
        t.setText(TITLES[i] + "\n\n(intro page " + (i + 1) + ")");
        t.setTextColor(Color.WHITE);
        t.setTextSize(24);
        t.setGravity(Gravity.CENTER);
        t.setBackgroundColor(COLORS[i]);
        return t;
    }
}

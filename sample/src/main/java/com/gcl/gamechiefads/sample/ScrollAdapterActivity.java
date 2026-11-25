package com.gcl.gamechiefads.sample;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gcl.gamechiefads.GamechiefAds;
import com.gcl.gamechiefads.ui.NativeAdAdapter;

/**
 * ONE native ad in a RecyclerView, controlled from Firebase, ONE request. The only ad code: wrap the adapter (onCreate) and destroy it (onDestroy).
 * The buttons play the part of changing the three Firebase values: enableNativeInScroll, positionOfAdAt, scrollNativeAdType.
 * Scroll down and back up as much as you like: the request counter stays at 1.
 */
public class ScrollAdapterActivity extends Activity {
    private NativeAdAdapter<RecyclerView.ViewHolder> adapter;
    private TextView status;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            status.setText("requests sent by this list: " + adapter.getRequestCount() + "   ad row shown: " + adapter.isAdShown()
                    + "\nFirebase: enable=" + GamechiefAds.getRemoteBoolean("enableNativeInScroll", false)
                    + " position=" + GamechiefAds.getRemoteLong("positionOfAdAt", -1)
                    + " type=" + GamechiefAds.getRemoteString("scrollNativeAdType", "(none)"));
            main.postDelayed(this, 500);
        }
    };

    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFAFAFA);

        TextView title = new TextView(this);
        title.setText("One native ad in a RecyclerView (NativeAdAdapter)");
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(17);
        title.setPadding((int) (12 * dp), (int) (36 * dp), (int) (12 * dp), 0);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(12);
        status.setTypeface(Typeface.MONOSPACE);
        status.setPadding((int) (12 * dp), (int) (2 * dp), (int) (12 * dp), (int) (2 * dp));
        root.addView(status);

        root.addView(row(
                button("enable ON", () -> DemoRemoteSource.INSTANCE.publish("enableNativeInScroll", true)),
                button("enable OFF", () -> DemoRemoteSource.INSTANCE.publish("enableNativeInScroll", false))));
        root.addView(row(
                button("position 3", () -> DemoRemoteSource.INSTANCE.publish("positionOfAdAt", 3L)),
                button("position 9", () -> DemoRemoteSource.INSTANCE.publish("positionOfAdAt", 9L))));
        root.addView(row(
                button("type small", () -> DemoRemoteSource.INSTANCE.publish("scrollNativeAdType", "small")),
                button("type medium", () -> DemoRemoteSource.INSTANCE.publish("scrollNativeAdType", "medium")),
                button("small_media", () -> DemoRemoteSource.INSTANCE.publish("scrollNativeAdType", "small_media"))));

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        // THE integration: wrap your adapter. That is all.
        adapter = (NativeAdAdapter<RecyclerView.ViewHolder>) (NativeAdAdapter<?>) new NativeAdAdapter<>(new Dummy());
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        main.post(tick);
    }

    private Button button(String text, Runnable r) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setOnClickListener(v -> r.run());
        return b;
    }

    private LinearLayout row(Button... buttons) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        for (Button b : buttons) l.addView(b, new LinearLayout.LayoutParams(0, -2, 1));
        return l;
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(tick);
        adapter.destroy();                       // the second line of the integration
        super.onDestroy();
    }

    /** Your own adapter: untouched. */
    private static class Dummy extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            float dp = parent.getResources().getDisplayMetrics().density;
            TextView t = new TextView(parent.getContext());
            t.setLayoutParams(new RecyclerView.LayoutParams(-1, (int) (64 * dp)));
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding((int) (20 * dp), 0, (int) (20 * dp), 0);
            t.setTextSize(16);
            t.setTextColor(Color.DKGRAY);
            return new RecyclerView.ViewHolder(t) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            ((TextView) holder.itemView).setText("Dummy item " + (position + 1));
        }

        @Override
        public int getItemCount() {
            return 40;
        }
    }
}

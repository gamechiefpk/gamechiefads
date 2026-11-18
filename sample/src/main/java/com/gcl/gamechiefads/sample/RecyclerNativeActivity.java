package com.gcl.gamechiefads.sample;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gcl.gamechiefads.ui.NativeListAds;
import com.gcl.gamechiefads.ui.NativeSize;

/**
 * Native ads inside a RecyclerView with dummy data. Every 6th row is an ad. Scroll down and back up: each ad is requested ONCE and
 * shown again from memory (watch logcat "native-list": one "request" per ad index, never one per scroll).
 */
public class RecyclerNativeActivity extends Activity {
    private static final int AD_EVERY = 6;          // one ad row after every 5 items
    private static final int ITEMS = 40;            // dummy data
    private RecyclerView list;
    private NativeListAds ads;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFFFAFAFA);

        TextView title = new TextView(this);
        title.setText("Native ads in a RecyclerView");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding((int) (16 * dp), (int) (40 * dp), (int) (16 * dp), (int) (4 * dp));
        root.addView(title);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding((int) (8 * dp), 0, (int) (8 * dp), 0);
        bar.addView(sizeButton("TINY ads", NativeSize.TINY), new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(sizeButton("SMALL ads", NativeSize.SMALL), new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(sizeButton("SMALL_MEDIA ads", NativeSize.SMALL_MEDIA), new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(bar);

        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        useSize(NativeSize.SMALL);
    }

    private Button sizeButton(String text, NativeSize size) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setOnClickListener(v -> useSize(size));
        return b;
    }

    /** A new list with ads of this size (the old ads are freed first). */
    private void useSize(NativeSize size) {
        if (ads != null) ads.destroy();
        ads = new NativeListAds("list_native", size, com.gcl.gamechiefads.ui.NativeStyle.DEFAULT, 6);
        list.setAdapter(new Adapter(ads));
    }

    @Override
    protected void onDestroy() {
        if (ads != null) ads.destroy();      // frees every ad of the list
        super.onDestroy();
    }

    /** Rows: dummy items, with an ad row at every AD_EVERY-th position. */
    private static class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_ITEM = 0;
        private static final int TYPE_AD = 1;
        private final NativeListAds ads;

        Adapter(NativeListAds ads) {
            this.ads = ads;
        }

        @Override
        public int getItemCount() {
            return ITEMS + ITEMS / (AD_EVERY - 1);
        }

        @Override
        public int getItemViewType(int position) {
            return isAd(position) ? TYPE_AD : TYPE_ITEM;
        }

        private static boolean isAd(int position) {
            return position % AD_EVERY == AD_EVERY - 1;
        }

        /** Item number without the ad rows. */
        private static int itemNumber(int position) {
            return position - position / AD_EVERY;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            float dp = parent.getResources().getDisplayMetrics().density;
            if (viewType == TYPE_AD) {
                // the row's root view is the FrameLayout the library fills
                FrameLayout frame = new FrameLayout(parent.getContext());
                frame.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
                frame.setPadding((int) (8 * dp), (int) (4 * dp), (int) (8 * dp), (int) (4 * dp));
                return new RecyclerView.ViewHolder(frame) { };
            }
            TextView t = new TextView(parent.getContext());
            t.setLayoutParams(new RecyclerView.LayoutParams(-1, (int) (72 * dp)));
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding((int) (20 * dp), 0, (int) (20 * dp), 0);
            t.setTextSize(16);
            t.setTextColor(Color.DKGRAY);
            return new RecyclerView.ViewHolder(t) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View v = holder.itemView;
            if (isAd(position)) {
                ads.bind((FrameLayout) v, position / AD_EVERY);       // ad number 0, 1, 2...
            } else {
                ((TextView) v).setText("Dummy item " + (itemNumber(position) + 1) + "  (position " + position + ")");
            }
        }
    }
}

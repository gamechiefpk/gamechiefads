package com.gcl.gamechiefads.sample;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gcl.gamechiefads.ui.NativeListAds;
import com.gcl.gamechiefads.ui.NativeListPositions;
import com.gcl.gamechiefads.ui.NativeSize;

/**
 * Native ads inside a RecyclerView with dummy data: the first ad after 4 rows, then one every 8 rows. Scroll down and back up: each ad is
 * requested ONCE and shown again from memory (logcat "native-list": one "request" per ad index, never one per scroll). Rotate the phone:
 * the ads survive (the NativeListAds object is handed to the new screen), so there is no new request either.
 */
public class RecyclerNativeActivity extends Activity {
    private static final int ITEMS = 40;                                      // dummy data
    private final NativeListPositions positions = new NativeListPositions();  // recommended: first ad after 4 rows, then every 8
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

        // after a rotation the previous screen handed its NativeListAds over: the loaded ads are shown again, no new request
        ads = (NativeListAds) getLastNonConfigurationInstance();
        if (ads == null) ads = new NativeListAds(NativeSize.SMALL);          // placement "list_native": ONE unit, no floors
        list.setAdapter(new Adapter(ads, positions));
    }

    private Button sizeButton(String text, NativeSize size) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setOnClickListener(v -> {
            ads.destroy();                                                   // a new size = a new list of ads
            ads = new NativeListAds(size);
            list.setAdapter(new Adapter(ads, positions));
        });
        return b;
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        return ads;                                                          // survives the rotation
    }

    @Override
    protected void onDestroy() {
        if (!isChangingConfigurations() && ads != null) ads.destroy();       // really leaving: free every ad
        super.onDestroy();
    }

    /** Rows: dummy items, with the ad rows where NativeListPositions says. */
    private static class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_ITEM = 0;
        private static final int TYPE_AD = 1;
        private final NativeListAds ads;
        private final NativeListPositions pos;

        Adapter(NativeListAds ads, NativeListPositions pos) {
            this.ads = ads;
            this.pos = pos;
        }

        @Override
        public int getItemCount() {
            return pos.count(ITEMS);
        }

        @Override
        public int getItemViewType(int position) {
            return pos.isAd(position) ? TYPE_AD : TYPE_ITEM;
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
            if (pos.isAd(position)) {
                ads.bind((FrameLayout) holder.itemView, pos.adIndex(position));   // ad number 0, 1, 2...
            } else {
                ((TextView) holder.itemView).setText("Dummy item " + (pos.itemIndex(position) + 1) + "  (row " + position + ")");
            }
        }
    }
}

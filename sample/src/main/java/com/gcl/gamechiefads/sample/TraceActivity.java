package com.gcl.gamechiefads.sample;

import android.graphics.Typeface;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Shows the library's own debug trace (logcat tag GamechiefAds) on the device, no adb needed. */
public class TraceActivity extends DemoActivity {
    private TextView out;

    @Override
    protected String screenTitle() {
        return "Library trace";
    }

    @Override
    protected void build() {
        note("Debug trace of the library: consent, init, every waterfall tier, policy decisions, mediation adapters.");
        button("Refresh", this::refresh);
        out = new TextView(this);
        out.setTextSize(10);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextIsSelectable(true);
        content.addView(out);
        refresh();
    }

    private void refresh() {
        out.setText("reading...");
        new Thread(() -> {
            StringBuilder sb = new StringBuilder();
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-v", "time", "-s", "GamechiefAds"});
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("-----")) continue;
                        // "11-11 10:00:00.123 D/GamechiefAds( 1234): [x] msg" -> "10:00:00.123 [x] msg"
                        int i = line.indexOf("): ");
                        if (i > 0 && line.length() > 18) sb.append(line, 6, 18).append(' ').append(line.substring(i + 3));
                        else sb.append(line);
                        sb.append('\n');
                    }
                }
            } catch (Exception e) {
                sb.append("cannot read logcat: ").append(e);
            }
            String text = sb.length() == 0 ? "(nothing yet; the trace prints in debug builds only)" : sb.toString();
            runOnUiThread(() -> out.setText(text));
        }).start();
    }
}

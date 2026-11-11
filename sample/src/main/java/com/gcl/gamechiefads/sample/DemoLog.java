package com.gcl.gamechiefads.sample;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Tiny in-app log shared by all demo screens (also mirrored to logcat tag SampleLog). Demo code only. */
final class DemoLog {
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss", Locale.US);
    private static final StringBuilder TEXT = new StringBuilder();

    private DemoLog() {
    }

    static synchronized String add(String line) {
        Log.d("SampleLog", line);
        String l = TIME.format(new Date()) + "  " + line + "\n";
        TEXT.append(l);
        if (TEXT.length() > 6000) TEXT.delete(0, TEXT.length() - 4000);
        return l;
    }

    static synchronized String all() {
        return TEXT.toString();
    }

    static synchronized void clear() {
        TEXT.setLength(0);
    }
}

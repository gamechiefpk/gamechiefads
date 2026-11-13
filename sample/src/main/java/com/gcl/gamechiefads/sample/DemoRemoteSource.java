package com.gcl.gamechiefads.sample;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.gcl.gamechiefads.RemoteConfigSource;
import com.gcl.gamechiefads.RemoteFetchListener;

import java.util.HashMap;
import java.util.Map;

/**
 * A pretend Firebase, only for this demo (the sample has no Firebase project). In your own app you write NONE of this:
 * with the remoteconfig module (included by the one-line dependency) and Firebase set up, GamechiefAds uses the real
 * Firebase Remote Config by itself. publish() plays the part of "you changed a value in the Firebase console".
 */
final class DemoRemoteSource implements RemoteConfigSource {
    static final DemoRemoteSource INSTANCE = new DemoRemoteSource();

    private final Map<String, Object> server = new HashMap<>();
    private final Map<String, Object> defaults = new HashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Runnable update;

    /** Like changing a value in the Firebase console: real-time update to the app. */
    void publish(String key, Object value) {
        server.put(key, value);
        if (update != null) update.run();
    }

    @Override
    public void setDefaults(@NonNull Map<String, ?> defaults) {
        this.defaults.putAll(defaults);
    }

    @Override
    public void setMinFetchIntervalSeconds(long seconds) {
    }

    @Override
    public void fetchAndActivate(@NonNull RemoteFetchListener callback) {
        main.postDelayed(() -> callback.onResult(true), 400);
    }

    @Override
    public boolean has(@NonNull String key) {
        return server.containsKey(key) || defaults.containsKey(key);
    }

    private Object value(String key) {
        Object v = server.get(key);
        return v != null ? v : defaults.get(key);
    }

    @NonNull
    @Override
    public String getString(@NonNull String key) {
        return String.valueOf(value(key));
    }

    @Override
    public boolean getBoolean(@NonNull String key) {
        return Boolean.parseBoolean(String.valueOf(value(key)));
    }

    @Override
    public long getLong(@NonNull String key) {
        return Long.parseLong(String.valueOf(value(key)));
    }

    @Override
    public double getDouble(@NonNull String key) {
        return Double.parseDouble(String.valueOf(value(key)));
    }

    @Override
    public void onUpdated(@NonNull Runnable listener) {
        update = listener;
    }
}

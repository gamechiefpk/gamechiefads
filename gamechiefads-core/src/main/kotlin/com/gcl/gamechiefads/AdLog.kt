package com.gcl.gamechiefads

import android.util.Log

/**
 * Debug-only trace log. Logcat tag "GamechiefAds", every line starts with an area: `[interstitial] ...`.
 * Switched on only for debuggable apps (see AdsRuntime) and silent in release. The message is a lambda,
 * so nothing is even built while logging is off. Filter with: `adb logcat -s GamechiefAds`.
 * Public only because the ui module calls it; it is not part of the supported API.
 */
object AdLog {
    const val TAG = "GamechiefAds"

    @Volatile @JvmField var enabled = false

    @Suppress("NOTHING_TO_INLINE")
    inline fun d(area: String, message: () -> String) {
        if (enabled) write(area, message())
    }

    @PublishedApi
    internal fun write(area: String, message: String) {
        try {
            Log.d(TAG, "[$area] $message")
        } catch (t: Throwable) { /* never let logging break an ad flow */ }
    }
}

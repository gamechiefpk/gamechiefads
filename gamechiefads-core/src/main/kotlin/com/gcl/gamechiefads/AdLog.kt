package com.gcl.gamechiefads

import android.util.Log

/** Debug-build-only logging (tag "GamechiefAds"): why an ad did or did not appear. Silent in release. */
internal object AdLog {
    @Volatile var enabled = false

    fun d(message: String) {
        if (enabled) Log.d("GamechiefAds", message)
    }
}

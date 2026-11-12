package com.gcl.gamechiefads.sdk

import android.os.Handler
import android.os.Looper

/**
 * The SDK reports ad events on its own threads. Everything above the sdk/ package (shower, listeners, the app's
 * onComplete / onDone) expects the main thread, so every SDK callback hops here first.
 */
internal object MainThread {
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    fun run(block: () -> Unit) {
        if (Looper.myLooper() === Looper.getMainLooper()) block() else handler.post(block)
    }
}

package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Result of the splash preparation. Shows the first ad, and the second ONLY after the first closed
 * (after a short settle), then calls onDone exactly once. Never shows an ad twice.
 */
internal class SplashAdsImpl(
    private val state: AdState,
    private val shower: FullScreenShower,
    private val screens: ScreenHost,
    private val scope: CoroutineScope,
    private val settleMs: Long = SETTLE_MS,
) : SplashAds {
    @Volatile var first: FullScreenAd? = null
    @Volatile var second: FullScreenAd? = null
    @Volatile var secondJob: Job? = null
    private var used = false

    override fun hasAd(): Boolean = first != null

    override fun show(activity: Activity, onDone: Runnable) {
        val done = Once(onDone)
        val a = first
        if (used || a == null || state.premium.value || !screens.isUsable(activity)) {
            dispose()
            done.run()
            return
        }
        used = true
        first = null
        shower.show(activity, a, object : FullScreenShower.Events {
            override fun onShown() {}
            override fun onFinished(shown: Boolean, reason: String?) {
                scope.launch {
                    delay(settleMs)
                    showSecond(activity, done)
                }
            }
        })
    }

    /** Frees anything not shown. Safe to call more than once. */
    fun dispose() {
        secondJob?.cancel()
        first?.destroy(); first = null
        second?.destroy(); second = null
    }

    private fun showSecond(activity: Activity, done: Once) {
        secondJob?.cancel()
        val b = second
        second = null
        if (b == null || state.premium.value || !screens.isUsable(activity)) {
            b?.destroy()
            done.run()
            return
        }
        shower.show(activity, b, object : FullScreenShower.Events {
            override fun onShown() {}
            override fun onFinished(shown: Boolean, reason: String?) = done.run()
        })
    }

    private class Once(private val r: Runnable) {
        private var ran = false
        fun run() {
            if (ran) return
            ran = true
            r.run()
        }
    }

    companion object {
        const val SETTLE_MS = 300L
    }
}

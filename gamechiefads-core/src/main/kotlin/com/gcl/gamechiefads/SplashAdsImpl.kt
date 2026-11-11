package com.gcl.gamechiefads

import android.app.Activity

/**
 * Result of the splash preparation. Shows the first ad and, if one was loaded, the second right behind it
 * (no gap, the ad break looks continuous), then calls onDone exactly once. Never shows an ad twice.
 */
internal class SplashAdsImpl(
    private val state: AdState,
    private val shower: FullScreenShower,
    private val screens: ScreenHost,
) : SplashAds {
    @Volatile var first: FullScreenAd? = null
    @Volatile var second: FullScreenAd? = null
    private var used = false

    override fun hasAd(): Boolean = first != null

    override fun show(activity: Activity, onDone: Runnable) {
        val done = Once(onDone)
        val a = first
        if (used || a == null || state.premium.value || !screens.isUsable(activity)) {
            AdLog.d("splash") { "show: no ad shown (used=$used, hasAd=${a != null}, premium=${state.premium.value}); onDone" }
            dispose()
            done.run()
            return
        }
        used = true
        val b = second
        first = null
        second = null
        AdLog.d("splash") { "show ${if (b != null) "2 ads back to back" else "1 ad"}" }
        shower.showAll(activity, listOfNotNull(a, b), object : FullScreenShower.Events {
            override fun onShown() {}
            override fun onFinished(shown: Boolean, reason: String?) {
                AdLog.d("splash") { "ad break finished (shown=$shown); onDone" }
                done.run()
            }
        })
    }

    /** Frees anything not shown. Safe to call more than once. */
    fun dispose() {
        first?.destroy(); first = null
        second?.destroy(); second = null
    }

    private class Once(private val r: Runnable) {
        private var ran = false
        fun run() {
            if (ran) return
            ran = true
            r.run()
        }
    }
}

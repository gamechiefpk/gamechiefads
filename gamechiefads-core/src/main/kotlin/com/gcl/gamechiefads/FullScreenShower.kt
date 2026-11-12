package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shows loaded [FullScreenAd]s and keeps [AdState] right: claims the "ad showing" flag, records the
 * shown time, releases the flag and the ads when done. Reports once. A watchdog frees the flag if the
 * SDK never reports shown or failed (no stuck screen, no permanently blocked ads).
 *
 * With several ads ([showAll]) all are started at the same moment and stack on screen (the user closes the top one
 * and the next is already underneath), the "ad showing" flag is held until the last closes, so it feels like one ad
 * break. The report ([Events.onFinished]) comes once, after the last ad.
 */
internal class FullScreenShower(
    private val state: AdState,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val startTimeoutMs: Long = START_TIMEOUT_MS,
) {
    interface Events {
        fun onShown()

        /** Once. shown=true: at least one ad was shown and closed. shown=false: nothing shown, [reason] says why. */
        fun onFinished(shown: Boolean, reason: String?)
    }

    fun show(activity: Activity, ad: FullScreenAd, events: Events) = showAll(activity, listOf(ad), events)

    fun showAll(activity: Activity, ads: List<FullScreenAd>, events: Events) {
        if (ads.isEmpty()) {
            events.onFinished(false, "no ad")
            return
        }
        state.setAdShowing(true)
        AdLog.d("shower") { "show ${ads.size} ad(s) on ${activity.javaClass.simpleName} at the same time, adShowing=true" }
        var finished = false
        var anyShown = false
        var remaining = ads.size
        var lastReason: String? = null

        fun finish() {
            if (finished) return
            finished = true
            AdLog.d("shower") { "finish shown=$anyShown reason=${if (anyShown) null else lastReason}, adShowing=false" }
            state.setAdShowing(false)
            events.onFinished(anyShown, if (anyShown) null else lastReason)
        }

        // All ads are started now, one right after the other. They stack on screen: the user closes the top one
        // and the next is already there, so it feels like the first ad never closed. No gap, no new launch.
        ads.forEachIndexed { index, ad ->
            var shown = false
            var done = false
            var watchdog: Job? = null

            fun adDone(wasShown: Boolean, reason: String?) {
                if (done) return
                done = true
                watchdog?.cancel()
                ad.destroy()
                if (!wasShown) lastReason = reason
                AdLog.d("shower") { "ad $index closed (shown=$wasShown), ${remaining - 1} left" }
                remaining--
                if (remaining == 0) finish()
            }

            watchdog = scope.launch {
                delay(startTimeoutMs)
                if (!shown) {
                    AdLog.d("shower") { "watchdog: ad $index never started within ${startTimeoutMs}ms" }
                    adDone(false, "show timeout")
                }
            }
            ad.show(activity, object : FullScreenAd.Listener {
                override fun onShown() {
                    shown = true
                    watchdog?.cancel()
                    state.markShown(clock())
                    AdLog.d("shower") { "ad $index on screen (gap + screen counter reset)" }
                    if (!anyShown) {
                        anyShown = true
                        if (!finished) events.onShown()
                    }
                }

                override fun onDismissed() = adDone(shown, null)
                override fun onFailed(reason: String) = adDone(false, reason)
            })
        }
    }

    companion object {
        const val START_TIMEOUT_MS = 5_000L
    }
}

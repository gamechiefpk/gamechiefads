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
 * With several ads ([showAll]) the next one is started inside the dismiss callback of the previous one,
 * with no delay and without ever releasing the "ad showing" flag, so the user sees one continuous ad
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
        AdLog.d("shower") { "show ${ads.size} ad(s) on ${activity.javaClass.simpleName}, adShowing=true" }
        var finished = false
        var anyShown = false
        var handedOut = -1 // highest index given to the SDK

        fun finish(wasShown: Boolean, reason: String?) {
            if (finished) return
            finished = true
            AdLog.d("shower") { "finish shown=$wasShown reason=$reason, adShowing=false" }
            state.setAdShowing(false)
            for (i in (handedOut + 1) until ads.size) ads[i].destroy() // never shown
            events.onFinished(wasShown, reason)
        }

        fun showAt(index: Int) {
            handedOut = index
            val ad = ads[index]
            var shown = false
            var done = false
            var watchdog: Job? = null

            fun adDone(wasShown: Boolean, reason: String?) {
                if (done) return
                done = true
                watchdog?.cancel()
                ad.destroy()
                if (wasShown && index + 1 < ads.size && !state.premium.value) {
                    AdLog.d("shower") { "ad $index closed -> next ad right away" }
                    showAt(index + 1)
                } else {
                    finish(anyShown || wasShown, reason)
                }
            }

            watchdog = scope.launch {
                delay(startTimeoutMs)
                if (!shown) {
                    AdLog.d("shower") { "watchdog: ad never started within ${startTimeoutMs}ms" }
                    adDone(false, "show timeout")
                }
            }
            ad.show(activity, object : FullScreenAd.Listener {
                override fun onShown() {
                    shown = true
                    watchdog?.cancel()
                    state.markShown(clock())
                    AdLog.d("shower") { "ad on screen (gap + screen counter reset)" }
                    if (!anyShown) {
                        anyShown = true
                        if (!finished) events.onShown()
                    }
                }

                override fun onDismissed() = adDone(shown, null)
                override fun onFailed(reason: String) = adDone(false, reason)
            })
        }
        showAt(0)
    }

    companion object {
        const val START_TIMEOUT_MS = 5_000L
    }
}

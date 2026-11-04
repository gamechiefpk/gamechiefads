package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Shows one loaded [FullScreenAd] and keeps [AdState] right: claims the "ad showing" flag, records the
 * shown time, releases the flag and the ad when done. Reports once. A watchdog frees the flag if the
 * SDK never reports shown or failed (no stuck screen, no permanently blocked ads).
 */
internal class FullScreenShower(
    private val state: AdState,
    private val clock: () -> Long,
    private val scope: CoroutineScope,
    private val startTimeoutMs: Long = START_TIMEOUT_MS,
) {
    interface Events {
        fun onShown()

        /** Once. shown=true: shown and closed. shown=false: never shown, [reason] says why. */
        fun onFinished(shown: Boolean, reason: String?)
    }

    fun show(activity: Activity, ad: FullScreenAd, events: Events) {
        state.setAdShowing(true)
        var finished = false
        var shown = false
        var watchdog: Job? = null

        fun finish(wasShown: Boolean, reason: String?) {
            if (finished) return
            finished = true
            watchdog?.cancel()
            state.setAdShowing(false)
            ad.destroy()
            events.onFinished(wasShown, reason)
        }

        watchdog = scope.launch {
            delay(startTimeoutMs)
            if (!shown) finish(false, "show timeout")
        }
        ad.show(activity, object : FullScreenAd.Listener {
            override fun onShown() {
                shown = true
                watchdog?.cancel()
                state.markShown(clock())
                if (!finished) events.onShown()
            }

            override fun onDismissed() = finish(shown, null)
            override fun onFailed(reason: String) = finish(false, reason)
        })
    }

    companion object {
        const val START_TIMEOUT_MS = 5_000L
    }
}

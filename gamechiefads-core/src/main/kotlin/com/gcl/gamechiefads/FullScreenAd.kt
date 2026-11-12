package com.gcl.gamechiefads

import android.app.Activity

/**
 * SDK-neutral handle to one loaded full-screen ad (interstitial or app open).
 * Controllers and the pool only know this type, so they are testable without the SDK.
 * Implementations must never throw: failures go to [Listener.onFailed].
 */
internal interface FullScreenAd {
    val unitId: String
    fun show(activity: Activity, listener: Listener)
    fun destroy()

    interface Listener {
        fun onShown()
        fun onDismissed()
        fun onFailed(reason: String)

        /** Rewarded ads only: the user earned the reward (before the ad is closed). */
        fun onRewarded(amount: Int, type: String) {}
    }
}

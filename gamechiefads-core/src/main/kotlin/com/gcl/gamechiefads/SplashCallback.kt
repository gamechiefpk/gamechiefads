package com.gcl.gamechiefads

import android.app.Activity

/** Called exactly once, inside the splash time budget, with or without an ad. */
fun interface SplashCallback {
    fun onReady(ads: SplashAds)
}

/** What [GamechiefAds.prepareSplash] resolved. Safe to use when [hasAd] is false. */
interface SplashAds {
    fun hasAd(): Boolean

    /**
     * Shows the first ad, then the second only after the first closed, then calls [onDone] ONCE.
     * [onDone] also runs immediately when there is nothing to show. Navigate inside it.
     */
    fun show(activity: Activity, onDone: Runnable)
}

package com.gcl.gamechiefads

import android.app.Activity

/**
 * A claim on "the one full-screen ad slot", for full-screen things the library does not show itself
 * (the ui module's full-screen native ad). While held, no interstitial, app-open or random ad can start;
 * [markShown] restarts the gap and screen counter like any ad. Always [release] it when the screen closes.
 */
class FullScreenSlot internal constructor(private val state: AdState, private val clock: () -> Long) {
    private var released = false

    fun markShown() = state.markShown(clock())

    @Synchronized
    fun release() {
        if (released) return
        released = true
        state.setAdShowing(false)
    }
}

/** Hands out the slot only when the placement's hard rules allow (enabled, not premium, no other ad showing). */
internal class FullScreenSlots(
    private val state: AdState,
    private val config: () -> AdsConfig,
    private val screens: ScreenHost,
    private val clock: () -> Long,
) {
    fun open(activity: Activity, name: String): FullScreenSlot? {
        val p = config().placement(name) ?: return null
        val decision = AdPolicy.evaluateExplicit(p, state.context(clock(), activity.javaClass.name))
        if (decision != Decision.ALLOW || !screens.isUsable(activity)) {
            AdLog.d("slot") { "$name: full-screen slot denied ($decision, usable=${screens.isUsable(activity)})" }
            return null
        }
        state.setAdShowing(true)
        AdLog.d("slot") { "$name: full-screen slot taken" }
        return FullScreenSlot(state, clock)
    }
}

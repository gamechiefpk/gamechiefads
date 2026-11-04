package com.gcl.gamechiefads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** The only shared mutable state of the library. Thread-safe; no Android imports. */
internal class AdState {
    private val _premium = MutableStateFlow(false)
    private val _adShowing = MutableStateFlow(false)
    private val _lastShownAtMs = MutableStateFlow<Long?>(null)
    private val _screensSinceAd = MutableStateFlow(0)

    val premium: StateFlow<Boolean> = _premium
    val adShowing: StateFlow<Boolean> = _adShowing
    val lastShownAtMs: StateFlow<Long?> = _lastShownAtMs
    val screensSinceAd: StateFlow<Int> = _screensSinceAd

    fun setPremium(value: Boolean) { _premium.value = value }
    fun setAdShowing(value: Boolean) { _adShowing.value = value }

    /** Counts one resumed (non-excluded) screen. */
    fun onScreenResumed() { _screensSinceAd.update { it + 1 } }

    /** Any ad (interstitial or app open) was shown: restarts the gap and the screen counter. */
    fun markShown(nowMs: Long) {
        _lastShownAtMs.value = nowMs
        _screensSinceAd.value = 0
    }

    fun context(nowMs: Long, screenName: String?) = AdContext(
        premium = _premium.value,
        adShowing = _adShowing.value,
        lastShownAtMs = _lastShownAtMs.value,
        screensSinceAd = _screensSinceAd.value,
        nowMs = nowMs,
        screenName = screenName,
    )
}

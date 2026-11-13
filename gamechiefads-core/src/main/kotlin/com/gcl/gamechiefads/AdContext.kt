package com.gcl.gamechiefads

/** Immutable snapshot of everything [AdPolicy] needs to decide. */
internal data class AdContext(
    val premium: Boolean,
    val adShowing: Boolean,
    val lastShownAtMs: Long?,
    val screensSinceAd: Int,
    val nowMs: Long,
    val screenName: String?,
    /** When the last app-open ad was closed (null = never). Interstitials stay quiet for [quietAfterAppOpenMs] after it. */
    val lastAppOpenClosedAtMs: Long? = null,
    val quietAfterAppOpenMs: Long = 0,
)

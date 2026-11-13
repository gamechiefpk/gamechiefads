package com.gcl.gamechiefads

internal enum class Decision {
    ALLOW, DENY_DISABLED, DENY_PREMIUM, DENY_AD_SHOWING, DENY_EXCLUDED_SCREEN, DENY_MIN_GAP, DENY_EVERY_N
}

/** PURE logic: "may I show now?". No Android, no state, no clock. */
internal object AdPolicy {

    /** Automatic triggers (random interstitial): all rules apply. */
    fun evaluate(p: PlacementConfig, c: AdContext): Decision {
        val base = evaluateExplicit(p, c)
        if (base != Decision.ALLOW) return base
        if (c.screenName != null && c.screenName in p.excludedScreens) return Decision.DENY_EXCLUDED_SCREEN
        val last = c.lastShownAtMs
        if (p.minGapSeconds > 0 && last != null && c.nowMs - last < p.minGapSeconds * 1000L) return Decision.DENY_MIN_GAP
        if (p.everyNScreens > 0 && c.screensSinceAd < p.everyNScreens) return Decision.DENY_EVERY_N
        return Decision.ALLOW
    }

    /** A click-driven random call: hard rules, excluded screens, min gap and every-N-calls. */
    fun evaluateClick(p: PlacementConfig, c: AdContext, calls: Int): Decision {
        val base = evaluateExplicit(p, c)
        if (base != Decision.ALLOW) return base
        if (c.screenName != null && c.screenName in p.excludedScreens) return Decision.DENY_EXCLUDED_SCREEN
        val last = c.lastShownAtMs
        if (p.minGapSeconds > 0 && last != null && c.nowMs - last < p.minGapSeconds * 1000L) return Decision.DENY_MIN_GAP
        if (p.everyNCalls > 1 && calls < p.everyNCalls) return Decision.DENY_EVERY_N
        return Decision.ALLOW
    }

    /** Explicit calls from the app: only the hard rules, no pacing. */
    fun evaluateExplicit(p: PlacementConfig, c: AdContext): Decision = when {
        !p.enabled -> Decision.DENY_DISABLED
        c.premium -> Decision.DENY_PREMIUM
        c.adShowing -> Decision.DENY_AD_SHOWING
        else -> Decision.ALLOW
    }
}

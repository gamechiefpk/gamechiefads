package com.gcl.gamechiefads

import org.junit.Assert.assertEquals
import org.junit.Test

class AdPolicyTest {
    private val rule = PlacementConfig(
        "random", AdFormat.INTERSTITIAL, everyNScreens = 3, minGapSeconds = 30,
        excludedScreens = setOf("com.app.Splash"),
    )
    private val ok = AdContext(
        premium = false, adShowing = false, lastShownAtMs = 0L,
        screensSinceAd = 3, nowMs = 60_000L, screenName = "com.app.Home",
    )

    @Test fun allowsWhenAllRulesPass() = assertEquals(Decision.ALLOW, AdPolicy.evaluate(rule, ok))
    @Test fun deniesDisabled() = assertEquals(Decision.DENY_DISABLED, AdPolicy.evaluate(rule.copy(enabled = false), ok))
    @Test fun deniesPremium() = assertEquals(Decision.DENY_PREMIUM, AdPolicy.evaluate(rule, ok.copy(premium = true)))
    @Test fun deniesWhileAdShowing() = assertEquals(Decision.DENY_AD_SHOWING, AdPolicy.evaluate(rule, ok.copy(adShowing = true)))
    @Test fun deniesExcludedScreen() =
        assertEquals(Decision.DENY_EXCLUDED_SCREEN, AdPolicy.evaluate(rule, ok.copy(screenName = "com.app.Splash")))
    @Test fun deniesInsideMinGap() =
        assertEquals(Decision.DENY_MIN_GAP, AdPolicy.evaluate(rule, ok.copy(lastShownAtMs = 40_000L)))
    @Test fun allowsExactlyAtGap() =
        assertEquals(Decision.ALLOW, AdPolicy.evaluate(rule, ok.copy(lastShownAtMs = 30_000L)))
    @Test fun neverShownIgnoresGap() =
        assertEquals(Decision.ALLOW, AdPolicy.evaluate(rule, ok.copy(lastShownAtMs = null)))
    @Test fun deniesBeforeNScreens() =
        assertEquals(Decision.DENY_EVERY_N, AdPolicy.evaluate(rule, ok.copy(screensSinceAd = 2)))
    @Test fun allowsMoreThanNScreens() =
        assertEquals(Decision.ALLOW, AdPolicy.evaluate(rule, ok.copy(screensSinceAd = 7)))
    @Test fun zeroRulesMeansNoPacing() {
        val open = rule.copy(everyNScreens = 0, minGapSeconds = 0, excludedScreens = emptySet())
        assertEquals(Decision.ALLOW, AdPolicy.evaluate(open, ok.copy(screensSinceAd = 0, lastShownAtMs = 59_999L)))
    }
    @Test fun unknownScreenIsNotExcluded() =
        assertEquals(Decision.ALLOW, AdPolicy.evaluate(rule, ok.copy(screenName = null)))

    @Test fun explicitIgnoresPacingAndExclusions() {
        val c = ok.copy(screensSinceAd = 0, lastShownAtMs = 59_000L, screenName = "com.app.Splash")
        assertEquals(Decision.ALLOW, AdPolicy.evaluateExplicit(rule, c))
    }
    @Test fun explicitStillHonoursPremiumAndShowing() {
        assertEquals(Decision.DENY_PREMIUM, AdPolicy.evaluateExplicit(rule, ok.copy(premium = true)))
        assertEquals(Decision.DENY_AD_SHOWING, AdPolicy.evaluateExplicit(rule, ok.copy(adShowing = true)))
    }
}

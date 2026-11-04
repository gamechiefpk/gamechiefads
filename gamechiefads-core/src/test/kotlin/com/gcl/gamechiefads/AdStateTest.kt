package com.gcl.gamechiefads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdStateTest {
    @Test fun startsClean() {
        val s = AdState()
        assertFalse(s.premium.value)
        assertFalse(s.adShowing.value)
        assertNull(s.lastShownAtMs.value)
        assertEquals(0, s.screensSinceAd.value)
    }

    @Test fun premiumAndShowingToggle() {
        val s = AdState()
        s.setPremium(true); s.setAdShowing(true)
        assertTrue(s.premium.value); assertTrue(s.adShowing.value)
        s.setPremium(false); s.setAdShowing(false)
        assertFalse(s.premium.value); assertFalse(s.adShowing.value)
    }

    @Test fun screensCountUpAndMarkShownResets() {
        val s = AdState()
        repeat(3) { s.onScreenResumed() }
        assertEquals(3, s.screensSinceAd.value)
        s.markShown(5_000L)
        assertEquals(0, s.screensSinceAd.value)
        assertEquals(5_000L, s.lastShownAtMs.value)
    }

    @Test fun contextReflectsState() {
        val s = AdState()
        s.setPremium(true); s.onScreenResumed(); s.markShown(100L); s.onScreenResumed()
        assertEquals(AdContext(true, false, 100L, 1, 900L, "X"), s.context(nowMs = 900L, screenName = "X"))
    }
}

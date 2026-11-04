package com.gcl.gamechiefads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsConfigTest {
    private class SplashActivity

    @Test fun buildsSketchFromTheBrief() {
        val c = AdsConfig.builder()
            .interstitial("random").units("HF", "MF", "NORMAL").everyNScreens(3).minGapSeconds(30)
            .excludeScreens(SplashActivity::class.java)
            .appOpen("splash").units("o1").tierTimeoutMs(3000)
            .remoteConfigKey("ads_config")
            .build()

        val r = c.placement("random")!!
        assertEquals(AdFormat.INTERSTITIAL, r.format)
        assertEquals(listOf("HF", "MF", "NORMAL"), r.units)
        assertEquals(3, r.everyNScreens)
        assertEquals(30, r.minGapSeconds)
        assertEquals(setOf(SplashActivity::class.java.name), r.excludedScreens)
        assertEquals(AdMode.ON_DEMAND, r.mode)
        assertEquals(AdFormat.APP_OPEN, c.placement("splash")!!.format)
        assertEquals("ads_config", c.remoteConfigKey)
        assertNull(c.placement("missing"))
    }

    @Test fun defaultsAreConservative() {
        val c = AdsConfig.builder().interstitial("x").build()
        assertFalse(c.fallbackToLoad)
        assertFalse(c.autoRefill)
        assertTrue(c.placement("x")!!.enabled)
        assertEquals(PlacementConfig.DEFAULT_TIER_TIMEOUT_MS, c.placement("x")!!.tierTimeoutMs)
        assertEquals(AdsConfig.DEFAULT_OVERALL_BUDGET_MS, c.overallBudgetMs)
    }

    @Test fun blankUnitsAreDropped() {
        val c = AdsConfig.builder().interstitial("x").units("a", "", " ", "b").build()
        assertEquals(listOf("a", "b"), c.placement("x")!!.units)
    }

    @Test fun negativeValuesAreClamped() {
        val p = AdsConfig.builder().interstitial("x").everyNScreens(-2).minGapSeconds(-1).tierTimeoutMs(0).build().placement("x")!!
        assertEquals(0, p.everyNScreens)
        assertEquals(0, p.minGapSeconds)
        assertEquals(1L, p.tierTimeoutMs)
    }

    @Test(expected = IllegalArgumentException::class) fun duplicatePlacementRejected() {
        AdsConfig.builder().interstitial("x").interstitial("x").build()
    }

    @Test(expected = IllegalArgumentException::class) fun blankNameRejected() {
        AdsConfig.builder().interstitial(" ")
    }

    @Test(expected = IllegalStateException::class) fun optionBeforePlacementRejected() {
        AdsConfig.builder().units("a")
    }
}

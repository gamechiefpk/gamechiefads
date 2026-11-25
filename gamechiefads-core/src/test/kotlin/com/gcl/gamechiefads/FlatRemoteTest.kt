package com.gcl.gamechiefads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FlatRemoteTest {
    private val base = AdsConfig.builder().splashAppOpenUnits("s").splashInterstitialUnits("si").randomInterstitialUnits("r")
        .bannerUnits("b").rewardedUnits("w").languageNativeUnits("l").build()

    private fun flat(vararg kv: Pair<String, String>) = FlatRemote.apply(base, mapOf(*kv)::get)

    @Test fun nothingSetChangesNothing() {
        assertEquals(base, FlatRemote.apply(base) { null })
        assertEquals(base, flat("splashDoubleEnable" to "  ", "randomClickAdInterval" to "x", "bannerAdType" to "nonsense"))
    }

    @Test fun doubleSwitchesAreTheOwnersMasterBothWays() {
        val on = flat("splashDoubleEnable" to "true", "doubleInterstitialAdEnable" to "true")
        assertTrue(on.placement(Placements.SPLASH)!!.wantsDouble(false)); assertTrue(on.placement(Placements.SPLASH_INTERSTITIAL)!!.wantsDouble(false))
        assertTrue(on.placement(Placements.RANDOM)!!.wantsDouble(false))
        val off = flat("splashDoubleEnable" to "false", "doubleInterstitialAdEnable" to "false")
        assertFalse(off.placement(Placements.SPLASH)!!.wantsDouble(true)); assertFalse(off.placement(Placements.RANDOM)!!.wantsDouble(true))
    }

    @Test fun randomClickKeys() {
        val c = flat("randomClickInterstitialAd" to "false", "randomClickAdInterval" to "4", "randomClickTimeGap" to "45", "randomClickAdAutoEnable" to "false")
        val r = c.placement(Placements.RANDOM)!!
        assertFalse(r.enabled); assertEquals(4, r.everyNScreens); assertEquals(4, r.everyNCalls); assertEquals(45, r.minGapSeconds); assertFalse(r.autoShow)
    }

    @Test fun bannerRewardedAndLanguageKeys() {
        val c = flat("bannerAdType" to "collapsible", "rewardedAdEnable" to "false", "langaugeSelectionNativeType" to "smallMedia")
        assertEquals(BannerType.COLLAPSIBLE_BOTTOM, c.placement(Placements.BANNER)!!.bannerType)
        assertFalse(c.placement(Placements.REWARDED)!!.enabled)
        assertEquals("small_media", c.placement(Placements.LANGUAGE_NATIVE)!!.nativeSize)
        assertEquals("medium", flat("languageSelectionNativeType" to "Medium").placement(Placements.LANGUAGE_NATIVE)!!.nativeSize) // the correct spelling works too
    }

    @Test fun nameParsing() {
        assertEquals("small_media", FlatRemote.normalize(" smallMedia ")); assertEquals("adaptive_large", FlatRemote.normalize("adaptive-Large"))
        assertEquals(BannerType.ADAPTIVE_LARGE, FlatRemote.bannerType("adaptiveLarge")); assertEquals(BannerType.ADAPTIVE, FlatRemote.bannerType("ADAPTIVE"))
        assertEquals(BannerType.COLLAPSIBLE_TOP, FlatRemote.bannerType("collapsibleTop")); assertNull(FlatRemote.bannerType("")); assertNull(FlatRemote.bannerType("x"))
    }

    @Test fun manualJsonStillWinsOverFlatKeys() {
        val c = RemoteOverrides.apply(flat("randomClickTimeGap" to "45"), """{"placements":{"random":{"gap":10}}}""")
        assertEquals(10, c.placement(Placements.RANDOM)!!.minGapSeconds)
    }
}

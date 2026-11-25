package com.gcl.gamechiefads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Extra ads (doubles, the full-screen native) are OFF until the config or Remote Config turns them on. */
class ExtraAdsOffByDefaultTest {
    private val c = AdsConfig.builder()
        .splashAppOpenUnits("s1", "s2").splashAppOpenDoubleUnit("s3")
        .splashInterstitialUnits("i1").splashInterstitialDoubleUnit("i2")
        .randomInterstitialUnits("r1", "r2").randomInterstitialDoubleUnit("r3")
        .languageNativeUnits("l1", "l2").introNativeUnits("n1").fullScreenNativeUnit("f1")
        .build()

    @Test fun theDoubleSwitchIsOffOnEverySlotEvenWhenTheSecondUnitIsGiven() {
        for (name in listOf(Placements.SPLASH, Placements.SPLASH_INTERSTITIAL, Placements.RANDOM)) {
            assertFalse("$name double", c.placement(name)!!.doubleAd)
            assertTrue("$name has its second unit ready", c.placement(name)!!.secondUnit != null)
        }
    }

    @Test fun remoteConfigTurnsTheDoubleOnPerSlot() {
        val on = RemoteOverrides.apply(c, """{"placements":{"splash":{"double":true},"random":{"double":true}}}""")
        assertTrue(on.placement(Placements.SPLASH)!!.wantsDouble()); assertTrue(on.placement(Placements.RANDOM)!!.wantsDouble())
        assertFalse(on.placement(Placements.SPLASH_INTERSTITIAL)!!.wantsDouble())          // only the slots named
    }

    @Test fun theFullScreenNativeIsOffUntilRemoteConfigOrTheCodeTurnsItOn() {
        assertFalse(c.placement(Placements.FULLSCREEN_NATIVE)!!.enabled)
        assertTrue(RemoteOverrides.apply(c, """{"placements":{"fullscreen_native":{"enabled":true}}}""").placement(Placements.FULLSCREEN_NATIVE)!!.enabled)
        assertTrue(AdsConfig.builder().fullScreenNativeUnit("f").enabled(true).build().placement(Placements.FULLSCREEN_NATIVE)!!.enabled)
        // the global "enabled": true does not switch a slot on that the code left off; the kill switch still wins
        assertFalse(RemoteOverrides.apply(c, """{"enabled":true}""").placement(Placements.FULLSCREEN_NATIVE)!!.enabled)
        assertFalse(RemoteOverrides.apply(c, """{"enabled":false,"placements":{"fullscreen_native":{"enabled":true}}}""").placement(Placements.FULLSCREEN_NATIVE)!!.enabled)
    }

    @Test fun theLanguageNativeIsAFixedNativeSlotWithFloorsAndTheOtherNativesStayOn() {
        val l = c.placement(Placements.LANGUAGE_NATIVE)!!
        assertEquals(AdFormat.NATIVE, l.format); assertEquals(listOf("l1", "l2"), l.units); assertTrue(l.enabled)
        assertTrue(c.placement(Placements.INTRO_NATIVE)!!.enabled)
    }
}

class DoubleSwitchTest {
    private fun slot(second: String? = "u2", code: Boolean = false, remote: Boolean? = null): PlacementConfig {
        var b = AdsConfig.builder().randomInterstitialUnits("u1")
        if (second != null) b = b.randomInterstitialDoubleUnit(second)
        if (code) b = b.doubleAd(true)
        val c = b.build()
        return (if (remote == null) c else RemoteOverrides.apply(c, """{"placements":{"random":{"double":$remote}}}""")).placement(Placements.RANDOM)!!
    }

    @Test fun remoteConfigIsTheMasterInBothDirections() {
        assertTrue(DoubleSwitch.resolve(slot(remote = true), false))          // code says single, Firebase says double: double
        assertTrue(DoubleSwitch.resolve(slot(remote = true), null))
        assertFalse(DoubleSwitch.resolve(slot(remote = false), true))         // code asks for double, Firebase says no: single
        assertFalse(DoubleSwitch.resolve(slot(code = true, remote = false), null))   // also over the config flag
    }

    @Test fun withoutAnyRemoteValueTheCodeDecidesAndBothOffMeansSingle() {
        assertTrue(DoubleSwitch.resolve(slot(), true)); assertTrue(DoubleSwitch.resolve(slot(code = true), null))
        assertFalse(DoubleSwitch.resolve(slot(), null)); assertFalse(DoubleSwitch.resolve(slot(), false))
        assertFalse(DoubleSwitch.resolve(slot(code = true), false))           // a call that says single beats the config flag
    }

    @Test fun noSecondUnitDeclaredStillDoublesWithTheSameRequest() {
        assertTrue(DoubleSwitch.resolve(slot(second = null, remote = true), false))   // remote master, same request twice
        assertFalse(DoubleSwitch.resolve(slot(second = null, code = true, remote = false), true))
        assertFalse(DoubleSwitch.resolve(null, true))                                 // unknown slot
        val p = slot(second = null)
        assertEquals(p, p.forSecondAd())                                              // the SAME request
        assertEquals(listOf("u2"), slot(second = "u2").forSecondAd().units)           // or the unit you declared
    }

    @Test fun aLaterRemoteLayerThatSaysNothingKeepsTheOwnersValue() {
        val c = AdsConfig.builder().randomInterstitialUnits("u1").randomInterstitialDoubleUnit("u2").build()
        val a = RemoteOverrides.apply(c, """{"placements":{"random":{"double":true}}}""")
        val b = RemoteOverrides.apply(a, """{"placements":{"random":{"gap":10}}}""")
        assertTrue(b.placement(Placements.RANDOM)!!.wantsDouble())
    }
}

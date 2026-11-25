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
        assertTrue(on.placement(Placements.SPLASH)!!.doubleAd); assertTrue(on.placement(Placements.RANDOM)!!.doubleAd)
        assertFalse(on.placement(Placements.SPLASH_INTERSTITIAL)!!.doubleAd)             // only the slots named
    }

    @Test fun theCodeSwitchStillWorksAndRemoteCanTurnItOff() {
        val code = AdsConfig.builder().randomInterstitialUnits("a").doubleAd(true).build()
        assertTrue(code.placement(Placements.RANDOM)!!.doubleAd)
        assertFalse(RemoteOverrides.apply(code, """{"placements":{"random":{"double":false}}}""").placement(Placements.RANDOM)!!.doubleAd)
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
    @Test fun theRemoteSwitchTurnsTheDoubleOnWhateverTheCodeAsked() {
        assertTrue(DoubleSwitch.resolve(null, true)); assertTrue(DoubleSwitch.resolve(false, true)); assertTrue(DoubleSwitch.resolve(true, true))
    }

    @Test fun theCodeCanAskForTheDoubleItselfAndBothOffMeansSingle() {
        assertTrue(DoubleSwitch.resolve(true, false))
        assertFalse(DoubleSwitch.resolve(null, false)); assertFalse(DoubleSwitch.resolve(false, false))
    }
}

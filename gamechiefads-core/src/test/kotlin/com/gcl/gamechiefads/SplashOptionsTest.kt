package com.gcl.gamechiefads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Splash options: app open (default) or interstitial, flooring on/off, double on/off, and every combination. */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashOptionsTest {
    private class Result {
        var calls = 0
        var ads: SplashAds? = null
        val callback = SplashCallback { calls++; ads = it }
    }

    private fun TestScope.rig(interstitial: Boolean, flooring: Boolean, double: Boolean): Rig {
        val b = AdsConfig.builder()
        if (interstitial) b.interstitial(Placements.SPLASH) else b.appOpen(Placements.SPLASH)
        b.units("hf", "mf", "normal").flooring(flooring).doubleAd(double)
        return Rig(this, b.build())
    }

    @Test fun appOpenIsTheDefaultSource() = runTest {
        val r = rig(interstitial = false, flooring = true, double = false); val res = Result()
        r.splash.prepare(r.activity, res.callback); advanceUntilIdle()
        assertEquals(listOf("hf"), r.loads); assertTrue(r.interstitialLoads.isEmpty())
    }

    @Test fun interstitialSplashUsesTheInterstitialSourceOnly() = runTest {
        val r = rig(interstitial = true, flooring = true, double = false); val res = Result()
        r.splash.prepare(r.activity, res.callback); advanceUntilIdle()
        assertTrue(r.loads.isEmpty()); assertEquals(listOf("hf"), r.interstitialLoads)
        assertTrue(res.ads!!.hasAd())
    }

    @Test fun flooringOnWalksEveryFloorUntilOneFills() = runTest {
        val r = rig(interstitial = false, flooring = true, double = false); val res = Result()
        r.loadBehavior = { if (it == "normal") FakeAd(it) else null }
        r.splash.prepare(r.activity, res.callback); advanceUntilIdle()
        assertEquals(listOf("hf", "mf", "normal"), r.loads); assertTrue(res.ads!!.hasAd())
    }

    @Test fun flooringOffMakesOneRequestToTheLowestFloorOnly() = runTest {
        val r = rig(interstitial = false, flooring = false, double = false); val res = Result()
        r.splash.prepare(r.activity, res.callback); advanceUntilIdle()
        assertEquals(listOf("normal"), r.loads)
    }

    @Test fun flooringOffWithNoFillDoesNotTryOtherUnits() = runTest {
        val r = rig(interstitial = false, flooring = false, double = false); val res = Result()
        r.loadBehavior = { null }
        r.splash.prepare(r.activity, res.callback); advanceUntilIdle()
        assertEquals(listOf("normal"), r.loads); assertFalse(res.ads!!.hasAd()); assertEquals(1, res.calls)
    }

    @Test fun doubleLoadsTwoFromTheSameUnitsAndShowsBackToBackThenNavigatesOnce() = runTest {
        val r = rig(interstitial = false, flooring = false, double = true); val res = Result()
        val a = FakeAd.manual(); val b = FakeAd.manual(); var n = 0
        r.loadBehavior = { if (n++ == 0) a else b }
        r.splash.prepare(r.activity, res.callback); advanceUntilIdle()
        assertEquals(2, r.loads.size)
        var done = 0
        res.ads!!.show(r.activity) { done++ }; runCurrent()
        assertEquals(1, a.shows); assertEquals(1, b.shows) // both at the same time
        b.listener!!.onDismissed()
        assertTrue(r.state.adShowing.value); assertEquals(0, done)
        a.listener!!.onDismissed()
        assertEquals(1, done); assertFalse(r.state.adShowing.value)
    }

    @Test fun doubleOffLoadsExactlyOne() = runTest {
        val r = rig(interstitial = false, flooring = false, double = false); val res = Result()
        r.splash.prepare(r.activity, res.callback); advanceUntilIdle()
        assertEquals(1, r.loads.size)
    }

    @Test fun interstitialWithDoubleAndNoFlooringStillBehaves() = runTest {
        val r = rig(interstitial = true, flooring = false, double = true); val res = Result()
        r.splash.prepare(r.activity, res.callback); advanceUntilIdle()
        assertEquals(listOf("normal", "normal"), r.interstitialLoads); assertTrue(r.loads.isEmpty())
        var done = 0
        res.ads!!.show(r.activity) { done++ }; advanceUntilIdle()
        assertEquals(1, done)
    }

    @Test fun missingSecondOfADoubleShowsOneAd() = runTest {
        val r = rig(interstitial = true, flooring = true, double = true); val res = Result()
        var n = 0
        r.loadBehavior = { if (n++ == 0) FakeAd(it) else null }
        r.splash.prepare(r.activity, res.callback); advanceUntilIdle()
        var done = 0
        res.ads!!.show(r.activity) { done++ }; advanceUntilIdle()
        assertEquals(1, done)
    }

    @Test fun remoteConfigSwitchesTheSplashToInterstitialAndTurnsFlooringAndDoubleOff() {
        val base = AdsConfig.builder().appOpen(Placements.SPLASH).units("a", "b").doubleAd(false).build()
        val p = RemoteOverrides.apply(
            base, """{"placements":{"splash":{"type":"interstitial","flooring":false,"double":true,"units":["i1","i2"]}}}""",
        ).placement(Placements.SPLASH)!!
        assertEquals(AdFormat.INTERSTITIAL, p.format); assertFalse(p.flooring); assertTrue(p.doubleAd)
        assertEquals(listOf("i2"), AdUnits.resolve(p, debug = false))
    }

    @Test fun remoteTypeIsIgnoredOutsideTheSplash() {
        val base = AdsConfig.builder().interstitial("random").units("a").build()
        val p = RemoteOverrides.apply(base, """{"placements":{"random":{"type":"app_open"}}}""").placement("random")!!
        assertEquals(AdFormat.INTERSTITIAL, p.format)
    }

    @Test fun flooringKeepsOneUnitEvenWhenAllButOneAreDisabled() {
        val p = PlacementConfig("x", AdFormat.APP_OPEN, units = listOf("a", "b", "c"), flooring = false, disabledUnits = setOf("c"))
        assertEquals(listOf("b"), AdUnits.resolve(p, debug = false))
    }
}

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

/** Splash slots: app open or interstitial (separate units), flooring on/off, single or double (separate second units). */
@OptIn(ExperimentalCoroutinesApi::class)
class SplashOptionsTest {
    private class Result {
        var calls = 0
        var ads: SplashAds? = null
        val callback = SplashCallback { calls++; ads = it }
    }

    private class Case(val rig: Rig, val interstitial: Boolean, val double: Boolean) {
        val res = Result()
        fun run() = rig.splash.prepare(
            rig.activity, res.callback,
            if (interstitial) Placements.SPLASH_INTERSTITIAL else Placements.SPLASH, double,
        )
    }

    private fun TestScope.case(interstitial: Boolean, flooring: Boolean, double: Boolean, secondUnits: Boolean = false): Case {
        val b = AdsConfig.builder()
        if (interstitial) b.splashInterstitialUnits("hf", "mf", "normal") else b.splashAppOpenUnits("hf", "mf", "normal")
        b.flooring(flooring)
        if (secondUnits) {
            if (interstitial) b.splashInterstitialDoubleUnits("hf2", "normal2") else b.splashAppOpenDoubleUnits("hf2", "normal2")
        }
        return Case(Rig(this, b.build()), interstitial, double)
    }

    @Test fun appOpenSlotUsesTheAppOpenSource() = runTest {
        val c = case(false, true, false); c.run(); advanceUntilIdle()
        assertEquals(listOf("hf"), c.rig.loads); assertTrue(c.rig.interstitialLoads.isEmpty())
    }

    @Test fun interstitialSlotUsesTheInterstitialSourceOnly() = runTest {
        val c = case(true, true, false); c.run(); advanceUntilIdle()
        assertTrue(c.rig.loads.isEmpty()); assertEquals(listOf("hf"), c.rig.interstitialLoads)
        assertTrue(c.res.ads!!.hasAd())
    }

    @Test fun flooringOnWalksEveryFloorUntilOneFills() = runTest {
        val c = case(false, true, false); c.rig.loadBehavior = { if (it == "normal") FakeAd(it) else null }
        c.run(); advanceUntilIdle()
        assertEquals(listOf("hf", "mf", "normal"), c.rig.loads); assertTrue(c.res.ads!!.hasAd())
    }

    @Test fun flooringOffMakesOneRequestToTheLowestFloorOnly() = runTest {
        val c = case(false, false, false); c.run(); advanceUntilIdle()
        assertEquals(listOf("normal"), c.rig.loads)
    }

    @Test fun flooringOffWithNoFillDoesNotTryOtherUnits() = runTest {
        val c = case(false, false, false); c.rig.loadBehavior = { null }
        c.run(); advanceUntilIdle()
        assertEquals(listOf("normal"), c.rig.loads); assertFalse(c.res.ads!!.hasAd()); assertEquals(1, c.res.calls)
    }

    @Test fun doubleLoadsTheSecondAdFromItsOwnUnitsAndShowsBothTogether() = runTest {
        val c = case(false, true, true, secondUnits = true)
        val first = FakeAd.manual(); val second = FakeAd.manual()
        c.rig.loadBehavior = { if (it.endsWith("2")) second else first }
        c.run(); advanceUntilIdle()
        assertEquals(listOf("hf", "hf2"), c.rig.loads.sorted().let { listOf(it[0], it[1]) }.sortedBy { it.length }) // one per unit set
        var done = 0
        c.res.ads!!.show(c.rig.activity) { done++ }; runCurrent()
        assertEquals(1, first.shows); assertEquals(1, second.shows) // at the same time
        second.listener!!.onDismissed()
        assertTrue(c.rig.state.adShowing.value); assertEquals(0, done)
        first.listener!!.onDismissed()
        assertEquals(1, done); assertFalse(c.rig.state.adShowing.value)
    }

    @Test fun doubleWithoutSecondUnitsUsesTheFirstUnitsTwice() = runTest {
        val c = case(false, false, true); c.run(); advanceUntilIdle()
        assertEquals(listOf("normal", "normal"), c.rig.loads)
    }

    @Test fun singleCallNeverLoadsTheSecondAdEvenIfSecondUnitsExist() = runTest {
        val c = case(false, true, false, secondUnits = true); c.run(); advanceUntilIdle()
        assertEquals(listOf("hf"), c.rig.loads)
    }

    @Test fun interstitialDoubleUsesTheInterstitialSourceForBoth() = runTest {
        val c = case(true, false, true, secondUnits = true); c.run(); advanceUntilIdle()
        assertTrue(c.rig.loads.isEmpty()); assertEquals(2, c.rig.interstitialLoads.size)
        var done = 0
        c.res.ads!!.show(c.rig.activity) { done++ }; advanceUntilIdle()
        assertEquals(1, done)
    }

    @Test fun missingSecondOfADoubleShowsOneAd() = runTest {
        val c = case(true, true, true)
        var n = 0
        c.rig.loadBehavior = { if (n++ == 0) FakeAd(it) else null }
        c.run(); advanceUntilIdle()
        var done = 0
        c.res.ads!!.show(c.rig.activity) { done++ }; advanceUntilIdle()
        assertEquals(1, done)
    }

    @Test fun remoteConfigCanChangeFlooringAndTheSecondUnits() {
        val base = AdsConfig.builder().splashAppOpenUnits("a", "b").build()
        val p = RemoteOverrides.apply(
            base, """{"placements":{"splash":{"flooring":false,"units":["x1","x2"],"secondUnits":["y1","y2"]}}}""",
        ).placement(Placements.SPLASH)!!
        assertFalse(p.flooring); assertEquals(listOf("y1", "y2"), p.secondUnits)
        assertEquals(listOf("x2"), AdUnits.resolve(p, debug = false))
    }

    @Test fun flooringKeepsOneUnitEvenWhenAllButOneAreDisabled() {
        val p = PlacementConfig("x", AdFormat.APP_OPEN, units = listOf("a", "b", "c"), flooring = false, disabledUnits = setOf("c"))
        assertEquals(listOf("b"), AdUnits.resolve(p, debug = false))
    }

    @Test fun doubleUnitsWithoutTheNormalUnitsFailAtBuildTime() {
        val e = runCatching { AdsConfig.builder().splashAppOpenDoubleUnits("a").build() }.exceptionOrNull()
        assertTrue(e is IllegalStateException)
    }

    @Test fun remoteEnabledFalseTurnsTheWholeConfigOff() {
        val base = AdsConfig.builder().splashAppOpenUnits("a").build()
        val c = RemoteOverrides.apply(base, """{"enabled":false}""")
        assertFalse(c.enabled); assertFalse(c.placement(Placements.SPLASH)!!.enabled)
    }
}

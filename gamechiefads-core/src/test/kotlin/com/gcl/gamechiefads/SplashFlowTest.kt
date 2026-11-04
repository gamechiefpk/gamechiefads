package com.gcl.gamechiefads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SplashFlowTest {
    private fun TestScope.rig(second: Boolean = false, ready: Boolean = true): Rig {
        val b = AdsConfig.builder().appOpen(Placements.SPLASH).units("s1")
        if (second) b.appOpen(Placements.SPLASH_SECOND).units("s2")
        return Rig(this, b.splashTimeoutMs(10_000).build(), ready)
    }

    private class Result {
        var calls = 0
        var ads: SplashAds? = null
        val callback = SplashCallback { calls++; ads = it }
    }

    @Test fun withAdReadyBeforeTheTimeout() = runTest {
        val r = rig(); val res = Result()
        r.splash.prepare(r.activity, res.callback)
        advanceUntilIdle()
        assertEquals(1, res.calls)
        assertTrue(res.ads!!.hasAd())
    }

    @Test fun premiumGetsNoAdAndNoRequest() = runTest {
        val r = rig(); val res = Result()
        r.state.setPremium(true)
        r.splash.prepare(r.activity, res.callback)
        advanceUntilIdle()
        assertEquals(1, res.calls); assertFalse(res.ads!!.hasAd()); assertTrue(r.loads.isEmpty())
        var done = 0
        res.ads!!.show(r.activity) { done++ }
        assertEquals(1, done)
    }

    @Test fun consentDeniedOrSdkNotReadyMeansNoAd() = runTest {
        val r = rig(ready = false); val res = Result()
        r.splash.prepare(r.activity, res.callback)
        advanceUntilIdle()
        assertEquals(1, res.calls); assertFalse(res.ads!!.hasAd()); assertTrue(r.loads.isEmpty())
    }

    @Test fun slowNetworkEndsAtTheHardTimeoutWithoutAnAd() = runTest {
        // tier and overall limits are loose here on purpose: the splash hard limit must still win
        val r = Rig(
            this,
            AdsConfig.builder().appOpen(Placements.SPLASH).units("s1").tierTimeoutMs(60_000)
                .overallBudgetMs(60_000).splashTimeoutMs(10_000).build(),
        )
        val res = Result()
        r.loadBehavior = { delay(60_000); FakeAd(it) }
        r.splash.prepare(r.activity, res.callback)
        advanceTimeBy(9_999); runCurrent()
        assertEquals(0, res.calls)
        advanceTimeBy(2); runCurrent()
        assertEquals(1, res.calls); assertFalse(res.ads!!.hasAd())
        assertEquals(10_001, currentTime) // never the old 15 s
    }

    @Test fun secondAdShowsOnlyAfterTheFirstClosedAndNavigatesOnce() = runTest { // old bug 3
        val r = rig(second = true); val res = Result()
        val first = FakeAd.manual(); val secondAd = FakeAd.manual()
        r.loadBehavior = { if (it == "s1") first else secondAd }
        r.splash.prepare(r.activity, res.callback)
        advanceUntilIdle()
        var done = 0
        res.ads!!.show(r.activity) { done++ }
        runCurrent()
        assertEquals(1, first.shows); assertEquals(0, secondAd.shows)
        advanceTimeBy(5_000)
        assertEquals(0, secondAd.shows) // still waiting for the first to close
        first.listener!!.onDismissed()
        assertEquals(0, secondAd.shows) // not immediately...
        advanceTimeBy(SplashAdsImpl.SETTLE_MS + 1); runCurrent()
        assertEquals(1, secondAd.shows) // ...only after it closed
        assertEquals(0, done)
        secondAd.listener!!.onDismissed()
        assertEquals(1, done)
    }

    @Test fun aSlowTierGivesUpAtItsOwnTimeoutNotTheHardLimit() = runTest {
        val r = rig(); val res = Result()
        r.loadBehavior = { delay(60_000); FakeAd(it) }
        r.splash.prepare(r.activity, res.callback)
        advanceUntilIdle()
        assertEquals(1, res.calls); assertFalse(res.ads!!.hasAd())
        assertEquals(3_000, currentTime) // default per-tier timeout
    }

    @Test fun missingSecondAdJustFinishesAfterTheFirst() = runTest {
        val r = rig(second = true); val res = Result()
        r.loadBehavior = { if (it == "s1") FakeAd() else null }
        r.splash.prepare(r.activity, res.callback)
        advanceUntilIdle()
        var done = 0
        res.ads!!.show(r.activity) { done++ }
        advanceUntilIdle()
        assertEquals(1, done)
    }

    @Test fun showingTwiceNeverReshowsAndNeverNavigatesTwiceInOneCall() = runTest {
        val r = rig(); val res = Result()
        val ad = FakeAd()
        r.loadBehavior = { ad }
        r.splash.prepare(r.activity, res.callback)
        advanceUntilIdle()
        var done = 0
        res.ads!!.show(r.activity) { done++ }
        advanceUntilIdle()
        res.ads!!.show(r.activity) { done++ }
        advanceUntilIdle()
        assertEquals(1, ad.shows)
        assertEquals(2, done) // each call's own onDone runs exactly once
    }

    @Test fun premiumTurnedOnBeforeShowSkipsStraightToDone() = runTest {
        val r = rig(); val res = Result()
        val ad = FakeAd()
        r.loadBehavior = { ad }
        r.splash.prepare(r.activity, res.callback)
        advanceUntilIdle()
        r.state.setPremium(true)
        var done = 0
        res.ads!!.show(r.activity) { done++ }
        assertEquals(1, done); assertEquals(0, ad.shows); assertTrue(ad.destroyed)
    }

    @Test fun noSplashPlacementConfiguredStillCallsBackOnce() = runTest {
        val r = Rig(this, AdsConfig.builder().interstitial("x").build()); val res = Result()
        r.splash.prepare(r.activity, res.callback)
        advanceUntilIdle()
        assertEquals(1, res.calls); assertFalse(res.ads!!.hasAd())
        assertNotNull(res.ads); assertNull(null)
    }
}

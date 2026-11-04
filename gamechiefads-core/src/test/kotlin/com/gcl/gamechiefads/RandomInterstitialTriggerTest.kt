package com.gcl.gamechiefads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RandomInterstitialTriggerTest {
    private fun TestScope.rig(options: AdsConfig.Builder.() -> AdsConfig.Builder = { this }) =
        Rig(this, AdsConfig.builder().interstitial(Placements.RANDOM).units("r").everyNScreens(3).options().build())

    private fun Rig.screens(n: Int) = repeat(n) { state.onScreenResumed() }

    @Test fun showsAfterNScreens() = runTest {
        val r = rig(); r.screens(3)
        r.random.onScreenResumed(r.activity)
        advanceUntilIdle()
        assertEquals(listOf("r"), r.loads)
        assertEquals(0, r.state.screensSinceAd.value) // counter restarts after the ad
    }

    @Test fun notBeforeNScreens() = runTest {
        val r = rig(); r.screens(2)
        r.random.onScreenResumed(r.activity)
        advanceUntilIdle()
        assertTrue(r.loads.isEmpty())
    }

    @Test fun excludedScreenNeverTriggers() = runTest {
        val r = rig { excludeScreens(TestActivity::class.java) }; r.screens(5)
        r.random.onScreenResumed(r.activity)
        advanceUntilIdle()
        assertTrue(r.loads.isEmpty())
    }

    @Test fun minGapBlocksTooSoon() = runTest {
        val r = rig { minGapSeconds(30) }; r.screens(5)
        r.state.markShown(r.now - 5_000); r.screens(5)
        r.random.onScreenResumed(r.activity)
        advanceUntilIdle()
        assertTrue(r.loads.isEmpty())
    }

    @Test fun premiumAndAdShowingBlock() = runTest {
        val r = rig(); r.screens(5)
        r.state.setPremium(true)
        r.random.onScreenResumed(r.activity); advanceUntilIdle()
        r.state.setPremium(false); r.state.setAdShowing(true)
        r.random.onScreenResumed(r.activity); advanceUntilIdle()
        assertTrue(r.loads.isEmpty())
    }

    @Test fun aNewScreenCancelsTheSettlingTrigger() = runTest {
        val r = rig(); r.screens(3)
        r.random.onScreenResumed(r.activity)
        r.random.onScreenResumed(r.activity) // quick second screen
        advanceUntilIdle()
        assertEquals(1, r.loads.size) // one ad, never two
    }

    @Test fun leavingTheScreenDuringSettleCancelsIt() = runTest {
        val r = rig(); r.screens(3)
        r.random.onScreenResumed(r.activity)
        r.screens.activity = null
        advanceUntilIdle()
        assertTrue(r.loads.isEmpty())
    }

    @Test fun noRandomPlacementConfiguredDoesNothing() = runTest {
        val r = Rig(this, AdsConfig.builder().interstitial("other").units("x").build())
        r.screens(9)
        r.random.onScreenResumed(r.activity); advanceUntilIdle()
        assertTrue(r.loads.isEmpty())
    }

    @Test fun preloadModeFillsFirstThenShowsWhatIsReady() = runTest {
        val r = rig { mode(AdMode.PRELOAD) }; r.screens(3)
        r.random.onScreenResumed(r.activity); advanceUntilIdle()
        assertEquals(1, r.loads.size) // asked for one for next time
        assertTrue(r.interstitial.isReady(Placements.RANDOM))
        assertEquals(3, r.state.screensSinceAd.value) // nothing shown yet
        r.random.onScreenResumed(r.activity); advanceUntilIdle()
        assertEquals(1, r.loads.size) // shown from the pool, no new request
        assertEquals(0, r.state.screensSinceAd.value)
    }
}

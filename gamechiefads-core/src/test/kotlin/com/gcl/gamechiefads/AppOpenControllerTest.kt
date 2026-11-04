package com.gcl.gamechiefads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppOpenControllerTest {
    private fun TestScope.rig(options: AdsConfig.Builder.() -> AdsConfig.Builder = { this }) =
        Rig(
            this,
            AdsConfig.builder().appOpen(Placements.APP_OPEN).units("o").excludeScreens(OtherActivity::class.java).options().build(),
        )

    @Test fun firstNormalScreenPrefetchesOnceOnly() = runTest {
        val r = rig()
        r.appOpen.onScreenResumed(r.activity); r.appOpen.onScreenResumed(r.activity)
        advanceUntilIdle()
        assertEquals(1, r.loads.size)
        assertTrue(r.appOpenPool.isReady(Placements.APP_OPEN))
    }

    @Test fun skipListScreenNeverPrefetchesOrShows() = runTest {
        val r = rig()
        val other = OtherActivity(); r.screens.activity = other
        r.appOpen.onScreenResumed(other)
        r.appOpen.onForeground()
        advanceUntilIdle()
        assertTrue(r.loads.isEmpty())
    }

    @Test fun foregroundShowsTheCachedAdThenRefetches() = runTest {
        val r = rig()
        val ad = FakeAd(); r.appOpenPool.put(Placements.APP_OPEN, ad)
        r.appOpen.onForeground(); advanceUntilIdle()
        assertEquals(1, ad.shows)
        assertEquals(1, r.loads.size) // fetched the next one after the show
        assertTrue(r.appOpenPool.isReady(Placements.APP_OPEN))
    }

    @Test fun foregroundWithoutAdFetchesForNextTime() = runTest {
        val r = rig()
        r.appOpen.onForeground(); advanceUntilIdle()
        assertEquals(1, r.loads.size)
        assertEquals(0, r.state.screensSinceAd.value)
    }

    @Test fun pauseOnceSkipsExactlyOneForeground() = runTest {
        val r = rig()
        val ad = FakeAd(); r.appOpenPool.put(Placements.APP_OPEN, ad)
        r.appOpen.pauseOnce()
        r.appOpen.onForeground(); advanceUntilIdle()
        assertEquals(0, ad.shows) // skipped (e.g. coming back from a purchase flow)
        r.appOpen.onForeground(); advanceUntilIdle()
        assertEquals(1, ad.shows)
    }

    @Test fun cooldownKeepsTheAdForLater() = runTest {
        val r = rig { minGapSeconds(30) }
        val ad = FakeAd(); r.appOpenPool.put(Placements.APP_OPEN, ad)
        r.state.markShown(r.now - 5_000)
        r.appOpen.onForeground(); advanceUntilIdle()
        assertEquals(0, ad.shows)
        assertFalse(ad.destroyed)
        r.now += 30_000
        r.appOpen.onForeground(); advanceUntilIdle()
        assertEquals(1, ad.shows)
    }

    @Test fun premiumAndShowingAdBlockEverything() = runTest {
        val r = rig()
        val ad = FakeAd(); r.appOpenPool.put(Placements.APP_OPEN, ad)
        r.state.setPremium(true)
        r.appOpen.onForeground(); advanceUntilIdle()
        r.state.setPremium(false); r.state.setAdShowing(true) // e.g. an interstitial is on screen
        r.appOpen.onForeground(); advanceUntilIdle()
        assertEquals(0, ad.shows)
        assertTrue(r.loads.isEmpty())
    }
}

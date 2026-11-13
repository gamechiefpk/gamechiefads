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

/** Interstitial and app open never overlap, and an interstitial stays quiet for 30 s after an app open CLOSED. */
@OptIn(ExperimentalCoroutinesApi::class)
class AdOverlapTest {
    private fun TestScope.rig() = Rig(
        this,
        AdsConfig.builder().appOpenUnit("ao").minGapSeconds(0)
            .randomInterstitialUnits("r").minGapSeconds(0).everyNScreens(1).build(),
    )

    private class Spy : InterstitialListener {
        val calls = mutableListOf<String>()
        override fun onNotShown(reason: String) { calls += "not:$reason" }
        override fun onComplete(wasShown: Boolean) { calls += "complete:$wasShown" }
    }

    /** Puts an app-open ad on screen (manual: stays until dismissed). */
    private fun TestScope.showAppOpen(r: Rig): FakeAd {
        val ad = FakeAd.manual()
        r.appOpenPool.put(Placements.APP_OPEN, ad)
        r.appOpen.onForeground(); runCurrent()
        return ad
    }

    @Test fun anInterstitialNeverShowsOverAnAppOpenAdThatIsOnScreen() = runTest {
        val r = rig(); val ao = showAppOpen(r)
        assertEquals(1, ao.shows); assertTrue(r.state.adShowing.value)
        val s = Spy()
        r.interstitial.showRandom(r.activity, s, false); advanceUntilIdle()                 // a click
        r.state.onScreenResumed(); r.random.onScreenResumed(r.activity); advanceUntilIdle() // the automatic ad
        r.interstitial.showOnDemand(r.activity, "random", null); advanceUntilIdle()         // a direct call
        assertTrue(r.loads.none { it == "r" })                                              // never even requested
        assertEquals(listOf("not:DENY_AD_SHOWING", "complete:false"), s.calls)
    }

    @Test fun anAppOpenNeverShowsOverAnInterstitialThatIsLoadingOrOnScreen() = runTest {
        val r = rig(); val inter = FakeAd.manual(); r.loadBehavior = { inter }
        r.interstitial.showRandom(r.activity, null, false); runCurrent()                    // loading, slot claimed
        val ao = FakeAd.manual(); r.appOpenPool.put(Placements.APP_OPEN, ao)
        r.appOpen.onForeground(); advanceUntilIdle()
        assertEquals(0, ao.shows)                                                           // refused while the interstitial is in flight
        assertEquals(1, inter.shows)
        r.appOpen.onForeground(); advanceUntilIdle()
        assertEquals(0, ao.shows)                                                           // and while it is on screen
    }

    @Test fun anInterstitialStaysQuiet30SecondsAfterTheAppOpenAdWasClosed() = runTest {
        val r = rig(); val ao = showAppOpen(r)
        r.now += 20_000                       // the user watched it for 20 s
        ao.listener!!.onDismissed()           // closed NOW
        assertFalse(r.state.adShowing.value)
        val s = Spy()
        r.interstitial.showRandom(r.activity, s, false); advanceUntilIdle()
        assertEquals("not:DENY_AFTER_APP_OPEN", s.calls[0]); assertTrue(r.loads.none { it == "r" })
        r.now += 29_000                       // 29 s after it closed: still quiet (shown 49 s ago, which is not what counts)
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertTrue(r.loads.none { it == "r" })
        r.now += 2_000                        // 31 s after it closed
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertEquals(1, r.loads.count { it == "r" })
    }

    @Test fun theAutomaticAdAndCustomInterstitialsAreQuietToo() = runTest {
        val r = Rig(
            this,
            AdsConfig.builder().appOpenUnit("ao").randomInterstitialUnits("r").minGapSeconds(0).everyNScreens(1)
                .interstitial("custom").units("c").build(),
        )
        val ao = FakeAd.manual(); r.appOpenPool.put(Placements.APP_OPEN, ao); r.appOpen.onForeground(); runCurrent()
        ao.listener!!.onDismissed()
        r.state.onScreenResumed(); r.random.onScreenResumed(r.activity); advanceUntilIdle()
        val s = Spy(); r.interstitial.showOnDemand(r.activity, "custom", s); advanceUntilIdle()
        assertTrue(r.loads.none { it == "r" || it == "c" }); assertEquals("not:DENY_AFTER_APP_OPEN", s.calls[0])
        r.now += 31_000
        r.state.onScreenResumed(); r.random.onScreenResumed(r.activity); advanceUntilIdle()
        assertEquals(1, r.loads.count { it == "r" })
    }

    @Test fun rewardedIsNotHeldBackAndTheQuietTimeIsConfigurable() = runTest {
        val r = Rig(
            this,
            AdsConfig.builder().appOpenUnit("ao").quietSecondsAfterAppOpen(0).randomInterstitialUnits("r").minGapSeconds(0).build(),
        )
        val ao = FakeAd.manual(); r.appOpenPool.put(Placements.APP_OPEN, ao); r.appOpen.onForeground(); runCurrent(); ao.listener!!.onDismissed()
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertEquals(1, r.loads.count { it == "r" })                                        // 0 = no quiet time
        val q = Rig(this, AdsConfig.builder().appOpenUnit("ao").rewardedUnits("rw").build())
        val a2 = FakeAd.manual(); q.appOpenPool.put(Placements.APP_OPEN, a2); q.appOpen.onForeground(); runCurrent(); a2.listener!!.onDismissed()
        q.rewarded.show(q.activity, "rewarded", null); advanceUntilIdle()
        assertEquals(1, q.loads.count { it == "rw" })                                       // the user asked for a reward: not held back
        assertEquals(30, AdsConfig.builder().build().quietSecondsAfterAppOpen)
        assertEquals(10, RemoteOverrides.apply(AdsConfig.builder().build(), """{"quietSecondsAfterAppOpen":10}""").quietSecondsAfterAppOpen)
    }

    @Test fun aSplashAppOpenAlsoStartsTheQuietTime() = runTest {
        val r = Rig(this, AdsConfig.builder().splashAppOpenUnits("s").randomInterstitialUnits("r").minGapSeconds(0).build())
        val ad = FakeAd.manual(); r.loadBehavior = { ad }
        var ads: SplashAds? = null
        r.splash.prepare(r.activity, { ads = it }); advanceUntilIdle()
        var done = 0; ads!!.show(r.activity) { done++ }; runCurrent()
        ad.listener!!.onDismissed(); assertEquals(1, done)
        val s = Spy(); r.interstitial.showRandom(r.activity, s, false); advanceUntilIdle()
        assertEquals("not:DENY_AFTER_APP_OPEN", s.calls[0])
    }

    @Test fun aSplashInterstitialDoesNotStartIt() = runTest {
        val r = Rig(this, AdsConfig.builder().splashInterstitialUnits("s").randomInterstitialUnits("r").minGapSeconds(0).build())
        val ad = FakeAd.manual(); r.loadBehavior = { ad }
        var ads: SplashAds? = null
        r.splash.prepare(r.activity, { ads = it }, Placements.SPLASH_INTERSTITIAL); advanceUntilIdle()
        ads!!.show(r.activity) { }; runCurrent(); ad.listener!!.onDismissed()
        val s = Spy(); r.interstitial.showRandom(r.activity, s, false); advanceUntilIdle()
        assertTrue(s.calls.none { it.contains("DENY_AFTER_APP_OPEN") })
    }
}

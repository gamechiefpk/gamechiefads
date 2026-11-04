package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InterstitialControllerTest {
    private fun cfg(options: AdsConfig.Builder.() -> AdsConfig.Builder = { this }) =
        AdsConfig.builder().interstitial("home").units("a", "b").options().build()

    private fun TestScope.rig(config: AdsConfig = cfg(), ready: Boolean = true) = Rig(this, config, ready)

    // ---- on-demand (the default path) ----

    @Test fun onDemandLoadsThenShowsAndNotifies() = runTest {
        val r = rig(); val l = RecordingListener()
        r.interstitial.showOnDemand(r.activity, "home", l)
        advanceUntilIdle()
        assertEquals(listOf("a"), r.loads)
        assertEquals(listOf("shown", "closed", "complete:true"), l.calls)
        assertFalse(r.state.adShowing.value)
        assertEquals(0, r.state.screensSinceAd.value)
    }

    @Test fun nullListenerNeverThrows() = runTest { // old bug 1: NPE with a null listener
        val r = rig()
        r.interstitial.showOnDemand(r.activity, "home", null)
        r.interstitial.showOnDemand(r.activity, "nope", null)
        advanceUntilIdle()
        assertEquals(listOf("a"), r.loads)
    }

    @Test fun waterfallFallsToNextUnit() = runTest {
        val r = rig(); val l = RecordingListener()
        r.loadBehavior = { u -> if (u == "a") null else FakeAd(u) }
        r.interstitial.showOnDemand(r.activity, "home", l)
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), r.loads)
        assertEquals("complete:true", l.calls.last())
    }

    @Test fun noFillIsNotShownAndFlagFreed() = runTest {
        val r = rig(); val l = RecordingListener()
        r.loadBehavior = { null }
        r.interstitial.showOnDemand(r.activity, "home", l)
        advanceUntilIdle()
        assertEquals(listOf("notShown:no fill", "complete:false"), l.calls)
        assertFalse(r.state.adShowing.value)
    }

    @Test fun premiumLoadsNothing() = runTest {
        val r = rig(); val l = RecordingListener()
        r.state.setPremium(true)
        r.interstitial.showOnDemand(r.activity, "home", l)
        advanceUntilIdle()
        assertTrue(r.loads.isEmpty())
        assertEquals(listOf("notShown:DENY_PREMIUM", "complete:false"), l.calls)
    }

    @Test fun premiumTurnedOnWhileLoadingDestroysTheAd() = runTest {
        val r = rig(); val l = RecordingListener()
        val ad = FakeAd()
        r.loadBehavior = { delay(500); ad }
        r.interstitial.showOnDemand(r.activity, "home", l)
        advanceTimeBy(100)
        r.state.setPremium(true)
        advanceUntilIdle()
        assertTrue(ad.destroyed); assertEquals(0, ad.shows)
        assertEquals("notShown:premium", l.calls.first())
    }

    @Test fun secondCallWhileLoadingIsRefusedSoNeverTwoAds() = runTest { // old bug 2: double interstitial
        val r = rig(); val l1 = RecordingListener(); val l2 = RecordingListener()
        val ads = mutableListOf<FakeAd>()
        r.loadBehavior = { delay(1_000); FakeAd().also { ads += it } }
        r.interstitial.showOnDemand(r.activity, "home", l1)
        r.interstitial.showOnDemand(r.activity, "home", l2)
        advanceUntilIdle()
        assertEquals(1, r.loads.size)
        assertEquals(1, ads.sumOf { it.shows })
        assertEquals(listOf("notShown:DENY_AD_SHOWING", "complete:false"), l2.calls)
        assertEquals("complete:true", l1.calls.last())
    }

    @Test fun screenGoneBeforeShowDropsTheAd() = runTest {
        val r = rig(); val l = RecordingListener()
        val ad = FakeAd()
        r.loadBehavior = { delay(500); ad }
        r.interstitial.showOnDemand(r.activity, "home", l)
        advanceTimeBy(100)
        r.screens.activity = null
        advanceUntilIdle()
        assertTrue(ad.destroyed); assertEquals(0, ad.shows)
        assertEquals("notShown:screen gone", l.calls.first())
        assertFalse(r.state.adShowing.value)
    }

    @Test fun unknownAndDisabledPlacements() = runTest {
        val r = rig(cfg { enabled(false) }); val l1 = RecordingListener(); val l2 = RecordingListener()
        r.interstitial.showOnDemand(r.activity, "missing", l1)
        r.interstitial.showOnDemand(r.activity, "home", l2)
        advanceUntilIdle()
        assertTrue(r.loads.isEmpty())
        assertTrue(l1.calls.first().startsWith("notShown:unknown placement"))
        assertEquals("notShown:DENY_DISABLED", l2.calls.first())
    }

    @Test fun loaderUiWrapsTheLoadEvenOnFailure() = runTest {
        val r = rig(); val log = mutableListOf<String>()
        r.loaderUi = object : AdLoaderUi {
            override fun show(activity: Activity) { log += "show" }
            override fun dismiss() { log += "dismiss" }
        }
        r.loadBehavior = { null }
        r.interstitial.showOnDemand(r.activity, "home", null)
        advanceUntilIdle()
        assertEquals(listOf("show", "dismiss"), log)
    }

    @Test fun sdkNotReadyMeansNoAdAndNoLoad() = runTest {
        val r = rig(ready = false); val l = RecordingListener()
        r.interstitial.showOnDemand(r.activity, "home", l)
        advanceUntilIdle()
        assertTrue(r.loads.isEmpty())
        assertEquals("notShown:no fill", l.calls.first())
    }

    // ---- preload (opt-in) ----

    @Test fun preloadFillsOnceAndOnlyWhenAsked() = runTest {
        val r = rig()
        advanceUntilIdle()
        assertTrue(r.loads.isEmpty()) // nothing happens by itself
        r.interstitial.preload("home"); r.interstitial.preload("home")
        advanceUntilIdle()
        assertEquals(1, r.loads.size)
        assertTrue(r.interstitial.isReady("home"))
    }

    @Test fun showPreloadedConsumesAndDoesNotRefillByDefault() = runTest {
        val r = rig(); val l = RecordingListener()
        r.interstitial.preload("home"); advanceUntilIdle()
        assertTrue(r.interstitial.showPreloaded(r.activity, "home", l))
        advanceUntilIdle()
        assertEquals("complete:true", l.calls.last())
        assertFalse(r.interstitial.isReady("home"))
        assertEquals(1, r.loads.size) // autoRefill is off: no extra request
    }

    @Test fun showPreloadedWithNothingReadyReturnsFalseAndLoadsNothing() = runTest {
        val r = rig()
        assertFalse(r.interstitial.showPreloaded(r.activity, "home", null))
        advanceUntilIdle()
        assertTrue(r.loads.isEmpty())
    }

    @Test fun fallbackToLoadStartsTheOnDemandPath() = runTest {
        val r = rig(AdsConfig.builder().interstitial("home").units("a").fallbackToLoad(true).build())
        val l = RecordingListener()
        assertTrue(r.interstitial.showPreloaded(r.activity, "home", l))
        advanceUntilIdle()
        assertEquals(listOf("a"), r.loads)
        assertEquals("complete:true", l.calls.last())
    }

    @Test fun autoRefillRequestsAgainOnlyWhenEnabledAndPreloadMode() = runTest {
        val on = rig(AdsConfig.builder().interstitial("home").units("a").mode(AdMode.PRELOAD).autoRefill(true).build())
        on.interstitial.preload("home"); advanceUntilIdle()
        on.interstitial.showPreloaded(on.activity, "home", null); advanceUntilIdle()
        assertEquals(2, on.loads.size)
        assertTrue(on.interstitial.isReady("home"))

        val off = rig(AdsConfig.builder().interstitial("home").units("a").mode(AdMode.PRELOAD).build())
        off.interstitial.preload("home"); advanceUntilIdle()
        off.interstitial.showPreloaded(off.activity, "home", null); advanceUntilIdle()
        assertEquals(1, off.loads.size)
    }

    @Test fun preloadedShowStillRefusedForPremiumOrWhileAnotherAdShows() = runTest {
        val r = rig()
        r.interstitial.preload("home"); advanceUntilIdle()
        r.state.setPremium(true)
        assertFalse(r.interstitial.showPreloaded(r.activity, "home", null))
        r.state.setPremium(false); r.state.setAdShowing(true)
        assertFalse(r.interstitial.showPreloaded(r.activity, "home", null))
        assertTrue(r.interstitial.isReady("home")) // the ready ad was not wasted
    }
}

package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** randomInterstitialUnits(...) is automatic over activity transitions by default; click methods are extra. */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoRandomTest {
    private fun TestScope.rig(options: AdsConfig.Builder.() -> AdsConfig.Builder = { this }) =
        Rig(this, AdsConfig.builder().randomInterstitialUnits("hf", "normal").options().build())

    private fun Rig.screens(n: Int) = repeat(n) { state.onScreenResumed() }

    private class LoaderSpy : AdLoaderUi {
        var shown = 0
        override fun show(activity: Activity) { shown++ }
        override fun dismiss() {}
    }

    @Test fun theSlotIsAutomaticByDefaultEvery3rdScreenWith30sGap() {
        val p = AdsConfig.builder().randomInterstitialUnits("a").build().placement(Placements.RANDOM)!!
        assertEquals(3, p.everyNScreens); assertEquals(30, p.minGapSeconds)
    }

    @Test fun itShowsByItselfOverTheThirdNewScreenWithTheLoadingDialog() = runTest {
        val r = rig(); val ui = LoaderSpy(); r.loaderUi = ui
        r.screens(3); r.random.onScreenResumed(r.activity); advanceUntilIdle()
        assertEquals(listOf("hf"), r.loads); assertEquals(1, ui.shown)
        assertEquals(0, r.state.screensSinceAd.value)
    }

    @Test fun notBeforeTheNthScreenAndNotOnAnExcludedScreen() = runTest {
        val a = rig(); a.screens(2); a.random.onScreenResumed(a.activity); advanceUntilIdle()
        assertTrue(a.loads.isEmpty())
        val b = rig { excludeScreens(TestActivity::class.java) }; b.screens(5); b.random.onScreenResumed(b.activity); advanceUntilIdle()
        assertTrue(b.loads.isEmpty())
    }

    @Test fun everyNScreensZeroTurnsTheAutomaticPartOffButClicksStillWork() = runTest {
        val r = rig { everyNScreens(0) }; r.screens(10); r.random.onScreenResumed(r.activity); advanceUntilIdle()
        assertTrue(r.loads.isEmpty())
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertEquals(listOf("hf"), r.loads)
    }

    @Test fun theAutomaticAdCanBeADouble() = runTest {
        val r = Rig(this, AdsConfig.builder().randomInterstitialUnits("hf", "n").randomInterstitialDoubleUnit("n2").doubleAd(true).build())
        val ads = mutableListOf<FakeAd>(); r.loadBehavior = { FakeAd.manual().also { ads += it } }
        r.screens(3); r.random.onScreenResumed(r.activity); advanceUntilIdle()
        assertEquals(2, ads.size); assertEquals(setOf("hf", "n2"), r.loads.toSet())
        assertEquals(1, ads[0].shows); assertEquals(1, ads[1].shows)
    }

    @Test fun clicksAndTheAutomaticAdShareOneGapAndOneCounter() = runTest {
        val r = rig { everyNCalls(3) }
        r.interstitial.showRandom(r.activity, null, false); r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertTrue(r.loads.isEmpty())                                      // calls 1 and 2: not the 3rd yet
        r.screens(3); r.random.onScreenResumed(r.activity); advanceUntilIdle()
        assertEquals(1, r.loads.size)                                      // the automatic ad showed
        r.now += 31_000
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertEquals(1, r.loads.size)                                      // click counter restarted: this is call 1 again
    }

    @Test fun aClickRightAfterAnAutomaticAdIsHeldByTheSharedGap() = runTest {
        val r = rig { everyNCalls(1) }
        r.screens(3); r.random.onScreenResumed(r.activity); advanceUntilIdle()
        val n = r.loads.size
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertEquals(n, r.loads.size)                                      // within 30 s of the automatic ad
    }

    @Test fun noFillOverATransitionIsSilentDuringTheWait() = runTest {
        val r = rig(); r.loadBehavior = { null }
        r.screens(3); r.random.onScreenResumed(r.activity); advanceUntilIdle()
        val loads = r.loads.size; val toasts = r.noAdToasts.size
        r.screens(3); r.random.onScreenResumed(r.activity); advanceUntilIdle()
        assertEquals(loads, r.loads.size); assertEquals(toasts, r.noAdToasts.size) // nothing new, no extra message
        assertFalse(r.state.adShowing.value)
    }
}

package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Slow connection: the ad is loaded but the SDK takes a while to put it on screen. The loading dialog must stay until
 * the ad is really visible, never an empty gap ("dialog gone, no ad, then an ad out of nowhere").
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LoaderStaysUntilShownTest {
    private class Spy : AdLoaderUi {
        var shown = 0; var dismissed = 0
        override fun show(activity: Activity) { shown++ }
        override fun dismiss() { dismissed++ }
    }

    @Test fun theDialogStaysAfterTheLoadAndGoesWhenTheAdIsOnScreen() = runTest {
        val r = Rig(this, AdsConfig.builder().randomInterstitialUnits("a").minGapSeconds(0).build())
        val ui = Spy(); r.loaderUi = ui
        val ad = FakeAd(onShow = { }); r.loadBehavior = { ad }
        r.interstitial.showOnDemand(r.activity, Placements.RANDOM, null)
        advanceTimeBy(100)
        assertEquals(1, ad.shows); assertEquals(0, ui.dismissed)     // loaded and show() called, not visible yet: dialog still up
        ad.listener!!.onShown()
        assertEquals(1, ui.dismissed)                                 // visible now: dialog goes
        ad.listener!!.onDismissed(); advanceUntilIdle()
    }

    @Test fun theDialogGoesIfTheAdNeverStarts() = runTest {
        val r = Rig(this, AdsConfig.builder().randomInterstitialUnits("a").minGapSeconds(0).build())
        val ui = Spy(); r.loaderUi = ui
        val ad = FakeAd(onShow = { }); r.loadBehavior = { ad }
        r.interstitial.showOnDemand(r.activity, Placements.RANDOM, null)
        advanceTimeBy(100); assertEquals(0, ui.dismissed)
        advanceUntilIdle()                                             // the watchdog gives up: no stuck dialog
        assertEquals(1, ui.dismissed)
    }

    @Test fun theDialogGoesIfTheShowFails() = runTest {
        val r = Rig(this, AdsConfig.builder().randomInterstitialUnits("a").minGapSeconds(0).build())
        val ui = Spy(); r.loaderUi = ui
        val ad = FakeAd(onShow = { }); r.loadBehavior = { ad }
        r.interstitial.showOnDemand(r.activity, Placements.RANDOM, null)
        advanceTimeBy(100)
        ad.listener!!.onFailed("boom")
        assertEquals(1, ui.dismissed)
    }
}

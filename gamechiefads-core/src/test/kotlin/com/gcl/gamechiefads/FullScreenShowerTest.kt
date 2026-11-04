package com.gcl.gamechiefads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FullScreenShowerTest {
    private val state = AdState()
    private var now = 1_000L
    private val activity = TestActivity()

    private class Rec : FullScreenShower.Events {
        val log = mutableListOf<String>()
        override fun onShown() { log += "shown" }
        override fun onFinished(shown: Boolean, reason: String?) { log += "finished:$shown:$reason" }
    }

    @Test fun normalShowMarksStateAndReleasesEverything() = runTest {
        val shower = FullScreenShower(state, { now }, this)
        val ad = FakeAd.manual(); val rec = Rec()
        shower.show(activity, ad, rec)
        assertTrue(state.adShowing.value)
        assertEquals(1_000L, state.lastShownAtMs.value)
        ad.listener!!.onDismissed()
        assertFalse(state.adShowing.value)
        assertTrue(ad.destroyed)
        assertEquals(listOf("shown", "finished:true:null"), rec.log)
    }

    @Test fun failedToShowReportsNotShownAndFreesFlag() = runTest {
        val shower = FullScreenShower(state, { now }, this)
        val ad = FakeAd(onShow = { it.onFailed("boom") }); val rec = Rec()
        shower.show(activity, ad, rec)
        assertFalse(state.adShowing.value)
        assertEquals(null, state.lastShownAtMs.value)
        assertEquals(listOf("finished:false:boom"), rec.log)
    }

    @Test fun reportsOnlyOnce() = runTest {
        val shower = FullScreenShower(state, { now }, this)
        val ad = FakeAd.manual(); val rec = Rec()
        shower.show(activity, ad, rec)
        ad.listener!!.onDismissed(); ad.listener!!.onDismissed(); ad.listener!!.onFailed("late")
        assertEquals(1, rec.log.count { it.startsWith("finished") })
    }

    @Test fun watchdogFreesFlagWhenSdkNeverReports() = runTest {
        val shower = FullScreenShower(state, { now }, this, startTimeoutMs = 5_000)
        val ad = FakeAd(onShow = { }); val rec = Rec()
        shower.show(activity, ad, rec)
        assertTrue(state.adShowing.value)
        advanceTimeBy(4_999); assertTrue(state.adShowing.value)
        advanceTimeBy(2)
        assertFalse(state.adShowing.value)
        assertEquals(listOf("finished:false:show timeout"), rec.log)
    }

    @Test fun watchdogDoesNotFireOnceShown() = runTest {
        val shower = FullScreenShower(state, { now }, this, startTimeoutMs = 5_000)
        val ad = FakeAd.manual(); val rec = Rec()
        shower.show(activity, ad, rec)
        advanceTimeBy(60_000) // user is looking at the ad for a minute
        assertTrue(state.adShowing.value)
        assertEquals(listOf("shown"), rec.log)
    }
}

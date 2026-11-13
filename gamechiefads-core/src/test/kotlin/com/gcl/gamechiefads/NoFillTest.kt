package com.gcl.gamechiefads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Floors are one pass (HF, MF, NORMAL once each). No ad = a message and move on, and no request loops. */
@OptIn(ExperimentalCoroutinesApi::class)
class NoFillTest {
    private class Spy : InterstitialListener {
        val calls = mutableListOf<String>()
        override fun onNotShown(reason: String) { calls += "not:$reason" }
        override fun onComplete(wasShown: Boolean) { calls += "complete:$wasShown" }
    }

    private fun TestScope.rig() = Rig(this, AdsConfig.builder().randomInterstitialUnits("hf", "mf", "normal").build())

    @Test fun aFailedPassTriesEachFloorOnceTellsTheUserAndNeverLoops() = runTest {
        val r = rig(); r.loadBehavior = { null }; val s = Spy()
        r.interstitial.showRandom(r.activity, s, false); advanceUntilIdle()
        assertEquals(listOf("hf", "mf", "normal"), r.loads)           // each unit exactly once
        assertEquals(1, r.noAdCalls)                                    // "no ad available" message once
        assertEquals(listOf("not:no fill", "complete:false"), s.calls)  // the app moves on at once
        assertFalse(r.state.adShowing.value)
        advanceTimeBy(120_000); advanceUntilIdle()
        assertEquals(3, r.loads.size)                                   // nothing retried by itself
    }

    @Test fun tappingAgainRightAwayMakesNoNewRequestsUntilTheWaitIsOver() = runTest {
        val r = rig(); r.loadBehavior = { null }
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        val s = Spy()
        r.interstitial.showRandom(r.activity, s, false); advanceUntilIdle()
        assertEquals(3, r.loads.size)                                   // still the first pass only
        assertEquals(2, r.noAdCalls); assertTrue(s.calls[0].startsWith("not:no ad available, retry in"))
        r.now += 6_000                                                  // more than the default 5 s
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertEquals(6, r.loads.size)                                   // one new pass, then waiting again
    }

    @Test fun aSuccessfulAdClearsTheWait() = runTest {
        val r = rig(); r.loadBehavior = { null }
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        r.now += 6_000; r.loadBehavior = { FakeAd(it) }
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertEquals(1, r.noAdCalls)                                    // only the first failure told the user
        assertTrue(r.loads.size >= 5)
    }

    @Test fun theWaitCanBeTurnedOffAndTheTimeIsCounted() = runTest {
        val r = Rig(this, AdsConfig.builder().randomInterstitialUnits("a").noFillRetrySeconds(0).build()); r.loadBehavior = { null }
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertEquals(2, r.loads.size)
    }

    @Test fun rewardedFollowsTheSameRules() = runTest {
        val r = Rig(this, AdsConfig.builder().rewardedUnits("hf", "normal").build()); r.loadBehavior = { null }
        val got = mutableListOf<String>()
        val l = object : RewardListener {
            override fun onNotAvailable(reason: String) { got += "na:$reason" }
            override fun onComplete(rewarded: Boolean) { got += "complete:$rewarded" }
        }
        r.rewarded.show(r.activity, "rewarded", l); advanceUntilIdle()
        r.rewarded.show(r.activity, "rewarded", l); advanceUntilIdle()
        assertEquals(listOf("hf", "normal"), r.loads); assertEquals(2, r.noAdCalls)
        assertEquals("na:no fill", got[0]); assertTrue(got[2].startsWith("na:no ad available, retry in"))
    }

    @Test fun appOpenDoesNotRefetchInALoopAfterAMiss() = runTest {
        val r = Rig(this, AdsConfig.builder().appOpenUnit("ao").build()); r.loadBehavior = { null }
        r.appOpen.onScreenResumed(r.activity); advanceUntilIdle()
        assertEquals(1, r.loads.size)
        r.appOpen.onForeground(); advanceUntilIdle()
        assertEquals(1, r.loads.size)                                   // within the wait: no new request
        r.now += 6_000
        r.appOpen.onForeground(); advanceUntilIdle()
        assertEquals(2, r.loads.size)
    }

    @Test fun remoteConfigCanChangeTheMessageAndTheWait() {
        val base = AdsConfig.builder().randomInterstitialUnits("a").build()
        assertEquals(5, base.noFillRetrySeconds); assertEquals(AdsConfig.DEFAULT_NO_AD_MESSAGE, base.noAdMessage)
        val c = RemoteOverrides.apply(base, """{"noAdMessage":"Try later","noFillRetrySeconds":12}""")
        assertEquals("Try later", c.noAdMessage); assertEquals(12, c.noFillRetrySeconds)
        assertEquals(null, RemoteOverrides.apply(base, """{"noAdMessage":""}""").noAdMessage)
        assertEquals(null, AdsConfig.builder().noAdMessage(null).build().noAdMessage)
    }
}

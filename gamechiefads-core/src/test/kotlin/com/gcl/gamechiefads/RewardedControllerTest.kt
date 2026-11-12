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
class RewardedControllerTest {
    private class Spy : RewardListener {
        val calls = mutableListOf<String>()
        override fun onRewardEarned(amount: Int, type: String) { calls += "earned:$amount:$type" }
        override fun onNotAvailable(reason: String) { calls += "na:$reason" }
        override fun onComplete(rewarded: Boolean) { calls += "complete:$rewarded" }
    }

    private fun TestScope.rig(options: AdsConfig.Builder.() -> AdsConfig.Builder = { this }) =
        Rig(this, AdsConfig.builder().rewarded("rw").units("hf", "mf", "normal").options().build())

    @Test fun rewardIsGivenOnlyWhenEarnedThenCompleteOnce() = runTest {
        val r = rig(); val ad = FakeAd.manual(); r.loadBehavior = { ad }; val s = Spy()
        r.rewarded.show(r.activity, "rw", s); advanceUntilIdle()
        assertEquals(1, ad.shows); assertTrue(s.calls.isEmpty()) // nothing yet while the ad plays
        ad.listener!!.onRewarded(5, "coins")
        assertEquals(listOf("earned:5:coins"), s.calls)
        ad.listener!!.onDismissed()
        assertEquals(listOf("earned:5:coins", "complete:true"), s.calls)
        assertFalse(r.state.adShowing.value)
    }

    @Test fun closingEarlyGivesNoRewardAndNoErrorMessage() = runTest {
        val r = rig(); val ad = FakeAd.manual(); r.loadBehavior = { ad }; val s = Spy()
        r.rewarded.show(r.activity, "rw", s); advanceUntilIdle()
        ad.listener!!.onDismissed()
        assertEquals(listOf("complete:false"), s.calls)
    }

    @Test fun noFillTellsTheAppAndFreesTheSlot() = runTest {
        val r = rig(); r.loadBehavior = { null }; val s = Spy()
        r.rewarded.show(r.activity, "rw", s); advanceUntilIdle()
        assertEquals(listOf("na:no fill", "complete:false"), s.calls)
        assertEquals(listOf("hf", "mf", "normal"), r.loads) // floors walked
        assertFalse(r.state.adShowing.value)
    }

    @Test fun floorsFallThroughToTheNextUnit() = runTest {
        val r = rig(); r.loadBehavior = { if (it == "normal") FakeAd.manual() else null }; val s = Spy()
        r.rewarded.show(r.activity, "rw", s); advanceUntilIdle()
        assertEquals(listOf("hf", "mf", "normal"), r.loads)
        assertTrue(r.state.adShowing.value)
    }

    @Test fun premiumGetsTheRewardWithoutAnyRequest() = runTest {
        val r = rig(); r.state.setPremium(true); val s = Spy()
        r.rewarded.show(r.activity, "rw", s); advanceUntilIdle()
        assertEquals(listOf("earned:1:premium", "complete:true"), s.calls); assertTrue(r.loads.isEmpty())
    }

    @Test fun premiumWithoutRewardOptionGetsNothing() = runTest {
        val r = rig { premiumGetsReward(false) }; r.state.setPremium(true); val s = Spy()
        r.rewarded.show(r.activity, "rw", s); advanceUntilIdle()
        assertEquals(listOf("na:premium", "complete:false"), s.calls); assertTrue(r.loads.isEmpty())
    }

    @Test fun aSecondTapWhileLoadingIsRefusedAndTheFirstStillWorks() = runTest {
        val r = rig(); val ad = FakeAd.manual(); r.loadBehavior = { ad }; val a = Spy(); val b = Spy()
        r.rewarded.show(r.activity, "rw", a); r.rewarded.show(r.activity, "rw", b); advanceUntilIdle()
        assertEquals(listOf("na:DENY_AD_SHOWING", "complete:false"), b.calls)
        assertEquals(1, ad.shows)
        ad.listener!!.onRewarded(1, "x"); ad.listener!!.onDismissed()
        assertEquals(listOf("earned:1:x", "complete:true"), a.calls)
    }

    @Test fun nullListenerNeverCrashes() = runTest {
        val r = rig(); val ad = FakeAd.manual(); r.loadBehavior = { ad }
        r.rewarded.show(r.activity, "rw", null); advanceUntilIdle()
        ad.listener!!.onRewarded(1, "x"); ad.listener!!.onDismissed()
        r.rewarded.show(r.activity, "missing", null)
        assertFalse(r.state.adShowing.value)
    }

    @Test fun unknownOrWrongFormatPlacementIsNotAvailable() = runTest {
        val r = Rig(this, AdsConfig.builder().rewarded("rw").units("a").interstitial("i").units("b").build())
        val s = Spy(); r.rewarded.show(r.activity, "nope", s); r.rewarded.show(r.activity, "i", s)
        assertEquals(4, s.calls.size); assertTrue(s.calls[0].startsWith("na:unknown")); assertTrue(r.loads.isEmpty())
    }

    @Test fun showFailureReportsNotAvailable() = runTest {
        val r = rig(); r.loadBehavior = { FakeAd(onShow = { it.onFailed("boom") }) }; val s = Spy()
        r.rewarded.show(r.activity, "rw", s); advanceUntilIdle()
        assertEquals(listOf("na:boom", "complete:false"), s.calls); assertFalse(r.state.adShowing.value)
    }

    @Test fun disabledPlacementAndRemoteSwitches() {
        val base = AdsConfig.builder().rewarded("rw").units("a").build()
        val p = RemoteOverrides.apply(base, """{"placements":{"rw":{"enabled":false,"premiumGetsReward":false}}}""").placement("rw")!!
        assertFalse(p.enabled); assertFalse(p.premiumGetsReward)
        assertEquals(listOf(AdUnits.TEST_REWARDED), AdUnits.resolve(p, debug = true))
    }
}

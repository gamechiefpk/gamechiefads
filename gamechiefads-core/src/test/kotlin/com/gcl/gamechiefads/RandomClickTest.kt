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

/** GamechiefAds.loadRandomInterstitialAd / ...DoubleAd: click-driven, paced by gap and every-N-calls. */
@OptIn(ExperimentalCoroutinesApi::class)
class RandomClickTest {
    private fun TestScope.rig(options: AdsConfig.Builder.() -> AdsConfig.Builder = { this }) =
        Rig(this, AdsConfig.builder().randomInterstitialUnits("hf", "normal").options().build())

    private class Spy : InterstitialListener {
        val calls = mutableListOf<String>()
        override fun onNotShown(reason: String) { calls += "not:$reason" }
        override fun onComplete(wasShown: Boolean) { calls += "complete:$wasShown" }
    }

    private class LoaderSpy : AdLoaderUi {
        var shown = 0; var dismissed = 0
        override fun show(activity: Activity) { shown++ }
        override fun dismiss() { dismissed++ }
    }

    @Test fun everyCallShowsByDefaultAndTheLoaderIsShownWhileLoading() = runTest {
        val r = rig(); val ui = LoaderSpy(); r.loaderUi = ui
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertEquals(listOf("hf"), r.loads); assertEquals(1, ui.shown); assertEquals(1, ui.dismissed)
    }

    @Test fun everyNCallsAsksOnlyOnTheNthCallAndThenRestarts() = runTest {
        val r = rig { everyNCalls(3) }; val a = Spy(); val b = Spy()
        r.interstitial.showRandom(r.activity, a, false); advanceUntilIdle()
        r.interstitial.showRandom(r.activity, b, false); advanceUntilIdle()
        assertEquals(listOf("not:DENY_EVERY_N", "complete:false"), a.calls); assertEquals(2, a.calls.size + b.calls.size - 2)
        assertTrue(r.loads.isEmpty())
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertEquals(1, r.loads.size) // 3rd call
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertEquals(1, r.loads.size) // counting again from 1
    }

    @Test fun minGapBlocksATooSoonClick() = runTest {
        val r = rig { minGapSeconds(30) }; val s = Spy()
        r.state.markShown(r.now - 5_000)
        r.interstitial.showRandom(r.activity, s, false); advanceUntilIdle()
        assertEquals(listOf("not:DENY_MIN_GAP", "complete:false"), s.calls); assertTrue(r.loads.isEmpty())
    }

    @Test fun premiumAndExcludedScreensGetNoAdAndNoLoader() = runTest {
        val r = rig { excludeScreens(TestActivity::class.java) }; val ui = LoaderSpy(); r.loaderUi = ui
        r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertTrue(r.loads.isEmpty()); assertEquals(0, ui.shown)
        val p = rig(); p.state.setPremium(true); p.interstitial.showRandom(p.activity, null, false); advanceUntilIdle()
        assertTrue(p.loads.isEmpty())
    }

    @Test fun singleLoadsOneAdAndDoubleLoadsTwoFromTheirOwnUnits() = runTest {
        val r = rig(); r.interstitial.showRandom(r.activity, null, false); advanceUntilIdle()
        assertEquals(listOf("hf"), r.loads)
        val d = Rig(this, AdsConfig.builder().randomInterstitialUnits("hf", "n").randomInterstitialDoubleUnits("hf2", "n2").build())
        val ads = mutableListOf<FakeAd>(); d.loadBehavior = { FakeAd.manual().also { ads += it } }
        d.interstitial.showRandom(d.activity, null, true); advanceUntilIdle()
        assertEquals(setOf("hf", "hf2"), d.loads.toSet())
        assertEquals(2, ads.size); assertEquals(1, ads[0].shows); assertEquals(1, ads[1].shows) // together
    }

    @Test fun notConfiguredOrAllOffIsNotAnError() = runTest {
        val r = Rig(this, AdsConfig.builder().bannerUnits("b").build()); val s = Spy()
        r.interstitial.showRandom(r.activity, s, false); advanceUntilIdle()
        assertEquals(2, s.calls.size); assertFalse(r.state.adShowing.value)
        r.interstitial.showRandom(r.activity, null, true)
    }

    @Test fun aSecondClickWhileOneIsLoadingIsRefused() = runTest {
        val r = rig(); val s = Spy(); r.loadBehavior = { FakeAd.manual() }
        r.interstitial.showRandom(r.activity, null, false); r.interstitial.showRandom(r.activity, s, false); advanceUntilIdle()
        assertEquals(listOf("not:DENY_AD_SHOWING", "complete:false"), s.calls)
    }

    @Test fun simpleBuilderSlotsAreNamedAndHaveTheRightFormats() {
        val c = AdsConfig.builder().appId("x")
            .splashAppOpenUnits("a1", "a2").splashAppOpenDoubleUnits("b1")
            .splashInterstitialUnits("i1").splashBannerUnits("sb").appOpenUnit("ao")
            .bannerUnits("b1", "b2").exitBannerUnits("eb").randomInterstitialUnits("r1").everyNCalls(4).minGapSeconds(20)
            .introNativeUnits("n1", "n2").fullScreenNativeUnit("fn").rewardedUnits("rw").build()
        assertEquals(AdFormat.APP_OPEN, c.placement(Placements.SPLASH)!!.format)
        assertEquals(listOf("b1"), c.placement(Placements.SPLASH)!!.secondUnits)
        assertEquals(AdFormat.INTERSTITIAL, c.placement(Placements.SPLASH_INTERSTITIAL)!!.format)
        assertEquals(BannerType.LARGE_BANNER, c.placement(Placements.SPLASH_BANNER)!!.bannerType)
        assertEquals(listOf("ao"), c.placement(Placements.APP_OPEN)!!.units)
        assertEquals(BannerType.MEDIUM_RECTANGLE, c.placement(Placements.EXIT_BANNER)!!.bannerType)
        assertEquals(4, c.placement(Placements.RANDOM)!!.everyNCalls); assertEquals(20, c.placement(Placements.RANDOM)!!.minGapSeconds)
        assertEquals(listOf("n1", "n2"), c.placement(Placements.INTRO_NATIVE)!!.units)
        assertEquals(listOf("fn"), c.placement(Placements.FULLSCREEN_NATIVE)!!.units)
        assertEquals(AdFormat.REWARDED, c.placement(Placements.REWARDED)!!.format)
    }
}

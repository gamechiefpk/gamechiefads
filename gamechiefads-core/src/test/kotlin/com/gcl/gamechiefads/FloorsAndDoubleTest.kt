package com.gcl.gamechiefads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FloorsAndDoubleTest {
    private fun TestScope.rig(options: AdsConfig.Builder.() -> AdsConfig.Builder = { this }) =
        Rig(this, AdsConfig.builder().interstitial("home").units("hf", "mf", "n").options().build())

    // ---- waterfall control ----

    @Test fun parallelWaterfallTakesTheFirstArrivalAndDropsTheRest() = runTest {
        val dropped = mutableListOf<String>()
        val hit = Waterfall.run(
            units = listOf("hf", "mf", "n"), timeoutFor = { 5_000L }, overallBudgetMs = 10_000L,
            mode = WaterfallMode.PARALLEL, discard = { dropped += it }, onTier = { _, _ -> },
        ) { unit -> delay(mapOf("hf" to 900L, "mf" to 200L, "n" to 300L).getValue(unit)); unit }
        assertEquals("mf", hit)
        assertEquals(200L, currentTime) // did not wait for the slower floors
    }

    @Test fun parallelWithAllMissesReturnsNull() = runTest {
        val hit = Waterfall.run<String>(
            listOf("a", "b"), { 100L }, 1_000L, WaterfallMode.PARALLEL, {}, { _, _ -> },
        ) { null }
        assertNull(hit)
    }

    @Test fun eachUnitCanHaveItsOwnTimeout() = runTest {
        val r = rig { unitTimeoutMs("hf", 500).tierTimeoutMs(3_000) }
        r.loadBehavior = { u -> if (u == "n") FakeAd(u) else { delay(60_000); null } }
        r.interstitial.showOnDemand(r.activity, "home", null)
        advanceUntilIdle()
        // hf gave up after 500, mf after the default 3000, then n loaded
        assertEquals(500L + 3_000L, currentTime)
        assertEquals(listOf("hf", "mf", "n"), r.loads)
    }

    @Test fun disabledUnitsAreNeverRequested() = runTest {
        val r = rig { disableUnit("hf") }
        r.interstitial.showOnDemand(r.activity, "home", null)
        advanceUntilIdle()
        assertEquals(listOf("mf"), r.loads)
    }

    @Test fun floorPolicyNeverLeavesAPlacementWithoutUnits() {
        var now = 0L
        val f = FloorPolicy { now }
        val p = PlacementConfig("p", AdFormat.INTERSTITIAL, skipAfterFails = 1, skipForSeconds = 30)
        f.record(p, "a", false); f.record(p, "b", false)
        assertEquals(listOf("a", "b"), f.active(p, listOf("a", "b"))) // all skipped -> try all
        f.record(p, "a", true)
        assertFalse(f.isSkipped(p, "a"))
        assertTrue(f.isSkipped(p, "b"))
        now += 31_000
        assertFalse(f.isSkipped(p, "b")) // pause is over
    }

    @Test fun aMissingFloorIsSkippedForAWhileThenRetried() {
        var now = 0L
        val f = FloorPolicy { now }
        val p = PlacementConfig("p", AdFormat.INTERSTITIAL, skipAfterFails = 2, skipForSeconds = 60)
        f.record(p, "hf", false)
        assertEquals(listOf("hf", "mf"), f.active(p, listOf("hf", "mf")))
        f.record(p, "hf", false)
        assertEquals(listOf("mf"), f.active(p, listOf("hf", "mf")))
        now += 61_000
        assertEquals(listOf("hf", "mf"), f.active(p, listOf("hf", "mf")))
    }

    // ---- double ads ----

    @Test fun doubleInterstitialLoadsTwoAndShowsThemBackToBackAsOneBreak() = runTest {
        val r = rig { doubleAd(true) }
        val ads = mutableListOf<FakeAd>()
        r.loadBehavior = { FakeAd.manual().also { ads += it } }
        val l = RecordingListener()
        r.interstitial.showOnDemand(r.activity, "home", l)
        advanceUntilIdle()
        assertEquals(2, ads.size)
        assertEquals(1, ads[0].shows); assertEquals(0, ads[1].shows)
        ads[0].listener!!.onDismissed()
        assertEquals(1, ads[1].shows) // right inside the dismiss callback
        assertTrue(r.state.adShowing.value)
        assertEquals(listOf("shown"), l.calls) // the app is told once, at the end of the break
        ads[1].listener!!.onDismissed()
        assertEquals(listOf("shown", "closed", "complete:true"), l.calls)
        assertFalse(r.state.adShowing.value)
    }

    @Test fun doubleFallsBackToOneAdWhenTheSecondIsMissing() = runTest {
        val r = rig { doubleAd(true) }
        var n = 0
        r.loadBehavior = { if (n++ == 0) FakeAd(it) else null }
        val l = RecordingListener()
        r.interstitial.showOnDemand(r.activity, "home", l)
        advanceUntilIdle()
        assertEquals("complete:true", l.calls.last())
    }

    @Test fun remoteConfigCanSwitchDoubleOnAndOff() {
        val base = AdsConfig.builder().interstitial("home").units("a").build()
        val on = RemoteOverrides.apply(base, """{"placements":{"home":{"double":true}}}""")
        assertTrue(on.placement("home")!!.doubleAd)
        val off = RemoteOverrides.apply(on, """{"placements":{"home":{"double":false}}}""")
        assertFalse(off.placement("home")!!.doubleAd)
    }

    @Test fun showAllWithAFailedFirstDestroysTheRestAndReportsNotShown() = runTest {
        val state = AdState()
        val shower = FullScreenShower(state, { 1L }, this)
        val a = FakeAd(onShow = { it.onFailed("boom") }); val b = FakeAd()
        var shown: Boolean? = null
        shower.showAll(TestActivity(), listOf(a, b), object : FullScreenShower.Events {
            override fun onShown() {}
            override fun onFinished(shown: Boolean, reason: String?) { this@FloorsAndDoubleTest.record(shown) }
        })
        runCurrent()
        shown = recorded
        assertEquals(false, shown); assertTrue(b.destroyed); assertEquals(0, b.shows)
        assertFalse(state.adShowing.value)
    }

    private var recorded: Boolean? = null
    private fun record(v: Boolean) { recorded = v }

    // ---- config surface ----

    @Test fun remoteConfigControlsFloors() {
        val base = AdsConfig.builder().interstitial("home").units("a", "b").build()
        val json = """{"placements":{"home":{"waterfall":"parallel","tierTimeouts":{"a":1500},
            "disabledUnits":["b"],"skipAfterFails":3,"skipForSec":120,"bannerType":"medium-rectangle"}}}"""
        val p = RemoteOverrides.apply(base, json).placement("home")!!
        assertEquals(WaterfallMode.PARALLEL, p.waterfall)
        assertEquals(mapOf("a" to 1500L), p.unitTimeouts)
        assertEquals(setOf("b"), p.disabledUnits)
        assertEquals(3, p.skipAfterFails); assertEquals(120, p.skipForSeconds)
        assertEquals(BannerType.MEDIUM_RECTANGLE, p.bannerType)
    }

    @Test fun placementConstantsAndBuilderAcceptTypedPlacements() {
        val home = AdPlacement.of("home")
        val c = AdsConfig.builder().interstitial(home).units("a").banner(AdPlacement.of("b1")).bannerType(BannerType.LEADERBOARD).build()
        assertEquals("home", c.placement("home")!!.name)
        assertEquals(BannerType.LEADERBOARD, c.placement("b1")!!.bannerType)
        assertEquals(Placements.RANDOM, AdPlacement.RANDOM.name)
        assertEquals(home, AdPlacement.of("home"))
    }
}

package com.gcl.gamechiefads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A dev who declares only ONE native spot still gets ads in every native space (switch: nativeUnitFallback, default on). */
@OptIn(ExperimentalCoroutinesApi::class)
class NativeFallbackTest {
    private class Result { val failed = mutableListOf<String>(); var loaded = 0 }

    /** Asks for [spot] and returns the unit ids that were requested and how it ended. (The fake SDK never fills: we only look at the requests.) */
    private fun TestScope.ask(cfg: AdsConfig, spot: String): Pair<List<String>, Result> {
        val seen = mutableListOf<String>()
        val pipeline = AdLoadPipeline({ cfg }, { true }, false, FloorPolicy { 0L })
        val controller = NativeController(AdState(), { cfg }, pipeline, FakeScreens(null), this, { u -> seen += u; null })
        val r = Result()
        controller.load(android.app.Activity(), spot, object : NativeLoadCallback {
            override fun onLoaded(ad: com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd) { r.loaded++ }
            override fun onFailed(reason: String) { r.failed += reason }
        })
        advanceUntilIdle()
        return seen to r
    }

    @Test fun oneDeclaredNativeSpotServesTheUndeclaredOnesWithTheirOwnRules() = runTest {
        val cfg = AdsConfig.builder().introNativeUnits("hi", "med", "all").build()
        assertEquals(listOf("hi", "med", "all"), ask(cfg, Placements.LANGUAGE_NATIVE).first)      // a floors spot walks the floors
        assertEquals(listOf("all"), ask(cfg, Placements.LIST_NATIVE).first)                      // a single-request spot: ONE request, the last unit
        assertEquals(listOf("all"), ask(cfg, Placements.INTRO_SLIDE_NATIVE).first)
        assertEquals(listOf("all"), ask(cfg, "my_own_native_spot").first.takeLast(1))            // any custom name works too
    }

    @Test fun aSingleListUnitServesTheIntroAndLanguageSpots() = runTest {
        val cfg = AdsConfig.builder().listNativeUnit("only").build()
        assertEquals(listOf("only"), ask(cfg, Placements.INTRO_NATIVE).first)
        assertEquals(listOf("only"), ask(cfg, Placements.LANGUAGE_NATIVE).first)
    }

    @Test fun aSpotKeepsItsOwnUnitsWhenItHasThem() = runTest {
        val cfg = AdsConfig.builder().introNativeUnits("intro").languageNativeUnits("lang").build()
        assertEquals(listOf("lang"), ask(cfg, Placements.LANGUAGE_NATIVE).first)
    }

    @Test fun theSwitchOffMeansNoUnitsNoAdAndNoRequest() = runTest {
        val cfg = AdsConfig.builder().introNativeUnits("a").nativeUnitFallback(false).build()
        val (seen, r) = ask(cfg, Placements.LIST_NATIVE)
        assertTrue(seen.isEmpty()); assertEquals(1, r.failed.size)
    }

    @Test fun theFullScreenNativeNeverBorrowsAndADisabledSpotIsNotADonor() = runTest {
        val cfg = AdsConfig.builder().introNativeUnits("a").fullScreenNativeUnit("fs").build()      // full-screen is off by default
        assertTrue(ask(cfg, Placements.FULLSCREEN_NATIVE).first.isEmpty())                          // disabled: nothing, and it does not borrow either
        val only = AdsConfig.builder().fullScreenNativeUnit("fs").build()
        assertTrue(ask(only, Placements.LIST_NATIVE).first.isEmpty())                               // a disabled spot is not a donor
        val noDeclared = AdsConfig.builder().bannerUnits("b").build()
        assertTrue(ask(noDeclared, Placements.LIST_NATIVE).first.isEmpty())                         // banners are not native units
    }

    @Test fun theRemoteRulesCanTurnTheSwitchOffWithoutAnAppUpdate() = runTest {
        val base = AdsConfig.builder().introNativeUnits("a").build()
        val off = RemoteOverrides.apply(base, """{"nativeUnitFallback":false}""")
        assertTrue(ask(off, Placements.LIST_NATIVE).first.isEmpty())
        assertEquals(listOf("a"), ask(RemoteOverrides.apply(off, """{"nativeUnitFallback":true}"""), Placements.LIST_NATIVE).first)
    }
}


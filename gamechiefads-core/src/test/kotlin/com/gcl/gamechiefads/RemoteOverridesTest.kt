package com.gcl.gamechiefads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteOverridesTest {
    private val base = AdsConfig.builder()
        .interstitial("random").units("a", "b").everyNScreens(3).minGapSeconds(30)
        .appOpen("splash").units("s1")
        .build()

    @Test fun nullBlankAndGarbageLeaveConfigUntouched() {
        assertSame(base, RemoteOverrides.apply(base, null))
        assertSame(base, RemoteOverrides.apply(base, "  "))
        assertSame(base, RemoteOverrides.apply(base, "{not json"))
    }

    @Test fun overridesFieldsOfOnePlacementOnly() {
        val json = """{"placements":{"random":{"units":["x","y","z"],"every":5,"gap":60,"enabled":false,"mode":"preload","tierTimeoutMs":2000}}}"""
        val c = RemoteOverrides.apply(base, json)
        val r = c.placement("random")!!
        assertEquals(listOf("x", "y", "z"), r.units)
        assertEquals(5, r.everyNScreens)
        assertEquals(60, r.minGapSeconds)
        assertFalse(r.enabled)
        assertEquals(AdMode.PRELOAD, r.mode)
        assertEquals(2000L, r.tierTimeoutMs)
        assertEquals(base.placement("splash"), c.placement("splash"))
    }

    @Test fun badValuesAreIgnoredIndividually() {
        val json = """{"placements":{"random":{"units":[],"every":-1,"gap":"soon","enabled":"yes","mode":"turbo","tierTimeoutMs":0}}}"""
        assertEquals(base.placement("random"), RemoteOverrides.apply(base, json).placement("random"))
    }

    @Test fun globalDisableTurnsEverythingOff() {
        val c = RemoteOverrides.apply(base, """{"enabled":false}""")
        assertTrue(c.placements.values.none { it.enabled })
    }

    @Test fun unknownPlacementsAreNotAdded() {
        val c = RemoteOverrides.apply(base, """{"placements":{"ghost":{"units":["q"]}}}""")
        assertEquals(base.placements.keys, c.placements.keys)
    }

    @Test fun modeIsCaseInsensitiveAndCanGoBackToOnDemand() {
        val pre = base.copy(placements = base.placements + ("random" to base.placement("random")!!.copy(mode = AdMode.PRELOAD)))
        val c = RemoteOverrides.apply(pre, """{"placements":{"random":{"mode":"ON_DEMAND"}}}""")
        assertEquals(AdMode.ON_DEMAND, c.placement("random")!!.mode)
    }
}

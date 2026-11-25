package com.gcl.gamechiefads.ui

import com.gcl.gamechiefads.Placements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NativeSizeDefaultsTest {
    @Test fun noSizeGivenMeansASmallCardAndSmallMediaOnTheLanguageScreen() {
        assertEquals(NativeSize.SMALL_MEDIA, NativeSize.defaultFor(Placements.LANGUAGE_NATIVE))
        assertEquals(NativeSize.SMALL_MEDIA, NativeSize.defaultFor(Placements.INTRO_NATIVE))
        for (p in listOf(Placements.LIST_NATIVE, Placements.FULLSCREEN_NATIVE, "my_own_spot", null)) assertEquals(NativeSize.SMALL, NativeSize.defaultFor(p))
    }

    @Test fun bigSizesAreNeverADefault() {
        val defaults = (listOf(Placements.LANGUAGE_NATIVE, Placements.INTRO_NATIVE, Placements.LIST_NATIVE, "x", null)).map { NativeSize.defaultFor(it) }
        assert(defaults.none { it == NativeSize.MEDIUM || it == NativeSize.LARGE || it == NativeSize.FULL })
    }

    @Test fun namesAreFoundInAnyCaseAndUnknownOnesAreNull() {
        assertEquals(NativeSize.SMALL_MEDIA, NativeSize.fromName(" Small_Media "))
        assertEquals(NativeSize.SMALL_MEDIA, NativeSize.fromName("smallMedia")); assertEquals(IntroPager.bigAdCount(false, 5), 0); assertEquals(IntroPager.bigAdCount(true, 2), 2); assertEquals(IntroPager.bigAdCount(true, 99), 20)
        assertNull(NativeSize.fromName("huge")); assertNull(NativeSize.fromName(null))
    }
}

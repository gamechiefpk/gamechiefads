package com.gcl.gamechiefads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagBannersTest {
    @Test fun eachTagMapsToItsPlacementAndFloorSetting() {
        assertEquals(TagBanners.Spec(Placements.BANNER, true), TagBanners.parse("gads_banner"))
        assertEquals(TagBanners.Spec(Placements.BANNER, false), TagBanners.parse("gads_banner_fast"))
        assertEquals(TagBanners.Spec(Placements.EXIT_BANNER, true), TagBanners.parse("gads_exit_banner"))
        assertEquals(TagBanners.Spec(Placements.EXIT_BANNER, false), TagBanners.parse("gads_exit_banner_fast"))
        assertEquals(TagBanners.Spec(Placements.SPLASH_BANNER, false, BannerType.LARGE_BANNER), TagBanners.parse("gads_splash_banner"))
    }

    @Test fun caseAndSpacesDoNotMatterAndOtherTagsAreIgnored() {
        assertEquals(Placements.BANNER, TagBanners.parse("  GADS_Banner ")?.placement)
        assertNull(TagBanners.parse(null)); assertNull(TagBanners.parse("")); assertNull(TagBanners.parse("my_frame")); assertNull(TagBanners.parse("gads_banner2"))
    }

    @Test fun nativeTagsCarryThePlacementAndAnOptionalSize() {
        assertEquals(TagBanners.NativeSpec("language_native", "medium"), TagBanners.parseNative("gads_native:language_native:medium"))
        assertEquals(TagBanners.NativeSpec("intro_native", null), TagBanners.parseNative(" GADS_NATIVE : intro_native "))
        assertNull(TagBanners.parseNative("gads_native")); assertNull(TagBanners.parseNative("gads_native:")); assertNull(TagBanners.parseNative("gads_native:a:b:c"))
        assertNull(TagBanners.parseNative("gads_banner")); assertNull(TagBanners.parseNative(null))
        assertNull(TagBanners.parse("gads_native:intro_native:small"))        // not a banner tag
    }

    @Test fun theSwitchIsOnByDefaultAndCanBeTurnedOffInCodeOrRemotely() {
        val base = AdsConfig.builder().bannerUnits("b").build()
        assertTrue(base.tagBanners)
        assertFalse(AdsConfig.builder().bannerUnits("b").tagBanners(false).build().tagBanners)
        assertFalse(RemoteOverrides.apply(base, """{"tagBanners":false}""").tagBanners)
        assertFalse(RemoteOverrides.apply(RemoteOverrides.apply(base, """{"tagBanners":false}"""), """{"enabled":true}""").tagBanners) // a layer that is silent keeps it
        assertTrue(RemoteOverrides.apply(base, """{"tagBanners":"no"}""").tagBanners)  // wrong type: ignored
    }
}

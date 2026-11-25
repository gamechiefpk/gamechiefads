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

class BannerTypeDefaultTest {
    @Test fun aTagCanCarryTheBannerTypeOfANormalBanner() {
        assertEquals(TagBanners.Spec(Placements.BANNER, true, BannerType.COLLAPSIBLE_BOTTOM), TagBanners.parse("gads_banner:collapsible_bottom"))
        assertEquals(TagBanners.Spec(Placements.BANNER, false, BannerType.ADAPTIVE), TagBanners.parse("GADS_BANNER_FAST : adaptive"))
        assertNull(TagBanners.parse("gads_banner:nonsense")); assertNull(TagBanners.parse("gads_exit_banner:banner")); assertNull(TagBanners.parse("gads_banner:a:b"))
    }

    @Test fun theDefaultBannerTypeOfTheAppAppliesToTheNormalBannerOnly() {
        val c = AdsConfig.builder().bannerUnits("b").exitBannerUnits("e").splashBannerUnit("s").defaultBannerType(BannerType.COLLAPSIBLE_BOTTOM).build()
        assertEquals(BannerType.COLLAPSIBLE_BOTTOM, c.placement(Placements.BANNER)!!.bannerType)
        assertEquals(BannerType.MEDIUM_RECTANGLE, c.placement(Placements.EXIT_BANNER)!!.bannerType)
        assertEquals(BannerType.LARGE_BANNER, c.placement(Placements.SPLASH_BANNER)!!.bannerType)
        assertEquals(BannerType.ADAPTIVE, AdsConfig.builder().bannerUnits("b").build().placement(Placements.BANNER)!!.bannerType)
        // Remote Config can still change it later
        assertEquals(BannerType.ADAPTIVE, RemoteOverrides.apply(c, """{"placements":{"banner":{"bannerType":"ADAPTIVE"}}}""").placement(Placements.BANNER)!!.bannerType)
    }
}

class NativeSizeRemoteTest {
    private val base = AdsConfig.builder().introNativeUnits("n").nativeAd("list_native").units("x").build()

    @Test fun theNativeSizeCanBeSetRemotelyPerPlacement() {
        assertNull(base.placement(Placements.INTRO_NATIVE)!!.nativeSize)
        val c = RemoteOverrides.apply(base, """{"placements":{"intro_native":{"nativeSize":" TINY "},"list_native":{"nativeSize":"small_media"}}}""")
        assertEquals("tiny", c.placement(Placements.INTRO_NATIVE)!!.nativeSize)
        assertEquals("small_media", c.placement("list_native")!!.nativeSize)
    }

    @Test fun aLaterLayerThatIsSilentKeepsItAndBlankOrOversizedValuesAreIgnored() {
        val a = RemoteOverrides.apply(base, """{"placements":{"intro_native":{"nativeSize":"large"}}}""")
        val b = RemoteOverrides.apply(a, """{"placements":{"intro_native":{"units":["z"]}}}""")
        assertEquals("large", b.placement(Placements.INTRO_NATIVE)!!.nativeSize)
        assertNull(RemoteOverrides.apply(base, """{"placements":{"intro_native":{"nativeSize":"  "}}}""").placement(Placements.INTRO_NATIVE)!!.nativeSize)
        assertNull(RemoteOverrides.apply(base, """{"placements":{"intro_native":{"nativeSize":"${"x".repeat(40)}"}}}""").placement(Placements.INTRO_NATIVE)!!.nativeSize)
    }
}

class AdaptiveLargeTypeTest {
    @Test fun theTallAdaptiveBannerIsItsOwnTypeSetByTagOrRemoteRules() {
        assertEquals(TagBanners.Spec(Placements.BANNER, true, BannerType.ADAPTIVE_LARGE), TagBanners.parse("gads_banner:adaptive_large"))
        val base = AdsConfig.builder().bannerUnits("b").build()
        assertEquals(BannerType.ADAPTIVE, base.placement(Placements.BANNER)!!.bannerType)
        val c = RemoteOverrides.apply(base, """{"placements":{"banner":{"bannerType":"ADAPTIVE_LARGE"}}}""")
        assertEquals(BannerType.ADAPTIVE_LARGE, c.placement(Placements.BANNER)!!.bannerType)
    }
}

/** The ready-made Firebase files in docs/firebase must stay valid and understood by the library. */
class FirebaseFilesTest {
    private val dir = java.io.File("../docs/firebase")
    private val rules get() = java.io.File(dir, "gamechief_ads_config.json").readText()

    @Test fun theRulesFileIsValidJsonAndAppliesWithoutChangingTheDefaults() {
        org.json.JSONObject(rules)                                              // parses
        val base = AdsConfig.builder().randomInterstitialUnits("r").bannerUnits("b").exitBannerUnits("e").rewardedUnits("w").build()
        val c = RemoteOverrides.apply(base, rules)
        assertTrue(c.enabled); assertTrue(c.tagBanners)
        assertEquals(5, c.noFillRetrySeconds); assertEquals(30, c.quietSecondsAfterAppOpen)
        assertEquals(base.splashTimeoutMs, c.splashTimeoutMs); assertEquals(base.overallBudgetMs, c.overallBudgetMs)
        val r = c.placement(Placements.RANDOM)!!
        assertEquals(3, r.everyNScreens); assertEquals(30, r.minGapSeconds)
        assertEquals(base.placement(Placements.RANDOM)!!.units, r.units)       // the file never overrides your ad units
        assertEquals(base.placement(Placements.BANNER)!!.units, c.placement(Placements.BANNER)!!.units)
    }

    @Test fun theFirebaseTemplateCarriesTheSameRulesInTheRightKey() {
        val t = org.json.JSONObject(java.io.File(dir, "remoteconfig.template.json").readText())
        assertEquals("0", t.getJSONObject("parameters").getJSONObject("noOfBigNativeAdInIntro").getJSONObject("defaultValue").getString("value"))   // the intro ads count, off by default
        val ps = t.getJSONObject("parameters")
        assertEquals("false", ps.getJSONObject("enableNativeInScroll").getJSONObject("defaultValue").getString("value"))      // the list ad is off by default
        assertEquals("4", ps.getJSONObject("positionOfAdAt").getJSONObject("defaultValue").getString("value"))
        assertEquals("small", ps.getJSONObject("scrollNativeAdType").getJSONObject("defaultValue").getString("value"))
        assertEquals("true", ps.getJSONObject("debugShakeEnabled").getJSONObject("defaultValue").getString("value"))        // shake for the debug panel: on by default, debug builds only
        val p = t.getJSONObject("parameters").getJSONObject(AdsConfig.DEFAULT_REMOTE_KEY)
        assertEquals("JSON", p.getString("valueType"))
        assertEquals(org.json.JSONObject(rules).toString(), org.json.JSONObject(p.getJSONObject("defaultValue").getString("value")).toString())
    }
}

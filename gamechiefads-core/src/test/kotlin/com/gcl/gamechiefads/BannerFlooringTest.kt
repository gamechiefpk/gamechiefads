package com.gcl.gamechiefads

import org.junit.Assert.assertEquals
import org.junit.Test

/** Banner floors on/off. The controller needs Android views, so the rule is checked where it lives: unit resolution. */
class BannerFlooringTest {
    private val c = AdsConfig.builder().bannerUnits("hf", "mf", "normal").exitBannerUnits("e1", "e2").splashBannerUnit("sb").build()

    @Test fun flooringOnUsesEveryUnitInOrder() =
        assertEquals(listOf("hf", "mf", "normal"), AdUnits.resolve(c.placement(Placements.BANNER)!!, debug = false))

    @Test fun flooringOffIsOneRequestToTheLastUnit() {
        val p = c.placement(Placements.BANNER)!!.copy(flooring = false)
        assertEquals(listOf("normal"), AdUnits.resolve(p, debug = false))
        assertEquals(listOf("e2"), AdUnits.resolve(c.placement(Placements.EXIT_BANNER)!!.copy(flooring = false), debug = false))
    }

    @Test fun theSplashBannerIsASingleLargeBannerUnit() {
        val p = c.placement(Placements.SPLASH_BANNER)!!
        assertEquals(listOf("sb"), p.units); assertEquals(BannerType.LARGE_BANNER, p.bannerType)
        assertEquals(listOf("sb"), AdUnits.resolve(p.copy(flooring = false), debug = false))
    }

    @Test fun flooringOffWithADisabledLastUnitUsesTheLastEnabledOne() {
        val p = c.placement(Placements.BANNER)!!.copy(flooring = false, disabledUnits = setOf("normal"))
        assertEquals(listOf("mf"), AdUnits.resolve(p, debug = false))
    }
}

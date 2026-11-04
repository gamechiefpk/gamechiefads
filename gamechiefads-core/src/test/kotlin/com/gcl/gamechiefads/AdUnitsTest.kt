package com.gcl.gamechiefads

import org.junit.Assert.assertEquals
import org.junit.Test

class AdUnitsTest {
    private val p = PlacementConfig("x", AdFormat.INTERSTITIAL, units = listOf("real1", "real2"))

    @Test fun releaseUsesConfiguredUnits() =
        assertEquals(listOf("real1", "real2"), AdUnits.resolve(p, debug = false))

    @Test fun debugAlwaysUsesOneTestUnitOfTheRightFormat() {
        assertEquals(listOf(AdUnits.TEST_INTERSTITIAL), AdUnits.resolve(p, debug = true))
        assertEquals(listOf(AdUnits.TEST_APP_OPEN), AdUnits.resolve(p.copy(format = AdFormat.APP_OPEN), debug = true))
        assertEquals(listOf(AdUnits.TEST_BANNER), AdUnits.resolve(p.copy(format = AdFormat.BANNER), debug = true))
        assertEquals(listOf(AdUnits.TEST_NATIVE), AdUnits.resolve(p.copy(format = AdFormat.NATIVE), debug = true))
    }

    @Test fun debugEvenWithNoConfiguredUnitsStillGetsATestAd() =
        assertEquals(1, AdUnits.resolve(p.copy(units = emptyList()), debug = true).size)
}

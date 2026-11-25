package com.gcl.gamechiefads.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdRowMappingTest {
    private fun M(position: Int, n: Int, shown: Boolean = true) = Triple(position, n, shown)

    @Test fun theAdRowSitsAtThePositionOrAtTheEndOfAShortList() {
        assertEquals(4, AdRowMapping.adPos(4, 20)); assertEquals(3, AdRowMapping.adPos(4, 3)); assertEquals(0, AdRowMapping.adPos(-5, 10)); assertEquals(0, AdRowMapping.adPos(4, 0))
    }

    @Test fun everyRealItemKeepsItsOrderAroundTheAdRowAndNothingIsLost() {
        for (position in 0..8) for (n in 0..12) {
            val outer = AdRowMapping.outerCount(n, true)
            assertEquals(n + 1, outer)
            val ads = (0 until outer).filter { AdRowMapping.isAd(it, position, n, true) }
            assertEquals("position=$position n=$n", 1, ads.size)                               // exactly one ad row
            val inners = (0 until outer).filter { !AdRowMapping.isAd(it, position, n, true) }.map { AdRowMapping.toInner(it, position, n, true) }
            assertEquals((0 until n).toList(), inners)                                          // the real items are 0..n-1 in order
            for (i in 0 until n) assertEquals(i, AdRowMapping.toInner(AdRowMapping.toOuter(i, position, n, true), position, n, true))   // and back
        }
    }

    @Test fun withoutTheRowNothingChanges() {
        for (n in 0..6) {
            assertEquals(n, AdRowMapping.outerCount(n, false))
            for (i in 0 until n) { assertEquals(i, AdRowMapping.toInner(i, 3, n, false)); assertEquals(i, AdRowMapping.toOuter(i, 3, n, false)); assertFalse(AdRowMapping.isAd(i, 3, n, false)) }
        }
        assertTrue(AdRowMapping.isAd(2, 2, 10, true))
    }
}

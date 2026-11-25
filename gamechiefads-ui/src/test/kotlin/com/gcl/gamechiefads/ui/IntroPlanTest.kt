package com.gcl.gamechiefads.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IntroPlanTest {
    @Test fun adsAreSpreadEvenlyBetweenThePages() {
        assertEquals(listOf(2), IntroPlan.adSlots(3, 1))
        assertEquals(listOf(1, 3), IntroPlan.adSlots(4, 2))
        assertEquals(listOf(1, 2, 3), IntroPlan.adSlots(4, 3))
        assertEquals(listOf(1, 3, 4), IntroPlan.adSlots(5, 3))
    }

    @Test fun neverMoreAdsThanGapsAndNeverBeforeTheFirstOrAfterTheLastPage() {
        assertEquals(0, IntroPlan.adCount(1, 5)); assertEquals(emptyList<Int>(), IntroPlan.adSlots(1, 5))   // one page: no gap
        assertEquals(emptyList<Int>(), IntroPlan.adSlots(0, 3)); assertEquals(emptyList<Int>(), IntroPlan.adSlots(4, 0)); assertEquals(emptyList<Int>(), IntroPlan.adSlots(4, -2))
        for (pages in 2..9) for (ads in 0..12) {
            val s = IntroPlan.adSlots(pages, ads)
            assertEquals(IntroPlan.adCount(pages, ads), s.size)
            assertTrue("pages=$pages ads=$ads $s", s.all { it in 1 until pages })          // between pages
            assertEquals("pages=$pages ads=$ads $s", s.toSet().size, s.size)               // one ad per gap
            assertEquals(s.sorted(), s)
        }
    }

    @Test fun onlyLoadedAdsGetARowAndTheOrderIsPageAdPage() {
        val slots = IntroPlan.adSlots(4, 2)                                                 // [1, 3]
        assertEquals(listOf(0, 1, 2, 3).map { IntroPlan.Item.Page(it) }, IntroPlan.build(4, slots, emptySet()))
        assertEquals(
            listOf(IntroPlan.Item.Page(0), IntroPlan.Item.Ad(0), IntroPlan.Item.Page(1), IntroPlan.Item.Page(2), IntroPlan.Item.Page(3)),
            IntroPlan.build(4, slots, setOf(0)),
        )
        assertEquals(
            listOf(IntroPlan.Item.Page(0), IntroPlan.Item.Ad(0), IntroPlan.Item.Page(1), IntroPlan.Item.Page(2), IntroPlan.Item.Ad(1), IntroPlan.Item.Page(3)),
            IntroPlan.build(4, slots, setOf(0, 1)),
        )
        assertTrue(IntroPlan.build(4, slots, setOf(0, 1)).last() is IntroPlan.Item.Page)    // the last row is always a page (Get started)
        assertTrue(IntroPlan.build(4, slots, setOf(0, 1)).first() is IntroPlan.Item.Page)
    }
}

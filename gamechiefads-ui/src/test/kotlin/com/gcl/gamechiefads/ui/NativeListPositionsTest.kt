package com.gcl.gamechiefads.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeListPositionsTest {
    @Test fun theRecommendedLayoutIsFirstAdAfter4RowsThenEvery8() {
        val p = NativeListPositions()
        val ads = (0 until 30).filter(p::isAd)
        assertEquals(listOf(4, 12, 20, 28), ads)
        assertEquals(listOf(0, 1, 2), listOf(4, 12, 20).map(p::adIndex).let { listOf(it[0], it[1], it[2]) })
    }

    @Test fun itemIndexSkipsTheAdRowsAndEveryItemIsShownExactlyOnce() {
        val p = NativeListPositions(first = 2, every = 3)
        val items = 17
        val rows = p.count(items)
        val shown = (0 until rows).filter { !p.isAd(it) }.map(p::itemIndex)
        assertEquals((0 until items).toList(), shown)
        assertFalse(p.isAd(rows - 1))                                   // never an ad as the last row
    }

    @Test fun shortListsAndEdgeCases() {
        val p = NativeListPositions()
        assertEquals(0, p.count(0))
        assertEquals(3, p.count(3)); assertEquals(4, p.count(4))        // no ad before 4 rows of content, none at the end
        assertEquals(6, p.count(5))                                     // 4 items, an ad, 1 item
        for (n in 0..60) {
            val rows = p.count(n)
            assertEquals(n, (0 until rows).count { !p.isAd(it) })
        }
    }

    @Test fun disabledMeansNoAdRowsAtAll() {
        val p = NativeListPositions(4, 8, enabled = false)
        assertEquals(20, p.count(20)); assertTrue((0 until 20).none(p::isAd)); assertEquals(7, p.itemIndex(7))
    }
}

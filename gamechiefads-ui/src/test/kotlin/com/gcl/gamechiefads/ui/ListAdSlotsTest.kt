package com.gcl.gamechiefads.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListAdSlotsTest {
    private var now = 0L
    private val freed = mutableListOf<String>()
    private fun slots(max: Int = 3, ttl: Long = 1000) = ListAdSlots<String>(max, ttl, { now }) { freed += it }

    @Test fun eachAdIndexIsRequestedOnceNoMatterHowOftenItsRowIsBound() {
        val s = slots()
        assertTrue(s.plan(0).request)
        assertFalse(s.plan(0).request)                    // scrolled away and back while loading: no second request
        assertEquals(ListAdSlots.Action.WAIT, s.plan(0).action)
        assertTrue(s.loaded(0, "a"))
        repeat(5) { assertFalse(s.plan(0).request); assertEquals("a", s.plan(0).ad) }
        assertEquals(1, s.plan(1).let { if (it.request) 1 else 0 })   // a different index is its own request
    }

    @Test fun aFailedAdIsNeverRequestedAgain() {
        val s = slots()
        s.plan(0); s.failed(0)
        repeat(3) { val p = s.plan(0); assertEquals(ListAdSlots.Action.FAILED, p.action); assertFalse(p.request) }
    }

    @Test fun moreThanMaxAdsIsSkippedAndNeverRequested() {
        val s = slots(max = 2)
        assertTrue(s.plan(0).request); assertTrue(s.plan(1).request)
        val p = s.plan(2)
        assertEquals(ListAdSlots.Action.SKIP, p.action); assertFalse(p.request)
        assertEquals(ListAdSlots.Action.WAIT, s.plan(1).action)       // slots that exist keep working
    }

    @Test fun anExpiredAdIsFreedAndRequestedAgainOnce() {
        val s = slots(ttl = 1000)
        s.plan(0); s.loaded(0, "old")
        now = 1000; assertEquals("old", s.plan(0).ad)                 // exactly at the limit: still good
        now = 1001
        val p = s.plan(0)
        assertTrue(p.request); assertEquals(listOf("old"), freed)
        assertFalse(s.plan(0).request)                                // the new request is in flight: not again
        s.loaded(0, "new"); assertEquals("new", s.plan(0).ad)
    }

    @Test fun destroyFreesEveryAdRefusesLateOnesAndAnyNewRow() {
        val s = slots()
        s.plan(0); s.loaded(0, "a"); s.plan(1)
        s.destroy()
        assertEquals(listOf("a"), freed)
        assertFalse(s.loaded(1, "late"))                              // the caller must free this one itself
        assertEquals(ListAdSlots.Action.SKIP, s.plan(0).action)
        assertNull(s.plan(5).ad)
    }
}

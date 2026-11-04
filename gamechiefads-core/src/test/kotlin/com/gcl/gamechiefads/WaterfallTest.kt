package com.gcl.gamechiefads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaterfallTest {

    @Test fun returnsFirstSuccessAndStops() = runTest {
        val tried = mutableListOf<String>()
        val r = Waterfall.run(listOf("a", "b", "c"), 3000, 10_000) { u -> tried += u; "ad-$u" }
        assertEquals("ad-a", r)
        assertEquals(listOf("a"), tried)
    }

    @Test fun successOnSecondTier() = runTest {
        val tried = mutableListOf<String>()
        val r = Waterfall.run(listOf("a", "b", "c"), 3000, 10_000) { u ->
            tried += u
            if (u == "b") "ad-b" else null
        }
        assertEquals("ad-b", r)
        assertEquals(listOf("a", "b"), tried)
    }

    @Test fun slowTierTimesOutThenNextWins() = runTest {
        val r = Waterfall.run(listOf("slow", "fast"), 3000, 10_000) { u ->
            if (u == "slow") { delay(60_000); "never" } else "ad-fast"
        }
        assertEquals("ad-fast", r)
        assertEquals(3000, currentTime)
    }

    @Test fun allFailReturnsNull() = runTest {
        assertNull(Waterfall.run<String>(listOf("a", "b", "c"), 3000, 10_000) { null })
    }

    @Test fun allTimeOutStaysInsideOverallBudget() = runTest {
        // 3 tiers x 5 s tier timeout would be 15 s; the 8 s budget must win (old code: 15 s vs a 12 s limit)
        val r = Waterfall.run<String>(listOf("a", "b", "c"), 5000, 8000) { delay(60_000); "never" }
        assertNull(r)
        assertEquals(8000, currentTime)
    }

    @Test fun throwingTierIsAMissNotACrash() = runTest {
        val r = Waterfall.run(listOf("boom", "ok"), 3000, 10_000) { u ->
            if (u == "boom") error("sdk exploded") else "ad-ok"
        }
        assertEquals("ad-ok", r)
    }

    @Test fun emptyAndBlankUnitsReturnNullWithoutLoading() = runTest {
        var calls = 0
        assertNull(Waterfall.run<String>(emptyList(), 3000, 10_000) { calls++; "x" })
        assertNull(Waterfall.run<String>(listOf("", " "), 3000, 10_000) { calls++; "x" })
        assertEquals(0, calls)
    }
}

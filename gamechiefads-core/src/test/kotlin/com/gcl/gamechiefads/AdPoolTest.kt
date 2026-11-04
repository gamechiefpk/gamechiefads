package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdPoolTest {
    private class FakeAd(override val unitId: String = "u") : FullScreenAd {
        var destroyed = false
        override fun show(activity: Activity, listener: FullScreenAd.Listener) {}
        override fun destroy() { destroyed = true }
    }

    private var now = 0L
    private val pool = AdPool(ttlMs = 1000, clock = { now })

    @Test fun emptyPoolIsNotReady() {
        assertFalse(pool.isReady("p"))
        assertNull(pool.take("p"))
    }

    @Test fun putThenTakeReturnsAdOnceAndEmpties() {
        val ad = FakeAd()
        assertTrue(pool.put("p", ad))
        assertTrue(pool.isReady("p"))
        assertSame(ad, pool.take("p"))
        assertFalse(pool.isReady("p"))
        assertNull(pool.take("p"))
        assertFalse(ad.destroyed)
    }

    @Test fun secondPutIsRejectedAndKeepsFirst() {
        val a = FakeAd(); val b = FakeAd()
        assertTrue(pool.put("p", a))
        assertFalse(pool.put("p", b))
        assertSame(a, pool.take("p"))
    }

    @Test fun placementsAreIndependent() {
        pool.put("a", FakeAd());
        assertFalse(pool.isReady("b"))
    }

    @Test fun expiredAdIsDroppedAndDestroyed() {
        val ad = FakeAd()
        pool.put("p", ad)
        now = 999
        assertTrue(pool.isReady("p"))
        now = 1000
        assertFalse(pool.isReady("p"))
        assertTrue(ad.destroyed)
        assertNull(pool.take("p"))
    }

    @Test fun expiredSlotCanBeRefilled() {
        pool.put("p", FakeAd())
        now = 5000
        val fresh = FakeAd()
        assertTrue(pool.put("p", fresh))
        assertSame(fresh, pool.take("p"))
    }

    @Test fun fillLoadsOnceWhenEmpty() = runTest {
        var loads = 0
        assertTrue(pool.fill("p") { loads++; FakeAd() })
        assertTrue(pool.isReady("p"))
        assertEquals(1, loads)
    }

    @Test fun fillDoesNotRequestWhenAlreadyReady() = runTest {
        pool.put("p", FakeAd())
        var loads = 0
        assertTrue(pool.fill("p") { loads++; FakeAd() })
        assertEquals(0, loads)
    }

    @Test fun fillReturnsFalseWhenLoadFails() = runTest {
        assertFalse(pool.fill("p") { null })
        assertFalse(pool.isReady("p"))
    }

    @Test fun fillFailureAllowsRetry() = runTest {
        assertFalse(pool.fill("p") { null })
        assertTrue(pool.fill("p") { FakeAd() })
    }

    @Test fun concurrentFillStartsOnlyOneLoad() = runTest {
        val gate = CompletableDeferred<FakeAd>()
        var loads = 0
        val first = launch { pool.fill("p") { loads++; gate.await() } }
        testScheduler.runCurrent()
        assertFalse(pool.fill("p") { loads++; FakeAd() }) // busy: no second request
        gate.complete(FakeAd())
        first.join()
        assertEquals(1, loads)
        assertTrue(pool.isReady("p"))
    }

    @Test fun cancelledFillReleasesTheSlot() = runTest {
        val gate = CompletableDeferred<FakeAd>()
        val job = launch { pool.fill("p") { gate.await() } }
        testScheduler.runCurrent()
        job.cancel()
        job.join()
        assertTrue(pool.fill("p") { FakeAd() })
    }

    @Test fun clearDestroysEverything() {
        val a = FakeAd(); val b = FakeAd()
        pool.put("a", a); pool.put("b", b)
        pool.clear()
        assertTrue(a.destroyed && b.destroyed)
        assertFalse(pool.isReady("a"))
    }
}

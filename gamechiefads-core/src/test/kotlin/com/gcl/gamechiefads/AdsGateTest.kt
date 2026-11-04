package com.gcl.gamechiefads

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsGateTest {
    private var now = 0L
    private var consentCalls = 0
    private var initCalls = 0
    private var consentResult = true
    private var initResult = true
    private val activity = TestActivity()

    private fun gate() = AdsGate(
        consent = { consentCalls++; consentResult },
        init = { initCalls++; initResult },
        clock = { now },
        retryDeniedAfterMs = 60_000,
    )

    @Test fun consentThenInitThenRemembered() = runTest {
        val g = gate()
        assertTrue(g.ready(activity))
        assertTrue(g.ready(null)) // no activity needed once ready
        assertEquals(1, consentCalls); assertEquals(1, initCalls)
    }

    @Test fun deniedConsentIsNotAskedAgainTooSoon() = runTest {
        consentResult = false
        val g = gate()
        assertFalse(g.ready(activity)); assertFalse(g.ready(activity))
        assertEquals(1, consentCalls); assertEquals(0, initCalls)
        now = 60_000; consentResult = true
        assertTrue(g.ready(activity))
        assertEquals(2, consentCalls)
    }

    @Test fun noActivityMeansNotReadyAndNoConsentAttempt() = runTest {
        assertFalse(gate().ready(null))
        assertEquals(0, consentCalls)
    }

    @Test fun failedInitIsRetriedWithoutAskingConsentAgain() = runTest {
        initResult = false
        val g = gate()
        assertFalse(g.ready(activity))
        initResult = true
        assertTrue(g.ready(activity))
        assertEquals(1, consentCalls); assertEquals(2, initCalls)
    }

    @Test fun throwingStepIsJustNotReady() = runTest {
        val g = AdsGate({ error("ump") }, { true }, { now })
        assertFalse(g.ready(activity))
    }

    @Test fun concurrentCallersShareOneRun() = runTest {
        val g = AdsGate({ consentCalls++; delay(1_000); true }, { initCalls++; true }, { now })
        val a = async { g.ready(activity) }; val b = async { g.ready(activity) }
        assertTrue(a.await()); assertTrue(b.await())
        assertEquals(1, consentCalls); assertEquals(1, initCalls)
    }
}

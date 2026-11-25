package com.gcl.gamechiefads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShakeDetectorTest {
    private val still = Triple(0f, 0f, 9.81f)             // the phone lying on a table
    private val jolt = Triple(0f, 0f, 35f)                // about 3.6 g

    private fun ShakeDetector.feed(a: Triple<Float, Float, Float>, t: Long) = onAcceleration(a.first, a.second, a.third, t)

    @Test fun aPhoneThatIsStillOrCarriedNeverShakes() {
        val d = ShakeDetector()
        for (t in 0L..20_000L step 20) assertFalse(d.feed(still, t))
        for (t in 0L..20_000L step 20) assertFalse(d.feed(Triple(1f, 2f, 14f), t))          // walking: about 1.5 g
    }

    @Test fun threeStrongJoltsInAboutASecondAreAShake() {
        val d = ShakeDetector()
        assertFalse(d.feed(jolt, 0)); assertFalse(d.feed(jolt, 200)); assertTrue(d.feed(jolt, 400))
    }

    @Test fun oneOrTwoJoltsAreNotAShakeAndOldJoltsExpire() {
        val d = ShakeDetector()
        assertFalse(d.feed(jolt, 0)); assertFalse(d.feed(jolt, 200))
        assertFalse(d.feed(jolt, 5_000))                    // the first two are far too old: this is jolt number one again
        assertFalse(d.feed(jolt, 5_300)); assertTrue(d.feed(jolt, 5_600))
    }

    @Test fun oneRingingJoltIsCountedOnce() {
        val d = ShakeDetector()
        for (t in 0L..60L step 10) assertFalse(d.feed(jolt, t))                              // readings every 10 ms of the same bump
    }

    @Test fun afterAShakeItIsQuietSoThePanelNeverOpensTwice() {
        val d = ShakeDetector()
        d.feed(jolt, 0); d.feed(jolt, 200); assertTrue(d.feed(jolt, 400))
        assertFalse(d.feed(jolt, 600)); assertFalse(d.feed(jolt, 800)); assertFalse(d.feed(jolt, 1_000))   // still shaking: ignored
        d.feed(jolt, 3_000); d.feed(jolt, 3_200)
        assertTrue(d.feed(jolt, 3_400))                                                       // a new shake later works again
    }

    @Test fun anyDirectionCounts() {
        val d = ShakeDetector()
        assertFalse(d.feed(Triple(40f, 0f, 0f), 0)); assertFalse(d.feed(Triple(0f, -40f, 0f), 200)); assertEquals(true, d.feed(Triple(0f, 0f, -40f), 400))
    }
}

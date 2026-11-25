package com.gcl.gamechiefads

import kotlin.math.sqrt

/**
 * Decides from accelerometer readings whether the phone was shaken. Pure maths (no Android), unit-tested.
 * A shake = at least [shakesNeeded] strong jolts (over [thresholdG] times gravity), each at least [minGapMs] after the last one, all within
 * [windowMs]. After a shake the detector is quiet for [cooldownMs], so one shake never opens the panel twice.
 */
internal class ShakeDetector(
    private val thresholdG: Double = 2.4,
    private val shakesNeeded: Int = 3,
    private val minGapMs: Long = 80,
    private val windowMs: Long = 1_200,
    private val cooldownMs: Long = 1_800,
) {
    private val jolts = ArrayDeque<Long>()
    private var quietUntil = Long.MIN_VALUE

    /** [x], [y], [z] in m/s2 as the sensor reports them (gravity included), [timeMs] a monotonic clock. True = a shake just completed. */
    fun onAcceleration(x: Float, y: Float, z: Float, timeMs: Long): Boolean {
        if (timeMs < quietUntil) return false
        val g = sqrt((x * x + y * y + z * z).toDouble()) / GRAVITY
        if (g < thresholdG) return false
        if (jolts.isNotEmpty() && timeMs - jolts.last() < minGapMs) return false            // the same jolt, still ringing
        while (jolts.isNotEmpty() && timeMs - jolts.first() > windowMs) jolts.removeFirst()
        jolts.addLast(timeMs)
        if (jolts.size < shakesNeeded) return false
        jolts.clear()
        quietUntil = timeMs + cooldownMs
        return true
    }

    private companion object {
        const val GRAVITY = 9.81
    }
}

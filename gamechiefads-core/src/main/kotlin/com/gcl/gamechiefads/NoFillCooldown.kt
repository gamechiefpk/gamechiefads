package com.gcl.gamechiefads

import java.util.concurrent.ConcurrentHashMap

/**
 * After a whole floors pass found no ad for a placement, new requests for that placement wait [seconds].
 * So a user tapping again and again (or the app returning to the foreground again and again) cannot make the
 * library send requests in a loop. Success clears it. A pass itself is never repeated: HF, MF, NORMAL once each.
 */
internal class NoFillCooldown(
    private val clock: () -> Long,
    private val seconds: () -> Int,
) {
    private val failedAt = ConcurrentHashMap<String, Long>()

    fun recordFail(key: String) {
        failedAt[key] = clock()
    }

    fun recordOk(key: String) {
        failedAt.remove(key)
    }

    /** Milliseconds still to wait, 0 = a request may start. */
    fun remainingMs(key: String): Long {
        val at = failedAt[key] ?: return 0
        val s = seconds()
        if (s <= 0) return 0
        return (at + s * 1000L - clock()).coerceAtLeast(0)
    }
}

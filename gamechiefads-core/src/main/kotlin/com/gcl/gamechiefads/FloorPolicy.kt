package com.gcl.gamechiefads

/**
 * Remembers which ad units ("floors") keep missing and skips them for a while, so a high floor that rarely
 * fills does not cost its timeout on every request. Per placement: skipAfterFails misses in a row put a unit
 * aside for skipForSeconds; one hit clears its record. A placement never ends up with zero units:
 * if everything is skipped, everything is tried. Pure logic with an injected clock.
 */
internal class FloorPolicy(private val clock: () -> Long) {
    private class Stat(var misses: Int = 0, var skipUntilMs: Long = 0)

    private val stats = HashMap<String, Stat>()

    @Synchronized
    fun active(p: PlacementConfig, units: List<String>): List<String> {
        if (p.skipAfterFails <= 0) return units
        val now = clock()
        val ok = units.filter { (stats[key(p, it)]?.skipUntilMs ?: 0L) <= now }
        return ok.ifEmpty { units }
    }

    @Synchronized
    fun record(p: PlacementConfig, unit: String, hit: Boolean) {
        if (p.skipAfterFails <= 0) return
        val s = stats.getOrPut(key(p, unit)) { Stat() }
        if (hit) {
            s.misses = 0
            s.skipUntilMs = 0
        } else if (++s.misses >= p.skipAfterFails) {
            s.misses = 0
            s.skipUntilMs = clock() + p.skipForSeconds * 1000L
        }
    }

    fun isSkipped(p: PlacementConfig, unit: String): Boolean = synchronized(this) {
        (stats[key(p, unit)]?.skipUntilMs ?: 0L) > clock()
    }

    private fun key(p: PlacementConfig, unit: String) = "${p.name}|$unit"
}

package com.gcl.gamechiefads

/**
 * One ready full-screen ad per placement, for the PRELOAD path only.
 * Expires stale ads (Google invalidates them after ~1 h), never starts a second load for the same
 * placement, and never refills by itself: [fill] runs only when the app asks.
 */
internal class AdPool(
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val clock: () -> Long,
) {
    private class Entry(val ad: FullScreenAd, val loadedAtMs: Long)

    private val lock = Any()
    private val ready = HashMap<String, Entry>()
    private val filling = HashSet<String>()

    fun isReady(placement: String): Boolean = synchronized(lock) {
        dropIfExpired(placement)
        placement in ready
    }

    /** Removes and returns the ready ad, or null. The caller owns it afterwards. */
    fun take(placement: String): FullScreenAd? = synchronized(lock) {
        dropIfExpired(placement)
        ready.remove(placement)?.ad
    }

    /** Stores [ad] unless a valid one is already there; false = not stored, caller must destroy it. */
    fun put(placement: String, ad: FullScreenAd): Boolean = synchronized(lock) {
        dropIfExpired(placement)
        if (placement in ready) false else { ready[placement] = Entry(ad, clock()); true }
    }

    /**
     * Makes sure one ad is ready using [load]. No request if one is ready or already loading.
     * Returns true when an ad is ready afterwards.
     */
    suspend fun fill(placement: String, load: suspend () -> FullScreenAd?): Boolean {
        val start = synchronized(lock) {
            dropIfExpired(placement)
            when {
                placement in ready -> Start.ALREADY_READY
                !filling.add(placement) -> Start.BUSY
                else -> Start.GO
            }
        }
        if (start == Start.ALREADY_READY) { AdLog.d("pool") { "$placement: fill skipped, already ready" }; return true }
        if (start == Start.BUSY) { AdLog.d("pool") { "$placement: fill skipped, already loading" }; return false }
        try {
            val ad = load() ?: return false
            if (!put(placement, ad)) ad.destroy()
            return true
        } finally {
            synchronized(lock) { filling.remove(placement) }
        }
    }

    fun clear() {
        val old = synchronized(lock) { ready.values.map { it.ad }.also { ready.clear() } }
        old.forEach { it.destroy() }
    }

    private enum class Start { ALREADY_READY, BUSY, GO }

    // caller holds the lock
    private fun dropIfExpired(placement: String) {
        val e = ready[placement] ?: return
        if (clock() - e.loadedAtMs >= ttlMs) {
            AdLog.d("pool") { "$placement: ready ad expired, dropped" }
            ready.remove(placement)
            e.ad.destroy()
        }
    }

    companion object {
        const val DEFAULT_TTL_MS = 55 * 60 * 1000L
    }
}

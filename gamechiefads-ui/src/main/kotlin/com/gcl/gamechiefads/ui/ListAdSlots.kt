package com.gcl.gamechiefads.ui

/**
 * The bookkeeping of [NativeListAds]: one slot per ad index, each requested ONCE. Pure logic (no Android), so it is unit-tested.
 *
 * A slot is empty -> loading -> loaded (kept) or failed (never retried). A loaded ad older than [ttlMs] is dropped and requested again
 * (native ads expire after about an hour). At most [maxAds] slots exist: more ad indexes are skipped, so a long list cannot send dozens of requests.
 */
internal class ListAdSlots<T : Any>(
    private val maxAds: Int,
    private val ttlMs: Long,
    private val clock: () -> Long,
    private val destroyAd: (T) -> Unit,
) {
    enum class Action { SKIP, SHOW, WAIT, FAILED }

    /** What a row should do now. [request] = the caller must start the request (and call [loaded] or [failed] later). */
    class Plan<T>(val action: Action, val ad: T? = null, val request: Boolean = false)

    private class Slot<T> {
        var ad: T? = null
        var loadedAt = 0L
        var loading = false
        var failed = false
    }

    private val slots = HashMap<Int, Slot<T>>()
    private var destroyed = false

    fun plan(key: Int): Plan<T> {
        if (destroyed) return Plan(Action.SKIP)
        val slot = slots[key] ?: run {
            if (slots.size >= maxAds) return Plan(Action.SKIP)
            Slot<T>().also { slots[key] = it }
        }
        val ad = slot.ad
        if (ad != null && clock() - slot.loadedAt > ttlMs) { // expired: free it and ask again
            destroyAd(ad)
            slot.ad = null
        }
        return when {
            slot.ad != null -> Plan(Action.SHOW, slot.ad)
            slot.failed -> Plan(Action.FAILED)
            slot.loading -> Plan(Action.WAIT)
            else -> { slot.loading = true; Plan(Action.WAIT, request = true) }
        }
    }

    /** The request of [key] finished with an ad. False = nobody wants it any more (destroyed): the caller frees it. */
    fun loaded(key: Int, ad: T): Boolean {
        val slot = slots[key]
        if (destroyed || slot == null) return false
        slot.loading = false
        slot.ad = ad
        slot.loadedAt = clock()
        return true
    }

    fun failed(key: Int) {
        slots[key]?.let { it.loading = false; it.failed = true }
    }

    fun current(key: Int): Plan<T> {
        val s = slots[key] ?: return Plan(Action.SKIP)
        return when {
            s.ad != null -> Plan(Action.SHOW, s.ad)
            s.failed -> Plan(Action.FAILED)
            else -> Plan(Action.WAIT)
        }
    }

    fun requestsInFlight(): Int = slots.values.count { it.loading }

    /** Frees every ad and refuses everything from now on. */
    fun destroy() {
        destroyed = true
        slots.values.forEach { s -> s.ad?.let(destroyAd); s.ad = null }
        slots.clear()
    }
}

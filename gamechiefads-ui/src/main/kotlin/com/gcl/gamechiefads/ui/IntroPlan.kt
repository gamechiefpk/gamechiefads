package com.gcl.gamechiefads.ui

import kotlin.math.roundToInt

/**
 * Where the full-screen native pages go in an intro slider. Pure logic (no Android), unit-tested.
 * Ads are always BETWEEN content pages, spread evenly: never before the first page, never after the last one (that one is "Get started").
 */
internal object IntroPlan {
    sealed class Item {
        data class Page(val index: Int) : Item()
        data class Ad(val adIndex: Int) : Item()
    }

    /** At most one ad between two neighbouring pages: [ads] is limited to pages - 1. */
    fun adCount(pages: Int, ads: Int): Int = ads.coerceIn(0, (pages - 1).coerceAtLeast(0))

    /** For each ad (0..n-1): how many content pages come BEFORE it. 4 pages and 2 ads: [1, 3] (after page 1 and after page 3). */
    fun adSlots(pages: Int, ads: Int): List<Int> {
        val n = adCount(pages, ads)
        if (n == 0) return emptyList()
        return (1..n).map { i -> ((i * pages) / (n + 1.0)).roundToInt().coerceIn(1, pages - 1) }
    }

    /** The pager rows: every content page, and an ad row after its page only for ads in [included] (ads that are loaded). */
    fun build(pages: Int, slots: List<Int>, included: Set<Int>): List<Item> {
        val out = ArrayList<Item>()
        for (p in 0 until pages) {
            out += Item.Page(p)
            slots.forEachIndexed { k, before -> if (before == p + 1 && k in included) out += Item.Ad(k) }
        }
        return out
    }
}

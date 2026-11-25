package com.gcl.gamechiefads.ui

/**
 * Positions of a list with ONE ad row inserted. Pure maths (no Android), unit-tested. The ad row sits at outer position `min(position, n)`: at the wanted
 * position, or at the end when the list is shorter than that. [n] is the number of items of the real list, [shown] says whether the ad row exists now.
 */
internal object AdRowMapping {
    fun adPos(position: Int, n: Int): Int = position.coerceAtLeast(0).coerceAtMost(n)

    fun outerCount(n: Int, shown: Boolean): Int = n + if (shown) 1 else 0

    fun isAd(outer: Int, position: Int, n: Int, shown: Boolean): Boolean = shown && outer == adPos(position, n)

    /** The position in the real list of an outer position (not for the ad row itself). */
    fun toInner(outer: Int, position: Int, n: Int, shown: Boolean): Int = if (!shown || outer < adPos(position, n)) outer else outer - 1

    /** The outer position of a position of the real list. */
    fun toOuter(inner: Int, position: Int, n: Int, shown: Boolean): Int = if (!shown || inner < adPos(position, n)) inner else inner + 1
}

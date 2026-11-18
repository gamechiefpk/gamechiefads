package com.gcl.gamechiefads.ui

/**
 * Where the ad rows go in a list, so the adapter never does the index maths by hand.
 *
 * Recommended: the first ad after 4 rows, then one every 8 rows (`NativeListPositions()`): rows 0-3 are content, row 4 is an ad, rows 5-11 content, row 12 an ad...
 * No ad before [first] rows of content, none at the very end of the list. [enabled] = false (premium users) gives a list with no ad rows at all.
 *
 * ```java
 * NativeListPositions pos = new NativeListPositions();                  // or (first, every) or (first, every, !isPremium)
 * getItemCount():              pos.count(items.size())
 * getItemViewType(position):   pos.isAd(position) ? TYPE_AD : TYPE_ITEM
 * onBindViewHolder, ad row:    ads.bind((FrameLayout) holder.itemView, pos.adIndex(position))
 * onBindViewHolder, item row:  items.get(pos.itemIndex(position))
 * ```
 */
class NativeListPositions @JvmOverloads constructor(
    private val first: Int = 4,
    private val every: Int = 8,
    private val enabled: Boolean = true,
) {
    init {
        require(first >= 1 && every >= 2) { "first must be at least 1 and every at least 2" }
    }

    /** True when the row at [position] is an ad row. */
    fun isAd(position: Int): Boolean = enabled && position >= first && (position - first) % every == 0

    /** Ad number of an ad row: 0 for the first ad, 1 for the second... (use it as the key of [NativeListAds.bind]). */
    fun adIndex(position: Int): Int = (position - first) / every

    /** The content index of a normal row (the ad rows above it are not counted). */
    fun itemIndex(position: Int): Int = position - adsBefore(position)

    /** The row count for [itemCount] content items: the items plus the ad rows between them (never an ad as the last row). */
    fun count(itemCount: Int): Int {
        if (itemCount <= 0) return 0
        var n = itemCount
        while (n - adsBefore(n) < itemCount) n++
        while (n > 0 && isAd(n - 1)) n--
        return n
    }

    private fun adsBefore(position: Int): Int = if (!enabled || position <= first) 0 else (position - first - 1) / every + 1
}

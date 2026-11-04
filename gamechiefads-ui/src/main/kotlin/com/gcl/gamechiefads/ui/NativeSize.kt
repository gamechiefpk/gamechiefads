package com.gcl.gamechiefads.ui

import androidx.annotation.LayoutRes
import com.gcl.gamechiefads.ui.R

/** Marker: the placeholder fills its parent instead of using a fixed height. */
internal const val MATCH_PARENT_HEIGHT = -1

/** Ready-made native ad layouts, smallest to largest. [placeholderDp] is the shimmer height while loading. */
enum class NativeSize(@param:LayoutRes internal val layout: Int, internal val placeholderDp: Int, internal val hasMedia: Boolean) {
    /** One-line strip: icon, headline, one text line, small button (~60dp). */
    TINY(R.layout.gads_native_tiny, 60, false),

    /** Compact card, no media: icon, headline, rating or advertiser, body, full-width button (~140dp). */
    SMALL(R.layout.gads_native_small, 140, false),

    /** Media thumbnail (video capable) on the left, text and button on the right (~116dp). */
    SMALL_MEDIA(R.layout.gads_native_small_media, 116, true),

    /** Media on top, then icon, headline, rating, body and button (~330dp). */
    MEDIUM(R.layout.gads_native_medium, 330, true),

    /** Tall media, bigger text, store and price, large button (~480dp). */
    LARGE(R.layout.gads_native_large, 480, true),

    /** Fills its parent; media takes the spare height. Use in dialogs or full-screen exit pages. */
    FULL(R.layout.gads_native_full, MATCH_PARENT_HEIGHT, true);

    internal companion object {
        fun fromAttr(value: Int): NativeSize = entries.getOrElse(value) { MEDIUM }
    }
}

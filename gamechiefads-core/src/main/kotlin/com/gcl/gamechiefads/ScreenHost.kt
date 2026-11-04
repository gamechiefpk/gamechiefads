package com.gcl.gamechiefads

import android.app.Activity

/** What the flows need to know about the visible screen. Implemented by [ScreenTracker]. */
internal interface ScreenHost {
    val current: Activity?

    /** True if [activity] is the visible, alive screen (safe to show an ad on). */
    fun isUsable(activity: Activity): Boolean
}

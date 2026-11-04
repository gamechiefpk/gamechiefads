package com.gcl.gamechiefads

import android.app.Activity

/** "Loading ad" overlay shown during an on-demand interstitial load. The ui module has a default. */
interface AdLoaderUi {
    fun show(activity: Activity)
    fun dismiss()
}

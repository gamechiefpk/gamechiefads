package com.gcl.gamechiefads

import android.app.Activity

/** "Loading ad" overlay shown during an on-demand interstitial load. The ui module has a default. */
interface AdLoaderUi {
    fun show(activity: Activity)

    /**
     * Same, with the placement and its optional custom texts (AdsConfig `loaderText`). The default ignores them
     * and calls [show], so existing implementations keep working.
     */
    fun show(activity: Activity, info: LoaderInfo) = show(activity)

    fun dismiss()
}

/** What the overlay may show. [title] / [message] are null when the placement has no custom text. */
data class LoaderInfo(val placement: String, val title: String?, val message: String?)

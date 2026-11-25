package com.gcl.gamechiefads.sdk

import com.gcl.gamechiefads.AdLog
import com.google.android.libraries.ads.mobile.sdk.MobileAds

/** Google's Ad Inspector (a screen that lists the requests and responses of this device). Never throws; [onClosed] gets an error text or null. */
internal object AdInspector {
    fun open(onClosed: (String?) -> Unit) {
        try {
            MobileAds.openAdInspector { error -> onClosed(error?.message) }
        } catch (t: Throwable) {
            AdLog.d("qa") { "ad inspector failed: $t" }
            onClosed(t.message ?: "ad inspector not available")
        }
    }
}

package com.gcl.gamechiefads

import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/** Lets the caller stop waiting (e.g. the view was detached). A late ad is destroyed. */
fun interface AdRequestHandle {
    fun cancel()
}

/** Result of [GamechiefAds.loadNative]. Exactly one method is called, on the main thread. */
interface NativeLoadCallback {
    /** The caller owns [ad] now and must call ad.destroy() when done. */
    fun onLoaded(ad: NativeAd)

    fun onFailed(reason: String)
}

/** Optional result of [GamechiefAds.banner]. Exactly one method is called, on the main thread. */
interface BannerListener {
    fun onLoaded() {}
    fun onFailed(reason: String) {}
}

package com.gcl.gamechiefads.sdk

import com.gcl.gamechiefads.AdLog
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean

/** Loads one native ad for one unit id. Call from the main thread. Never throws. */
internal object NativeSource {
    suspend fun load(unitId: String): NativeAd? = suspendCancellableCoroutine { c ->
        val once = AtomicBoolean(false)
        try {
            val request = NativeAdRequest.Builder(unitId, listOf(NativeAd.NativeAdType.NATIVE)).build()
            NativeAdLoader.load(request, object : NativeAdLoaderCallback {
                override fun onNativeAdLoaded(nativeAd: NativeAd) = deliver(c, once, nativeAd)
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    AdLog.d("native load failed: ${adError.code} ${adError.message}")
                    deliver<NativeAd>(c, once, null)
                }
            })
        } catch (t: Throwable) {
            deliver<NativeAd>(c, once, null)
        }
    }
}

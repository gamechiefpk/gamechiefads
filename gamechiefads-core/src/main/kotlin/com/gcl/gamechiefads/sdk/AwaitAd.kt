package com.gcl.gamechiefads.sdk

import com.gcl.gamechiefads.AdLog
import com.google.android.libraries.ads.mobile.sdk.common.Ad
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Callback -> suspend. Any error, throw or no-fill gives null. A late ad after cancellation is destroyed. */
internal suspend fun <T : Ad> awaitAd(start: (AdLoadCallback<T>) -> Unit): T? =
    suspendCancellableCoroutine { c ->
        val once = AtomicBoolean(false)
        try {
            start(object : AdLoadCallback<T> {
                override fun onAdLoaded(ad: T) {
                    AdLog.d("sdk") { "load ok: ${ad.javaClass.simpleName} via ${networkOf(ad)}" }
                    deliver(c, once, ad)
                }
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    AdLog.d("sdk") { "load failed: code=${adError.code} ${adError.message}" }
                    deliver<T>(c, once, null)
                }
            })
        } catch (t: Throwable) {
            AdLog.d("sdk") { "load threw: $t" }
            deliver<T>(c, once, null)
        }
    }

internal fun <T : Ad> deliver(c: CancellableContinuation<T?>, once: AtomicBoolean, ad: T?) {
    if (!once.compareAndSet(false, true)) return
    if (c.isActive) c.resume(ad) else runCatching { ad?.destroy() }
}

/** Debug only: which network served the ad, plus every mediation source that was tried (name, latency, error). */
private fun networkOf(ad: Ad): String = try {
    val info = ad.getResponseInfo()
    val won = info.loadedAdSourceResponseInfo?.let { "${it.name} (${it.latencyMillis}ms)" } ?: info.adapterClassName ?: "unknown"
    val tried = info.adSourceResponses.joinToString(", ") { r ->
        "${r.name}:${if (r.adError == null) "ok" else "err"} ${r.latencyMillis}ms"
    }
    if (tried.isEmpty()) won else "$won [tried: $tried]"
} catch (t: Throwable) {
    "unknown"
}

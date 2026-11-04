package com.gcl.gamechiefads.sdk

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
                override fun onAdLoaded(ad: T) = deliver(c, once, ad)
                override fun onAdFailedToLoad(adError: LoadAdError) = deliver<T>(c, once, null)
            })
        } catch (t: Throwable) {
            deliver<T>(c, once, null)
        }
    }

internal fun <T : Ad> deliver(c: CancellableContinuation<T?>, once: AtomicBoolean, ad: T?) {
    if (!once.compareAndSet(false, true)) return
    if (c.isActive) c.resume(ad) else runCatching { ad?.destroy() }
}

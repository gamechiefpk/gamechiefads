package com.gcl.gamechiefads.sdk

import android.content.Context
import com.gcl.gamechiefads.AdLog
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationStatus
import com.google.android.libraries.ads.mobile.sdk.initialization.OnAdapterInitializationCompleteListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * One-time Next-Gen SDK init, off the main thread. Safe to call many times: concurrent callers
 * share one init and later calls return at once. A failed or timed-out init is retried next call.
 * Only call it after consent allows requesting ads.
 */
internal class SdkInit(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()

    suspend fun ensure(appId: String, timeoutMs: Long): Boolean = mutex.withLock {
        if (isReady()) return@withLock true
        AdLog.d("sdk") { "MobileAds.initialize start (timeout ${timeoutMs}ms)" }
        val t0 = System.nanoTime()
        val ok = withContext(Dispatchers.Default) {
            withTimeoutOrNull(timeoutMs) { initialize(appId) } ?: false
        }
        AdLog.d("sdk") { "MobileAds.initialize ${if (ok) "done" else "FAILED/timeout"} in ${(System.nanoTime() - t0) / 1_000_000}ms" }
        ok
    }

    private fun isReady(): Boolean = try { MobileAds.isInitialized } catch (t: Throwable) { false }

    private suspend fun initialize(appId: String): Boolean = suspendCancellableCoroutine { c ->
        val once = AtomicBoolean(false)
        fun finish(ok: Boolean) { if (once.compareAndSet(false, true) && c.isActive) c.resume(ok) }
        try {
            MobileAds.initialize(
                appContext,
                InitializationConfig.Builder(appId).build(),
                object : OnAdapterInitializationCompleteListener {
                    override fun onAdapterInitializationComplete(status: InitializationStatus) = finish(true)
                },
            )
        } catch (t: Throwable) {
            AdLog.d("sdk") { "MobileAds.initialize threw: $t" }
            finish(false)
        }
    }
}

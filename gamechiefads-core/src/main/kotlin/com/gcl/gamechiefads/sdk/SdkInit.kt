package com.gcl.gamechiefads.sdk

import android.content.Context
import com.gcl.gamechiefads.AdLog
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationStatus
import com.google.android.libraries.ads.mobile.sdk.initialization.OnAdapterInitializationCompleteListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * One-time Next-Gen SDK init, off the main thread. Safe to call many times: concurrent callers
 * share one init run. A caller that stops waiting (its timeout) does NOT cancel the init: it keeps
 * running and the next caller picks up the result, so a slow first init is not thrown away.
 * Only a finished-and-failed init is retried.
 * Only call it after consent allows requesting ads.
 */
internal class SdkInit(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var pending: Deferred<Boolean>? = null

    suspend fun ensure(appId: String, timeoutMs: Long): Boolean {
        if (isReady()) return true
        val run = synchronized(lock) {
            val cur = pending
            if (cur != null && !(cur.isCompleted && !cur.getCompleted())) {
                AdLog.d("sdk") { "MobileAds.initialize already running/done, waiting up to ${timeoutMs}ms" }
                cur
            } else {
                AdLog.d("sdk") { "MobileAds.initialize start (this caller waits up to ${timeoutMs}ms)" }
                scope.async { runInit(appId) }.also { pending = it }
            }
        }
        val ok = withTimeoutOrNull(timeoutMs) { run.await() } ?: false
        if (!ok) AdLog.d("sdk") { "init not ready for this caller (it keeps running if still pending)" }
        return ok
    }

    private suspend fun runInit(appId: String): Boolean {
        val t0 = System.nanoTime()
        val ok = withTimeoutOrNull(HARD_LIMIT_MS) { initialize(appId) } ?: false
        AdLog.d("sdk") { "MobileAds.initialize ${if (ok) "done" else "FAILED"} in ${(System.nanoTime() - t0) / 1_000_000}ms" }
        return ok
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

    private companion object {
        const val HARD_LIMIT_MS = 60_000L // a never-answering SDK must not block retries forever
    }
}

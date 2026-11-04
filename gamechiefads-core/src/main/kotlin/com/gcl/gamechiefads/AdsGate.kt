package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * "May the SDK request ads yet?": consent first, then one-time init. Concurrent callers share one run.
 * Success is remembered. A denied consent is not asked again for [retryDeniedAfterMs] (no UMP spam).
 * Never throws: any failure is "not ready".
 */
internal class AdsGate(
    private val consent: suspend (Activity) -> Boolean,
    private val init: suspend () -> Boolean,
    private val clock: () -> Long,
    private val retryDeniedAfterMs: Long = 60_000L,
) {
    private val mutex = Mutex()
    @Volatile private var consentOk = false
    @Volatile private var sdkOk = false
    private var deniedAt: Long? = null

    suspend fun ready(activity: Activity?): Boolean {
        if (consentOk && sdkOk) return true
        return mutex.withLock {
            if (consentOk && sdkOk) return@withLock true
            try {
                if (!consentOk) {
                    val denied = deniedAt
                    if (denied != null && clock() - denied < retryDeniedAfterMs) {
                        AdLog.d("gate") { "consent denied earlier; not asking again yet" }
                        return@withLock false
                    }
                    val a = activity ?: run {
                        AdLog.d("gate") { "no activity to show consent with" }
                        return@withLock false
                    }
                    AdLog.d("gate") { "consent: gathering" }
                    consentOk = consent(a)
                    AdLog.d("gate") { "consent: canRequestAds=$consentOk" }
                    if (!consentOk) {
                        deniedAt = clock()
                        return@withLock false
                    }
                }
                sdkOk = init()
                AdLog.d("gate") { "sdk init: $sdkOk" }
                sdkOk
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                AdLog.d("gate") { "gate threw: $t" }
                false
            }
        }
    }
}

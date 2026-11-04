package com.gcl.gamechiefads

import android.app.Activity
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * UMP wrapper: update consent info, show the form if required, answer "may I request ads?".
 * Fail open on errors: falls back to the last known canRequestAds(). Callers add a timeout.
 * Must be called on the main thread.
 */
internal class Consent {

    suspend fun gather(activity: Activity, appId: String?, underAge: Boolean = false): Boolean =
        suspendCancellableCoroutine { c ->
            val info = UserMessagingPlatform.getConsentInformation(activity)
            val done = AtomicBoolean(false)
            fun finish() {
                if (done.compareAndSet(false, true) && c.isActive) c.resume(safeCanRequest(activity))
            }
            try {
                val params = ConsentRequestParameters.Builder()
                    .setTagForUnderAgeOfConsent(underAge)
                    .apply { if (!appId.isNullOrBlank()) setAdMobAppId(appId) }
                    .build()
                info.requestConsentInfoUpdate(
                    activity, params,
                    { UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { finish() } },
                    { finish() },
                )
            } catch (t: Throwable) {
                finish()
            }
        }

    private fun safeCanRequest(activity: Activity): Boolean =
        try { UserMessagingPlatform.getConsentInformation(activity).canRequestAds() } catch (t: Throwable) { false }
}

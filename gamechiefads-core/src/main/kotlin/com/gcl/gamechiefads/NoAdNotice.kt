package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.delay

/**
 * Tells the user "no ad available, please try again in a few seconds": inside the loading dialog (it turns into the
 * message for [AdsConfig.noAdMessageMs], then closes). If the app's own dialog cannot show it, a toast is used.
 * Always leaves the dialog closed when it returns. Never throws.
 */
internal class NoAdNotice(
    private val config: () -> AdsConfig,
    private val ui: () -> AdLoaderUi?,
    private val toast: (String) -> Unit,
) {
    suspend fun show(activity: Activity, info: LoaderInfo) {
        val c = config()
        val message = c.noAdMessage
        val dialog = ui()
        try {
            if (message == null) return
            val handled = runCatching { dialog?.showNoAd(activity, info, message) }.getOrNull() == true
            if (handled) {
                AdLog.d("loader") { "no ad: message shown in the dialog for ${c.noAdMessageMs}ms" }
                delay(c.noAdMessageMs)
            } else {
                runCatching { toast(message) }
            }
        } finally {
            runCatching { dialog?.dismiss() }
        }
    }
}

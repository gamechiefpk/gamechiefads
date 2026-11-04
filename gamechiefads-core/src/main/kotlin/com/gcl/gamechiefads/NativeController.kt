package com.gcl.gamechiefads

import android.app.Activity
import android.content.Context
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Loads one native ad through the waterfall and hands it to the caller (the ui module's template view).
 * Reports exactly once, on the main thread, unless cancelled (then a late ad is destroyed).
 */
internal class NativeController(
    private val state: AdState,
    private val config: () -> AdsConfig,
    private val pipeline: AdLoadPipeline,
    private val screens: ScreenHost,
    private val scope: CoroutineScope,
    private val source: suspend (String) -> NativeAd?,
) {
    fun load(context: Context, name: String, callback: NativeLoadCallback): AdRequestHandle {
        val p = config().placement(name)
        AdLog.d("native") { "$name: load requested (known=${p != null}, premium=${state.premium.value})" }
        when {
            p == null -> callback.onFailed("unknown placement: $name")
            !p.enabled -> callback.onFailed("disabled")
            state.premium.value -> callback.onFailed("premium")
            else -> return start(context, p, callback)
        }
        return AdRequestHandle { }
    }

    private fun start(context: Context, p: PlacementConfig, callback: NativeLoadCallback): AdRequestHandle {
        val job = scope.launch {
            try {
                val ad = pipeline.load(p, context as? Activity ?: screens.current, source)
                when {
                    !isActive -> { AdLog.d("native") { "${p.name}: cancelled, late ad destroyed" }; ad?.destroy() }
                    ad == null -> { AdLog.d("native") { "${p.name}: no fill" }; callback.onFailed("no fill") }
                    state.premium.value -> { ad.destroy(); callback.onFailed("premium") }
                    else -> { AdLog.d("native") { "${p.name}: loaded" }; callback.onLoaded(ad) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                callback.onFailed(t.message ?: "error")
            }
        }
        return AdRequestHandle { job.cancel() }
    }
}

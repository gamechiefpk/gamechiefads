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
        val p = resolve(name)
        AdLog.d("native") { "$name: load requested (known=${p != null}, premium=${state.premium.value})" }
        when {
            p == null -> callback.onFailed("unknown placement: $name")
            !p.enabled -> callback.onFailed("disabled")
            state.premium.value -> callback.onFailed("premium")
            else -> return start(context, p, callback)
        }
        return AdRequestHandle { }
    }

    /**
     * The placement to load. A native spot with no units of its own borrows the units of another native spot the dev declared (when [AdsConfig.nativeUnitFallback]
     * is on), so one native unit serves every native space instead of the others showing nothing. Spots that are meant to be single-request (list, intro slide)
     * use only the LAST borrowed unit. The full-screen native popup never borrows: it is an extra that is off by default.
     */
    private fun resolve(name: String): PlacementConfig? {
        val c = config()
        val own = c.placement(name)
        if (own != null && own.units.isNotEmpty()) return own
        if (!c.nativeUnitFallback || name == Placements.FULLSCREEN_NATIVE || (own != null && !own.enabled)) return own
        val order = listOf(Placements.INTRO_NATIVE, Placements.LANGUAGE_NATIVE, Placements.LIST_NATIVE, Placements.INTRO_SLIDE_NATIVE)
        val donor = (order.mapNotNull { c.placement(it) } + c.placements.values)
            .firstOrNull { it.name != name && it.format == AdFormat.NATIVE && it.enabled && it.units.isNotEmpty() && it.name != Placements.FULLSCREEN_NATIVE } ?: return own
        AdLog.d("native") { "$name has no units: using the units of ${donor.name}" }
        val singleRequest = name == Placements.LIST_NATIVE || name == Placements.INTRO_SLIDE_NATIVE
        return (own ?: PlacementConfig(name, AdFormat.NATIVE, units = donor.units, tierTimeoutMs = donor.tierTimeoutMs)).copy(
            units = donor.units, flooring = !singleRequest && donor.flooring, unitTimeouts = donor.unitTimeouts,
        )
    }

    private fun start(context: Context, p: PlacementConfig, callback: NativeLoadCallback): AdRequestHandle {
        val job = scope.launch {
            try {
                val ad = pipeline.load(p, context as? Activity ?: screens.current, source) { it.destroy() }
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

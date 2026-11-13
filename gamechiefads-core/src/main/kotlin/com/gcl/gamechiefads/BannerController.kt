package com.gcl.gamechiefads

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import com.gcl.gamechiefads.sdk.LoadedBanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Loads a banner into a FrameLayout. Reuses a live banner already in the container, destroys it when
 * the container leaves the window, hides the container on failure (no empty gap) and when premium.
 */
internal class BannerController(
    private val state: AdState,
    private val config: () -> AdsConfig,
    private val pipeline: AdLoadPipeline,
    private val screens: ScreenHost,
    private val scope: CoroutineScope,
    private val load: suspend (android.content.Context, String, Int, BannerType) -> LoadedBanner?,
    private val placeholder: () -> BannerPlaceholder? = { null },
) {
    private val live = ArrayList<WeakReference<FrameLayout>>()

    /** [forceType] ignores the placement's configured type (loadSplashBannerAd always uses LARGE_BANNER). */
    fun attach(container: FrameLayout, name: String, listener: BannerListener? = null, forceType: BannerType? = null) {
        val p = config().placement(name)?.takeIf { config().enabled }?.let { if (forceType != null) it.copy(bannerType = forceType) else it }
        attachPlacement(container, name, p, listener)
    }

    /** One ad unit, one type, no floors, no config entry: full control from the call site. */
    fun attachUnit(container: FrameLayout, unitId: String, type: BannerType, listener: BannerListener? = null) {
        val p = if (config().enabled && unitId.isNotBlank()) {
            PlacementConfig("custom_banner", AdFormat.BANNER, units = listOf(unitId), bannerType = type, tierTimeoutMs = 5_000L)
        } else null
        attachPlacement(container, "custom_banner", p, listener)
    }

    private fun attachPlacement(container: FrameLayout, name: String, p: PlacementConfig?, listener: BannerListener?) {
        if (p == null || !p.enabled || state.premium.value) {
            AdLog.d("banner") { "$name: not loaded (known=${p != null}, enabled=${p?.enabled}, premium=${state.premium.value})" }
            clear(container)
            container.visibility = View.GONE
            listener?.onFailed(if (p == null) "unknown placement: $name" else if (!p.enabled) "disabled" else "premium")
            return
        }
        if (container.getTag(TAG) != null) {
            AdLog.d("banner") { "$name: reusing live/loading banner" }
            return // live banner already there or loading: reuse
        }
        AdLog.d("banner") { "$name: load start" }
        runCatching { placeholder()?.show(container, p.bannerType) }
        val ctx = container.context
        val widthDp = widthDp(container)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val banner = pipeline.load(p, ctx as? Activity ?: screens.current, { unit -> load(ctx, unit, widthDp, p.bannerType) }) { it.destroy() }
                if (banner == null || !container.isAttachedToWindow || state.premium.value) {
                    AdLog.d("banner") { "$name: dropped (loaded=${banner != null}, attached=${container.isAttachedToWindow}, premium=${state.premium.value})" }
                    banner?.destroy()
                    runCatching { placeholder()?.remove(container) }
                    container.setTag(TAG, null)
                    container.visibility = View.GONE
                    listener?.onFailed("no fill")
                    return@launch
                }
                container.removeAllViews()
                container.addView(banner.view)
                container.setTag(TAG, banner)
                AdLog.d("banner") { "$name: shown" }
                container.visibility = View.VISIBLE
                remember(container)
                listener?.onLoaded()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                AdLog.d("banner") { "$name: error $t" }
                runCatching { placeholder()?.remove(container) }
                container.setTag(TAG, null)
                container.visibility = View.GONE
                listener?.onFailed(t.message ?: "error")
            }
        }
        container.setTag(TAG, job) // marks "loading"; replaced by the banner on success
        job.start()
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                AdLog.d("banner") { "$name: view detached -> destroyed" }
                job.cancel()
                clear(container)
                container.removeOnAttachStateChangeListener(this)
            }
        })
    }

    /** Premium turned on: remove every banner this controller put on screen. */
    fun hideAll() {
        AdLog.d("banner") { "premium on -> hiding all banners" }
        live.removeAll { ref ->
            ref.get()?.let { clear(it); it.visibility = View.GONE }
            true
        }
    }

    private fun clear(container: FrameLayout) {
        when (val t = container.getTag(TAG)) {
            is LoadedBanner -> t.destroy()
            is kotlinx.coroutines.Job -> t.cancel()
        }
        container.setTag(TAG, null)
        container.removeAllViews()
    }

    private fun remember(container: FrameLayout) {
        live.removeAll { it.get() == null || it.get() === container }
        live.add(WeakReference(container))
    }

    private fun widthDp(container: FrameLayout): Int {
        val dm = container.resources.displayMetrics
        val px = if (container.width > 0) container.width else dm.widthPixels
        return (px / dm.density).toInt().coerceAtLeast(320)
    }

    companion object {
        // View.setTag(int, Object) needs an id; use a stable generated-style constant
        private const val TAG = 0x4741_4442
    }
}

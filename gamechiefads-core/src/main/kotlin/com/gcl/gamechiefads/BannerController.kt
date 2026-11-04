package com.gcl.gamechiefads

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import com.gcl.gamechiefads.sdk.LoadedBanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
    private val load: suspend (android.content.Context, String, Int) -> LoadedBanner?,
) {
    private val live = ArrayList<WeakReference<FrameLayout>>()

    fun attach(container: FrameLayout, name: String) {
        val p = config().placement(name)
        if (p == null || !p.enabled || state.premium.value) {
            clear(container)
            container.visibility = View.GONE
            return
        }
        if (container.getTag(TAG) != null) return // live banner already there or loading: reuse
        val ctx = container.context
        val widthDp = widthDp(container)
        val job = scope.launch {
            try {
                val banner = pipeline.load(p, ctx as? Activity ?: screens.current) { unit -> load(ctx, unit, widthDp) }
                if (banner == null || !container.isAttachedToWindow || state.premium.value) {
                    banner?.destroy()
                    container.setTag(TAG, null)
                    container.visibility = View.GONE
                    return@launch
                }
                container.removeAllViews()
                container.addView(banner.view)
                container.setTag(TAG, banner)
                container.visibility = View.VISIBLE
                remember(container)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                container.setTag(TAG, null)
                container.visibility = View.GONE
            }
        }
        container.setTag(TAG, job) // marks "loading"; replaced by the banner on success
        container.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                job.cancel()
                clear(container)
                container.removeOnAttachStateChangeListener(this)
            }
        })
    }

    /** Premium turned on: remove every banner this controller put on screen. */
    fun hideAll() {
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

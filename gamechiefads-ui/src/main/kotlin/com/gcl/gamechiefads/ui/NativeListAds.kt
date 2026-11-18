package com.gcl.gamechiefads.ui

import android.view.View
import android.widget.FrameLayout
import com.gcl.gamechiefads.AdLog
import com.gcl.gamechiefads.AdRequestHandle
import com.gcl.gamechiefads.GamechiefAds
import com.gcl.gamechiefads.NativeLoadCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import java.lang.ref.WeakReference

/**
 * Native ads inside a RecyclerView (or any recycling list). One object per list.
 *
 * ```java
 * NativeListAds ads = new NativeListAds("list_native", NativeSize.SMALL);       // placement name from your config
 *
 * // in onBindViewHolder of the AD row (the row's root view is a FrameLayout):
 * ads.bind((FrameLayout) holder.itemView, adIndex);                              // adIndex: 0 for the 1st ad, 1 for the 2nd...
 *
 * // in the screen's onDestroy:
 * ads.destroy();
 * ```
 *
 * Why not just call `AdsUi.loadNativeAd` in onBindViewHolder? A recycled row is detached, which destroys its ad, so every scroll back
 * would send a new request. Here each ad index is requested ONCE and the loaded ad is kept: scrolling away and back shows the same
 * ad again with no request. A failed ad index is not retried (no request loops): its row collapses (GONE).
 *
 * [maxAds] is a safety limit: ad indexes beyond it stay empty (GONE), so a very long list cannot send dozens of requests.
 * Main thread only, like every view call.
 */
class NativeListAds @JvmOverloads constructor(
    private val placement: String,
    private val size: NativeSize = NativeSize.SMALL,
    private val style: NativeStyle = NativeStyle.DEFAULT,
    private val maxAds: Int = 5,
) {
    private class Slot {
        var ad: NativeAd? = null
        var handle: AdRequestHandle? = null
        var failed = false
        var shownIn: WeakReference<FrameLayout>? = null
    }

    private val slots = HashMap<Int, Slot>()
    private var destroyed = false

    /** Fills [container] (the ad row) for ad number [adIndex]. Call it from onBindViewHolder. */
    fun bind(container: FrameLayout, adIndex: Int) {
        if (destroyed || (adIndex !in slots && slots.size >= maxAds)) {
            container.visibility = View.GONE
            return
        }
        val slot = slots.getOrPut(adIndex) { Slot() }
        slot.shownIn = WeakReference(container)
        container.setTag(TAG, adIndex)
        render(container, slot)
        if (slot.ad == null && slot.handle == null && !slot.failed) request(container, adIndex, slot)
    }

    /** Frees every ad and stops every request. Call it in onDestroy of the screen (or fragment view). */
    fun destroy() {
        destroyed = true
        slots.values.forEach { s ->
            s.handle?.cancel(); s.handle = null
            s.ad?.let { runCatching { it.destroy() } }; s.ad = null
        }
        slots.clear()
    }

    private fun request(container: FrameLayout, adIndex: Int, slot: Slot) {
        AdLog.d("native-list") { "$placement #$adIndex: request" }
        val h = GamechiefAds.loadNative(container.context, placement, object : NativeLoadCallback {
            override fun onLoaded(ad: NativeAd) {
                slot.handle = null
                if (destroyed) { runCatching { ad.destroy() }; return }
                slot.ad = ad
                AdLog.d("native-list") { "$placement #$adIndex: loaded" }
                refresh(adIndex, slot)
            }

            override fun onFailed(reason: String) {
                slot.handle = null
                slot.failed = true
                AdLog.d("native-list") { "$placement #$adIndex: failed ($reason), not retried" }
                refresh(adIndex, slot)
            }
        })
        if (slot.ad == null && !slot.failed) slot.handle = h
    }

    /** The ad (or the failure) arrived: update the row, if that row still shows this ad index. */
    private fun refresh(adIndex: Int, slot: Slot) {
        val c = slot.shownIn?.get() ?: return
        if (c.getTag(TAG) != adIndex) return // the row was recycled for another ad index
        render(c, slot)
    }

    private fun render(container: FrameLayout, slot: Slot) {
        val view = (container.getChildAt(0) as? NativeAdTemplateView) ?: NativeAdTemplateView(container.context).apply {
            this.size = this@NativeListAds.size
            this.style = this@NativeListAds.style
            autoLoad = false
            container.removeAllViews()
            container.addView(this, FrameLayout.LayoutParams(-1, -2))
        }
        val ad = slot.ad
        when {
            ad != null -> { container.visibility = View.VISIBLE; view.bindShared(ad) }
            slot.failed -> { view.destroy(); container.visibility = View.GONE }
            else -> { container.visibility = View.VISIBLE; view.showPlaceholder() }
        }
    }

    private companion object {
        const val TAG = 0x4741_4443
    }
}

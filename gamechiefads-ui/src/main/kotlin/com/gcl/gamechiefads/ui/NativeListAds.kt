package com.gcl.gamechiefads.ui

import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import com.gcl.gamechiefads.AdLog
import com.gcl.gamechiefads.AdRequestHandle
import com.gcl.gamechiefads.GamechiefAds
import com.gcl.gamechiefads.NativeLoadCallback
import com.gcl.gamechiefads.Placements
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import java.lang.ref.WeakReference

/**
 * Native ads inside a RecyclerView (or any recycling list). One object per list. Units: `.listNativeUnit(id)` in the config
 * (ONE unit, no floors: one request per ad).
 *
 * ```java
 * NativeListAds ads = new NativeListAds(NativeSize.SMALL);                       // uses the "list_native" placement
 * NativeListPositions pos = new NativeListPositions();                            // first ad after 4 rows, then every 8
 *
 * // onBindViewHolder of the AD row (the row's root view is a FrameLayout):
 * ads.bind((FrameLayout) holder.itemView, pos.adIndex(position));
 *
 * // when the screen is finished (onDestroy; or a ViewModel's onCleared to survive rotation):
 * ads.destroy();
 * ```
 *
 * Why not call `AdsUi.loadNativeAd` in onBindViewHolder? A recycled row is detached, which destroys its ad, so every scroll back would
 * send a new request. Here each ad index is requested ONCE and the loaded ad is kept: scrolling away and back shows the same ad with no
 * request. A failed ad index is not retried (no request loops): its row collapses (GONE). A kept ad older than [ttlMinutes] (native ads
 * expire after about an hour) is replaced by a new request when its row is bound again. [maxAds] is a safety limit: ad indexes beyond it stay
 * empty (GONE). The size can be changed from Remote Config (`"nativeSize"` on the `list_native` placement).
 *
 * Rotation: the screen is recreated, and so would be this object. Keep it in a ViewModel (or return it from onRetainNonConfigurationInstance)
 * and the loaded ads survive with no new request. Main thread only, like every view call.
 */
class NativeListAds @JvmOverloads constructor(
    private val placement: String = Placements.LIST_NATIVE,
    private val size: NativeSize = NativeSize.SMALL,
    private val style: NativeStyle = NativeStyle.DEFAULT,
    private val maxAds: Int = 5,
    private val ttlMinutes: Int = 50,
) {
    constructor(size: NativeSize) : this(Placements.LIST_NATIVE, size)
    constructor(size: NativeSize, style: NativeStyle) : this(Placements.LIST_NATIVE, size, style)

    private val slots = ListAdSlots<NativeAd>(
        maxAds, ttlMinutes * 60_000L, { SystemClock.elapsedRealtime() }, { ad -> runCatching { ad.destroy() } },
    )
    private val handles = HashMap<Int, AdRequestHandle>()
    private val shownIn = HashMap<Int, WeakReference<FrameLayout>>()

    /** Fills [container] (the ad row) for ad number [adIndex]. Call it from onBindViewHolder. */
    fun bind(container: FrameLayout, adIndex: Int) {
        val plan = slots.plan(adIndex)
        if (plan.action == ListAdSlots.Action.SKIP) {
            container.visibility = View.GONE
            return
        }
        shownIn[adIndex] = WeakReference(container)
        container.setTag(TAG, adIndex)
        render(container, plan.action, plan.ad)
        if (plan.request) request(container, adIndex)
    }

    /** Frees every ad and stops every request. Call it in onDestroy of the screen (a ViewModel's onCleared, or a fragment's onDestroyView). */
    fun destroy() {
        handles.values.forEach { it.cancel() }
        handles.clear()
        slots.destroy()
        shownIn.clear()
    }

    private fun request(container: FrameLayout, adIndex: Int) {
        AdLog.d("native-list") { "$placement #$adIndex: request" }
        var finished = false
        val h = GamechiefAds.loadNative(container.context, placement, object : NativeLoadCallback {
            override fun onLoaded(ad: NativeAd) {
                finished = true
                handles.remove(adIndex)
                if (!slots.loaded(adIndex, ad)) { runCatching { ad.destroy() }; return }
                AdLog.d("native-list") { "$placement #$adIndex: loaded" }
                refresh(adIndex)
            }

            override fun onFailed(reason: String) {
                finished = true
                handles.remove(adIndex)
                slots.failed(adIndex)
                AdLog.d("native-list") { "$placement #$adIndex: failed ($reason), not retried" }
                refresh(adIndex)
            }
        })
        if (!finished) handles[adIndex] = h
    }

    /** The ad (or the failure) arrived: update the row, if that row still shows this ad index. */
    private fun refresh(adIndex: Int) {
        val c = shownIn[adIndex]?.get() ?: return
        if (c.getTag(TAG) != adIndex) return // the row was recycled for another ad index
        val p = slots.current(adIndex)
        render(c, p.action, p.ad)
    }

    private fun render(container: FrameLayout, action: ListAdSlots.Action, ad: NativeAd?) {
        val view = (container.getChildAt(0) as? NativeAdTemplateView) ?: NativeAdTemplateView(container.context).apply {
            this.size = NativeSize.fromName(GamechiefAds.nativeSizeOf(this@NativeListAds.placement)) ?: this@NativeListAds.size // Remote Config can override
            this.style = this@NativeListAds.style
            autoLoad = false
            container.removeAllViews()
            container.addView(this, FrameLayout.LayoutParams(-1, -2))
        }
        when {
            action == ListAdSlots.Action.SHOW && ad != null -> { container.visibility = View.VISIBLE; view.bindShared(ad) }
            action == ListAdSlots.Action.FAILED -> { view.destroy(); container.visibility = View.GONE }
            else -> { container.visibility = View.VISIBLE; view.showPlaceholder() }
        }
    }

    private companion object {
        const val TAG = 0x4741_4443
    }
}

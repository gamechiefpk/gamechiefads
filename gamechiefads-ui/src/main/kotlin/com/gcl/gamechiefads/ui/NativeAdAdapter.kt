package com.gcl.gamechiefads.ui

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.gcl.gamechiefads.AdLog
import com.gcl.gamechiefads.AdRequestHandle
import com.gcl.gamechiefads.GamechiefAds
import com.gcl.gamechiefads.NativeLoadCallback
import com.gcl.gamechiefads.Placements
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * ONE native ad inside any RecyclerView, controlled from Firebase, with ONE request. Wrap your adapter, nothing else changes:
 *
 * ```java
 * NativeAdAdapter<MyHolder> adapter = new NativeAdAdapter<>(myAdapter);     // in onCreate
 * recyclerView.setAdapter(adapter);
 * // in onDestroy:
 * adapter.destroy();
 * ```
 *
 * Firebase Remote Config values (the app never handles them):
 *  - `enableNativeInScroll` (boolean, default false): the switch. false = no ad, no request.
 *  - `positionOfAdAt` (number, default 4): the row of the ad (the end of the list when the list is shorter).
 *  - `scrollNativeAdType` (text, default `small`): the size: tiny, small, small_media, medium, large.
 *
 * Why it never floods Google with requests: the ad is requested ONCE, when the list is created (not when you scroll, not when rows are recycled),
 * kept for as long as the screen lives, and shown again from memory when you scroll back. If the switch is false there is no request at all. If it
 * turns false while the screen is open the row is removed with the normal list animation and the ad is freed; turning it on again sends ONE new request.
 * No ad (not loaded, no fill, cannot be drawn) means NO row: no placeholder and no empty box. One unit, no floors: `.listNativeUnit(id)` (any other native
 * unit you declared is used when this one is missing).
 *
 * Your adapter's own changes (insert, remove, move) are forwarded; while the ad row is shown they refresh the list without the item animations.
 * For several ads in one list use [NativeListAds] instead.
 */
class NativeAdAdapter<VH : RecyclerView.ViewHolder> @JvmOverloads constructor(
    private val inner: RecyclerView.Adapter<VH>,
    private val placement: String = Placements.LIST_NATIVE,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val main = Handler(Looper.getMainLooper())
    private var recycler: RecyclerView? = null
    private var ad: NativeAd? = null
    private var handle: AdRequestHandle? = null
    private var requested = false
    private var rowShown = false
    private var broken = false                       // the ad could not be drawn: no row for this ad
    private var destroyed = false
    private var enabled = false
    private var position = DEFAULT_POSITION
    private var size = NativeSize.SMALL

    /** How many ad requests this adapter has sent. For QA: it stays 1 however much you scroll. */
    var requestCount = 0
        private set

    /** Is the ad row part of the list right now? */
    val isAdShown: Boolean get() = rowShown

    private val remoteListener = Runnable { applyRemote() }

    private val observer = object : RecyclerView.AdapterDataObserver() {
        override fun onChanged() = notifyDataSetChanged()

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) = changed(positionStart, itemCount, null)

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int, payload: Any?) = changed(positionStart, itemCount, payload)

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (!rowShown) notifyItemRangeInserted(positionStart, itemCount) else notifyDataSetChanged()
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            if (!rowShown) notifyItemRangeRemoved(positionStart, itemCount) else notifyDataSetChanged()
        }

        override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
            if (!rowShown && itemCount == 1) notifyItemMoved(fromPosition, toPosition) else notifyDataSetChanged()
        }

        private fun changed(start: Int, count: Int, payload: Any?) {
            when {
                !rowShown -> notifyItemRangeChanged(start, count, payload)
                count == 1 -> notifyItemChanged(AdRowMapping.toOuter(start, position, inner.itemCount, true), payload)
                else -> notifyDataSetChanged()
            }
        }
    }

    init {
        super.setHasStableIds(inner.hasStableIds())
        inner.registerAdapterDataObserver(observer)
    }

    // ---- the list

    private fun n() = inner.itemCount

    override fun getItemCount() = AdRowMapping.outerCount(n(), rowShown)

    private fun isAd(pos: Int) = AdRowMapping.isAd(pos, position, n(), rowShown)

    private fun innerPos(pos: Int) = AdRowMapping.toInner(pos, position, n(), rowShown)

    override fun getItemViewType(position: Int) = if (isAd(position)) AD_TYPE else inner.getItemViewType(innerPos(position))

    override fun getItemId(position: Int) = if (isAd(position)) AD_ID else inner.getItemId(innerPos(position))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == AD_TYPE) AdHolder(parent) { dropBroken() } else inner.onCreateViewHolder(parent, viewType)

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is AdHolder) holder.bind(ad, size) else inner.onBindViewHolder(holder as VH, innerPos(position))
    }

    @Suppress("UNCHECKED_CAST")
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (holder is AdHolder) holder.bind(ad, size) else inner.onBindViewHolder(holder as VH, innerPos(position), payloads)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) { if (holder !is AdHolder) inner.onViewRecycled(holder as VH) }

    @Suppress("UNCHECKED_CAST")
    override fun onFailedToRecycleView(holder: RecyclerView.ViewHolder) = if (holder is AdHolder) false else inner.onFailedToRecycleView(holder as VH)

    @Suppress("UNCHECKED_CAST")
    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) { if (holder !is AdHolder) inner.onViewAttachedToWindow(holder as VH) }

    @Suppress("UNCHECKED_CAST")
    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) { if (holder !is AdHolder) inner.onViewDetachedFromWindow(holder as VH) }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recycler = recyclerView
        inner.onAttachedToRecyclerView(recyclerView)
        GamechiefAds.addRemoteChangeListener(remoteListener)
        applyRemote()                                              // the one place where the ad is requested: when the list is created
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        GamechiefAds.removeRemoteChangeListener(remoteListener)
        inner.onDetachedFromRecyclerView(recyclerView)
        if (recycler === recyclerView) recycler = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    // ---- Firebase: switch, position, size

    /** Reads the three Firebase values again now (it also happens by itself when they change). */
    fun refresh() = applyRemote()

    private fun applyRemote() {
        if (destroyed) return
        enabled = GamechiefAds.getRemoteBoolean(KEY_ENABLE, false) && !GamechiefAds.isPremium()
        val newPos = GamechiefAds.getRemoteLong(KEY_POSITION, DEFAULT_POSITION.toLong()).coerceIn(0L, 1000L).toInt()
        val newSize = NativeSize.fromName(GamechiefAds.getRemoteString(KEY_TYPE, ""))
            ?: NativeSize.fromName(GamechiefAds.nativeSizeOf(placement)) ?: NativeSize.SMALL
        val posChanged = newPos != position
        if (rowShown && posChanged) {                              // move: take the row out at the old place, put it back at the new one
            rowShown = false; notifyItemRemoved(AdRowMapping.adPos(position, n()))
            position = newPos
            rowShown = true; notifyItemInserted(AdRowMapping.adPos(position, n()))
        } else position = newPos
        if (newSize != size) {
            size = newSize
            if (rowShown) notifyItemChanged(AdRowMapping.adPos(position, n()))     // same ad, drawn in the new size: no new request
        }
        if (enabled) {
            if (!requested && !broken) request()
            else updateRow()
        } else clean()
    }

    private fun request() {
        val ctx = recycler?.context ?: return                      // waits for setAdapter
        requested = true
        requestCount++
        AdLog.d("native-scroll") { "$placement: ONE request (Firebase $KEY_ENABLE=true, position=$position, type=$size)" }
        var finished = false
        val h = GamechiefAds.loadNative(ctx, placement, object : NativeLoadCallback {
            override fun onLoaded(ad: NativeAd) {
                finished = true; handle = null
                if (destroyed || !enabled) { runCatching { ad.destroy() }; requested = false; return }   // switched off while it loaded
                AdLog.d("native-scroll") { "$placement: loaded" }
                this@NativeAdAdapter.ad = ad
                updateRow()
            }

            override fun onFailed(reason: String) {
                finished = true; handle = null
                AdLog.d("native-scroll") { "$placement: no ad ($reason), no row, no retry" }       // requested stays true: never a second try
            }
        })
        if (!finished) handle = h
    }

    /** Brings the list in line with "is there an ad and is it wanted": a smooth insert or remove, never a reload. */
    private fun updateRow() {
        val want = enabled && ad != null && !broken && !destroyed
        if (want && !rowShown) { rowShown = true; notifyItemInserted(AdRowMapping.adPos(position, n())) }
        else if (!want && rowShown) { val p = AdRowMapping.adPos(position, n()); rowShown = false; notifyItemRemoved(p) }
    }

    /** The switch turned false: stop a running request, remove the row smoothly, free the ad. */
    private fun clean() {
        handle?.cancel(); handle = null
        val old = ad
        ad = null
        requested = false
        updateRow()
        if (old != null) main.postDelayed({ runCatching { old.destroy() } }, 500)        // after the remove animation
    }

    private fun dropBroken() {
        main.post {
            if (destroyed) return@post
            broken = true
            val old = ad; ad = null
            updateRow()
            old?.let { runCatching { it.destroy() } }
        }
    }

    /** Frees the ad and stops listening. Call it in onDestroy. */
    fun destroy() {
        destroyed = true
        GamechiefAds.removeRemoteChangeListener(remoteListener)
        handle?.cancel(); handle = null
        runCatching { inner.unregisterAdapterDataObserver(observer) }
        ad?.let { runCatching { it.destroy() } }; ad = null
        rowShown = false
        main.removeCallbacksAndMessages(null)
    }

    /** The ad row: your ad in a padded FrameLayout. */
    private class AdHolder(parent: ViewGroup, private val onBroken: () -> Unit) : RecyclerView.ViewHolder(FrameLayout(parent.context)) {
        private val view = NativeAdTemplateView(parent.context).apply {
            autoLoad = false
            listener = object : NativeAdTemplateView.Listener {
                override fun onFailed(reason: String) { onBroken() }           // cannot be drawn: the row goes away again
            }
        }

        init {
            val dp = parent.resources.displayMetrics.density
            (itemView as FrameLayout).apply {
                layoutParams = RecyclerView.LayoutParams(-1, -2)
                setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
                addView(view, FrameLayout.LayoutParams(-1, -2))
            }
        }

        fun bind(ad: NativeAd?, size: NativeSize) {
            if (ad == null) { itemView.visibility = View.GONE; return }
            itemView.visibility = View.VISIBLE
            view.size = size
            view.bindShared(ad)
        }
    }

    companion object {
        /** The Firebase Remote Config values (names fixed). */
        const val KEY_ENABLE = "enableNativeInScroll"
        const val KEY_POSITION = "positionOfAdAt"
        const val KEY_TYPE = "scrollNativeAdType"
        const val DEFAULT_POSITION = 4
        private const val AD_TYPE = -0x41444
        private const val AD_ID = Long.MIN_VALUE + 77
    }
}

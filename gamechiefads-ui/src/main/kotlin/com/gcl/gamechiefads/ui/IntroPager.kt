package com.gcl.gamechiefads.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.gcl.gamechiefads.AdLog
import com.gcl.gamechiefads.AdRequestHandle
import com.gcl.gamechiefads.GamechiefAds
import com.gcl.gamechiefads.NativeLoadCallback
import com.gcl.gamechiefads.Placements
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * An intro slider with the ads built in. You give the page layouts, the library does the rest.
 *
 * ```xml
 * <com.gcl.gamechiefads.ui.IntroPager android:id="@+id/introPager" android:layout_width="match_parent" android:layout_height="match_parent" />
 * ```
 * ```java
 * IntroPager pager = findViewById(R.id.introPager);
 * pager.setPages(R.layout.intro_1, R.layout.intro_2, R.layout.intro_3);       // only layouts, no fragments
 * pager.setOnFinished(() -> startActivity(new Intent(this, HomeActivity.class)));   // "Get started" on the last page
 * ```
 *
 * Full-screen native ads: the number of ad pages is set from Firebase Remote Config with the value `noOfBigNativeAdInIntro` (a number,
 * 0 or missing = none, the default). They are always BETWEEN content pages, spread evenly, never first and never after the last page. Each ad is
 * requested ONCE (one unit, no floors, `.introSlideNativeUnit(id)` in the config), as soon as the pager is set up, one after the other, so it is
 * ready when the user gets there. An ad that is not loaded, failed or is turned off never leaves a blank page: its page is simply not there.
 * An ad page has a close button that appears after [closeDelaySeconds] (until then the page cannot be swiped); tap it to go to the next page.
 * The last content page shows the "Get started" button.
 */
class IntroPager @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    /** Makes one page view. [parent] is the pager (use it only for layout params). */
    fun interface PageFactory {
        fun create(parent: ViewGroup): View
    }

    /** A content page was bound (shown or about to be): wire its buttons here. [index] counts content pages only (0, 1, 2...). */
    fun interface OnPageBoundListener {
        fun onPageBound(index: Int, page: View)
    }

    /** Seconds before the close button of an ad page works. */
    var closeDelaySeconds = 3

    /** The "Get started" button (shown on the last content page). Change its text, colors or font as you like. */
    val getStartedButton: Button = Button(context).apply { setText(R.string.gads_get_started); isAllCaps = false; visibility = GONE }

    var dotColor = 0x55888888
    var dotSelectedColor = 0xFF444444.toInt()

    /** For tests and QA: how many ad pages to use instead of the Firebase value. Null = read `noOfBigNativeAdInIntro`. */
    var adCountOverride: Int? = null

    private val main = Handler(Looper.getMainLooper())
    private val pager = ViewPager2(context)
    private val dots = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private val bar = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }

    private var factories: List<PageFactory> = emptyList()
    private var slots: List<Int> = emptyList()
    private var items: MutableList<IntroPlan.Item> = ArrayList()
    private val included = HashSet<Int>()
    private val shownAds = HashSet<Int>()
    private var ads: ListAdSlots<NativeAd>? = null
    private var handle: AdRequestHandle? = null
    private var countdown: Runnable? = null
    private var onBound: OnPageBoundListener? = null
    private var onFinished: Runnable? = null
    private var destroyed = false

    init {
        val dp = resources.displayMetrics.density
        pager.layoutParams = LayoutParams(-1, -1)
        addView(pager)
        bar.addView(dots, LinearLayout.LayoutParams(-2, -2))
        bar.addView(getStartedButton, LinearLayout.LayoutParams((280 * dp).toInt(), -2).apply { topMargin = (12 * dp).toInt() })
        addView(bar, LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = (24 * dp).toInt() })
        getStartedButton.setOnClickListener { onFinished?.run() }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = selected(position)
        })
    }

    fun setOnPageBound(listener: OnPageBoundListener?) { onBound = listener }

    /** Called when the user taps "Get started" on the last page. */
    fun setOnFinished(r: Runnable?) { onFinished = r }

    /** The pages as layout files (the usual way). */
    fun setPages(@LayoutRes vararg layouts: Int) {
        setPageFactories(layouts.map { res -> PageFactory { parent -> LayoutInflater.from(context).inflate(res, parent, false) } })
    }

    /** The pages as views you create yourself. */
    fun setPageFactories(pages: List<PageFactory>) {
        resetAds()
        factories = pages
        val wanted = adCountOverride ?: GamechiefAds.getRemoteLong(REMOTE_KEY, 0L).coerceIn(0L, 20L).toInt()
        val n = if (GamechiefAds.isPremium()) 0 else IntroPlan.adCount(pages.size, wanted)
        slots = IntroPlan.adSlots(pages.size, n)
        items = IntroPlan.build(pages.size, slots, emptySet()).toMutableList()
        included.clear(); shownAds.clear()
        rebuildDots()
        pager.adapter = PagesAdapter()
        pager.post { selected(pager.currentItem) }
        AdLog.d("intro") { "pager: ${pages.size} pages, $n full-screen native page(s) wanted (Firebase $REMOTE_KEY=$wanted), slots after page $slots" }
        if (n > 0) {
            ads = ListAdSlots(n, 50 * 60_000L, { SystemClock.elapsedRealtime() }, { ad -> runCatching { ad.destroy() } })
            requestNext()
        }
    }

    // ---- ads: one request per ad, one after the other, loaded ahead of time

    private fun requestNext() {
        val s = ads ?: return
        if (destroyed) return
        for (k in slots.indices) {
            val p = s.plan(k)
            if (p.request) { load(k); return }
            if (p.action == ListAdSlots.Action.WAIT) return        // one is in flight: wait for it
        }
    }

    private fun load(k: Int) {
        AdLog.d("intro") { "full-screen native #$k: request" }
        var finished = false
        val h = GamechiefAds.loadNative(context, Placements.INTRO_SLIDE_NATIVE, object : NativeLoadCallback {
            override fun onLoaded(ad: NativeAd) {
                finished = true; handle = null
                val s = ads
                if (s == null || !s.loaded(k, ad)) { runCatching { ad.destroy() }; return }
                AdLog.d("intro") { "full-screen native #$k: loaded" }
                insertAd(k)
                requestNext()
            }

            override fun onFailed(reason: String) {
                finished = true; handle = null
                ads?.failed(k)
                AdLog.d("intro") { "full-screen native #$k: no ad ($reason), its page is skipped" }
                requestNext()
            }
        })
        if (!finished) handle = h
    }

    /** A loaded ad gets its page only if it is still AHEAD of the user: nothing is ever inserted at or behind the page being looked at. */
    private fun insertAd(k: Int) {
        val wanted = IntroPlan.build(factories.size, slots, included + k)
        val at = wanted.indexOfFirst { it is IntroPlan.Item.Ad && it.adIndex == k }
        if (at <= pager.currentItem) { AdLog.d("intro") { "full-screen native #$k: loaded too late (user is past it), not shown" }; return }
        included += k
        items = wanted.toMutableList()
        pager.adapter?.notifyItemInserted(at)
        rebuildDots()
    }

    // ---- the page being looked at

    private fun selected(position: Int) {
        val item = items.getOrNull(position)
        cancelCountdown()
        when (item) {
            is IntroPlan.Item.Ad -> {
                bar.visibility = GONE                                  // a full-screen ad: no dots, no button
                startCountdown(item.adIndex)
            }
            is IntroPlan.Item.Page -> {
                pager.isUserInputEnabled = true
                bar.visibility = VISIBLE
                getStartedButton.visibility = if (item.index == factories.size - 1) VISIBLE else GONE
                updateDots(item.index)
            }
            null -> Unit
        }
    }

    /** The close button appears after the delay; until then the user stays on the ad. */
    private fun startCountdown(k: Int) {
        val holder = adHolder(k)
        if (k in shownAds) { holder?.showClose(); pager.isUserInputEnabled = true; return }
        shownAds += k
        pager.isUserInputEnabled = false
        var left = closeDelaySeconds.coerceAtLeast(0)
        val tick = object : Runnable {
            override fun run() {
                val h = adHolder(k)
                if (left <= 0) { h?.showClose(); pager.isUserInputEnabled = true; countdown = null; return }
                h?.showCountdown(left--)
                main.postDelayed(this, 1000)
            }
        }
        countdown = tick
        tick.run()
    }

    private fun cancelCountdown() {
        countdown?.let { main.removeCallbacks(it) }
        countdown = null
    }

    private fun adHolder(k: Int): AdHolder? {
        val pos = items.indexOfFirst { it is IntroPlan.Item.Ad && it.adIndex == k }
        return (pager.getChildAt(0) as? RecyclerView)?.findViewHolderForAdapterPosition(pos) as? AdHolder
    }

    private fun next(fromAdIndex: Int) {
        val pos = items.indexOfFirst { it is IntroPlan.Item.Ad && it.adIndex == fromAdIndex }
        if (pos >= 0 && pos + 1 < items.size) pager.setCurrentItem(pos + 1, true)
    }

    // ---- dots

    private fun rebuildDots() {
        dots.removeAllViews()
        val dp = resources.displayMetrics.density
        repeat(factories.size) {
            val d = View(context)
            d.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(dotColor) }
            dots.addView(d, LinearLayout.LayoutParams((8 * dp).toInt(), (8 * dp).toInt()).apply { marginStart = (4 * dp).toInt(); marginEnd = (4 * dp).toInt() })
        }
        (items.getOrNull(pager.currentItem) as? IntroPlan.Item.Page)?.let { updateDots(it.index) }
    }

    private fun updateDots(page: Int) {
        for (i in 0 until dots.childCount) (dots.getChildAt(i).background as? GradientDrawable)?.setColor(if (i == page) dotSelectedColor else dotColor)
    }

    // ---- cleanup

    private fun resetAds() {
        cancelCountdown()
        handle?.cancel(); handle = null
        ads?.destroy(); ads = null
    }

    override fun onDetachedFromWindow() {
        destroyed = true
        resetAds()
        super.onDetachedFromWindow()
    }

    // ---- adapter

    private inner class PagesAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = items.size

        override fun getItemViewType(position: Int) = when (val i = items[position]) {
            is IntroPlan.Item.Page -> i.index
            is IntroPlan.Item.Ad -> AD_TYPE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            if (viewType == AD_TYPE) return AdHolder(context) { k -> next(k) }
            val v = factories[viewType].create(parent)
            v.layoutParams = RecyclerView.LayoutParams(-1, -1)           // ViewPager2 pages must fill the pager
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val i = items[position]) {
                is IntroPlan.Item.Page -> onBound?.onPageBound(i.index, holder.itemView)
                is IntroPlan.Item.Ad -> (holder as AdHolder).bind(i.adIndex, ads?.current(i.adIndex)?.ad, i.adIndex !in shownAds)
            }
        }
    }

    /** A full-screen native page: the ad, and a close button that counts down and then works. */
    private class AdHolder(context: Context, private val onClose: (Int) -> Unit) : RecyclerView.ViewHolder(FrameLayout(context)) {
        private val view = NativeAdTemplateView(context).apply { size = NativeSize.FULL; autoLoad = false }
        private val close = TextView(context).apply {
            gravity = Gravity.CENTER; setTextColor(0xFF202124.toInt()); textSize = 16f; visibility = GONE
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xEEFFFFFF.toInt()) }   // light circle, dark mark: visible on dark and light ads
        }
        /** Swallows every touch while the close button counts down: the ad cannot be clicked by an early tap or swipe (no accidental clicks). */
        private val shield = View(context).apply { visibility = GONE; isClickable = true; setOnTouchListener { _, _ -> true } }
        private var adIndex = -1

        init {
            val dp = context.resources.displayMetrics.density
            val root = itemView as FrameLayout
            root.layoutParams = RecyclerView.LayoutParams(-1, -1)
            root.addView(view, FrameLayout.LayoutParams(-1, -1))
            root.addView(shield, FrameLayout.LayoutParams(-1, -1))
            root.addView(close, FrameLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt(), Gravity.TOP or Gravity.END).apply {
                topMargin = (40 * dp).toInt(); marginEnd = (16 * dp).toInt()
            })
            close.setOnClickListener { if (close.isClickable) onClose(adIndex) }
        }

        fun bind(k: Int, ad: NativeAd?, protectFromTouch: Boolean) {
            adIndex = k
            close.visibility = GONE
            shield.visibility = if (protectFromTouch) VISIBLE else GONE   // a fresh ad page ignores touches from the first moment
            if (ad != null) view.bindShared(ad)
        }

        fun showCountdown(seconds: Int) { shield.visibility = VISIBLE; close.visibility = VISIBLE; close.isClickable = false; close.text = seconds.toString() }

        fun showClose() { shield.visibility = GONE; close.visibility = VISIBLE; close.isClickable = true; close.text = "✕" }
    }

    companion object {
        /** The Firebase Remote Config value that sets how many full-screen native pages the intro gets (0 = none). */
        const val REMOTE_KEY = "noOfBigNativeAdInIntro"
        private const val AD_TYPE = -1
    }
}

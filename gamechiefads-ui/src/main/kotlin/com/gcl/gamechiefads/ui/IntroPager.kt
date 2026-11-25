package com.gcl.gamechiefads.ui

import android.content.Context
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
 * Two ads, two sets of units (both in the app config):
 *  - SMALL native at the bottom of every page: `.introNativeUnits(HIGH, MEDIUM, ALL)`, with floors. Small media size.
 *  - FULL-SCREEN native pages between the pages: `.introSlideNativeUnit(id)`, ONE unit, no floors. How many: Firebase number parameter
 *    `noOfBigNativeAdInIntro` (0 or missing = none, the default). Spread evenly, never first, never after the last page.
 *
 * Light and never stuck: every ad is requested ONCE, one after the other (small ads for the page you are on and the next one only), and an ad that is
 * loading, failed, turned off or cannot be shown leaves NOTHING behind: no placeholder, no empty box, no blank page. The slides never wait for an ad.
 * A full-screen ad page has the same close button as the full-screen native ad: "Close in 3", then a close mark (until then the page ignores touches
 * and cannot be swiped, so it cannot be clicked by accident). Tap it to go to the next slide. The last slide shows "Get started".
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

    /** Seconds before the close button of a full-screen ad page works. */
    var closeDelaySeconds = 3

    /** The "Get started" button (shown on the last content page). Change its text, colors or font as you like. */
    val getStartedButton: Button = Button(context).apply { setText(R.string.gads_get_started); isAllCaps = false; visibility = GONE }

    var dotColor = 0x55888888
    var dotSelectedColor = 0xFF444444.toInt()

    /** For tests and QA: how many full-screen ad pages to use instead of the Firebase value. Null = read `noOfBigNativeAdInIntro`. */
    var adCountOverride: Int? = null

    /** Show the small native (units `introNativeUnits`, floors) at the bottom of every content page. false = only the full-screen ad pages. */
    var smallNativeOnPages = true

    private val dp = resources.displayMetrics.density
    private val main = Handler(Looper.getMainLooper())
    private val pager = ViewPager2(context)
    private val dots = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private val bar = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }

    private var factories: List<PageFactory> = emptyList()
    private var slots: List<Int> = emptyList()
    private var items: MutableList<IntroPlan.Item> = ArrayList()
    private val included = HashSet<Int>()
    private val shownAds = HashSet<Int>()

    // full-screen ad pages: one request per ad, one after the other
    private var big: ListAdSlots<NativeAd>? = null
    private var bigHandle: AdRequestHandle? = null

    // small native of each content page: one request per page, current page first, then the next, one at a time
    private var small: ListAdSlots<NativeAd>? = null
    private var smallHandle: AdRequestHandle? = null

    private var generation = 0                     // a late answer of an old setup is thrown away
    private var countdown: Runnable? = null
    private var onBound: OnPageBoundListener? = null
    private var onFinished: Runnable? = null
    private var destroyed = false

    init {
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

    /**
     * Call it from the activity's onBackPressed: `if (!pager.handleBack()) super.onBackPressed();`. Returns true while a full-screen ad page counts down
     * (Back never skips the countdown, like the full-screen native ad).
     */
    fun handleBack(): Boolean = countdown != null

    /** The pages as layout files (the usual way). */
    fun setPages(@LayoutRes vararg layouts: Int) {
        setPageFactories(layouts.map { res -> PageFactory { parent -> LayoutInflater.from(context).inflate(res, parent, false) } })
    }

    /** The pages as views you create yourself. */
    fun setPageFactories(pages: List<PageFactory>) {
        resetAds()
        val gen = generation
        factories = pages
        val wanted = adCountOverride ?: GamechiefAds.getRemoteLong(REMOTE_KEY, 0L).coerceIn(0L, 20L).toInt()
        val premium = GamechiefAds.isPremium()
        val n = if (premium) 0 else IntroPlan.adCount(pages.size, wanted)
        slots = IntroPlan.adSlots(pages.size, n)
        items = IntroPlan.build(pages.size, slots, emptySet()).toMutableList()
        included.clear(); shownAds.clear()
        rebuildDots()
        pager.adapter = PagesAdapter()
        pager.post { if (gen == generation) selected(pager.currentItem) }
        AdLog.d("intro") { "pager: ${pages.size} pages, $n full-screen ad page(s) wanted (Firebase $REMOTE_KEY=$wanted), after page(s) $slots, small native=${smallNativeOnPages && !premium}" }
        if (n > 0) {
            big = ListAdSlots(n, TTL_MS, { SystemClock.elapsedRealtime() }, { ad -> runCatching { ad.destroy() } })
            requestNextBig(gen)
        }
        if (smallNativeOnPages && !premium && pages.isNotEmpty()) {
            small = ListAdSlots(pages.size, TTL_MS, { SystemClock.elapsedRealtime() }, { ad -> runCatching { ad.destroy() } })
            pumpSmall(gen)
        }
    }

    // ---- full-screen ad pages: one request per ad, one after the other, loaded ahead of time

    private fun requestNextBig(gen: Int) {
        val s = big ?: return
        if (destroyed || gen != generation) return
        for (k in slots.indices) {
            val p = s.plan(k)
            if (p.request) { loadBig(k, gen); return }
            if (p.action == ListAdSlots.Action.WAIT) return        // one is in flight: wait for it
        }
    }

    private fun loadBig(k: Int, gen: Int) {
        AdLog.d("intro") { "full-screen native #$k: request" }
        var finished = false
        val h = GamechiefAds.loadNative(context, Placements.INTRO_SLIDE_NATIVE, object : NativeLoadCallback {
            override fun onLoaded(ad: NativeAd) {
                finished = true; bigHandle = null
                val s = big
                if (gen != generation || s == null || !s.loaded(k, ad)) { runCatching { ad.destroy() }; return }
                AdLog.d("intro") { "full-screen native #$k: loaded" }
                insertAd(k)
                requestNextBig(gen)
            }

            override fun onFailed(reason: String) {
                finished = true; bigHandle = null
                if (gen != generation) return
                big?.failed(k)
                AdLog.d("intro") { "full-screen native #$k: no ad ($reason), its page is not added" }
                requestNextBig(gen)
            }
        })
        if (!finished) bigHandle = h
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

    /** An ad page whose ad cannot be drawn is taken out again at once, so there is never an empty page. */
    private fun dropAd(k: Int) {
        val pos = items.indexOfFirst { it is IntroPlan.Item.Ad && it.adIndex == k }
        if (pos < 0) return
        AdLog.d("intro") { "full-screen native #$k: could not be shown, page removed" }
        included -= k
        val wasCurrent = pos == pager.currentItem
        items.removeAt(pos)
        pager.adapter?.notifyItemRemoved(pos)
        if (wasCurrent) {
            cancelCountdown()
            pager.isUserInputEnabled = true
            val gen = generation
            pager.post { if (gen == generation) selected(pager.currentItem) }
        }
    }

    // ---- small native of each content page

    /** One request at a time: the page you are on first, then the next one. Failed or loading pages show nothing at all. */
    private fun pumpSmall(gen: Int) {
        val s = small ?: return
        if (destroyed || gen != generation || smallHandle != null) return
        val here = currentContentPage()
        for (k in intArrayOf(here, here + 1)) {
            if (k !in factories.indices) continue
            if (s.plan(k).request) { loadSmall(k, gen); return }
        }
    }

    private fun currentContentPage(): Int {
        var i = pager.currentItem.coerceIn(0, (items.size - 1).coerceAtLeast(0))
        while (i >= 0) { (items.getOrNull(i) as? IntroPlan.Item.Page)?.let { return it.index }; i-- }
        return 0
    }

    private fun loadSmall(k: Int, gen: Int) {
        AdLog.d("intro") { "small native, page $k: request" }
        var finished = false
        val h = GamechiefAds.loadNative(context, Placements.INTRO_NATIVE, object : NativeLoadCallback {
            override fun onLoaded(ad: NativeAd) {
                finished = true; smallHandle = null
                val s = small
                if (gen != generation || s == null || !s.loaded(k, ad)) { runCatching { ad.destroy() }; return }
                AdLog.d("intro") { "small native, page $k: loaded" }
                bindSmall(k)
                pumpSmall(gen)
            }

            override fun onFailed(reason: String) {
                finished = true; smallHandle = null
                if (gen != generation) return
                small?.failed(k)
                AdLog.d("intro") { "small native, page $k: no ad ($reason), nothing is shown" }
                pumpSmall(gen)
            }
        })
        if (!finished) smallHandle = h
    }

    private fun bindSmall(k: Int) {
        val pos = items.indexOfFirst { it is IntroPlan.Item.Page && it.index == k }
        val holder = (pager.getChildAt(0) as? RecyclerView)?.findViewHolderForAdapterPosition(pos) as? PageHolder ?: return
        holder.host.showSmall(small?.current(k)?.ad)
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
                pumpSmall(generation)
            }
            null -> Unit
        }
    }

    /** The close button counts down; until it works the page ignores touches (shield) and cannot be swiped. */
    private fun startCountdown(k: Int) {
        val holder = adHolder(k)
        if (k in shownAds) { holder?.ready(); pager.isUserInputEnabled = true; return }
        shownAds += k
        pager.isUserInputEnabled = false
        var left = closeDelaySeconds.coerceAtLeast(0)
        val tick = object : Runnable {
            override fun run() {
                val h = adHolder(k)
                if (left <= 0) { h?.ready(); pager.isUserInputEnabled = true; countdown = null; return }
                h?.countdown(left--)
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
        generation++                                  // answers of requests that are still running are thrown away
        cancelCountdown()
        pager.isUserInputEnabled = true               // a reset never leaves the pager locked
        bigHandle?.cancel(); bigHandle = null
        smallHandle?.cancel(); smallHandle = null
        big?.destroy(); big = null
        small?.destroy(); small = null
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
            if (viewType == AD_TYPE) {
                val gen = generation
                // posted: the adapter must not change while it is binding
                return AdHolder(context, { k -> next(k) }, { k -> main.post { if (gen == generation) dropAd(k) } })
            }
            val page = factories[viewType].create(parent)
            val last = viewType == factories.size - 1
            return PageHolder(PageHost(context, page, bottomReserve = if (last) LAST_PAGE_RESERVE_DP else PAGE_RESERVE_DP, dp = dp))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val i = items[position]) {
                is IntroPlan.Item.Page -> {
                    onBound?.onPageBound(i.index, (holder as PageHolder).host.page)
                    holder.host.showSmall(small?.current(i.index)?.ad)       // null (loading, failed, off) = nothing shown
                }
                is IntroPlan.Item.Ad -> (holder as AdHolder).bind(i.adIndex, big?.current(i.adIndex)?.ad, i.adIndex !in shownAds)
            }
        }

        private val RecyclerView.ViewHolder.host get() = (this as PageHolder).host
    }

    /** A content page: your layout, and below it the small native when there is one. The slot is GONE otherwise: no empty box. */
    private class PageHost(context: Context, val page: View, bottomReserve: Int, private val dp: Float) : LinearLayout(context) {
        private val slot = FrameLayout(context).apply { visibility = GONE }
        private var adView: NativeAdTemplateView? = null

        init {
            orientation = VERTICAL
            layoutParams = RecyclerView.LayoutParams(-1, -1)
            addView(page, LayoutParams(-1, 0, 1f))
            addView(slot, LayoutParams(-1, -2).apply { setMargins((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt()) })
            setPadding(0, 0, 0, (bottomReserve * dp).toInt())          // room for the dots (and the Get started button on the last page)
        }

        fun showSmall(ad: NativeAd?) {
            if (ad == null) { slot.visibility = GONE; adView?.destroy(); return }
            val v = adView ?: NativeAdTemplateView(context).apply {
                size = NativeSize.fromName(GamechiefAds.nativeSizeOf(Placements.INTRO_NATIVE)) ?: NativeSize.defaultFor(Placements.INTRO_NATIVE)
                autoLoad = false
                listener = object : NativeAdTemplateView.Listener {
                    override fun onFailed(reason: String) { slot.visibility = GONE }     // cannot be drawn: gone, no empty box
                }
                slot.addView(this, LayoutParams(-1, -2))
                adView = this
            }
            slot.visibility = VISIBLE
            v.bindShared(ad)
        }
    }

    private class PageHolder(val host: PageHost) : RecyclerView.ViewHolder(host)

    /** A full-screen native page: the ad, the shield and the close button (same look as the full-screen native ad). */
    private class AdHolder(context: Context, private val onClose: (Int) -> Unit, private val onBindFailed: (Int) -> Unit) :
        RecyclerView.ViewHolder(FrameLayout(context)) {
        private var adIndex = -1
        private val view = NativeAdTemplateView(context).apply {
            size = NativeSize.FULL; autoLoad = false
            listener = object : NativeAdTemplateView.Listener {
                override fun onFailed(reason: String) { onBindFailed(adIndex) }          // cannot be drawn: this page is removed again
            }
        }
        private val close = NativeCloseButton.create(context)

        /** Swallows every touch while the close button counts down: the ad cannot be clicked by an early tap or swipe. */
        private val shield = View(context).apply { visibility = GONE; isClickable = true; setOnTouchListener { _, _ -> true } }

        init {
            val root = itemView as FrameLayout
            root.layoutParams = RecyclerView.LayoutParams(-1, -1)
            root.addView(view, FrameLayout.LayoutParams(-1, -1))
            root.addView(shield, FrameLayout.LayoutParams(-1, -1))
            NativeCloseButton.place(root, close)
            close.visibility = GONE
            close.setOnClickListener { if (close.isEnabled) onClose(adIndex) }
        }

        fun bind(k: Int, ad: NativeAd?, protectFromTouch: Boolean) {
            adIndex = k
            close.visibility = GONE
            shield.visibility = if (protectFromTouch) VISIBLE else GONE   // a fresh ad page ignores touches from the first moment
            if (ad != null) view.bindShared(ad) else onBindFailed(k)
        }

        fun countdown(seconds: Int) { shield.visibility = VISIBLE; close.visibility = VISIBLE; NativeCloseButton.countdown(close, seconds) }

        fun ready() { shield.visibility = GONE; close.visibility = VISIBLE; NativeCloseButton.ready(close) }
    }

    companion object {
        /** The Firebase Remote Config value that sets how many full-screen native pages the intro gets (0 = none). */
        const val REMOTE_KEY = "noOfBigNativeAdInIntro"
        private const val AD_TYPE = -1
        private const val TTL_MS = 50 * 60_000L
        private const val PAGE_RESERVE_DP = 40            // dots
        private const val LAST_PAGE_RESERVE_DP = 100      // dots + Get started
    }
}

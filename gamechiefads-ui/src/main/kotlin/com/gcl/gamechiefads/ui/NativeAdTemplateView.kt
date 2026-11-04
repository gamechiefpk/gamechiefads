package com.gcl.gamechiefads.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.gcl.gamechiefads.AdRequestHandle
import com.gcl.gamechiefads.GamechiefAds
import com.gcl.gamechiefads.NativeLoadCallback
import com.gcl.gamechiefads.ui.R
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * One drop-in native ad. XML:
 * `<com.gcl.gamechiefads.ui.NativeAdTemplateView app:gads_size="medium" app:gads_placement="native_home" />`
 * or in code `view.setSize(NativeSize.SMALL); view.load("native_home");`.
 *
 * Shows a shimmer while loading, the ad when ready, and hides itself (GONE) if there is no ad, the
 * user is premium or the SDK is unavailable. The ad is destroyed when the view leaves the window.
 */
class NativeAdTemplateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    interface Listener {
        fun onLoaded() {}
        fun onFailed(reason: String) {}
    }

    var size: NativeSize = NativeSize.MEDIUM
        set(value) {
            if (field == value) return
            field = value
            val loading = shimmer != null
            val bound = ad
            removeContent()
            if (loading) showShimmer()
            if (bound != null) bindInternal(bound)
        }

    var style: NativeStyle = NativeStyle.DEFAULT
        set(value) {
            field = value
            content?.let { NativeStyler.apply(it, value) }
        }

    /** Placement name from AdsConfig. With [autoLoad] the ad loads when the view attaches. */
    var placement: String? = null

    var autoLoad: Boolean = true

    /** true (default): GONE when there is no ad. false: stays in the layout, empty. */
    var hideOnFail: Boolean = true

    var listener: Listener? = null

    private var ad: NativeAd? = null
    private var handle: AdRequestHandle? = null
    private var content: NativeAdView? = null
    private var shimmer: ShimmerView? = null

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.gads_NativeAdTemplateView)
            try {
                if (a.hasValue(R.styleable.gads_NativeAdTemplateView_gads_size)) {
                    size = NativeSize.fromAttr(a.getInt(R.styleable.gads_NativeAdTemplateView_gads_size, 0))
                }
                placement = a.getString(R.styleable.gads_NativeAdTemplateView_gads_placement)
                autoLoad = a.getBoolean(R.styleable.gads_NativeAdTemplateView_gads_autoLoad, true)
                hideOnFail = a.getBoolean(R.styleable.gads_NativeAdTemplateView_gads_hideOnFail, true)
            } finally {
                a.recycle()
            }
        }
        if (isInEditMode) showShimmer()
    }

    /** Starts (or restarts) loading [placement]. */
    fun load(placement: String) {
        this.placement = placement
        startLoad()
    }

    /** Shows an ad you loaded yourself with GamechiefAds.loadNative. This view owns and destroys it. */
    fun bind(nativeAd: NativeAd) {
        cancelLoad()
        releaseAd()
        ad = nativeAd
        visibility = VISIBLE
        bindInternal(nativeAd)
    }

    /** Stops loading and frees the ad now. Also done automatically on detach. */
    fun destroy() {
        cancelLoad()
        releaseAd()
        removeContent()
        hideShimmer()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoLoad && placement != null && ad == null && handle == null) startLoad()
    }

    override fun onDetachedFromWindow() {
        destroy()
        super.onDetachedFromWindow()
    }

    private fun startLoad() {
        val p = placement ?: return
        cancelLoad()
        releaseAd()
        removeContent()
        visibility = VISIBLE
        showShimmer()
        var finished = false
        val h = GamechiefAds.loadNative(context, p, object : NativeLoadCallback {
            override fun onLoaded(ad: NativeAd) {
                finished = true
                handle = null
                this@NativeAdTemplateView.ad = ad
                bindInternal(ad)
                listener?.onLoaded()
            }

            override fun onFailed(reason: String) {
                finished = true
                handle = null
                hideShimmer() // also on failure: never leave a shimmer behind
                if (hideOnFail) visibility = GONE
                listener?.onFailed(reason)
            }
        })
        if (!finished) handle = h
    }

    private fun bindInternal(nativeAd: NativeAd) {
        val view = ensureContent()
        NativeStyler.apply(view, style)
        try {
            NativeAdBinder.bind(view, nativeAd)
        } catch (t: Throwable) {
            releaseAd()
            removeContent()
            hideShimmer()
            if (hideOnFail) visibility = GONE
            listener?.onFailed(t.message ?: "bind failed")
            return
        }
        view.visibility = VISIBLE
        hideShimmer()
    }

    private fun ensureContent(): NativeAdView {
        content?.let { return it }
        val v = LayoutInflater.from(context).inflate(size.layout, this, false) as NativeAdView
        v.visibility = INVISIBLE
        addView(v, 0)
        content = v
        return v
    }

    private fun removeContent() {
        content?.let {
            runCatching { it.destroy() }
            removeView(it)
        }
        content = null
    }

    private fun showShimmer() {
        if (shimmer != null) return
        val h = if (size.placeholderDp == MATCH_PARENT_HEIGHT) ViewGroup.LayoutParams.MATCH_PARENT
        else (size.placeholderDp * resources.displayMetrics.density).toInt()
        val s = ShimmerView(context)
        addView(s, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
        shimmer = s
    }

    private fun hideShimmer() {
        shimmer?.let { removeView(it) }
        shimmer = null
    }

    private fun cancelLoad() {
        handle?.cancel()
        handle = null
    }

    private fun releaseAd() {
        val a = ad
        ad = null
        if (a != null) runCatching { a.destroy() }
    }
}

package com.gcl.gamechiefads.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.gcl.gamechiefads.BannerListener
import com.gcl.gamechiefads.GamechiefAds
import com.gcl.gamechiefads.ui.R

/**
 * Drop-in adaptive banner with a shimmer while loading:
 * `<com.gcl.gamechiefads.ui.BannerAdView app:gads_placement="banner_home" ... />`
 * or `view.load("banner_home")`. Becomes GONE if there is no ad (also for premium users), the shimmer
 * is removed on success AND on failure, and the banner is destroyed when the view leaves the window.
 */
class BannerAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var placement: String? = null
    var autoLoad: Boolean = true
    var hideOnFail: Boolean = true
    var listener: BannerListener? = null

    private var holder: FrameLayout? = null
    private var shimmer: ShimmerView? = null

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.gads_BannerAdView)
            try {
                placement = a.getString(R.styleable.gads_BannerAdView_gads_placement)
                autoLoad = a.getBoolean(R.styleable.gads_BannerAdView_gads_autoLoad, true)
                hideOnFail = a.getBoolean(R.styleable.gads_BannerAdView_gads_hideOnFail, true)
            } finally {
                a.recycle()
            }
        }
        if (isInEditMode) addShimmer()
    }

    fun load(placement: String) {
        this.placement = placement
        startLoad()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (autoLoad && placement != null && holder == null) startLoad()
    }

    override fun onDetachedFromWindow() {
        clear() // the core destroys the banner when its holder leaves the window
        super.onDetachedFromWindow()
    }

    private fun startLoad() {
        val p = placement ?: return
        clear()
        visibility = VISIBLE
        val h = FrameLayout(context)
        holder = h
        addView(h, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addShimmer()
        GamechiefAds.banner(h, p, object : BannerListener {
            override fun onLoaded() {
                removeShimmer()
                listener?.onLoaded()
            }

            override fun onFailed(reason: String) {
                removeShimmer() // also on failure: never leave a shimmer behind
                if (hideOnFail) visibility = GONE
                listener?.onFailed(reason)
            }
        })
    }

    private fun addShimmer() {
        if (shimmer != null) return
        val s = ShimmerView(context)
        addView(s, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (PLACEHOLDER_DP * resources.displayMetrics.density).toInt()))
        shimmer = s
    }

    private fun removeShimmer() {
        shimmer?.let { removeView(it) }
        shimmer = null
    }

    private fun clear() {
        removeShimmer()
        holder?.let { removeView(it) }
        holder = null
    }

    private companion object {
        const val PLACEHOLDER_DP = 90
    }
}

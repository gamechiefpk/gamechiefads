package com.gcl.gamechiefads.ui

import android.view.View
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import com.gcl.gamechiefads.ui.R
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView

/**
 * Fills an inflated template with one [NativeAd] and registers the asset views with the SDK.
 * Missing assets hide their view (no empty gaps); layouts may omit any optional view.
 */
internal object NativeAdBinder {

    fun bind(root: NativeAdView, ad: NativeAd) {
        val headline = root.findViewById<TextView>(R.id.gads_headline)
        val body = root.findViewById<TextView>(R.id.gads_body)
        val cta = root.findViewById<TextView>(R.id.gads_cta)
        val icon = root.findViewById<ImageView>(R.id.gads_icon)
        val advertiser = root.findViewById<TextView>(R.id.gads_advertiser)
        val store = root.findViewById<TextView>(R.id.gads_store)
        val price = root.findViewById<TextView>(R.id.gads_price)
        val rating = root.findViewById<RatingBar>(R.id.gads_rating)
        val media = root.findViewById<MediaView>(R.id.gads_media)

        headline?.let { it.text = ad.headline.orEmpty(); root.headlineView = it }
        show(body, ad.body) { root.bodyView = it }
        show(cta, ad.callToAction) { root.callToActionView = it }
        show(price, ad.price) { root.priceView = it }
        show(store, ad.store) { root.storeView = it }
        // advertiser line falls back to nothing: the store already tells where it lives
        show(advertiser, ad.advertiser) { root.advertiserView = it }

        val drawable = ad.icon?.drawable
        icon?.let {
            if (drawable != null) { it.setImageDrawable(drawable); it.visibility = View.VISIBLE; root.iconView = it }
            else it.visibility = View.GONE
        }

        val stars = ad.starRating
        rating?.let {
            if (stars != null && stars > 0) { it.rating = stars.toFloat(); it.visibility = View.VISIBLE; root.starRatingView = it }
            else it.visibility = View.GONE
        }

        root.registerNativeAd(ad, media)
    }

    private fun show(view: TextView?, value: String?, register: (TextView) -> Unit) {
        view ?: return
        if (value.isNullOrBlank()) {
            view.visibility = View.GONE
        } else {
            view.text = value
            view.visibility = View.VISIBLE
            register(view)
        }
    }
}

package com.gcl.gamechiefads.ui

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.RatingBar
import android.widget.TextView
import com.gcl.gamechiefads.ui.R

/** Paints an inflated template: card, text colours, "Ad" badge, button. Null style fields use the resource colours. */
internal object NativeStyler {

    fun apply(root: View, style: NativeStyle) {
        val ctx = root.context
        val density = ctx.resources.displayMetrics.density
        fun color(override: Int?, res: Int) = override ?: ctx.getColor(res)

        val radius = (style.cornerRadiusDp?.times(density)) ?: ctx.resources.getDimension(R.dimen.gads_corner_radius)
        val strokeW = ((style.strokeWidthDp?.times(density)) ?: ctx.resources.getDimension(R.dimen.gads_stroke_width)).toInt()

        root.findViewById<View>(R.id.gads_card)?.let { card ->
            card.background = GradientDrawable().apply {
                setColor(color(style.background, R.color.gads_card_bg))
                cornerRadius = radius
                if (strokeW > 0) setStroke(strokeW, color(style.stroke, R.color.gads_card_stroke))
            }
            card.clipToOutline = true
            card.outlineProvider = ViewOutlineProvider.BACKGROUND
        }

        val primary = color(style.headline, R.color.gads_text_primary)
        val bodyColor = color(style.body ?: style.secondary, R.color.gads_text_secondary)
        val secondary = color(style.secondary, R.color.gads_text_secondary)
        text(root, R.id.gads_headline, primary)
        text(root, R.id.gads_body, bodyColor)
        text(root, R.id.gads_advertiser, secondary)
        text(root, R.id.gads_store, secondary)
        text(root, R.id.gads_price, primary)

        root.findViewById<TextView>(R.id.gads_badge)?.apply {
            setTextColor(color(style.badgeText, R.color.gads_badge_text))
            background = GradientDrawable().apply {
                setColor(color(style.badgeBackground, R.color.gads_badge_bg))
                cornerRadius = 4 * density
            }
            setPadding((5 * density).toInt(), (1 * density).toInt(), (5 * density).toInt(), (1 * density).toInt())
        }

        root.findViewById<TextView>(R.id.gads_cta)?.apply {
            val bg = color(style.ctaBackground, R.color.gads_cta_bg)
            val shape = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = minOf(radius, 24 * density)
            }
            val ripple = ColorStateList.valueOf((color(style.ctaText, R.color.gads_cta_text) and 0x00FFFFFF) or 0x33000000)
            background = RippleDrawable(ripple, shape, null)
            setTextColor(color(style.ctaText, R.color.gads_cta_text))
        }

        root.findViewById<RatingBar>(R.id.gads_rating)?.apply {
            val star = ColorStateList.valueOf(ctx.getColor(R.color.gads_star))
            progressTintList = star
            secondaryProgressTintList = star
            progressBackgroundTintList = ColorStateList.valueOf(secondary and 0x00FFFFFF or 0x55000000)
        }
    }

    private fun text(root: View, id: Int, color: Int) {
        root.findViewById<TextView>(id)?.setTextColor(color)
    }
}

package com.gcl.gamechiefads.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.gcl.gamechiefads.AdLoaderUi
import com.gcl.gamechiefads.ui.R

/**
 * Default "loading ad" overlay: dimmed screen, small card with a spinner and text.
 * Install with `GamechiefAds.setLoaderUi(new DefaultLoaderDialog())`. For your own design use [LayoutLoaderUi].
 * Never throws: if the dialog cannot be shown the ad flow simply continues without it.
 */
class DefaultLoaderDialog @JvmOverloads constructor(private val message: CharSequence? = null) : AdLoaderUi {
    private var dialog: Dialog? = null

    override fun show(activity: Activity) {
        dismiss()
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            val d = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            d.setCancelable(false)
            d.setContentView(content(activity))
            d.show()
            dialog = d
        } catch (t: Throwable) {
            dialog = null
        }
    }

    override fun dismiss() {
        val d = dialog
        dialog = null
        if (d != null) runCatching { d.dismiss() }
    }

    private fun content(activity: Activity): FrameLayout {
        val dp = activity.resources.displayMetrics.density
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = (24 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(activity.getColor(R.color.gads_card_bg))
                cornerRadius = 16 * dp
            }
            addView(ProgressBar(activity))
            addView(
                TextView(activity).apply {
                    text = message ?: activity.getString(R.string.gads_loading_ad)
                    setTextColor(activity.getColor(R.color.gads_text_primary))
                    textSize = 14f
                    setPadding(0, (12 * dp).toInt(), 0, 0)
                },
            )
        }
        return FrameLayout(activity).apply {
            setBackgroundColor(Color.argb(140, 0, 0, 0))
            addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
    }
}

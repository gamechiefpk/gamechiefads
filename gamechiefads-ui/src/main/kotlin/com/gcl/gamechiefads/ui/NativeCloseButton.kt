package com.gcl.gamechiefads.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

/**
 * The close button of a full-screen native ad, one look everywhere (the full-screen native dialog and the IntroPager ad pages):
 * a dark pill that counts "Close in 3", then becomes a close mark. It sits below the status bar and the camera cutout.
 */
internal object NativeCloseButton {
    fun create(context: Context): TextView {
        val dp = context.resources.displayMetrics.density
        return TextView(context).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            minWidth = (36 * dp).toInt()
            minHeight = (36 * dp).toInt()
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
            background = GradientDrawable().apply { setColor(0xAA000000.toInt()); cornerRadius = 18 * dp }
            isEnabled = false
        }
    }

    /** Puts [close] in the top right corner of [root], below the status bar. */
    fun place(root: FrameLayout, close: TextView) {
        val dp = root.resources.displayMetrics.density
        root.addView(
            close,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                .apply { setMargins(0, (16 * dp).toInt(), (16 * dp).toInt(), 0) },
        )
        root.setOnApplyWindowInsetsListener { _, insets ->
            (close.layoutParams as FrameLayout.LayoutParams).topMargin = (16 * dp).toInt() + insets.systemWindowInsetTop
            close.requestLayout()
            insets
        }
    }

    fun countdown(close: TextView, secondsLeft: Int) {
        close.isEnabled = false
        close.text = close.context.getString(R.string.gads_close_in, secondsLeft)
    }

    fun ready(close: TextView) {
        close.isEnabled = true
        close.text = "✕"
        close.contentDescription = close.context.getString(R.string.gads_close)
    }
}

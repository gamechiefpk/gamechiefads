package com.gcl.gamechiefads.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import com.gcl.gamechiefads.GamechiefAds

/** The debug panel as a bottom sheet over the current screen. Debug builds only (GamechiefAds.showDebugPanel calls this by name). */
object DebugSheet {
    private var current: Dialog? = null

    @JvmStatic
    fun show(activity: Activity) {
        if (!GamechiefAds.isDebugBuild() || activity.isFinishing || activity.isDestroyed) return
        if (current?.isShowing == true) return          // a second shake or call while the panel is open does nothing
        val d = Dialog(activity, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(DebugPanelView(activity), ViewGroup.LayoutParams(-1, (activity.resources.displayMetrics.heightPixels * 0.78).toInt()))
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setWindowAnimations(android.R.style.Animation_InputMethod)
            setDimAmount(0.35f)
        }
        d.setCanceledOnTouchOutside(true)
        d.setOnDismissListener { if (current === d) current = null }
        current = d
        d.show()
    }
}

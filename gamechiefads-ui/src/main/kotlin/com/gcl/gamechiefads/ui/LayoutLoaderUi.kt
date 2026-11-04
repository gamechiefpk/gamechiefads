package com.gcl.gamechiefads.ui

import android.app.Activity
import android.app.Dialog
import androidx.annotation.LayoutRes
import com.gcl.gamechiefads.AdLoaderUi
import com.gcl.gamechiefads.AdLog

/**
 * "Loading ad" overlay from your own XML layout (full screen, translucent dialog).
 * `GamechiefAds.setLoaderUi(new LayoutLoaderUi(R.layout.my_ad_loader))`
 */
class LayoutLoaderUi(@param:LayoutRes private val layout: Int) : AdLoaderUi {
    private var dialog: Dialog? = null

    override fun show(activity: Activity) {
        dismiss()
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            val d = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            d.setCancelable(false)
            d.setContentView(layout)
            d.show()
            dialog = d
            AdLog.d("loader") { "shown on ${activity.javaClass.simpleName}" }
        } catch (t: Throwable) {
            AdLog.d("loader") { "show failed (flow continues): $t" }
            dialog = null
        }
    }

    override fun dismiss() {
        val d = dialog
        dialog = null
        if (d != null) {
            AdLog.d("loader") { "dismissed" }
            runCatching { d.dismiss() }
        }
    }
}

package com.gcl.gamechiefads.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import com.gcl.gamechiefads.AdLoaderUi
import com.gcl.gamechiefads.AdLog
import com.gcl.gamechiefads.LoaderInfo
import com.gcl.gamechiefads.ui.R

/**
 * Default "loading ad" overlay: the screen dims (and blurs on Android 12+), a rounded card scales in with a
 * spinner, a title, a message and optional rotating tips. Colours follow light and dark theme.
 *
 * ```java
 * GamechiefAds.setLoaderUi(DefaultLoaderDialog.builder()
 *     .title("Designing your room")
 *     .message("A short ad keeps this app free")
 *     .tips("Tip: try the night mode", "Tip: swipe to compare")
 *     .accentColor(0xFF6C4CF1).build());
 * ```
 * Texts can also be set per placement (`AdsConfig.Builder.loaderText`, or Remote Config `loaderTitle`/`loaderMessage`);
 * those win over the ones set here. Never throws: if the dialog cannot be shown the ad flow simply continues.
 */
class DefaultLoaderDialog private constructor(private val o: Options) : AdLoaderUi {

    /** Backwards compatible: only a message. */
    @JvmOverloads
    constructor(message: CharSequence? = null) : this(Options(message = message))

    private class Options(
        val title: CharSequence? = null,
        val message: CharSequence? = null,
        val tips: List<CharSequence> = emptyList(),
        @param:ColorInt val accent: Int? = null,
        @param:ColorInt val card: Int? = null,
        @param:ColorInt val text: Int? = null,
        val cornerDp: Float = 28f,
        val dim: Float = 0.6f,
        val blur: Boolean = true,
    )

    class Builder internal constructor() {
        private var title: CharSequence? = null
        private var message: CharSequence? = null
        private var tips: List<CharSequence> = emptyList()
        private var accent: Int? = null
        private var card: Int? = null
        private var text: Int? = null
        private var cornerDp = 28f
        private var dim = 0.6f
        private var blur = true

        fun title(t: CharSequence?) = apply { title = t }
        fun message(m: CharSequence?) = apply { message = m }

        /** Short lines that fade through one after another while the ad loads. */
        fun tips(vararg t: CharSequence) = apply { tips = t.toList() }
        fun accentColor(@ColorInt c: Int) = apply { accent = c }
        fun cardColor(@ColorInt c: Int) = apply { card = c }
        fun textColor(@ColorInt c: Int) = apply { text = c }
        fun cornerRadiusDp(dp: Float) = apply { cornerDp = dp.coerceAtLeast(0f) }
        fun dimAmount(amount: Float) = apply { dim = amount.coerceIn(0f, 1f) }

        /** Blur what is behind the overlay (Android 12+ only, ignored below). */
        fun blurBackground(on: Boolean) = apply { blur = on }
        fun build(): DefaultLoaderDialog = DefaultLoaderDialog(Options(title, message, tips, accent, card, text, cornerDp, dim, blur))
    }

    private var dialog: Dialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tipRunnable: Runnable? = null

    override fun show(activity: Activity) = show(activity, LoaderInfo("", null, null))

    override fun show(activity: Activity, info: LoaderInfo) {
        dismiss()
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            val d = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            d.setCancelable(false)
            d.setContentView(content(activity, info))
            d.window?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(o.dim)
                if (o.blur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    attributes = attributes.also { it.blurBehindRadius = 28 }
                }
            }
            d.show()
            dialog = d
            AdLog.d("loader") { "shown on ${activity.javaClass.simpleName} for '${info.placement}'" }
        } catch (t: Throwable) {
            AdLog.d("loader") { "show failed (flow continues): $t" }
            dialog = null
        }
    }

    override fun dismiss() {
        tipRunnable?.let { handler.removeCallbacks(it) }
        tipRunnable = null
        val d = dialog
        dialog = null
        if (d != null) {
            AdLog.d("loader") { "dismissed" }
            runCatching { d.dismiss() }
        }
    }

    private fun content(activity: Activity, info: LoaderInfo): FrameLayout {
        val dp = activity.resources.displayMetrics.density
        val accent = o.accent ?: activity.getColor(R.color.gads_cta_bg)
        val cardColor = o.card ?: activity.getColor(R.color.gads_card_bg)
        val primary = o.text ?: activity.getColor(R.color.gads_text_primary)
        val secondary = (primary and 0x00FFFFFF) or 0xB3000000.toInt()

        val title = info.title ?: o.title ?: activity.getString(R.string.gads_loader_title)
        val message = info.message ?: o.message ?: activity.getString(R.string.gads_loader_message)

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            minimumWidth = (264 * dp).toInt()
            setPadding((28 * dp).toInt(), (30 * dp).toInt(), (28 * dp).toInt(), (26 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = o.cornerDp * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), (primary and 0x00FFFFFF) or 0x1A000000)
            }
            elevation = 24 * dp
        }
        card.addView(ArcSpinner(activity, accent), LinearLayout.LayoutParams((52 * dp).toInt(), (52 * dp).toInt()))
        card.addView(
            TextView(activity).apply {
                text = title
                setTextColor(primary)
                textSize = 18f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(0, (20 * dp).toInt(), 0, 0)
            },
        )
        card.addView(
            TextView(activity).apply {
                text = message
                setTextColor(secondary)
                textSize = 14f
                gravity = Gravity.CENTER
                maxWidth = (232 * dp).toInt()
                setLineSpacing(0f, 1.1f)
                setPadding(0, (6 * dp).toInt(), 0, 0)
            },
        )
        if (o.tips.isNotEmpty()) card.addView(tipView(activity, dp, accent))

        return FrameLayout(activity).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        }
    }

    /** One line of text that cross-fades through the tips every ~2.4 s. */
    private fun tipView(activity: Activity, dp: Float, accent: Int): TextView {
        val tips = o.tips
        var i = 0
        val v = TextView(activity).apply {
            text = tips[0]
            setTextColor(accent)
            textSize = 12.5f
            gravity = Gravity.CENTER
            maxWidth = (232 * dp).toInt()
            setPadding(0, (16 * dp).toInt(), 0, 0)
        }
        if (tips.size > 1) {
            val r = object : Runnable {
                override fun run() {
                    v.animate().alpha(0f).setDuration(180).withEndAction {
                        i = (i + 1) % tips.size
                        v.text = tips[i]
                        v.animate().alpha(1f).setDuration(180).start()
                    }.start()
                    handler.postDelayed(this, 2400)
                }
            }
            tipRunnable = r
            handler.postDelayed(r, 2400)
        }
        return v
    }

    companion object {
        @JvmStatic
        fun builder() = Builder()
    }
}

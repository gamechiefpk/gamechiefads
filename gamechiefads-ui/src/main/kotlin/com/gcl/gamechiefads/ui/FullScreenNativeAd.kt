package com.gcl.gamechiefads.ui

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.gcl.gamechiefads.AdLoaderUi
import com.gcl.gamechiefads.AdLog
import com.gcl.gamechiefads.AdPlacement
import com.gcl.gamechiefads.FullScreenSlot
import com.gcl.gamechiefads.GamechiefAds
import com.gcl.gamechiefads.InterstitialListener
import com.gcl.gamechiefads.NativeLoadCallback
import com.gcl.gamechiefads.ui.R
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd

/**
 * A native ad shown like an interstitial: it covers the whole screen, has a close button that appears after a
 * short countdown, and takes the library's single full-screen ad slot (no other ad can start meanwhile, premium
 * users never see it, and it resets the gap and screen counter like any other ad).
 *
 * ```java
 * GamechiefAds.showInterstitial ... // same listener type:
 * FullScreenNativeAd.show(this, AdIds.EXIT_NATIVE, new InterstitialListener() {
 *     @Override public void onComplete(boolean wasShown) { finish(); }
 * });
 * ```
 * The placement must be a `nativeAd(...)` placement. Fails open: every problem ends in `onNotShown` + `onComplete(false)`.
 */
object FullScreenNativeAd {

    class Options @JvmOverloads constructor(
        /** Seconds before the close button works. 0 = immediately. Back is blocked until then too. */
        val closeDelaySeconds: Int = 3,
        val style: NativeStyle = NativeStyle.DEFAULT,
        /** Optional overlay while the native ad loads, e.g. DefaultLoaderDialog.builder().build(). */
        val loader: AdLoaderUi? = null,
    )

    @JvmStatic
    @JvmOverloads
    fun show(activity: Activity, placement: AdPlacement, listener: InterstitialListener? = null, options: Options = Options()) {
        val events = Once(listener)
        val slot = GamechiefAds.openFullScreenSlot(activity, placement)
        if (slot == null) {
            events.notShown("not allowed now (premium, disabled or another ad is showing)")
            return
        }
        val loader = options.loader
        runCatching { loader?.show(activity) }
        GamechiefAds.loadNative(
            activity, placement,
            object : NativeLoadCallback {
                override fun onLoaded(ad: NativeAd) {
                    runCatching { loader?.dismiss() }
                    present(activity, ad, slot, events, options)
                }

                override fun onFailed(reason: String) {
                    runCatching { loader?.dismiss() }
                    slot.release()
                    events.notShown(reason)
                }
            },
        )
    }

    private fun present(activity: Activity, ad: NativeAd, slot: FullScreenSlot, events: Once, o: Options) {
        if (activity.isFinishing || activity.isDestroyed) {
            runCatching { ad.destroy() }
            slot.release()
            events.notShown("screen gone")
            return
        }
        try {
            val dp = activity.resources.displayMetrics.density
            val handler = Handler(Looper.getMainLooper())
            val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            dialog.setCancelable(false)

            val nativeView = NativeAdTemplateView(activity).apply {
                size = NativeSize.FULL
                style = o.style
                hideOnFail = false
                autoLoad = false
                bind(ad) // the view now owns the ad and destroys it with the dialog
            }
            val close = TextView(activity).apply {
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                minWidth = (36 * dp).toInt()
                minHeight = (36 * dp).toInt()
                setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), 0)
                background = GradientDrawable().apply {
                    setColor(0xAA000000.toInt())
                    cornerRadius = 18 * dp
                }
                isEnabled = false
            }
            val root = FrameLayout(activity).apply {
                setBackgroundColor(Color.BLACK)
                addView(nativeView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                addView(
                    close,
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                        .apply { setMargins(0, (16 * dp).toInt(), (16 * dp).toInt(), 0) },
                )
                // keep the close button below the status bar / cutout
                setOnApplyWindowInsetsListener { _, insets ->
                    (close.layoutParams as FrameLayout.LayoutParams).topMargin = (16 * dp).toInt() + insets.systemWindowInsetTop
                    close.requestLayout()
                    insets
                }
            }
            dialog.setContentView(root)

            var closable = false
            var left = o.closeDelaySeconds.coerceAtLeast(0)
            val tick = object : Runnable {
                override fun run() {
                    if (left <= 0) {
                        closable = true
                        close.isEnabled = true
                        close.text = "✕" // ✕
                        close.contentDescription = activity.getString(R.string.gads_close)
                    } else {
                        close.text = activity.getString(R.string.gads_close_in, left--)
                        handler.postDelayed(this, 1000)
                    }
                }
            }
            val lifecycle = object : Application.ActivityLifecycleCallbacks {
                override fun onActivityDestroyed(a: Activity) {
                    if (a === activity) runCatching { dialog.dismiss() }
                }

                override fun onActivityCreated(a: Activity, b: Bundle?) {}
                override fun onActivityStarted(a: Activity) {}
                override fun onActivityResumed(a: Activity) {}
                override fun onActivityPaused(a: Activity) {}
                override fun onActivityStopped(a: Activity) {}
                override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            }
            activity.application.registerActivityLifecycleCallbacks(lifecycle)

            close.setOnClickListener { if (closable) dialog.dismiss() }
            dialog.setOnKeyListener { _, keyCode, e ->
                if (keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP && closable) dialog.dismiss()
                keyCode == KeyEvent.KEYCODE_BACK // always swallow: back never skips the countdown
            }
            dialog.setOnDismissListener {
                handler.removeCallbacks(tick)
                activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
                runCatching { nativeView.destroy() }
                slot.release()
                AdLog.d("native-fs") { "closed" }
                events.closed()
            }

            dialog.show()
            slot.markShown()
            events.shown()
            AdLog.d("native-fs") { "shown full screen, close after ${o.closeDelaySeconds}s" }
            tick.run()
        } catch (t: Throwable) {
            AdLog.d("native-fs") { "present failed: $t" }
            runCatching { ad.destroy() }
            slot.release()
            events.notShown(t.message ?: "error")
        }
    }

    /** Null-safe, once-only bridge to the optional listener. */
    private class Once(private val l: InterstitialListener?) {
        private var finished = false
        private var wasShown = false

        fun shown() {
            wasShown = true
            l?.onShown()
        }

        fun closed() = finish { l?.onClosed() }
        fun notShown(reason: String) = finish { l?.onNotShown(reason) }

        private fun finish(report: () -> Unit) {
            if (finished) return
            finished = true
            report()
            l?.onComplete(wasShown)
        }
    }
}

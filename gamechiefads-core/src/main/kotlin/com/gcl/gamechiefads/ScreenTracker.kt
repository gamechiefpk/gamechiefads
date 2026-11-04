package com.gcl.gamechiefads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import java.lang.ref.WeakReference

/**
 * ActivityLifecycleCallbacks: the visible screen (weak), the screen counter, resumed events.
 * Ignores the SDK's own ad activities and rotation/config-change recreation, and counts a screen
 * only when a different Activity instance comes to the front (returning from an ad is not a screen).
 */
internal class ScreenTracker(private val state: AdState) : Application.ActivityLifecycleCallbacks, ScreenHost {
    private var currentRef: WeakReference<Activity>? = null
    private var lastCounted: WeakReference<Activity>? = null
    private var configChange = false

    /** Called after a new screen was counted. */
    var onScreen: ((Activity) -> Unit)? = null

    override val current: Activity? get() = currentRef?.get()

    override fun isUsable(activity: Activity): Boolean =
        activity === current && !activity.isFinishing && !activity.isDestroyed

    override fun onActivityResumed(activity: Activity) {
        if (isAdActivity(activity)) return
        currentRef = WeakReference(activity)
        if (configChange) {
            configChange = false
            lastCounted = WeakReference(activity)
            return
        }
        if (lastCounted?.get() === activity) return
        lastCounted = WeakReference(activity)
        state.onScreenResumed()
        AdLog.d("screen") { "new screen ${activity.javaClass.simpleName}, count since last ad=${state.screensSinceAd.value}" }
        onScreen?.invoke(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (!isAdActivity(activity) && activity.isChangingConfigurations) configChange = true
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (currentRef?.get() === activity) currentRef = null
        if (lastCounted?.get() === activity) lastCounted = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

    private fun isAdActivity(activity: Activity): Boolean {
        val n = activity.javaClass.name
        return n.startsWith("com.google.android.gms.ads.") || n.startsWith("com.google.android.libraries.ads.")
    }
}

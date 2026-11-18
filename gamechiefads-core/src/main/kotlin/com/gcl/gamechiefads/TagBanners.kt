package com.gcl.gamechiefads

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Banners without any code in the screen: put a tag on a FrameLayout in the layout XML and the library fills it when the
 * screen opens (same placements, floors, shimmer, premium hiding and cleanup as the load methods).
 *
 *     <FrameLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:tag="gads_banner" />
 *
 * Tags: gads_banner, gads_banner_fast (no floors), gads_exit_banner, gads_exit_banner_fast, gads_splash_banner.
 * Each screen instance is scanned once, so coming back to it never causes a new request. Fragments or views added later
 * are not found: call the load method for those.
 */
internal class TagBanners(
    private val banner: BannerController,
    private val config: () -> AdsConfig,
    /** Loads a native ad into a container (the ui module, found by name). Null = no ui module: native tags are ignored. */
    private val native: ((FrameLayout, String, String?) -> Unit)? = null,
) {
    private val done = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Activity, Boolean>())

    /** A screen came to the front: fill its tagged containers (once per screen instance). */
    fun onScreen(activity: Activity) {
        if (!config().tagBanners || !done.add(activity)) return
        // after layout, so the container has its real width (adaptive banners size from it)
        activity.window?.decorView?.post { scan(activity) }
    }

    private fun scan(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val root = activity.window?.decorView ?: return
        var found = 0
        fun walk(v: View, depth: Int) {
            if (depth > MAX_DEPTH || found >= MAX_FOUND) return
            if (v is FrameLayout) {
                val nat = parseNative(v.tag?.toString())
                if (nat != null) {
                    found++
                    AdLog.d("native") { "tag '${v.tag}' on ${activity.javaClass.simpleName}: loading ${nat.placement}" }
                    native?.invoke(v, nat.placement, nat.size)
                    return
                }
                val spec = parse(v.tag?.toString())
                if (spec != null) {
                    found++
                    AdLog.d("banner") { "tag '${v.tag}' on ${activity.javaClass.simpleName}: loading ${spec.placement}" }
                    banner.attach(v, spec.placement, null, spec.forceType, spec.flooring)
                    return
                }
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i), depth + 1)
        }
        runCatching { walk(root, 0) }
    }

    /** A native tag: placement name and optional size name. */
    internal data class NativeSpec(val placement: String, val size: String?)

    /** What a tag asks for. */
    internal data class Spec(val placement: String, val flooring: Boolean, val forceType: BannerType? = null)

    internal companion object {
        const val MAX_DEPTH = 40
        const val MAX_FOUND = 8

        /** Pure: "gads_native:<placement>[:<size>]" (size = tiny, small, small_media, medium, large, full) or null. */
        fun parseNative(tag: String?): NativeSpec? {
            val parts = tag?.trim()?.split(':') ?: return null
            if (parts.size !in 2..3 || !parts[0].trim().equals("gads_native", ignoreCase = true)) return null
            val name = parts[1].trim().takeIf { it.isNotEmpty() } ?: return null
            return NativeSpec(name, parts.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() })
        }

        /** Pure: a tag string to a request, or null when it is not one of ours. */
        fun parse(tag: String?): Spec? = when (tag?.trim()?.lowercase()) {
            "gads_banner" -> Spec(Placements.BANNER, true)
            "gads_banner_fast" -> Spec(Placements.BANNER, false)
            "gads_exit_banner" -> Spec(Placements.EXIT_BANNER, true)
            "gads_exit_banner_fast" -> Spec(Placements.EXIT_BANNER, false)
            "gads_splash_banner" -> Spec(Placements.SPLASH_BANNER, false, BannerType.LARGE_BANNER)
            else -> null
        }
    }
}

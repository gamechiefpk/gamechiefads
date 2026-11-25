package com.gcl.gamechiefads

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Debug-only trace log. Logcat tag "GamechiefAds", every line starts with an area: `[interstitial] ...`.
 * Switched on only for debuggable apps (see AdsRuntime) and silent in release. The message is a lambda,
 * so nothing is even built while logging is off. Filter with: `adb logcat -s GamechiefAds`.
 * The last [MAX_LINES] lines are also kept in memory for the debug panel (GamechiefAds.showDebugPanel); nothing is kept in release builds.
 * Public only because the ui module calls it; it is not part of the supported API.
 */
object AdLog {
    const val TAG = "GamechiefAds"
    const val MAX_LINES = 400

    @Volatile @JvmField var enabled = false

    private val lines = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Called (on whatever thread logged) with every new line while the debug panel is open. */
    @Volatile @JvmField var listener: ((String) -> Unit)? = null

    @Suppress("NOTHING_TO_INLINE")
    inline fun d(area: String, message: () -> String) {
        if (enabled) write(area, message())
    }

    @PublishedApi
    internal fun write(area: String, message: String) {
        try {
            Log.d(TAG, "[$area] $message")
            keep("[$area] $message")
        } catch (t: Throwable) { /* never let logging break an ad flow */ }
    }

    /** A line for the panel only (setup messages and the like), also in logcat. */
    fun note(message: String) { if (enabled) write("panel", message) }

    private fun keep(line: String) {
        val stamped = synchronized(time) { time.format(Date()) } + " " + line
        synchronized(lines) {
            if (lines.size >= MAX_LINES) lines.removeFirst()
            lines.addLast(stamped)
        }
        listener?.invoke(stamped)
    }

    /** The kept lines, oldest first. Empty in release builds. */
    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    fun clear() = synchronized(lines) { lines.clear() }
}

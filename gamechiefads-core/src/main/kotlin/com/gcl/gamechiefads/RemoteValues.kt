package com.gcl.gamechiefads

import java.util.concurrent.ConcurrentHashMap

/**
 * Remote values for the app, anywhere: `getString/getBoolean/getLong/getDouble(key, fallback)`.
 * Order: the server value (or the source's default) first, then the defaults you added in code, then your fallback.
 * Works without any source too (everything then comes from your defaults and fallbacks). Never throws.
 */
internal class RemoteValues(private val source: RemoteConfigSource?) {
    private val defaults = ConcurrentHashMap<String, Any>()
    @Volatile private var changed: Runnable? = null

    val available: Boolean get() = source != null

    /** Adds or replaces default values (also at runtime) and tells the source. */
    fun addDefaults(values: Map<String, Any>) {
        val clean = values.filterValues { it is String || it is Boolean || it is Number }
        if (clean.isEmpty()) return
        defaults.putAll(clean)
        safe { source?.setDefaults(clean) }
    }

    fun setListener(listener: Runnable?) {
        changed = listener
    }

    /**
     * Starts the source: defaults, fetch interval, a first fetch, and real-time updates.
     * [onActivated] runs after every successful fetch or update (the runtime re-applies the ad config JSON there).
     */
    fun start(intervalSeconds: Long, onActivated: () -> Unit) {
        val s = source ?: return
        safe { s.setMinFetchIntervalSeconds(intervalSeconds) }
        safe { if (defaults.isNotEmpty()) s.setDefaults(defaults.toMap()) }
        safe {
            s.onUpdated(Runnable {
                AdLog.d("remote") { "values updated" }
                runCatching(onActivated)
                notifyChanged()
            })
        }
        fetch { ok -> if (ok) runCatching(onActivated) }
    }

    fun fetch(done: (Boolean) -> Unit) {
        val s = source
        if (s == null) {
            done(false)
            return
        }
        try {
            s.fetchAndActivate(RemoteFetchListener { ok ->
                AdLog.d("remote") { "fetch ${if (ok) "ok" else "failed"}" }
                runCatching { done(ok) }
                if (ok) notifyChanged()
            })
        } catch (t: Throwable) {
            AdLog.d("remote") { "fetch error: $t" }
            done(false)
        }
    }

    fun string(key: String, fallback: String): String =
        remote(key) { it.getString(key) } ?: defaults[key]?.toString() ?: fallback

    fun boolean(key: String, fallback: Boolean): Boolean =
        remote(key) { it.getBoolean(key) } ?: defaults[key]?.let(::asBoolean) ?: fallback

    fun long(key: String, fallback: Long): Long =
        remote(key) { it.getLong(key) } ?: defaults[key]?.let(::asLong) ?: fallback

    fun double(key: String, fallback: Double): Double =
        remote(key) { it.getDouble(key) } ?: defaults[key]?.let(::asDouble) ?: fallback

    private fun <T> remote(key: String, read: (RemoteConfigSource) -> T): T? {
        val s = source ?: return null
        return try {
            if (s.has(key)) read(s) else null
        } catch (t: Throwable) {
            null
        }
    }

    private fun notifyChanged() {
        runCatching { changed?.run() }
    }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (t: Throwable) { AdLog.d("remote") { "source error ignored: $t" } }
    }

    private fun asBoolean(v: Any): Boolean = when (v) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        else -> v.toString().trim().equals("true", ignoreCase = true)
    }

    private fun asLong(v: Any): Long? = when (v) {
        is Number -> v.toLong()
        else -> v.toString().trim().toLongOrNull()
    }

    private fun asDouble(v: Any): Double? = when (v) {
        is Number -> v.toDouble()
        else -> v.toString().trim().toDoubleOrNull()
    }
}

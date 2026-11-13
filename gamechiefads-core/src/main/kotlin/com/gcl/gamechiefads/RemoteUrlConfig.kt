package com.gcl.gamechiefads

import org.json.JSONObject

/**
 * One shared ads-rules JSON file on an https address (for example a file in your GitHub repo or on your site), read by
 * every app that points at it. Change the file and all those apps pick it up on their next fetch: no app update, no
 * library update, no Firebase console per app. The last good copy is kept on the device, so it works offline.
 *
 * Fail open: a wrong address, no network, a bad status or bad JSON changes nothing. Only https is used.
 * Blocking: [refresh] must run off the main thread.
 */
internal class RemoteUrlConfig(
    private val url: String,
    private val intervalSeconds: Long,
    private val clock: () -> Long,
    private val store: Store,
    private val http: Http,
) {
    interface Store {
        fun get(key: String): String?
        fun put(key: String, value: String?)
    }

    fun interface Http {
        fun get(url: String, etag: String?): Response
    }

    class Response(val code: Int, val body: String?, val etag: String?)

    /** The last good JSON from a previous run (instant, no network), or null. */
    fun cached(): String? = store.get(K_JSON)?.takeIf(::valid)

    /**
     * Fetches when due (at most every [intervalSeconds]) or when forced. Returns the new JSON when it changed, else null.
     */
    fun refresh(force: Boolean = false): String? {
        if (!url.startsWith("https://")) {
            AdLog.d("remote") { "config url ignored (only https is used)" }
            return null
        }
        val now = clock()
        val last = store.get(K_AT)?.toLongOrNull() // null = never fetched: due
        if (!force && last != null && now - last in 0 until intervalSeconds * 1000) return null
        return try {
            val r = http.get(url, store.get(K_ETAG))
            val body = r.body
            when {
                r.code == 304 -> { store.put(K_AT, now.toString()); null }
                r.code == 200 && body != null && valid(body) -> {
                    val changed = body != store.get(K_JSON)
                    store.put(K_JSON, body); store.put(K_ETAG, r.etag); store.put(K_AT, now.toString())
                    AdLog.d("remote") { "config url: ${if (changed) "new rules" else "unchanged"} (${body.length} chars)" }
                    if (changed) body else null
                }
                else -> { AdLog.d("remote") { "config url: status ${r.code}, kept the last good rules" }; null }
            }
        } catch (t: Throwable) {
            AdLog.d("remote") { "config url failed, kept the last good rules: $t" }
            null
        }
    }

    private fun valid(json: String): Boolean =
        json.length <= MAX_CHARS && runCatching { JSONObject(json) }.isSuccess

    private companion object {
        const val K_JSON = "json"
        const val K_ETAG = "etag"
        const val K_AT = "fetched_at"
        const val MAX_CHARS = 128 * 1024
    }
}

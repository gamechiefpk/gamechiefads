package com.gcl.gamechiefads

/**
 * Where remote values come from. The core module has no Firebase code: the `gamechiefads-remoteconfig` module implements
 * this with Firebase Remote Config and the core finds it by name. You can also implement it yourself (another backend, a fake in tests).
 * Callbacks must come on the main thread.
 */
interface RemoteConfigSource {
    /** Values used until the server answers (and for keys the server does not have). */
    fun setDefaults(defaults: Map<String, Any>)

    fun setMinFetchIntervalSeconds(seconds: Long)

    /** Fetch from the server and make the new values the current ones. [callback] gets true on success (call it on the main thread). */
    fun fetchAndActivate(callback: RemoteFetchListener)

    /** True if the key has a value from the server or from the defaults. */
    fun has(key: String): Boolean

    fun getString(key: String): String
    fun getBoolean(key: String): Boolean
    fun getLong(key: String): Long
    fun getDouble(key: String): Double

    /** Called when new values were activated without a fetch call (real-time updates). Optional. */
    fun onUpdated(listener: Runnable)
}

/** Result of [GamechiefAds.fetchRemoteConfig]. */
fun interface RemoteFetchListener {
    fun onResult(success: Boolean)
}

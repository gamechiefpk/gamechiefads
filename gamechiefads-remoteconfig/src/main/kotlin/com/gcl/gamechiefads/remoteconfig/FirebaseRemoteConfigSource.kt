package com.gcl.gamechiefads.remoteconfig

import com.gcl.gamechiefads.RemoteConfigSource
import com.gcl.gamechiefads.RemoteFetchListener
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Firebase Remote Config behind the core's [RemoteConfigSource]. The core module creates this class by name, so the app
 * writes no Firebase Remote Config code. If Firebase is not set up in the app (no google-services.json) the constructor
 * throws and the core simply runs without remote values (defaults and fallbacks).
 */
class FirebaseRemoteConfigSource : RemoteConfigSource {
    private val rc: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    override fun setDefaults(defaults: Map<String, Any>) {
        rc.setDefaultsAsync(defaults)
    }

    override fun setMinFetchIntervalSeconds(seconds: Long) {
        rc.setConfigSettingsAsync(FirebaseRemoteConfigSettings.Builder().setMinimumFetchIntervalInSeconds(seconds).build())
    }

    override fun fetchAndActivate(callback: RemoteFetchListener) {
        rc.fetchAndActivate().addOnCompleteListener { task -> callback.onResult(task.isSuccessful) }
    }

    /** A value that is neither from the server nor from the defaults is the SDK's "static" placeholder. */
    override fun has(key: String): Boolean = rc.getValue(key).source != FirebaseRemoteConfig.VALUE_SOURCE_STATIC

    override fun getString(key: String): String = rc.getString(key)
    override fun getBoolean(key: String): Boolean = rc.getBoolean(key)
    override fun getLong(key: String): Long = rc.getLong(key)
    override fun getDouble(key: String): Double = rc.getDouble(key)

    override fun onUpdated(listener: Runnable) {
        rc.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                rc.activate().addOnCompleteListener { listener.run() }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                // real-time updates are a bonus; the normal fetch still works
            }
        })
    }
}

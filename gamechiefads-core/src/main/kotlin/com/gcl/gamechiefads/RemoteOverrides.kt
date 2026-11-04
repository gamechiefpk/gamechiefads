package com.gcl.gamechiefads

import org.json.JSONArray
import org.json.JSONObject

/**
 * Applies an optional JSON string (Firebase Remote Config value) over a base [AdsConfig]:
 * {"enabled":true,"placements":{"random":{"units":["a","b"],"every":3,"gap":30,"enabled":true,"mode":"preload","tierTimeoutMs":3000}}}
 * Fail open: null/blank/invalid JSON or bad single values leave the base config untouched.
 * Only existing placements are changed; a remote string can never add a new placement.
 */
internal object RemoteOverrides {

    fun apply(base: AdsConfig, json: String?): AdsConfig {
        if (json.isNullOrBlank()) return base
        val root = try { JSONObject(json) } catch (e: Exception) { return base }
        val allEnabled = if (root.opt("enabled") is Boolean) root.getBoolean("enabled") else true
        val remote = root.optJSONObject("placements")
        val merged = base.placements.mapValues { (name, p) ->
            val patched = remote?.optJSONObject(name)?.let { patch(p, it) } ?: p
            if (allEnabled) patched else patched.copy(enabled = false)
        }
        return base.copy(placements = merged)
    }

    private fun patch(p: PlacementConfig, o: JSONObject): PlacementConfig {
        var r = p
        stringList(o.optJSONArray("units"))?.let { if (it.isNotEmpty()) r = r.copy(units = it) }
        nonNegativeInt(o, "every")?.let { r = r.copy(everyNScreens = it) }
        nonNegativeInt(o, "gap")?.let { r = r.copy(minGapSeconds = it) }
        if (o.opt("enabled") is Boolean) r = r.copy(enabled = o.getBoolean("enabled"))
        parseMode(o.optString("mode", ""))?.let { r = r.copy(mode = it) }
        val tier = (o.opt("tierTimeoutMs") as? Number)?.toLong() ?: 0L
        if (tier > 0) r = r.copy(tierTimeoutMs = tier)
        return r
    }

    private fun stringList(a: JSONArray?): List<String>? {
        a ?: return null
        return (0 until a.length()).mapNotNull { a.optString(it, "").takeIf(String::isNotBlank) }
    }

    private fun nonNegativeInt(o: JSONObject, key: String): Int? {
        val v = o.opt(key) as? Number ?: return null
        return v.toInt().takeIf { it >= 0 }
    }

    private fun parseMode(s: String): AdMode? = when (s.lowercase()) {
        "preload" -> AdMode.PRELOAD
        "on_demand", "ondemand", "on-demand" -> AdMode.ON_DEMAND
        else -> null
    }
}

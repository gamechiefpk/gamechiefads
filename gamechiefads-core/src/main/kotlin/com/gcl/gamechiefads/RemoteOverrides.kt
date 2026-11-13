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
        val title = if (root.has("loaderTitle")) root.optString("loaderTitle").takeIf { it.isNotBlank() } else base.loaderTitle
        val message = if (root.has("loaderMessage")) root.optString("loaderMessage").takeIf { it.isNotBlank() } else base.loaderMessage
        return base.copy(placements = merged, loaderTitle = title, loaderMessage = message, enabled = allEnabled)
    }

    private fun patch(p: PlacementConfig, o: JSONObject): PlacementConfig {
        var r = p
        stringList(o.optJSONArray("units"))?.let { if (it.isNotEmpty()) r = r.copy(units = it) }
        o.optString("secondUnit", "").takeIf { it.isNotBlank() }?.let { r = r.copy(secondUnit = it) }
        (o.opt("everyNCalls") as? Number)?.toInt()?.takeIf { it >= 1 }?.let { r = r.copy(everyNCalls = it) }
        nonNegativeInt(o, "every")?.let { r = r.copy(everyNScreens = it) }
        nonNegativeInt(o, "gap")?.let { r = r.copy(minGapSeconds = it) }
        if (o.opt("enabled") is Boolean) r = r.copy(enabled = o.getBoolean("enabled"))
        parseMode(o.optString("mode", ""))?.let { r = r.copy(mode = it) }
        val tier = (o.opt("tierTimeoutMs") as? Number)?.toLong() ?: 0L
        if (tier > 0) r = r.copy(tierTimeoutMs = tier)
        if (o.opt("double") is Boolean) r = r.copy(doubleAd = o.getBoolean("double"))
        if (o.opt("premiumGetsReward") is Boolean) r = r.copy(premiumGetsReward = o.getBoolean("premiumGetsReward"))
        parseEnum<BannerType>(o.optString("bannerType", ""))?.let { r = r.copy(bannerType = it) }
        if (o.opt("flooring") is Boolean) r = r.copy(flooring = o.getBoolean("flooring"))
        parseEnum<WaterfallMode>(o.optString("waterfall", ""))?.let { r = r.copy(waterfall = it) }
        o.optJSONObject("tierTimeouts")?.let { t ->
            val m = t.keys().asSequence().mapNotNull { k -> (t.opt(k) as? Number)?.toLong()?.takeIf { it > 0 }?.let { k to it } }.toMap()
            r = r.copy(unitTimeouts = m)
        }
        o.optJSONArray("disabledUnits")?.let { r = r.copy(disabledUnits = stringList(it).orEmpty().toSet()) }
        nonNegativeInt(o, "skipAfterFails")?.let { r = r.copy(skipAfterFails = it) }
        nonNegativeInt(o, "skipForSec")?.takeIf { it > 0 }?.let { r = r.copy(skipForSeconds = it) }
        if (o.has("loaderTitle")) r = r.copy(loaderTitle = o.optString("loaderTitle").takeIf { it.isNotBlank() })
        if (o.has("loaderMessage")) r = r.copy(loaderMessage = o.optString("loaderMessage").takeIf { it.isNotBlank() })
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

    private inline fun <reified E : Enum<E>> parseEnum(s: String): E? =
        enumValues<E>().firstOrNull { it.name.equals(s.trim().replace('-', '_'), ignoreCase = true) }

    private fun parseMode(s: String): AdMode? = when (s.lowercase()) {
        "preload" -> AdMode.PRELOAD
        "on_demand", "ondemand", "on-demand" -> AdMode.ON_DEMAND
        else -> null
    }
}

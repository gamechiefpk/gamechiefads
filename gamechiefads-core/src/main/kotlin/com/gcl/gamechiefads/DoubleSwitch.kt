package com.gcl.gamechiefads

/**
 * Single or double? One place for the rule, used by the facade and the automatic random interstitial.
 * Remote Config (the owner) is the master in both directions; the code's ask is the fallback; no declared second unit = single, quietly.
 */
internal object DoubleSwitch {
    fun resolve(slot: PlacementConfig?, requestedByCode: Boolean?): Boolean = slot?.wantsDouble(requestedByCode) ?: false
}

package com.gcl.gamechiefads

/**
 * Single or double? The owner controls it from Remote Config: when the slot's switch is on (`"double": true`), the double ad is shown
 * whatever the app's code asked for. The code can also ask for it (`loadSplashAppOpenAd(activity, true, callback)`). Both off = single.
 */
internal object DoubleSwitch {
    fun resolve(requestedByCode: Boolean?, slotSwitch: Boolean): Boolean = slotSwitch || requestedByCode == true
}

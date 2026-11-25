package com.gcl.gamechiefads

/**
 * The setup checker: finds the usual integration mistakes and says what to do, in plain words. Pure logic (the facts about the app come in
 * as [Env]), so it is unit-tested. It runs by itself in debug builds (messages in logcat, tag "GamechiefAds") and
 * `GamechiefAds.checkSetup()` returns the same list at any time. Release builds never log it by themselves.
 */
internal object SetupChecker {
    /** What the app looks like from the outside. */
    class Env(
        val debuggable: Boolean,
        val hasAdMobAppIdInManifest: Boolean,
        val legacyPlayServicesAdsPresent: Boolean,
        val uiModulePresent: Boolean,
        val firebaseModulePresent: Boolean,
    )

    private val UNIT_ID = Regex("""ca-app-pub-\d{16}/\d{10}""")
    private const val GOOGLE_TEST_PREFIX = "ca-app-pub-3940256099942544/"

    /** Messages start with [ERROR] (will not work), [WARN] (works but is probably a mistake) or [INFO]. Empty = nothing to report. */
    fun check(config: AdsConfig, env: Env): List<String> {
        val out = ArrayList<String>()
        if (!env.hasAdMobAppIdInManifest) out += "[ERROR] AndroidManifest.xml has no <meta-data android:name=\"com.google.android.gms.ads.APPLICATION_ID\" ...> inside <application>. Add your AdMob app id there (ca-app-pub-XXXX~YYYY), the consent form needs it."
        if (env.legacyPlayServicesAdsPresent) out += "[ERROR] The old play-services-ads library is in the app next to the Next-Gen SDK. Exclude com.google.android.gms:play-services-ads and play-services-ads-lite in build.gradle (see the README)."
        if (config.placements.isEmpty()) out += "[WARN] No ad units are configured. Give them in AdsConfig.builder(), e.g. .bannerUnits(...), .splashAppOpenUnits(...)."
        if (config.appId.isNullOrBlank() && !env.debuggable) out += "[WARN] .appId(...) is not set: Google's TEST app id is used, ads will not earn. Set your AdMob app id in the config."
        if (config.appId != null && !Regex("""ca-app-pub-\d{16}~\d{10}""").matches(config.appId)) out += "[WARN] .appId(\"${config.appId}\") does not look like an AdMob app id (ca-app-pub-XXXXXXXXXXXXXXXX~XXXXXXXXXX)."

        for ((name, p) in config.placements) {
            if (p.units.isEmpty()) { out += "[WARN] Slot \"$name\" has no ad unit ids: nothing will load there."; continue }
            for (u in p.units + listOfNotNull(p.secondUnit)) {
                when {
                    u.startsWith(GOOGLE_TEST_PREFIX) && !env.debuggable -> out += "[ERROR] Slot \"$name\" uses a Google TEST unit id ($u) in a release build: no revenue. Use your own unit id."
                    !u.startsWith(GOOGLE_TEST_PREFIX) && !UNIT_ID.matches(u) -> out += "[WARN] Slot \"$name\": \"$u\" does not look like an AdMob unit id (ca-app-pub-XXXXXXXXXXXXXXXX/XXXXXXXXXX)."
                }
            }
            if (p.units.size != p.units.toSet().size) out += "[INFO] Slot \"$name\" lists the same unit id twice: floors only help with different ids."
            if (p.doubleAd && !p.hasSecondUnit) out += "[INFO] Slot \"$name\" asks for a double ad but declares no second unit (use the ...DoubleUnit method): it shows a single ad."
        }
        if (config.remoteConfigUrl != null && !config.remoteConfigUrl.startsWith("https://")) out += "[WARN] .remoteConfigUrl must be an https address, the file is ignored otherwise."
        if (!env.uiModulePresent) out += "[WARN] The gamechiefads-ui module is not in the app: no loading dialog, banner shimmer or native ads."
        if (config.remoteConfigKey != null && !env.firebaseModulePresent && config.remoteSource == null && config.remoteConfigUrl == null)
            out += "[INFO] No remote source: Firebase Remote Config module is missing, so the ads rules come from your code only."
        return out
    }
}

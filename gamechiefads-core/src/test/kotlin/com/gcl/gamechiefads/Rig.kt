package com.gcl.gamechiefads

import kotlinx.coroutines.CoroutineScope

/** Wires the real controllers to fakes: no SDK, no Android, virtual time via the given scope. */
internal class Rig(scope: CoroutineScope, var config: AdsConfig, var gateReady: Boolean = true) {
    var now = 1_000_000L
    val state = AdState()
    val activity = TestActivity()
    val screens = FakeScreens(activity)
    var loaderUi: AdLoaderUi? = null

    val loads = mutableListOf<String>()
    var loadBehavior: suspend (String) -> FullScreenAd? = { FakeAd(it) }
    private val source: suspend (String) -> FullScreenAd? = { unit -> loads += unit; loadBehavior(unit) }

    val pool = AdPool(clock = { now })
    val appOpenPool = AdPool(clock = { now })
    private val pipeline = AdLoadPipeline({ config }, { gateReady }, debug = false)
    private val shower = FullScreenShower(state, { now }, scope)

    val interstitial = InterstitialController(
        state, { config }, pool, pipeline, shower, screens, scope, { now }, { loaderUi }, source,
    )
    val random = RandomInterstitialTrigger(state, { config }, screens, interstitial, scope, { now })
    val splash = SplashFlow(state, { config }, pipeline, shower, screens, scope, source)
    val appOpen = AppOpenController(state, { config }, appOpenPool, pipeline, shower, screens, scope, { now }, source)
}

package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Rewarded ads, on demand: claim the single ad slot, loader overlay, floors (waterfall), show, report.
 * The reward is reported only when the SDK says it was earned. A premium user gets the reward without an ad
 * (placement option premiumGetsReward). The listener is called once at the end, whatever happens.
 */
internal class RewardedController(
    private val state: AdState,
    private val config: () -> AdsConfig,
    private val pipeline: AdLoadPipeline,
    private val shower: FullScreenShower,
    private val screens: ScreenHost,
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val loaderUi: () -> AdLoaderUi?,
    private val source: suspend (String) -> FullScreenAd?,
    private val cooldown: NoFillCooldown? = null,
    private val noAd: NoAdNotice? = null,
) {
    private class Result(private val l: RewardListener?) {
        var earned = false
        private var finished = false

        fun earned(amount: Int, type: String) {
            if (finished || earned) return
            earned = true
            l?.onRewardEarned(amount, type)
        }

        /** [reason] null = a normal end. Otherwise "no ad shown" and the reason is told to the app (unless it was rewarded). */
        fun finish(reason: String?) {
            if (finished) return
            finished = true
            if (!earned && reason != null) l?.onNotAvailable(reason)
            l?.onComplete(earned)
        }
    }

    fun show(activity: Activity, name: String, listener: RewardListener?) {
        val result = Result(listener)
        AdLog.d("rewarded") { "$name: show requested from ${activity.javaClass.simpleName}" }
        val p = config().placement(name)
        if (p == null || p.format != AdFormat.REWARDED) {
            result.finish("unknown rewarded placement: $name")
            return
        }
        if (!p.enabled) {
            result.finish("disabled")
            return
        }
        if (state.premium.value) {
            AdLog.d("rewarded") { "$name: premium -> reward without an ad = ${p.premiumGetsReward}" }
            if (p.premiumGetsReward) {
                result.earned(1, "premium")
                result.finish(null)
            } else {
                result.finish("premium")
            }
            return
        }
        val decision = AdPolicy.evaluateExplicit(p, state.context(clock(), activity.javaClass.name))
        if (decision != Decision.ALLOW) {
            AdLog.d("rewarded") { "$name: denied: $decision" }
            result.finish(decision.name)
            return
        }
        val info = LoaderInfo(name, p.loaderTitle ?: config().loaderTitle, p.loaderMessage ?: config().loaderMessage)
        val wait = cooldown?.remainingMs(name) ?: 0L
        if (wait > 0) {
            AdLog.d("rewarded") { "$name: no fill a moment ago, not requesting again for ${wait}ms" }
            state.setAdShowing(true) // the message is on screen: no other ad meanwhile
            scope.launch {
                try { noAd?.show(activity, info) } finally { state.setAdShowing(false) }
                result.finish("no ad available, retry in ${(wait + 999) / 1000}s")
            }
            return
        }
        state.setAdShowing(true) // claim the slot before loading: a second tap is refused
        scope.launch {
            var handedOver = false
            val ui = loaderUi()
            try {
                runCatching { ui?.show(activity, info) }
                val ad = try {
                    pipeline.load(p, activity, source) { it.destroy() }
                } catch (t: Throwable) {
                    runCatching { ui?.dismiss() }
                    throw t
                }
                if (ad != null) runCatching { ui?.dismiss() }
                when {
                    ad == null -> {
                        cooldown?.recordFail(name)
                        if (noAd != null) noAd.show(activity, info) else runCatching { ui?.dismiss() }
                        result.finish("no fill")
                    }
                    state.premium.value -> { ad.destroy(); result.earned(1, "premium"); result.finish(null) }
                    !screens.isUsable(activity) -> { ad.destroy(); result.finish("screen gone") }
                    else -> {
                        cooldown?.recordOk(name)
                        handedOver = true
                        shower.show(activity, ad, object : FullScreenShower.Events {
                            override fun onShown() {}
                            override fun onRewarded(amount: Int, type: String) = result.earned(amount, type)
                            override fun onFinished(shown: Boolean, reason: String?) =
                                result.finish(if (shown) null else reason ?: "not shown")
                        })
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                AdLog.d("rewarded") { "$name: error $t" }
                result.finish(t.message ?: "error")
            } finally {
                if (!handedOver) state.setAdShowing(false)
            }
        }
    }
}

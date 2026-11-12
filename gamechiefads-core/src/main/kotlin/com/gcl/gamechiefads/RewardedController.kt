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
        state.setAdShowing(true) // claim the slot before loading: a second tap is refused
        scope.launch {
            var handedOver = false
            val ui = loaderUi()
            try {
                runCatching { ui?.show(activity, LoaderInfo(name, p.loaderTitle ?: config().loaderTitle, p.loaderMessage ?: config().loaderMessage)) }
                val ad = try {
                    pipeline.load(p, activity, source) { it.destroy() }
                } finally {
                    runCatching { ui?.dismiss() }
                }
                when {
                    ad == null -> result.finish("no fill")
                    state.premium.value -> { ad.destroy(); result.earned(1, "premium"); result.finish(null) }
                    !screens.isUsable(activity) -> { ad.destroy(); result.finish("screen gone") }
                    else -> {
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

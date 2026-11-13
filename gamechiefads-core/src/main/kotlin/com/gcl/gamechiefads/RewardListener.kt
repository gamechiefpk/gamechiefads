package com.gcl.gamechiefads

/**
 * Result of [GamechiefAds.loadRewardedAd]. Every method is optional (empty default, also from Java).
 * All callbacks run on the main thread.
 *
 * Give the reward ONLY in [onRewardEarned]. Closing the ad early gives [onComplete] with false and no reward.
 * Order: [onRewardEarned] (if earned), then [onNotAvailable] (if no ad could be shown), then [onComplete], always, once.
 */
interface RewardListener {
    /** The user watched enough: grant the reward here. [type] and [amount] come from the ad unit ("premium", 1 for premium users). */
    fun onRewardEarned(amount: Int, type: String) {}

    /** No ad was shown (no fill, offline, timeout, consent denied, another ad showing...). Show your own message here. */
    fun onNotAvailable(reason: String) {}

    /** Always last, once. [rewarded] = the reward was earned (or granted to a premium user). */
    fun onComplete(rewarded: Boolean) {}
}

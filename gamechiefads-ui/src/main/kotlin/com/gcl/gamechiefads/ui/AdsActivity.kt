package com.gcl.gamechiefads.ui

import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import com.gcl.gamechiefads.GamechiefAds
import com.gcl.gamechiefads.InterstitialListener
import com.gcl.gamechiefads.RewardListener

/**
 * Optional base screen: extend it instead of AppCompatActivity and every ad call is one short method of this class.
 * Each one is open, so a screen (or your own base class) can override it to do something else.
 *
 *     public class HomeActivity extends AdsActivity {
 *         @Override protected void onCreate(Bundle b) {
 *             super.onCreate(b);
 *             setContentView(R.layout.activity_home);
 *             loadBannerAd(R.id.bannerFrame);                       // banner from the library's settings
 *             findViewById(R.id.btn).setOnClickListener(v -> loadRandomInterstitialAd());
 *         }
 *     }
 *
 * No ad code of your own is needed for banners if the layout uses the tags (android:tag="gads_banner"): see docs/USAGE.md.
 * Java has one parent class: if a screen already has one, call GamechiefAds directly, the result is the same.
 */
open class AdsActivity : AppCompatActivity() {

    /** Banner for a normal screen (the bannerUnits slot, with floors). Override to change what this screen shows. */
    protected open fun loadBannerAd(container: FrameLayout) = GamechiefAds.loadBannerAd(container)

    /** Banner for the exit screen (the exitBannerUnits slot). */
    protected open fun loadExitBannerAd(container: FrameLayout) = GamechiefAds.loadExitBannerAd(container)

    /** Same as [loadBannerAd], found by view id. Nothing happens if the id is not a FrameLayout of this screen. */
    protected fun loadBannerAd(@IdRes containerId: Int) { findViewById<FrameLayout>(containerId)?.let { loadBannerAd(it) } }

    /** Same as [loadExitBannerAd], found by view id. */
    protected fun loadExitBannerAd(@IdRes containerId: Int) { findViewById<FrameLayout>(containerId)?.let { loadExitBannerAd(it) } }

    /** The random interstitial on a button of this screen (the library decides if it is time, shows the loading dialog). */
    protected open fun loadRandomInterstitialAd(listener: InterstitialListener?) = GamechiefAds.loadRandomInterstitialAd(this, listener)

    protected fun loadRandomInterstitialAd() = loadRandomInterstitialAd(null)

    /** A rewarded ad for a button of this screen. */
    protected open fun loadRewardedAd(listener: RewardListener?) = GamechiefAds.loadRewardedAd(this, listener)
}

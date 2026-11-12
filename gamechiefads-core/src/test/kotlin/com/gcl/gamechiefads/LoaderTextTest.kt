package com.gcl.gamechiefads

import android.app.Activity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoaderTextTest {
    private class SpyUi : AdLoaderUi {
        var info: LoaderInfo? = null
        override fun show(activity: Activity) {}
        override fun show(activity: Activity, info: LoaderInfo) { this.info = info }
        override fun dismiss() {}
    }

    private fun base(placementText: Boolean) = AdsConfig.builder()
        .defaultLoaderText("Ad is loading", "Please wait")
        .interstitial("home").units("a").also { if (placementText) it.loaderText("Saving", "One moment") }
        .build()

    @Test fun globalTextIsUsedWhenThePlacementHasNone() = runTest {
        val r = Rig(this, base(false)); val ui = SpyUi(); r.loaderUi = ui
        r.interstitial.showOnDemand(r.activity, "home", null); advanceUntilIdle()
        assertEquals("Ad is loading", ui.info?.title); assertEquals("Please wait", ui.info?.message)
    }

    @Test fun placementTextWinsOverTheGlobalOne() = runTest {
        val r = Rig(this, base(true)); val ui = SpyUi(); r.loaderUi = ui
        r.interstitial.showOnDemand(r.activity, "home", null); advanceUntilIdle()
        assertEquals("Saving", ui.info?.title); assertEquals("One moment", ui.info?.message)
    }

    @Test fun remoteConfigCanChangeTheGlobalText() {
        val c = RemoteOverrides.apply(base(false), """{"loaderTitle":"Loading ad","loaderMessage":"Hold on"}""")
        assertEquals("Loading ad", c.loaderTitle); assertEquals("Hold on", c.loaderMessage)
        assertEquals("Ad is loading", RemoteOverrides.apply(base(false), """{"enabled":true}""").loaderTitle)
    }
}

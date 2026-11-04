package com.gcl.gamechiefads

import android.app.Activity

class TestActivity : Activity()
class OtherActivity : Activity()

/** Fake ad. show() behaviour is chosen per test. */
internal class FakeAd(
    override val unitId: String = "u",
    var onShow: (FullScreenAd.Listener) -> Unit = { it.onShown(); it.onDismissed() },
) : FullScreenAd {
    var shows = 0
    var destroyed = false
    var listener: FullScreenAd.Listener? = null

    override fun show(activity: Activity, listener: FullScreenAd.Listener) {
        shows++
        this.listener = listener
        onShow(listener)
    }

    override fun destroy() { destroyed = true }

    companion object {
        /** Shown but stays open until the test calls dismiss(). */
        fun manual() = FakeAd(onShow = { it.onShown() })
    }
}

internal class FakeScreens(var activity: Activity? = null) : ScreenHost {
    override val current: Activity? get() = activity
    override fun isUsable(activity: Activity) = activity === this.activity
}

internal class RecordingListener : InterstitialListener {
    val calls = mutableListOf<String>()
    override fun onShown() { calls += "shown" }
    override fun onClosed() { calls += "closed" }
    override fun onNotShown(reason: String) { calls += "notShown:$reason" }
    override fun onComplete(wasShown: Boolean) { calls += "complete:$wasShown" }
}

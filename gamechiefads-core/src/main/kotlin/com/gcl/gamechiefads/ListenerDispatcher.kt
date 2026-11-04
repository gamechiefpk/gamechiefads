package com.gcl.gamechiefads

/** Null-safe, once-only bridge from [FullScreenShower.Events] to an optional [InterstitialListener]. */
internal class ListenerDispatcher(private val listener: InterstitialListener?) : FullScreenShower.Events {
    private var finished = false

    override fun onShown() {
        if (!finished) listener?.onShown()
    }

    override fun onFinished(shown: Boolean, reason: String?) {
        if (finished) return
        finished = true
        val l = listener ?: return
        if (shown) l.onClosed() else l.onNotShown(reason ?: "not shown")
        l.onComplete(shown)
    }
}

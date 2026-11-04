package com.gcl.gamechiefads

/**
 * Optional, every method has an empty default (also from Java). Pass null if you do not care.
 * Exactly one of [onClosed] / [onNotShown] is called, then [onComplete]: navigate in either.
 */
interface InterstitialListener {
    fun onShown() {}

    /** The ad was shown and the user closed it. */
    fun onClosed() {}

    /** Nothing was shown (premium, no fill, timeout, screen gone, error...). Safe to continue. */
    fun onNotShown(reason: String) {}

    /** Always last, once. [wasShown] tells which of the two happened. */
    fun onComplete(wasShown: Boolean) {}
}

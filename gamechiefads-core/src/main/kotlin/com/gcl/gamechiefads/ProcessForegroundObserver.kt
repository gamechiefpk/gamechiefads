package com.gcl.gamechiefads

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

/** ProcessLifecycleOwner ON_START = the app came to the foreground (replaces deprecated @OnLifecycleEvent). */
internal class ProcessForegroundObserver(private val onForeground: () -> Unit) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) = onForeground()
}

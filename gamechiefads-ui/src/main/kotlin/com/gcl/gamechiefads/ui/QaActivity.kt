package com.gcl.gamechiefads.ui

import android.app.Activity
import android.os.Bundle
import com.gcl.gamechiefads.GamechiefAds

/** The debug panel as a full screen (GamechiefAds.openQaScreen). Debug builds only: in a release build it closes at once. */
class QaActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!GamechiefAds.isDebugBuild()) { finish(); return }
        setContentView(DebugPanelView(this))
    }
}

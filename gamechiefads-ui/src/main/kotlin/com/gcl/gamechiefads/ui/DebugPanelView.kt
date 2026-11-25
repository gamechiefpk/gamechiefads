package com.gcl.gamechiefads.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.gcl.gamechiefads.AdLog
import com.gcl.gamechiefads.BannerType
import com.gcl.gamechiefads.GamechiefAds
import com.gcl.gamechiefads.Placements

/**
 * The debug panel: Logs (live library trace), Setup (the setup check), Config (the current rules, and a box to try a rules JSON like Firebase would
 * send) and Test (a button for every ad type). Debug builds only: it is opened by GamechiefAds.showDebugPanel (a bottom sheet) or
 * GamechiefAds.openQaScreen (full screen). Built in code, no XML.
 */
internal class DebugPanelView(private val activity: Activity) : LinearLayout(activity) {
    private val dp = resources.displayMetrics.density
    private val content = FrameLayout(activity)
    private val tabs = LinearLayout(activity)
    private val logText = mono()
    private val logScroll = ScrollView(activity)
    private val tabViews = LinkedHashMap<String, View>()
    private val tabButtons = LinkedHashMap<String, TextView>()
    private lateinit var adFrame: FrameLayout
    private val config = mono()
    private val setup = mono()
    private val flags = mono()

    init {
        orientation = VERTICAL
        setBackgroundColor(0xFF1B1B1F.toInt())
        val title = TextView(activity).apply {
            text = "GamechiefAds debug (debug builds only)"; setTextColor(Color.WHITE); textSize = 15f; typeface = Typeface.DEFAULT_BOLD
            setPadding(px(16), px(12), px(16), px(4))
        }
        addView(title)
        tabs.orientation = HORIZONTAL
        addView(tabs, LayoutParams(-1, -2))
        addView(content, LayoutParams(-1, 0, 1f))

        tab("Logs", logTab())
        tab("Setup", setupTab())
        tab("Config", configTab())
        tab("Test", testTab())
        show("Logs")
    }

    // ---- tabs

    private fun tab(name: String, view: View) {
        tabViews[name] = view
        content.addView(view, FrameLayout.LayoutParams(-1, -1))
        val b = TextView(activity).apply {
            text = name; gravity = Gravity.CENTER; setTextColor(0xFFAAAAAA.toInt()); textSize = 14f; setPadding(0, px(10), 0, px(10))
            setOnClickListener { show(name) }
        }
        tabButtons[name] = b
        tabs.addView(b, LayoutParams(0, -2, 1f))
    }

    private fun show(name: String) {
        tabViews.forEach { (n, v) -> v.visibility = if (n == name) VISIBLE else GONE }
        tabButtons.forEach { (n, b) -> b.setTextColor(if (n == name) Color.WHITE else 0xFFAAAAAA.toInt()); b.setTypeface(null, if (n == name) Typeface.BOLD else Typeface.NORMAL) }
        when (name) {
            "Setup" -> refreshSetup()
            "Config" -> refreshConfig()
        }
    }

    // ---- logs

    private fun logTab(): View {
        val col = LinearLayout(activity).apply { orientation = VERTICAL }
        val row = LinearLayout(activity)
        row.addView(small("Copy") {
            (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("GamechiefAds log", logText.text))
        })
        row.addView(small("Clear") { AdLog.clear(); logText.text = "" })
        col.addView(row)
        logScroll.addView(logText)
        col.addView(logScroll, LayoutParams(-1, 0, 1f))
        logText.text = AdLog.snapshot().joinToString("\n")
        logScroll.post { logScroll.fullScroll(FOCUS_DOWN) }
        return col
    }

    private fun appendLog(line: String) {
        logText.append("\n$line")
        if (logText.lineCount > AdLog.MAX_LINES + 50) logText.text = AdLog.snapshot().joinToString("\n")
        logScroll.post { logScroll.fullScroll(FOCUS_DOWN) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        AdLog.listener = { line -> post { appendLog(line) } }
    }

    override fun onDetachedFromWindow() {
        AdLog.listener = null
        super.onDetachedFromWindow()
    }

    // ---- setup + config

    private fun setupTab(): View = ScrollView(activity).apply { addView(setup) }

    private fun refreshSetup() {
        val r = GamechiefAds.checkSetup()
        setup.text = if (r.isEmpty()) "Setup check: nothing to report. All good." else r.joinToString("\n\n")
    }

    private fun configTab(): View {
        val col = LinearLayout(activity).apply { orientation = VERTICAL }
        val scroll = ScrollView(activity).apply { addView(LinearLayout(activity).apply { orientation = VERTICAL; addView(flags); addView(config) }) }
        col.addView(scroll, LayoutParams(-1, 0, 1f))
        val box = EditText(activity).apply {
            hint = "{\"placements\":{\"splash\":{\"double\":true}}}"; setHintTextColor(0xFF777777.toInt()); setTextColor(Color.WHITE); textSize = 12f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; setBackgroundColor(0xFF2A2A30.toInt()); setPadding(px(8), px(8), px(8), px(8))
        }
        col.addView(box, LayoutParams(-1, -2).apply { setMargins(px(8), px(4), px(8), 0) })
        val row = LinearLayout(activity)
        row.addView(small("Apply rules JSON") { GamechiefAds.applyRemoteConfig(box.text.toString().ifBlank { null }); AdLog.note("rules JSON applied from the debug panel"); refreshConfig() })
        row.addView(small("Clear") { GamechiefAds.applyRemoteConfig(null); box.setText(""); AdLog.note("rules JSON cleared"); refreshConfig() })
        col.addView(row)
        return col
    }

    private fun refreshConfig() {
        flags.text = "premium=${GamechiefAds.isPremium()}   test mode=${GamechiefAds.isTestMode()}   Firebase intro big ads=${IntroPager.bigAdCount(GamechiefAds.getRemoteBoolean(IntroPager.ENABLE_KEY, false), GamechiefAds.getRemoteLong(IntroPager.REMOTE_KEY, 0))}\n"
        config.text = GamechiefAds.debugConfig().joinToString("\n\n")
    }

    // ---- test

    private fun testTab(): View {
        val col = LinearLayout(activity).apply { orientation = VERTICAL; setPadding(px(8), px(4), px(8), px(8)) }
        fun section(t: String) = col.addView(TextView(activity).apply { text = t; setTextColor(0xFF8AB4F8.toInt()); textSize = 12f; setPadding(0, px(10), 0, px(2)) })
        fun action(label: String, run: () -> Unit) = col.addView(small(label) { AdLog.note("test: $label"); run() }, LayoutParams(-1, -2))

        section("Switches")
        action("Premium ON / OFF") { GamechiefAds.setPremium(!GamechiefAds.isPremium()); AdLog.note("premium=${GamechiefAds.isPremium()}") }
        action("Test mode (Google test ids) ON / OFF") { GamechiefAds.setTestMode(!GamechiefAds.isTestMode()) }
        action("Open Google Ad Inspector") { GamechiefAds.openAdInspector() }

        section("Banners and natives (shown in the box at the top)")
        action("Banner (floors)") { GamechiefAds.loadBannerAd(fresh()) }
        action("Banner (no floors)") { GamechiefAds.loadBannerAd(fresh(), false) }
        action("Banner collapsible bottom") { GamechiefAds.loadBannerAd(fresh(), BannerType.COLLAPSIBLE_BOTTOM) }
        action("Splash banner (large)") { GamechiefAds.loadSplashBannerAd(fresh()) }
        action("Exit banner") { GamechiefAds.loadExitBannerAd(fresh()) }
        action("Native: language (small media)") { AdsUi.loadLanguageNativeAd(fresh()) }
        action("Native: intro (small media)") { AdsUi.loadIntroNativeAd(fresh()) }
        action("Native: medium on the intro placement") { AdsUi.loadNativeAd(fresh(), Placements.INTRO_NATIVE, NativeSize.MEDIUM) }

        section("Full-screen ads")
        action("Random interstitial (follows Firebase double)") { GamechiefAds.loadRandomInterstitialAd(activity, null) }
        action("Random interstitial DOUBLE (if a second unit exists)") { GamechiefAds.loadRandomInterstitialAd(activity, true, null) }
        action("Rewarded") { GamechiefAds.loadRewardedAd(activity, null) }
        action("Full-screen native") { AdsUi.loadFullScreenNativeAd(activity) }
        action("Splash app open") { GamechiefAds.loadSplashAppOpenAd(activity) { ads -> ads.show(activity) { AdLog.note("splash test finished") } } }
        action("Splash interstitial") { GamechiefAds.loadSplashInterstitialAd(activity) { ads -> ads.show(activity) { AdLog.note("splash test finished") } } }
        // the preview box stays on top so a loaded banner or native is seen without scrolling
        adFrame = FrameLayout(activity)
        return LinearLayout(activity).apply {
            orientation = VERTICAL
            addView(adFrame, LayoutParams(-1, -2))
            addView(ScrollView(activity).apply { addView(col) }, LayoutParams(-1, 0, 1f))
        }
    }

    private fun fresh(): FrameLayout {
        val f = FrameLayout(activity)
        adFrame.removeAllViews()
        adFrame.addView(f, LayoutParams(-1, -2))
        return f
    }

    // ---- small helpers

    private fun px(v: Int) = (v * dp).toInt()

    private fun mono() = TextView(activity).apply {
        typeface = Typeface.MONOSPACE; textSize = 11f; setTextColor(0xFFE0E0E0.toInt()); setPadding(px(10), px(6), px(10), px(6)); setTextIsSelectable(true)
    }

    private fun small(label: String, click: () -> Unit) = Button(activity).apply {
        text = label; isAllCaps = false; textSize = 12f; setOnClickListener { click() }
        layoutParams = ViewGroup.MarginLayoutParams(-2, -2)
    }
}

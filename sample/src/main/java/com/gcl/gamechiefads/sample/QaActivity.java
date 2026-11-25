package com.gcl.gamechiefads.sample;

import com.gcl.gamechiefads.GamechiefAds;

import java.util.List;

/** The QA tools of the library: the setup checker, the test mode (Google test ids in any build) and Google's Ad Inspector. */
public class QaActivity extends DemoActivity {
    @Override
    protected String screenTitle() {
        return "QA tools";
    }

    @Override
    protected void build() {
        note("Debug builds already print the setup check in logcat at start (tag GamechiefAds). Here you can ask for it any time.");
        button("GamechiefAds.checkSetup()", () -> {
            List<String> issues = GamechiefAds.checkSetup();
            if (issues.isEmpty()) say("setup: nothing to report");
            for (String s : issues) say(s);
        });
        button("GamechiefAds.setTestMode(true)  (Google test ids, any build)", () -> {
            GamechiefAds.setTestMode(true);
            say("test mode: " + GamechiefAds.isTestMode());
        });
        button("GamechiefAds.setTestMode(false)", () -> {
            GamechiefAds.setTestMode(false);
            say("test mode: " + GamechiefAds.isTestMode());
        });
        button("GamechiefAds.openAdInspector()", () -> {
            say("opening the ad inspector...");
            GamechiefAds.openAdInspector(() -> say("ad inspector closed"));
        });
    }
}

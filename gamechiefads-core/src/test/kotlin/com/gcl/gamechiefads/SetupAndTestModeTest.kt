package com.gcl.gamechiefads

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCheckerTest {
    private val REAL_UNIT = "ca-app-pub-1234567890123456/1234567890"
    private val REAL_APP = "ca-app-pub-1234567890123456~1234567890"
    private fun env(debug: Boolean = false, meta: Boolean = true, legacy: Boolean = false, ui: Boolean = true, fb: Boolean = true) =
        SetupChecker.Env(debug, meta, legacy, ui, fb)

    private fun good() = AdsConfig.builder().appId(REAL_APP).bannerUnits(REAL_UNIT).build()

    @Test fun aCorrectSetupReportsNothing() {
        assertEquals(emptyList<String>(), SetupChecker.check(good(), env()))
    }

    @Test fun missingManifestAppIdAndTheOldAdsLibraryAreErrors() {
        val r = SetupChecker.check(good(), env(meta = false, legacy = true))
        assertTrue(r.any { it.startsWith("[ERROR]") && "APPLICATION_ID" in it })
        assertTrue(r.any { it.startsWith("[ERROR]") && "play-services-ads" in it })
    }

    @Test fun googleTestUnitIdsAreFineInDebugButAnErrorInRelease() {
        val c = AdsConfig.builder().appId(REAL_APP).bannerUnits("ca-app-pub-3940256099942544/9214589741").build()
        assertEquals(emptyList<String>(), SetupChecker.check(c, env(debug = true)))
        assertTrue(SetupChecker.check(c, env(debug = false)).any { it.startsWith("[ERROR]") && "TEST unit id" in it })
    }

    @Test fun badIdsMissingUnitsAndNoConfigAreWarnings() {
        assertTrue(SetupChecker.check(AdsConfig.builder().appId(REAL_APP).bannerUnits("banner-1").build(), env()).any { "does not look like an AdMob unit id" in it })
        assertTrue(SetupChecker.check(AdsConfig.builder().appId("nope").bannerUnits(REAL_UNIT).build(), env()).any { "does not look like an AdMob app id" in it })
        assertTrue(SetupChecker.check(AdsConfig.builder().build(), env()).any { "No ad units are configured" in it })
        assertTrue(SetupChecker.check(AdsConfig.builder().bannerUnits(REAL_UNIT).build(), env(debug = false)).any { "appId" in it })   // release without an app id
        assertFalse(SetupChecker.check(AdsConfig.builder().bannerUnits(REAL_UNIT).build(), env(debug = true)).any { "appId" in it })
    }

    @Test fun aDoubleWithoutASecondUnitIsOnlyInformation() {
        val c = AdsConfig.builder().appId(REAL_APP).randomInterstitialUnits(REAL_UNIT).doubleAd(true).build()
        val r = SetupChecker.check(c, env())
        assertTrue(r.any { it.startsWith("[INFO]") && "single ad" in it }); assertFalse(r.any { it.startsWith("[ERROR]") })
        val ok = AdsConfig.builder().appId(REAL_APP).randomInterstitialUnits(REAL_UNIT).randomInterstitialDoubleUnit(REAL_UNIT).doubleAd(true).build()
        assertEquals(emptyList<String>(), SetupChecker.check(ok, env()))
    }

    @Test fun aMissingUiModuleAndAnHttpRulesFileAreWarned() {
        assertTrue(SetupChecker.check(good(), env(ui = false)).any { "gamechiefads-ui" in it })
        val c = AdsConfig.builder().appId(REAL_APP).bannerUnits(REAL_UNIT).remoteConfigUrl("http://x.com/a.json").build()
        assertTrue(SetupChecker.check(c, env()).any { "https" in it })
    }
}

class TestModeTest {
    @Test fun testModeUsesGoogleTestIdsEvenInAReleaseBuild() = runTest {
        val cfg = AdsConfig.builder().bannerUnits("ca-app-pub-1234567890123456/1234567890").build()
        val p = cfg.placement(Placements.BANNER)!!
        val seen = mutableListOf<String>()
        var test = false
        val pipeline = AdLoadPipeline({ cfg }, { true }, false, FloorPolicy { 0L }) { test }   // debug = false: a release build
        pipeline.load(p, null, { u -> seen += u; null })
        assertEquals(listOf("ca-app-pub-1234567890123456/1234567890"), seen)                // your own id in release
        seen.clear(); test = true
        pipeline.load(p, null, { u -> seen += u; null })
        assertTrue(seen.single().startsWith("ca-app-pub-3940256099942544/"))                // test mode: Google's test id
        seen.clear(); test = false
        pipeline.load(p, null, { u -> seen += u; null })
        assertEquals(listOf("ca-app-pub-1234567890123456/1234567890"), seen)                // and off again
    }
}

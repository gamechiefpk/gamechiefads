package com.gcl.gamechiefads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteUrlConfigTest {
    private class MemStore : RemoteUrlConfig.Store {
        val m = mutableMapOf<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String?) { if (value == null) m.remove(key) else m[key] = value }
    }

    private class FakeHttp(var response: () -> RemoteUrlConfig.Response) : RemoteUrlConfig.Http {
        var calls = 0
        var lastEtag: String? = "unset"
        override fun get(url: String, etag: String?): RemoteUrlConfig.Response { calls++; lastEtag = etag; return response() }
    }

    private val ok = RemoteUrlConfig.Response(200, """{"placements":{"random":{"every":5}}}""", "v1")

    private fun file(store: MemStore, http: FakeHttp, now: () -> Long = { 1_000_000L }, url: String = "https://example.com/ads.json") =
        RemoteUrlConfig(url, 3_600, now, store, http)

    @Test fun aFetchReturnsTheNewRulesAndKeepsThemForTheNextRun() {
        val s = MemStore(); val h = FakeHttp { ok }
        assertNull(file(s, h).cached())
        assertEquals(ok.body, file(s, h).refresh())
        assertEquals(ok.body, file(s, FakeHttp { error("offline") }).cached())            // a later run has them at once, offline too
    }

    @Test fun itFetchesOnlyWhenDueAndAForceAlwaysFetches() {
        var t = 1_000_000L; val s = MemStore(); val h = FakeHttp { ok }; val f = file(s, h, { t })
        f.refresh(); f.refresh()
        assertEquals(1, h.calls)
        t += 3_601_000; f.refresh()
        assertEquals(2, h.calls)
        f.refresh(force = true)
        assertEquals(3, h.calls)
    }

    @Test fun anUnchangedFileReturnsNullAndTheEtagIsSentBack() {
        val s = MemStore(); val h = FakeHttp { ok }; val f = file(s, h)
        f.refresh()
        assertNull(f.refresh(force = true)); assertEquals("v1", h.lastEtag)
        h.response = { RemoteUrlConfig.Response(304, null, null) }
        assertNull(f.refresh(force = true)); assertEquals(ok.body, f.cached())            // 304: the kept copy stays
    }

    @Test fun anyFailureKeepsTheLastGoodRulesAndNeverThrows() {
        val s = MemStore(); val h = FakeHttp { ok }; val f = file(s, h)
        f.refresh()
        h.response = { error("no network") }; assertNull(f.refresh(force = true))
        h.response = { RemoteUrlConfig.Response(500, "oops", null) }; assertNull(f.refresh(force = true))
        h.response = { RemoteUrlConfig.Response(200, "not json", null) }; assertNull(f.refresh(force = true))
        h.response = { RemoteUrlConfig.Response(200, "x".repeat(200_000), null) }; assertNull(f.refresh(force = true))
        assertEquals(ok.body, f.cached())
    }

    @Test fun onlyHttpsIsUsed() {
        val h = FakeHttp { ok }
        assertNull(file(MemStore(), h, url = "http://example.com/ads.json").refresh(force = true))
        assertEquals(0, h.calls)
    }

    @Test fun changedRulesAreReturnedAgain() {
        val s = MemStore(); val h = FakeHttp { ok }; val f = file(s, h)
        f.refresh()
        val newer = """{"enabled":false}"""
        h.response = { RemoteUrlConfig.Response(200, newer, "v2") }
        assertEquals(newer, f.refresh(force = true)); assertEquals(newer, f.cached())
    }

    // ---- layers: config < shared file < Firebase < manual (the runtime applies them in this order)

    private val base = AdsConfig.builder().randomInterstitialUnits("a").bannerUnits("b").build()

    private fun layers(vararg json: String?) = json.fold(base) { c, j -> RemoteOverrides.apply(c, j) }

    @Test fun aLaterLayerWinsAndAnEarlierOneStillShowsThroughWhereTheLaterIsSilent() {
        val c = layers("""{"placements":{"random":{"everyNCalls":9,"gap":50}}}""", """{"placements":{"random":{"gap":10}}}""")
        val p = c.placement(Placements.RANDOM)!!
        assertEquals(9, p.everyNCalls); assertEquals(10, p.minGapSeconds)
    }

    @Test fun aKillSwitchInTheSharedFileSurvivesALayerThatSaysNothingAboutIt() {
        val c = layers("""{"enabled":false}""", """{"placements":{"banner":{"units":["x"]}}}""")
        assertFalse(c.enabled); assertFalse(c.placement(Placements.BANNER)!!.enabled)
    }

    @Test fun timingsAreNeverChangedRemotely() {
        val c = layers("""{"global":{"splashTimeoutMs":15000,"overallBudgetMs":6000,"initTimeoutMs":9000,"doubleSecondWaitMs":1000,"noAdMessageMs":1200}}""")
        assertEquals(base.splashTimeoutMs, c.splashTimeoutMs); assertEquals(base.overallBudgetMs, c.overallBudgetMs); assertEquals(base.initTimeoutMs, c.initTimeoutMs)
        assertEquals(base.noAdMessageMs, c.noAdMessageMs)
    }

    @Test fun theBuilderKeepsTheUrlAndASaneInterval() {
        val c = AdsConfig.builder().remoteConfigUrl("https://example.com/ads.json", 5).build()
        assertEquals("https://example.com/ads.json", c.remoteConfigUrl); assertEquals(60L, c.remoteConfigUrlIntervalSeconds)
        assertNull(AdsConfig.builder().remoteConfigUrl(" ").build().remoteConfigUrl)
        assertTrue(AdsConfig.builder().build().remoteConfigUrl == null)
    }
}

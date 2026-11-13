package com.gcl.gamechiefads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteValuesTest {
    private class FakeSource : RemoteConfigSource {
        val server = mutableMapOf<String, Any>()
        val defaults = mutableMapOf<String, Any>()
        var fetchResult = true
        var fetches = 0
        var interval = -1L
        var update: Runnable? = null
        var throwOnRead = false

        override fun setDefaults(defaults: Map<String, Any>) { this.defaults.putAll(defaults) }
        override fun setMinFetchIntervalSeconds(seconds: Long) { interval = seconds }
        override fun fetchAndActivate(callback: RemoteFetchListener) { fetches++; callback.onResult(fetchResult) }
        override fun has(key: String) = key in server || key in defaults
        private fun v(key: String): Any { if (throwOnRead) error("boom"); return server[key] ?: defaults.getValue(key) }
        override fun getString(key: String) = v(key).toString()
        override fun getBoolean(key: String) = v(key).toString().toBoolean()
        override fun getLong(key: String) = v(key).toString().toLong()
        override fun getDouble(key: String) = v(key).toString().toDouble()
        override fun onUpdated(listener: Runnable) { update = listener }
    }

    @Test fun withoutASourceTheDefaultsAndFallbacksAnswer() {
        val r = RemoteValues(null)
        assertFalse(r.available)
        assertEquals("fb", r.string("a", "fb"))
        r.addDefaults(mapOf("a" to "x", "n" to 7, "d" to 1.5, "b" to true))
        assertEquals("x", r.string("a", "fb")); assertEquals(7L, r.long("n", 0)); assertEquals(1.5, r.double("d", 0.0), 0.0)
        assertTrue(r.boolean("b", false)); assertEquals(9L, r.long("missing", 9))
    }

    @Test fun serverValueBeatsDefaultAndDefaultBeatsFallback() {
        val s = FakeSource(); val r = RemoteValues(s)
        r.addDefaults(mapOf("title" to "default", "limit" to 3))
        s.server["title"] = "from server"
        assertEquals("from server", r.string("title", "fb")); assertEquals(3L, r.long("limit", 0)); assertEquals("fb", r.string("nope", "fb"))
        assertEquals(mapOf<String, Any>("title" to "default", "limit" to 3), s.defaults) // the source got the defaults too
    }

    @Test fun typesAreConvertedAndBadValuesFallBack() {
        val s = FakeSource(); val r = RemoteValues(s)
        s.server["flag"] = "true"; s.server["count"] = "12"; s.server["rate"] = "0.25"
        assertTrue(r.boolean("flag", false)); assertEquals(12L, r.long("count", 0)); assertEquals(0.25, r.double("rate", 0.0), 0.0)
        r.addDefaults(mapOf("weird" to "abc")); // a default that is not a number
        assertEquals(5L, RemoteValues(null).also { it.addDefaults(mapOf("weird" to "abc")) }.long("weird", 5))
    }

    @Test fun addDefaultsIgnoresValuesOfOtherTypes() {
        val r = RemoteValues(null)
        r.addDefaults(mapOf("list" to listOf(1), "ok" to "yes"))
        assertEquals("fb", r.string("list", "fb")); assertEquals("yes", r.string("ok", "fb"))
    }

    @Test fun startSetsIntervalDefaultsFetchesAndTellsTheRuntimeOnlyOnSuccess() {
        val s = FakeSource(); val r = RemoteValues(s); var applied = 0
        r.addDefaults(mapOf("k" to "v"))
        r.start(120) { applied++ }
        assertEquals(120L, s.interval); assertEquals(1, s.fetches); assertEquals(1, applied)
        val f = FakeSource().apply { fetchResult = false }; var a2 = 0
        RemoteValues(f).start(60) { a2++ }
        assertEquals(1, f.fetches); assertEquals(0, a2)
    }

    @Test fun realTimeUpdatesReapplyAndNotifyTheListener() {
        val s = FakeSource(); val r = RemoteValues(s); var applied = 0; var notified = 0
        r.setListener { notified++ }
        r.start(60) { applied++ }
        val afterStart = notified
        s.update!!.run()
        assertEquals(2, applied); assertEquals(afterStart + 1, notified)
    }

    @Test fun fetchReportsTheResultAndNotifiesOnSuccess() {
        val s = FakeSource(); val r = RemoteValues(s); var notified = 0; var result: Boolean? = null
        r.setListener { notified++ }
        r.fetch { result = it }
        assertEquals(true, result); assertEquals(1, notified)
        s.fetchResult = false; r.fetch { result = it }
        assertEquals(false, result); assertEquals(1, notified)
        RemoteValues(null).fetch { result = it }
        assertEquals(false, result)
    }

    @Test fun aBrokenSourceNeverThrowsAndFallsBackToDefaults() {
        val s = FakeSource().apply { throwOnRead = true; server["a"] = "x" }; val r = RemoteValues(s)
        r.addDefaults(mapOf("a" to "default"))
        assertEquals("default", r.string("a", "fb"))
        val bad = object : RemoteConfigSource by FakeSource() {
            override fun setDefaults(defaults: Map<String, Any>) = error("no")
            override fun fetchAndActivate(callback: RemoteFetchListener) = error("no")
        }
        val rv = RemoteValues(bad); rv.addDefaults(mapOf("k" to 1)); var ok: Boolean? = null
        rv.fetch { ok = it }; rv.start(1) { }
        assertEquals(false, ok); assertEquals(1L, rv.long("k", 0))
    }

    @Test fun builderKeepsOnlySupportedValueTypesAndHasTheDefaultAdsKey() {
        val c = AdsConfig.builder().remoteDefault("a", "x").remoteDefault("b", 2).remoteDefault("c", listOf(1))
            .remoteDefaults(mapOf("d" to true)).remoteFetchIntervalSeconds(30).build()
        assertEquals(setOf("a", "b", "d"), c.remoteDefaults.keys); assertEquals(30L, c.remoteFetchIntervalSeconds)
        assertEquals("gamechief_ads_config", c.remoteConfigKey)
        assertEquals(null, AdsConfig.builder().remoteConfigKey(null).build().remoteConfigKey)
    }
}

package com.gcl.gamechiefads

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Android side of [RemoteUrlConfig]: where the last good copy is kept and how the file is downloaded. */
internal object RemoteUrlAndroid {

    fun store(context: Context): RemoteUrlConfig.Store {
        val prefs = context.getSharedPreferences("gamechiefads_remote_url", Context.MODE_PRIVATE)
        return object : RemoteUrlConfig.Store {
            override fun get(key: String): String? = prefs.getString(key, null)
            override fun put(key: String, value: String?) {
                prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
            }
        }
    }

    /** One GET, short timeouts, a size limit. No user data is sent (no ids, no app details). */
    val http = RemoteUrlConfig.Http { url, etag ->
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 5_000
            c.readTimeout = 5_000
            c.instanceFollowRedirects = true
            c.setRequestProperty("Accept", "application/json")
            if (etag != null) c.setRequestProperty("If-None-Match", etag)
            val code = c.responseCode
            if (code != 200) {
                RemoteUrlConfig.Response(code, null, null)
            } else {
                RemoteUrlConfig.Response(code, c.inputStream.use { readLimited(it, 128 * 1024 + 1) }, c.getHeaderField("ETag"))
            }
        } finally {
            c.disconnect()
        }
    }

    private fun readLimited(input: InputStream, max: Int): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) {
            val n = input.read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

package io.github.notoboto.unison.sources

import android.util.Log
import io.github.notoboto.unison.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min

/**
 * The real host for plugins on the phone (SOURCES_DESIGN.md §5.3):
 * - fetch only to the plugin's `domains`, honest User-Agent, the plugin's `rateLimit` (token bucket per plugin);
 * - 429/503: waits Retry-After with exponential back-off, after 3 failures RATE_LIMITED;
 * - 418 or a maintenance page: UNAVAILABLE (no retries); 403 with Cf-Mitigated: challenge: NEEDS_BROWSER;
 * - storage: a small JSON file per plugin (≤ 1 MB), no secrets in it (§13).
 * - login token, browser UA and cookies from [auth] (§19.2), added by the host: plugins never see them.
 * Rate-limit waits happen outside JavaScript, so they do not count against the 30 s JS limit (§4).
 */
class HttpHostIo(
    http: OkHttpClient,
    private val storageDir: File,
    private val auth: SourceAuth? = null,
) : HostIo {
    // Redirects are followed by hand: every hop must stay within the plugin's domains, and login cookies / token are
    // computed for each hop's own URL (OkHttp would resend a hand-set Cookie header to whatever host a redirect names).
    private val http = http.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val buckets = ConcurrentHashMap<String, Bucket>()
    private val storageLocks = ConcurrentHashMap<String, Mutex>()

    override suspend fun fetch(plugin: PluginInfo, url0: String, method0: String, headers: Map<String, String>, body0: String?): FetchResult {
        if (!hostAllowed(url0, plugin.domains)) throw PluginException("ENGINE", "fetch to a domain outside ${plugin.domains}: $url0")
        var url = url0; var method = method0; var body = body0
        var attempt = 0
        var hops = 0
        while (true) {
            val hostHeaders = (auth?.headersFor(plugin, url) ?: emptyMap()).let { h ->
                // a plugin may pin a site preference with its own cookie (Author.Today: mobile-mode=false); it is merged
                // into the login cookies, the plugin's value winning
                val own = headers.entries.firstOrNull { it.key.equals("Cookie", true) }?.value
                val host = h["Cookie"]
                if (own != null && host != null) h + ("Cookie" to mergeCookies(host, own)) else h
            }
            buckets.getOrPut(plugin.id) { Bucket(plugin.rateLimit) }.take()
            val req = Request.Builder().url(url)
                .header("User-Agent", USER_AGENT)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .apply { hostHeaders.forEach { (k, v) -> header(k, v) } }
                .method(method.uppercase(), body?.toRequestBody() ?: if (method.equals("POST", true)) ByteArray(0).toRequestBody() else null)
                .build()
            val r = withContext(Dispatchers.IO) {
                http.newCall(req).execute().use { resp ->
                    if (auth != null && auth.usesCookies(plugin)) auth.storeCookies(resp.request.url.toString(), resp.headers("Set-Cookie"))
                    val text = resp.body?.string() ?: ""     // charset from Content-Type: plugins always get a string
                    // every response header except cookies (a plugin may ask for its own, e.g. Author.Today's Reader-Secret, §5.3)
                    val h = buildMap { for (name in resp.headers.names()) if (!name.equals("Set-Cookie", true)) put(name.lowercase(), resp.headers(name).joinToString(", ")) }
                    Triple(resp.code, h, FetchResult(resp.code, h, text, resp.request.url.toString()))
                }
            }
            val (code, h, result) = r
            if (code in REDIRECTS && h["location"] != null) {
                val next = url.toHttpUrlOrNull()?.resolve(h["location"]!!)?.toString()
                    ?: throw PluginException("CHANGED_LAYOUT", "bad redirect from $url", url)
                if (++hops > 5) throw PluginException("CHANGED_LAYOUT", "too many redirects", url0)
                if (!hostAllowed(next, plugin.domains)) throw PluginException("ENGINE", "redirect to a domain outside ${plugin.domains}: $next", url0)
                if (code == 303 || ((code == 301 || code == 302) && !method.equals("GET", true) && !method.equals("HEAD", true))) { method = "GET"; body = null }
                url = next
                continue
            }
            when {
                code == 418 || (code == 503 && MAINTENANCE.containsMatchIn(result.text.take(4000))) ->
                    throw PluginException("UNAVAILABLE", "${plugin.name}: site is down for maintenance ($code)", url)
                code == 403 && h["cf-mitigated"] == "challenge" ->
                    throw PluginException("NEEDS_BROWSER", "${plugin.name}: the site asks for a browser check", url)
                code == 429 || code == 503 -> {
                    if (++attempt > 3) throw PluginException("RATE_LIMITED", "${plugin.name}: HTTP $code after 3 retries", url)
                    val retryAfter = h["retry-after"]?.trim()?.toLongOrNull()
                    delay(min(retryAfter?.times(1000) ?: (2000L shl attempt), 120_000))
                }
                else -> return result
            }
        }
    }

    /**
     * Downloads a picture of a chapter for the host (not for the plugin): same domains, rate limit, login and browser
     * state as the plugin's requests, plus the site as Referer (RanobeLib answers 403 without it). False on failure.
     */
    suspend fun download(plugin: PluginInfo, url: String, to: File): Boolean {
        var cur = url
        repeat(6) {
            if (!hostAllowed(cur, plugin.domains)) return false
            val hostHeaders = runCatching { auth?.headersFor(plugin, cur) }.getOrNull() ?: emptyMap()
            buckets.getOrPut(plugin.id) { Bucket(plugin.rateLimit) }.take()
            val req = Request.Builder().url(cur).header("User-Agent", USER_AGENT).header("Referer", plugin.site.trimEnd('/') + "/")
                .apply { hostHeaders.forEach { (k, v) -> header(k, v) } }.build()
            val next: String? = withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(req).execute().use { r ->
                        if (r.code in REDIRECTS) return@use r.header("Location")?.let { req.url.resolve(it)?.toString() }
                        if (!r.isSuccessful || r.header("Content-Type")?.startsWith("image/") != true) return@use null
                        to.parentFile?.mkdirs()
                        val tmp = File.createTempFile(to.name, ".part", to.parentFile)
                        r.body!!.byteStream().use { inp -> tmp.outputStream().use { inp.copyTo(it) } }
                        if (tmp.renameTo(to) || (to.delete() && tmp.renameTo(to))) "" else { tmp.delete(); null }
                    }
                }.getOrNull()
            }
            when {
                next == null -> return false
                next.isEmpty() -> return true      // saved
                else -> cur = next                 // redirect: checked against the domains on the next round
            }
        }
        return false
    }

    override suspend fun storageGet(plugin: PluginInfo, key: String): JsonElement =
        lockFor(plugin).withLock { readStore(plugin)[key] ?: JsonNull }

    override suspend fun storageSet(plugin: PluginInfo, key: String, value: JsonElement) = lockFor(plugin).withLock {
        val map = readStore(plugin).toMutableMap()
        if (value is JsonNull) map.remove(key) else map[key] = value
        val text = JsonObject(map).toString()
        if (text.length > STORAGE_LIMIT) throw PluginException("ENGINE", "${plugin.id}: storage is over ${STORAGE_LIMIT / 1024} KB")
        withContext(Dispatchers.IO) {
            storageDir.mkdirs()
            val f = storeFile(plugin)
            val tmp = File(f.path + ".tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }

    override fun log(plugin: PluginInfo, level: String, message: String) {
        Log.i("Plugin/${plugin.id}", message.take(2000))
    }

    private fun lockFor(p: PluginInfo) = storageLocks.getOrPut(p.id) { Mutex() }
    private fun storeFile(p: PluginInfo) = File(storageDir, "${p.id}.json")
    private suspend fun readStore(p: PluginInfo): Map<String, JsonElement> = withContext(Dispatchers.IO) {
        val f = storeFile(p)
        if (!f.exists()) emptyMap() else runCatching { pluginJson.parseToJsonElement(f.readText()).jsonObject.toMap() }.getOrDefault(emptyMap())
    }

    /** Token bucket: `burst` requests at once, then one every 1/rps seconds. */
    private class Bucket(limit: RateLimit) {
        private val intervalMs = (1000.0 / limit.rps).toLong().coerceAtLeast(1)
        private val capacity = limit.burst.coerceAtLeast(1)
        private var tokens = capacity.toDouble()
        private var last = System.nanoTime()
        private val mutex = Mutex()

        suspend fun take() {
            while (true) {
                val wait = mutex.withLock {
                    val now = System.nanoTime()
                    tokens = min(capacity.toDouble(), tokens + (now - last) / 1e6 / intervalMs)
                    last = now
                    if (tokens >= 1) { tokens -= 1; 0L } else ((1 - tokens) * intervalMs).toLong() + 1
                }
                if (wait == 0L) return
                delay(wait)
            }
        }
    }

    companion object {
        val USER_AGENT = "Unison/${BuildConfig.VERSION_NAME} (https://github.com/NotoBoto/unison)"
        private const val STORAGE_LIMIT = 1024 * 1024
        private val MAINTENANCE = Regex("down for maintenance|maintenance mode|на техническом обслуживании", RegexOption.IGNORE_CASE)

        private val REDIRECTS = setOf(301, 302, 303, 307, 308)

        /** "a=1; b=2" + "b=3" → "a=1; b=3". */
        fun mergeCookies(base: String, over: String): String {
            val m = LinkedHashMap<String, String>()
            for (part in (base.split(';') + over.split(';'))) {
                val p = part.trim(); if (p.isEmpty()) continue
                m[p.substringBefore('=').trim()] = p
            }
            return m.values.joinToString("; ")
        }

        fun hostAllowed(url: String, domains: List<String>): Boolean {
            val host = url.toHttpUrlOrNull()?.host ?: return false
            return domains.any { host == it || host.endsWith(".$it") }
        }
    }
}

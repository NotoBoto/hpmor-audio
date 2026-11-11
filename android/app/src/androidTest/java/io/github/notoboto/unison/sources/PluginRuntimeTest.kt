package io.github.notoboto.unison.sources

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The golden cases of NotoBoto/unison-sources (assets/golden), run through the real Kotlin bridge (§19.1)
 * on quickjs-kt (quickjs-ng 0.12.1): every call must return exactly what quickjs-ng returns in Node, Python and V8.
 * This closes the last engine risk of SOURCES_DESIGN.md §17.
 */
@RunWith(AndroidJUnit4::class)
class PluginRuntimeTest {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private fun asset(name: String) = assets.open("golden/$name").bufferedReader().use { it.readText() }

    /** Serves recorded responses by URL, like tests/driver.js does. */
    private class FixtureIo(private val fixtures: JsonObject) : HostIo {
        val missing = mutableListOf<String>()
        private val store = mutableMapOf<String, JsonElement>()
        override suspend fun fetch(plugin: PluginInfo, url: String, method: String, headers: Map<String, String>, body: String?): FetchResult {
            val r = fixtures[url]?.jsonObject ?: run { missing += url; throw PluginException("FIXTURE_MISSING", url) }
            val h = (r["headers"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
            return FetchResult(r["status"]!!.jsonPrimitive.int, h, r["text"]!!.jsonPrimitive.content, url)
        }
        override suspend fun storageGet(plugin: PluginInfo, key: String) = store[key] ?: JsonNull
        override suspend fun storageSet(plugin: PluginInfo, key: String, value: JsonElement) { store[key] = value }
        override fun log(plugin: PluginInfo, level: String, message: String) {}
    }

    private fun runCase(name: String) = runBlocking<Unit> {
        val case = pluginJson.parseToJsonElement(asset("$name.case.json")).jsonObject
        val expected = pluginJson.parseToJsonElement(asset("$name.expected.json")).jsonObject
        val io = FixtureIo(case["fixtures"]!!.jsonObject)
        PluginRuntime.load(asset("wikisource-ru.js"), io).use { rt ->
            assertEquals(expected["manifest"]!!.jsonObject["id"], pluginJson.parseToJsonElement(pluginJson.encodeToString(PluginInfo.serializer(), rt.manifest)).jsonObject["id"])
            assertEquals(expected["core"]!!.jsonObject["version"]!!.jsonPrimitive.content, rt.coreVersion)
            val calls = case["calls"]!!.jsonArray
            val want = expected["results"]!!.jsonArray
            assertEquals(want.size, calls.size)
            for (i in calls.indices) {
                val c = calls[i].jsonObject
                val fn = c["fn"]!!.jsonPrimitive.content
                val got: JsonElement = try {
                    JsonObject(mapOf("ok" to rt.call(fn, (c["args"] as JsonArray).toString())))
                } catch (e: PluginException) {
                    JsonObject(mapOf("err" to pluginJson.encodeToJsonElement(PluginErrorInfo.serializer(), e.info)))
                }
                assertEquals("$name: call $i $fn", want[i].jsonObject["result"], got)
            }
            assertTrue("$name: missing fixtures ${io.missing}", io.missing.isEmpty())
        }
    }

    @Test fun goldenHero() = runCase("hero")
    @Test fun goldenSingleAndNested() = runCase("single-and-nested")

    @Test fun javascriptTimeoutIsEnforced() = runBlocking<Unit> {
        val code = """globalThis.__unison = { manifest: ${minimalManifest()}, core: { version: "t" },
            call(id, fn, a) { for (;;) {} } };"""
        PluginRuntime.load(code, FixtureIo(JsonObject(emptyMap())), PluginRuntime.Limits(jsTimeMillis = 1000)).use { rt ->
            try { rt.call("details", "[\"x\"]"); fail("no timeout") } catch (e: PluginException) { assertEquals("TIMEOUT", e.info.code) }
        }
    }

    @Test fun memoryLimitIsEnforced() = runBlocking<Unit> {
        val code = """globalThis.__unison = { manifest: ${minimalManifest()}, core: { version: "t" },
            call(id, fn, a) { const x = []; for (;;) x.push(new Array(100000).fill(1.5)); } };"""
        PluginRuntime.load(code, FixtureIo(JsonObject(emptyMap())), PluginRuntime.Limits(memoryBytes = 32L shl 20)).use { rt ->
            try { rt.call("details", "[\"x\"]"); fail("no error") } catch (e: PluginException) { assertEquals("ENGINE", e.info.code) }
        }
    }

    @Test fun waitsForHostLongerThanTheJsLimit() = runBlocking<Unit> {
        // A slow server must not trip the 30 s JS limit: only time spent running JS counts (§4).
        val slow = object : HostIo by FixtureIo(JsonObject(emptyMap())) {
            override suspend fun fetch(plugin: PluginInfo, url: String, method: String, headers: Map<String, String>, body: String?): FetchResult {
                kotlinx.coroutines.delay(2500)
                return FetchResult(200, emptyMap(), "ok", url)
            }
        }
        PluginRuntime.load(asset("wikisource-ru.js"), slow, PluginRuntime.Limits(jsTimeMillis = 1000)).use { rt ->
            try { rt.call("details", "[\"Слово\"]") } catch (e: PluginException) {
                // "ok" is not JSON, so the plugin reports CHANGED_LAYOUT — the point is that it is not TIMEOUT
                assertEquals("CHANGED_LAYOUT", e.info.code)
            }
        }
    }

    /** Live: the real HttpHostIo (OkHttp, User-Agent, rate limit) against ru.wikisource.org. Needs internet. */
    @Test fun liveWikisourceSearch() = runBlocking<Unit> {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val io = HttpHostIo(OkHttpClient(), File(ctx.cacheDir, "plugin-test-storage"))
        PluginRuntime.load(asset("wikisource-ru.js"), io).use { rt ->
            val r = rt.call("search", "[\"Хамелеон\", 1]").jsonObject
            assertTrue("no search results", r["items"]!!.jsonArray.isNotEmpty())
        }
    }

    private fun minimalManifest() = """{"id":"t","name":"t","version":"0","api":1,"lang":"ru","site":"https://example.org",
        "domains":["example.org"],"rateLimit":{"rps":1,"burst":1}}"""
}

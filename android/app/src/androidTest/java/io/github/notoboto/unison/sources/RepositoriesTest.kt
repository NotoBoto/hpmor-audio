package io.github.notoboto.unison.sources

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Signed repositories, installing from the APK and the network, tamper checks and the instance pool (§6, §8.1). */
@RunWith(AndroidJUnit4::class)
class RepositoriesTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(ctx.cacheDir, "sources-test")
    private val http = OkHttpClient()
    private fun repos() = PluginRepositories(ctx, http, root)
    private fun asset(path: String) = ctx.assets.open("sources/unison-sources/$path").use { it.readBytes() }

    @Before fun clean() { root.deleteRecursively() }

    @Test fun builtInIndexSignatureIsValid() {
        assertTrue(IndexSignature.verify(asset("index.json"), String(asset("index.json.sig"))))
    }

    @Test fun tamperedIndexOrForeignKeyIsRejected() {
        val idx = asset("index.json"); val sig = String(asset("index.json.sig"))
        assertFalse(IndexSignature.verify(idx + " ".toByteArray(), sig))
        assertFalse(IndexSignature.verify(idx, sig, keys = listOf("M+HRrE4huKpomrk9cvI292wfvqZe0q9XarsKP8rUPxA=")))   // spare key did not sign it
        assertFalse(IndexSignature.verify(idx, "not base64 !"))
    }

    @Test fun installsBuiltInsOfflineAndDetectsTampering() = runBlocking<Unit> {
        val r = repos()
        r.ensureBuiltIns()
        val inst = r.installed()
        assertTrue("wikisource-ru installed from assets", "wikisource-ru" in inst)
        assertEquals(PluginRepositories.BUILTIN_URL, inst["wikisource-ru"]!!.repo)
        assertTrue(r.code("wikisource-ru").contains("wikisource-ru"))
        assertTrue(r.iconFile("wikisource-ru").length() > 0)
        File(root, "plugins/wikisource-ru.js").appendText("\n// tampered")
        try { r.code("wikisource-ru"); fail("tampered plugin accepted") } catch (e: PluginRepositories.RepoException) { assertTrue(e.message!!.contains("sha256") || e.message!!.contains("size")) }
    }

    @Test fun builtInRepositoryOnlineIsVerifiedAndCompatible() = runBlocking<Unit> {
        val errors = mutableMapOf<String, String>()
        val list = repos().repos(errors)
        assertTrue("errors: $errors", errors.isEmpty())
        val builtIn = list.single { it.builtIn }
        assertFalse("fetched from the network", builtIn.fromCache)
        assertTrue(builtIn.compatible.any { it.id == "wikisource-ru" })
    }

    @Test fun foreignRepositoryIsRefused() = runBlocking<Unit> {
        try { repos().addRepo("https://example.org/"); fail("unsigned repository accepted") } catch (e: Exception) { /* expected */ }
        assertEquals(listOf(PluginRepositories.BUILTIN_URL), repos().repoUrls())
    }

    @Test fun sourcesCallInstalledPluginLive() = runBlocking<Unit> {
        val r = repos(); r.ensureBuiltIns()
        val io = HttpHostIo(http, r.storageDir)
        val pool = PluginPool({ id -> PluginRuntime.load(r.code(id), io) })
        val res = pool.call("wikisource-ru", "search", Sources.encodeArgs(listOf("Хамелеон", 1))).jsonObject
        assertTrue(res["items"]!!.jsonArray.isNotEmpty())
        pool.unloadAll()
    }

    @Test fun poolKeepsAtMostFourAndUnloadsIdle() = runBlocking<Unit> {
        fun code(id: String) = """globalThis.__unison = { manifest: {"id":"$id","name":"$id","version":"1","api":1,"lang":"ru",
            "site":"https://example.org","domains":["example.org"],"rateLimit":{"rps":1,"burst":1}}, core: {version: "t"},
            call(c, fn, a) { __host_done(c, JSON.stringify({ok: "$id"})); } };"""
        val io = HttpHostIo(http, root)
        val pool = PluginPool(loader = { id -> PluginRuntime.load(code(id), io) }, maxLive = 4, idleMillis = 1500)
        for (id in listOf("a", "b", "c", "d", "e")) assertEquals(id, pool.call(id, "details", "[]").toString().trim('"'))
        assertEquals(listOf("b", "c", "d", "e"), pool.liveIds())   // "a" was the least recently used
        pool.call("b", "details", "[]")
        assertEquals(listOf("c", "d", "e", "b"), pool.liveIds())
        delay(2500)
        assertTrue("idle instances unloaded: ${pool.liveIds()}", pool.liveIds().isEmpty())
    }
}

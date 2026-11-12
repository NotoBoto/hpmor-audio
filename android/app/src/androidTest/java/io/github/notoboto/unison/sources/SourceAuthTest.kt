package io.github.notoboto.unison.sources

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Logins of sources (SOURCES_DESIGN.md §19.2): token from localStorage, encrypted storage, headers, browser state. */
@RunWith(AndroidJUnit4::class)
class SourceAuthTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val jar = mutableMapOf<String, String>()
    private val cookies = object : SourceAuth.CookieStore {
        override fun get(url: String) = jar[url]
        override fun set(url: String, setCookie: String) { jar[url] = setCookie.substringBefore(';') }
        override fun clear(urls: List<String>) { urls.forEach { jar.remove(it) } }
    }
    private val file = File(ctx.noBackupFilesDir, "source-auth.bin")
    private val ranobelib = PluginInfo(id = "ranobelib-test", name = "RanobeLib", version = "1", api = 1, lang = "ru", site = "https://ranobelib.me",
        domains = listOf("api.cdnlibs.org", "ranobelib.me"), rateLimit = RateLimit(1.0, 3),
        login = PluginLogin(url = "https://ranobelib.me/ru/front/auth", kind = "localStorage", key = "auth", path = "/token/access_token", header = "Authorization: Bearer {token}"))

    @After fun clean() { SourceAuth(ctx, cookies).logout(ranobelib) }

    /** The value RanobeLib keeps in localStorage.auth (shape checked 12.11.2026), with an unsigned JWT that expires in [inSec]. */
    private fun authValue(inSec: Long): String {
        val b64 = { s: String -> android.util.Base64.encodeToString(s.toByteArray(), android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP) }
        val jwt = b64("""{"alg":"none"}""") + "." + b64("""{"sub":"1","exp":${System.currentTimeMillis() / 1000 + inSec}}""") + ".sig"
        return """{"token":{"token_type":"Bearer","expires_in":604800,"access_token":"$jwt","refresh_token":"r","timestamp":1}, "auth":{"id":1,"username":"u"}}"""
    }

    @Test fun tokenFromLocalStorageGoesIntoHeaders() {
        val auth = SourceAuth(ctx, cookies)
        assertFalse(auth.saveLocalStorage(ranobelib, null))
        assertFalse("not logged in yet", auth.saveLocalStorage(ranobelib, """{"token":{}}"""))
        assertFalse(auth.loggedIn(ranobelib))
        assertTrue(auth.headersFor(ranobelib, "https://api.cdnlibs.org/api/manga").isEmpty())

        val raw = authValue(3600)
        assertTrue(auth.saveLocalStorage(ranobelib, raw))
        assertTrue(auth.loggedIn(ranobelib))
        val token = SourceAuth.tokenFrom(raw, "/token/access_token")!!
        assertEquals(mapOf("Authorization" to "Bearer $token"), auth.headersFor(ranobelib, "https://api.cdnlibs.org/api/manga"))
        val exp = auth.entry(ranobelib.id).expires!!
        assertTrue("expiry from the JWT", exp > System.currentTimeMillis() && exp <= System.currentTimeMillis() + 3600_000)

        // a fresh instance reads the encrypted file; the token is not stored in clear text
        assertEquals(token, SourceAuth(ctx, cookies).entry(ranobelib.id).token)
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains(token.take(20)))
    }

    @Test fun expiredTokenAsksToLogIn() {
        val auth = SourceAuth(ctx, cookies)
        assertTrue(auth.saveLocalStorage(ranobelib, authValue(-60)))
        assertFalse(auth.loggedIn(ranobelib))
        try { auth.headersFor(ranobelib, "https://api.cdnlibs.org/x"); fail("must throw") }
        catch (e: PluginException) { assertEquals("NEEDS_LOGIN", e.info.code) }
    }

    @Test fun browserCheckAddsUaAndCookies() {
        val always = ranobelib.copy(id = "rr-test", browser = "always", login = null)
        val auth = SourceAuth(ctx, cookies)
        try {
            try { auth.headersFor(always, "https://ranobelib.me/x"); fail("must throw") }
            catch (e: PluginException) { assertEquals("NEEDS_BROWSER", e.info.code); assertEquals(always.site, e.info.url) }
            auth.saveBrowser(always.id, "Mozilla/5.0 (Linux; Android 16; wv)")
            auth.storeCookies("https://ranobelib.me/x", listOf("cf_clearance=abc; Path=/; Secure"))
            assertEquals(mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 16; wv)", "Cookie" to "cf_clearance=abc"), auth.headersFor(always, "https://ranobelib.me/x"))
            auth.logout(always)
            assertNull(auth.entry(always.id).browserUa)
        } finally { auth.logout(always) }
    }

    @Test fun pointersAndTemplates() {
        assertEquals("Authorization" to "Bearer {token}", SourceAuth.headerTemplate(null))
        assertEquals("X-Token" to "{token}", SourceAuth.headerTemplate("X-Token: {token}"))
        assertEquals("t", SourceAuth.tokenFrom("\"t\"", null))
        assertEquals("v", SourceAuth.tokenFrom("""{"a/b":{"c~d":["x","v"]}}""", "/a~1b/c~0d/1"))
        assertNull(SourceAuth.tokenFrom("not json", "/a"))
        assertEquals(1_700_000_000_000, SourceAuth.expiresFrom(kotlinx.serialization.json.JsonPrimitive(1_700_000_000)))
        assertEquals(1_700_000_000_000, SourceAuth.expiresFrom(kotlinx.serialization.json.JsonPrimitive("2023-11-14T22:13:20Z")))
    }
}

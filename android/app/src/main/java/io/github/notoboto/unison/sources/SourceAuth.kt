package io.github.notoboto.unison.sources

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Login tokens and browser state of sources (SOURCES_DESIGN.md §5.1, §8.2, §13, §19.2).
 *
 * - Tokens taken from the site's localStorage after a WebView login are encrypted with an Android Keystore key
 *   (AES-GCM, never leaves the device) and kept in `noBackupFilesDir`: not in Auto Backup, not in Unison backups.
 * - After a browser check (or for `browser: always`) requests of the source carry the WebView's User-Agent and cookies
 *   (`cf_clearance` is bound to the UA). Cookies stay in the WebView cookie store, which backup rules exclude.
 * - Plugins never see any of it: the host adds the headers (§5.3).
 */
class SourceAuth(context: Context, private val cookies: CookieStore = WebViewCookies) {
    @Serializable
    data class Entry(
        val token: String? = null,
        val expires: Long? = null,        // epoch ms, when known (expiresPath or the JWT "exp")
        val browserUa: String? = null,    // set after a browser check in the WebView
        val cookieLogin: Boolean = false, // login.kind = cookies done
    )

    interface CookieStore {
        fun get(url: String): String?
        fun set(url: String, setCookie: String)
        fun clear(urls: List<String>)
    }

    private val file = File(context.noBackupFilesDir, "source-auth.bin")
    private val lock = Any()
    private var cache: MutableMap<String, Entry>? = null

    /** Bumped on every change: screens retry after a login or a browser check. */
    val changes = MutableStateFlow(0)

    fun entry(pluginId: String): Entry = synchronized(lock) { load()[pluginId] ?: Entry() }

    fun loggedIn(p: PluginInfo): Boolean {
        val e = entry(p.id)
        return when (p.login?.kind) {
            "localStorage" -> e.token != null && !expired(e)
            "cookies" -> e.cookieLogin
            else -> false
        }
    }

    private fun expired(e: Entry) = e.expires != null && e.expires <= System.currentTimeMillis()

    /**
     * Saves the login from the raw `localStorage[login.key]` value. Returns false when there is no token in it
     * (the user has not logged in yet).
     */
    fun saveLocalStorage(p: PluginInfo, raw: String?): Boolean {
        val login = p.login ?: return false
        val token = tokenFrom(raw, login.path) ?: return false
        val expires = login.expiresPath?.let { ptr -> runCatching { expiresFrom(pointer(pluginJson.parseToJsonElement(raw!!), ptr)) }.getOrNull() }
            ?: jwtExpires(token)
        update(p.id) { it.copy(token = token, expires = expires) }
        return true
    }

    fun saveCookieLogin(p: PluginInfo) = update(p.id) { it.copy(cookieLogin = true) }

    fun saveBrowser(pluginId: String, userAgent: String) = update(pluginId) { it.copy(browserUa = userAgent) }

    /** Forgets the login and the browser state; removes the source's cookies. */
    fun logout(p: PluginInfo) {
        synchronized(lock) { val m = load(); m.remove(p.id); save(m) }
        runCatching { cookies.clear((p.domains.map { "https://$it/" } + p.site).distinct()) }
        changes.value++
    }

    /**
     * Headers the host adds to a request of [p] (§5.3, §19.2). Throws NEEDS_LOGIN when the stored token is known to be
     * expired and NEEDS_BROWSER for `browser: always` before the first browser visit.
     */
    fun headersFor(p: PluginInfo, url: String): Map<String, String> {
        val e = entry(p.id)
        val out = LinkedHashMap<String, String>()
        if (p.browser == "always" && e.browserUa == null) throw PluginException("NEEDS_BROWSER", "${p.name}: open the site once in the browser", p.site)
        if (e.browserUa != null) out["User-Agent"] = e.browserUa
        if (e.browserUa != null || e.cookieLogin) cookies.get(url)?.takeIf { it.isNotBlank() }?.let { out["Cookie"] = it }
        val login = p.login
        if (login?.kind == "localStorage" && e.token != null) {
            if (expired(e)) throw PluginException("NEEDS_LOGIN", "${p.name}: login expired", login.url)
            val (name, value) = headerTemplate(login.header).let { (n, v) -> n to v.replace("{token}", e.token) }
            out[name] = value
        }
        return out
    }

    /** Keeps the WebView cookie store in step with the source's responses (session and `cf_clearance` renewals). */
    fun usesCookies(p: PluginInfo): Boolean = entry(p.id).let { it.browserUa != null || it.cookieLogin }

    fun storeCookies(url: String, setCookies: List<String>) { setCookies.forEach { runCatching { cookies.set(url, it) } } }

    private fun update(id: String, f: (Entry) -> Entry) {
        synchronized(lock) { val m = load(); m[id] = f(m[id] ?: Entry()); save(m) }
        changes.value++
    }

    // ---- storage: JSON → AES-GCM with a Keystore key → file ----

    private fun load(): MutableMap<String, Entry> {
        cache?.let { return it }
        val m: MutableMap<String, Entry> = runCatching {
            if (!file.exists()) mutableMapOf() else
                pluginJson.decodeFromString(MapSerializer(String.serializer(), Entry.serializer()), decrypt(file.readBytes())).toMutableMap()
        }.getOrElse { mutableMapOf() }     // a lost key (e.g. restored to another phone) only means logging in again
        cache = m
        return m
    }

    private fun save(m: MutableMap<String, Entry>) {
        cache = m
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeBytes(encrypt(pluginJson.encodeToString(MapSerializer(String.serializer(), Entry.serializer()), m)))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return gen.generateKey()
    }

    private fun encrypt(text: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return byteArrayOf(c.iv.size.toByte()) + c.iv + c.doFinal(text.toByteArray())
    }

    private fun decrypt(data: ByteArray): String {
        val n = data[0].toInt()
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 1, n)) }
        return String(c.doFinal(data, 1 + n, data.size - 1 - n))
    }

    companion object {
        private const val KEY_ALIAS = "unison-source-auth"

        /** "Authorization: Bearer {token}" → ("Authorization", "Bearer {token}"). */
        fun headerTemplate(t: String?): Pair<String, String> {
            val s = t ?: "Authorization: Bearer {token}"
            val i = s.indexOf(':')
            return if (i <= 0) "Authorization" to "Bearer {token}" else s.substring(0, i).trim() to s.substring(i + 1).trim()
        }

        /** The token from a raw localStorage value: the string itself, or JSON + JSON Pointer [path] (RFC 6901). */
        fun tokenFrom(raw: String?, path: String?): String? {
            if (raw.isNullOrBlank() || raw == "null") return null
            if (path.isNullOrEmpty()) return raw.trim().removeSurrounding("\"").takeIf { it.isNotBlank() }
            val el = runCatching { pointer(pluginJson.parseToJsonElement(raw), path) }.getOrNull() ?: return null
            return (el as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }

        fun pointer(root: JsonElement, path: String): JsonElement? {
            if (path.isEmpty()) return root
            var cur: JsonElement? = root
            for (raw in path.removePrefix("/").split('/')) {
                val k = raw.replace("~1", "/").replace("~0", "~")
                cur = when (val c = cur) {
                    is JsonObject -> c[k]
                    is JsonArray -> k.toIntOrNull()?.let { c.getOrNull(it) }
                    else -> null
                }
            }
            return cur
        }

        /** Seconds, milliseconds or ISO 8601 → epoch ms. */
        fun expiresFrom(el: JsonElement?): Long? {
            val p = el as? JsonPrimitive ?: return null
            p.longOrNull?.let { return if (it < 100_000_000_000L) it * 1000 else it }
            return p.contentOrNull?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
        }

        /** "exp" of a JWT access token, when the token is one. */
        fun jwtExpires(token: String): Long? = runCatching {
            val parts = token.split('.')
            if (parts.size != 3) return null
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
            (pluginJson.parseToJsonElement(payload) as JsonObject)["exp"]?.jsonPrimitive?.longOrNull?.times(1000)
        }.getOrNull()
    }
}

/** The WebView cookie store (shared with the login and browser-check screens). */
object WebViewCookies : SourceAuth.CookieStore {
    private val cm get() = android.webkit.CookieManager.getInstance()
    override fun get(url: String): String? = cm.getCookie(url)
    override fun set(url: String, setCookie: String) { cm.setCookie(url, setCookie) }
    override fun clear(urls: List<String>) {
        for (u in urls) {
            val names = cm.getCookie(u)?.split(';')?.mapNotNull { it.substringBefore('=').trim().takeIf(String::isNotEmpty) } ?: continue
            val host = android.net.Uri.parse(u).host ?: continue
            for (n in names) {
                cm.setCookie(u, "$n=; Max-Age=0; Path=/")
                cm.setCookie(u, "$n=; Max-Age=0; Path=/; Domain=$host")
                cm.setCookie(u, "$n=; Max-Age=0; Path=/; Domain=.${host.removePrefix("www.")}")
            }
        }
        cm.flush()
    }
}

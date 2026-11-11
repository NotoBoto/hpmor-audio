package io.github.notoboto.unison.sources

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/**
 * Plugin repositories and installed plugins (SOURCES_DESIGN.md §6, §8.1, §19.3).
 *
 * Files under filesDir/sources/:
 *   repos.json                 added repository URLs (the built-in one is implicit)
 *   installed.json             installed plugins: repository + the signed index entry (sha256, size, …)
 *   plugins/<id>.js            plugin code — checked against sha256 from the signed index on every load
 *   icons/<id>.png
 *   index-cache/<key>.json/.sig  last good index of each repository (works offline)
 * The built-in repository also ships in the APK (assets/sources/unison-sources) and is installed from there.
 */
class PluginRepositories(
    private val context: Context,
    http: OkHttpClient,
    private val root: File = File(context.filesDir, "sources"),
) {
    private val http = http.newBuilder().followRedirects(true).build()
    private val lock = Mutex()

    @Serializable data class RepoInfo(val id: String, val name: String)
    @Serializable data class RepoIndex(val format: Int, val repo: RepoInfo, val generated: String, val plugins: List<PluginInfo>)
    @Serializable data class Installed(val repo: String, val info: PluginInfo, val installedAt: Long)
    @Serializable private data class ReposFile(val repos: List<String> = emptyList())
    @Serializable private data class InstalledFile(val plugins: Map<String, Installed> = emptyMap())

    /** A repository with its verified index; [fromCache] when the network failed and the last good copy is used. */
    data class Repo(val url: String, val index: RepoIndex, val fromCache: Boolean, val builtIn: Boolean) {
        /** Plugins this app can run: same contract api and engine (§4). */
        val compatible get() = index.plugins.filter { it.api == PLUGIN_API && it.engine == PLUGIN_ENGINE }
    }

    class RepoException(message: String) : Exception(message)

    // ---- repositories ----

    fun repoUrls(): List<String> = listOf(BUILTIN_URL) + readRepos().repos.filter { it != BUILTIN_URL }

    /** Adds a repository after checking that it serves an index signed by our keys. */
    suspend fun addRepo(url: String): Repo = lock.withLock {
        val u = normalize(url)
        val repo = fetchRepo(u, useCacheOnFailure = false)
        val current = readRepos()
        if (u != BUILTIN_URL && u !in current.repos) writeRepos(current.copy(repos = current.repos + u))
        repo
    }

    suspend fun removeRepo(url: String) = lock.withLock {
        val u = normalize(url)
        require(u != BUILTIN_URL) { "the built-in repository cannot be removed" }
        writeRepos(ReposFile(readRepos().repos - u))
    }

    /** All repositories with their indexes; a failing repository is skipped (its error is in [errors]). */
    suspend fun repos(errors: MutableMap<String, String>? = null): List<Repo> = repoUrls().mapNotNull { url ->
        runCatching { fetchRepo(url, useCacheOnFailure = true) }.onFailure { errors?.put(url, it.message ?: it.toString()) }.getOrNull()
    }

    // ---- installed plugins ----

    fun installed(): Map<String, Installed> = readInstalled().plugins

    /** Installs or updates [info] from [repoUrl]: downloads, checks size and sha256 from the signed index, saves atomically. */
    suspend fun install(repoUrl: String, info: PluginInfo) = lock.withLock {
        require(info.api == PLUGIN_API && info.engine == PLUGIN_ENGINE) { "${info.id}: needs api ${info.api} / ${info.engine}" }
        checkPaths(info)
        val base = normalize(repoUrl)
        val code = if (base == BUILTIN_URL && assetsHas(info)) readAsset(info.file) else download(base + info.file)
        verifyCode(info, code)
        val icon = runCatching { if (base == BUILTIN_URL && assetsHas(info)) readAsset(info.icon) else download(base + info.icon) }.getOrNull()
        withContext(Dispatchers.IO) {
            atomicWrite(File(root, "plugins/${info.id}.js"), code)
            icon?.let { atomicWrite(File(root, "icons/${info.id}.png"), it) }
        }
        val cur = readInstalled()
        writeInstalled(cur.copy(plugins = cur.plugins + (info.id to Installed(base, info, System.currentTimeMillis()))))
    }

    suspend fun uninstall(id: String) = lock.withLock {
        withContext(Dispatchers.IO) {
            File(root, "plugins/$id.js").delete()
            File(root, "icons/$id.png").delete()
            File(root, "storage/$id.json").delete()
        }
        writeInstalled(InstalledFile(readInstalled().plugins - id))
    }

    /** Plugin code, verified against the signed index entry it was installed from (§8.1). */
    suspend fun code(id: String): String {
        val inst = installed()[id] ?: throw RepoException("$id is not installed")
        val bytes = withContext(Dispatchers.IO) { File(root, "plugins/$id.js").takeIf { it.exists() }?.readBytes() }
            ?: throw RepoException("$id: plugin file is missing")
        verifyCode(inst.info, bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    fun iconFile(id: String): File = File(root, "icons/$id.png")
    val storageDir get() = File(root, "storage")

    /**
     * First start and app updates: installs or updates the plugins that ship in the APK, offline.
     * A plugin already installed in a newer version (from the network) is kept.
     */
    suspend fun ensureBuiltIns() {
        val index = runCatching { assetIndex() }.getOrNull() ?: return
        val inst = installed()
        for (p in index.plugins.filter { it.api == PLUGIN_API && it.engine == PLUGIN_ENGINE }) {
            val have = inst[p.id]
            if (have == null || (have.repo == BUILTIN_URL && compareVersions(p.version, have.info.version) > 0)) install(BUILTIN_URL, p)
        }
    }

    /** Installed plugins with a newer compatible version in their repository (§1.1 decision 16). */
    suspend fun updates(): List<Pair<Installed, PluginInfo>> {
        val byUrl = repos().associateBy { it.url }
        return installed().values.mapNotNull { inst ->
            val newer = byUrl[inst.repo]?.compatible?.firstOrNull { it.id == inst.info.id } ?: return@mapNotNull null
            if (compareVersions(newer.version, inst.info.version) > 0 || newer.sha256 != inst.info.sha256 && newer.version == inst.info.version) inst to newer else null
        }
    }

    // ---- internals ----

    private suspend fun fetchRepo(url: String, useCacheOnFailure: Boolean): Repo {
        val cache = File(root, "index-cache/${sha256Hex(url.toByteArray()).take(16)}")
        val online = runCatching {
            // An index built for our engine version takes precedence over the general one (§19.3).
            val (idx, sig) = runCatching { download(url + "engine/$PLUGIN_ENGINE/index.json") to download(url + "engine/$PLUGIN_ENGINE/index.json.sig") }
                .getOrElse { download(url + "index.json") to download(url + "index.json.sig") }
            val parsed = parseVerified(idx, sig.toString(Charsets.UTF_8))
            withContext(Dispatchers.IO) { atomicWrite(File("$cache.json"), idx); atomicWrite(File("$cache.sig"), sig) }
            parsed
        }
        online.getOrNull()?.let { return Repo(url, it, fromCache = false, builtIn = url == BUILTIN_URL) }
        if (useCacheOnFailure) {
            val cached = withContext(Dispatchers.IO) {
                runCatching { parseVerified(File("$cache.json").readBytes(), File("$cache.sig").readText()) }.getOrNull()
            } ?: if (url == BUILTIN_URL) runCatching { assetIndex() }.getOrNull() else null
            if (cached != null) return Repo(url, cached, fromCache = true, builtIn = url == BUILTIN_URL)
        }
        throw RepoException(online.exceptionOrNull()?.message ?: "cannot read $url")
    }

    private fun parseVerified(index: ByteArray, sig: String): RepoIndex {
        if (!IndexSignature.verify(index, sig)) throw RepoException("index signature is not valid: repository is not signed by Unison")
        val idx = pluginJson.decodeFromString(RepoIndex.serializer(), index.toString(Charsets.UTF_8))
        if (idx.format != 1) throw RepoException("index format ${idx.format} is not supported, update the app")
        return idx
    }

    private suspend fun assetIndex(): RepoIndex = withContext(Dispatchers.IO) {
        parseVerified(readAsset("index.json"), readAsset("index.json.sig").toString(Charsets.UTF_8))
    }

    private fun assetsHas(info: PluginInfo): Boolean = runCatching {
        context.assets.open("$ASSETS/${info.file}").use { true }
    }.getOrDefault(false) && runCatching { verifyCode(info, readAsset(info.file)); true }.getOrDefault(false)

    private fun readAsset(path: String): ByteArray = context.assets.open("$ASSETS/$path").use { it.readBytes() }

    private suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).header("User-Agent", HttpHostIo.USER_AGENT).build()).execute().use { r ->
            if (!r.isSuccessful) throw RepoException("HTTP ${r.code}: $url")
            r.body?.bytes() ?: throw RepoException("empty response: $url")
        }
    }

    private fun verifyCode(info: PluginInfo, code: ByteArray) {
        if (info.size > 0 && code.size.toLong() != info.size) throw RepoException("${info.id}: size ${code.size} ≠ ${info.size} from the signed index")
        if (sha256Hex(code) != info.sha256.lowercase()) throw RepoException("${info.id}: sha256 does not match the signed index")
    }

    private fun checkPaths(info: PluginInfo) {
        if (!ID.matches(info.id)) throw RepoException("bad plugin id ${info.id}")
        if (info.file != "dist/${info.id}.js") throw RepoException("${info.id}: unexpected file ${info.file}")
        if (info.icon.isNotEmpty() && info.icon != "icons/${info.id}.png") throw RepoException("${info.id}: unexpected icon ${info.icon}")
    }

    private fun readRepos() = readJson(File(root, "repos.json"), ReposFile.serializer()) ?: ReposFile()
    private fun writeRepos(v: ReposFile) = atomicWrite(File(root, "repos.json"), pluginJson.encodeToString(ReposFile.serializer(), v).toByteArray())
    private fun readInstalled() = readJson(File(root, "installed.json"), InstalledFile.serializer()) ?: InstalledFile()
    private fun writeInstalled(v: InstalledFile) = atomicWrite(File(root, "installed.json"), pluginJson.encodeToString(InstalledFile.serializer(), v).toByteArray())

    private fun <T> readJson(f: File, s: kotlinx.serialization.KSerializer<T>): T? =
        if (!f.exists()) null else runCatching { pluginJson.decodeFromString(s, f.readText()) }.getOrNull()

    private fun atomicWrite(f: File, bytes: ByteArray) {
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) throw RepoException("cannot write ${f.name}") }
    }

    companion object {
        const val BUILTIN_URL = "https://notoboto.github.io/unison-sources/"
        private const val ASSETS = "sources/unison-sources"
        private val ID = Regex("^[a-z0-9-]{1,64}$")

        fun normalize(url: String): String {
            val u = url.trim()
            require(u.startsWith("https://")) { "repository address must start with https://" }
            return if (u.endsWith("/")) u else u.substringBeforeLast("/index.json").trimEnd('/') + "/"
        }

        fun sha256Hex(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

        /** Numeric dotted-version comparison ("1.10.0" > "1.9.3"); non-numeric parts compare as 0. */
        fun compareVersions(a: String, b: String): Int {
            val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
            val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(x.size, y.size)) {
                val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }
    }
}

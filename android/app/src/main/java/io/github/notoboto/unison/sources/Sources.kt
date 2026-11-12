package io.github.notoboto.unison.sources

import android.content.Context
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient

/**
 * Entry point for book-source plugins: repositories, the network/storage host and the pool of live instances.
 * One per app (UnisonApp). SOURCES_DESIGN.md §4–§8.
 */
class Sources(context: Context, http: OkHttpClient) {
    val repos = PluginRepositories(context.applicationContext, http)
    /** Login tokens, browser UA and cookies of sources (§19.2). */
    val auth = SourceAuth(context.applicationContext)
    val io = HttpHostIo(http, repos.storageDir, auth)
    val pool = PluginPool(loader = { id -> load(id) })
    /** Plugin books on the phone: chapter tables, texts (§8.3, §19.5). */
    val books by lazy { SrcBooks(context.applicationContext, this) }

    private suspend fun load(id: String): PluginRuntime {
        val inst = repos.installed()[id] ?: throw PluginException("NOT_FOUND", "plugin $id is not installed")
        val rt = PluginRuntime.load(repos.code(id), io)
        // the bundle's own manifest must agree with the signed index entry it was installed from
        if (rt.manifest.id != id || rt.manifest.version != inst.info.version) {
            rt.close()
            throw PluginException("ENGINE", "$id: bundle manifest ${rt.manifest.id} ${rt.manifest.version} ≠ index ${inst.info.version}")
        }
        return rt
    }

    /** Typed calls (§5.2). */
    suspend fun search(id: String, query: String, page: Int, filters: JsonElement? = null) =
        decode(BookPage.serializer(), if (filters == null) call(id, "search", query, page) else call(id, "search", query, page, filters))
    suspend fun popular(id: String, page: Int, filters: JsonElement? = null) =
        decode(BookPage.serializer(), if (filters == null) call(id, "popular", page) else call(id, "popular", page, filters))
    suspend fun filters(id: String) = decode(kotlinx.serialization.builtins.ListSerializer(SourceFilter.serializer()), call(id, "filters"))
    suspend fun latest(id: String, page: Int) = decode(BookPage.serializer(), call(id, "latest", page))
    suspend fun details(id: String, bookId: String) = decode(BookDetails.serializer(), call(id, "details", bookId))
    suspend fun chapters(id: String, bookId: String) = decode(kotlinx.serialization.builtins.ListSerializer(ChapterStub.serializer()), call(id, "chapters", bookId))
    suspend fun chapter(id: String, bookId: String, chapterId: String) = decode(ChapterContent.serializer(), call(id, "chapter", bookId, chapterId))

    private fun <T> decode(s: kotlinx.serialization.KSerializer<T>, e: JsonElement): T =
        runCatching { pluginJson.decodeFromJsonElement(s, e) }.getOrElse { throw PluginException("CHANGED_LAYOUT", "unexpected result: ${it.message}") }

    /** Calls a plugin function; [args] are encoded as a JSON array. */
    suspend fun call(pluginId: String, fn: String, vararg args: Any?): JsonElement =
        pool.call(pluginId, fn, encodeArgs(args.toList()))

    /** After install/update/uninstall: the next call loads the new code. */
    suspend fun reload(pluginId: String) = pool.unload(pluginId)

    /** Bumped after every change of repositories or installed plugins, for screens to refresh. */
    val changes = kotlinx.coroutines.flow.MutableStateFlow(0)

    suspend fun install(repoUrl: String, info: PluginInfo) {
        repos.install(repoUrl, info); reload(info.id); changes.value++
    }

    suspend fun uninstall(pluginId: String) {
        reload(pluginId); repos.uninstall(pluginId); changes.value++
    }

    suspend fun addRepo(url: String) = repos.addRepo(url).also { changes.value++ }

    suspend fun removeRepo(url: String) { repos.removeRepo(url); changes.value++ }

    /** Installs every available update (decision 16: same api and engine only — the index filter guarantees it). */
    suspend fun updateAll(): List<String> {
        val done = mutableListOf<String>()
        for ((inst, newer) in repos.updates()) {
            runCatching { install(inst.repo, newer) }.onSuccess { done += newer.id }
        }
        return done
    }

    companion object {
        fun encodeArgs(args: List<Any?>): String = kotlinx.serialization.json.JsonArray(args.map {
            when (it) {
                null -> kotlinx.serialization.json.JsonNull
                is JsonElement -> it
                is Number -> kotlinx.serialization.json.JsonPrimitive(it)
                is Boolean -> kotlinx.serialization.json.JsonPrimitive(it)
                else -> kotlinx.serialization.json.JsonPrimitive(it.toString())
            }
        }).toString()
    }
}

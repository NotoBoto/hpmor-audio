package io.github.notoboto.unison.sources

import android.content.Context
import io.github.notoboto.unison.data.TextBooks
import io.github.notoboto.unison.data.json
import io.github.notoboto.unison.data.pluginSourceUrl
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Book sources in the app backup (SOURCES_DESIGN.md §13): a "sources" section next to the app state.
 *
 * In: added repositories, installed plugins (id, repository, version — the code itself is downloaded again and
 * checked against the signed index), plugin storage, plugin books (meta, origin, chapter table, index).
 * Not in: chapter texts and audio (downloaded again), cookies, login tokens, private-source tokens.
 * Older app versions ignore the section (the state is read with ignoreUnknownKeys).
 */
object SourcesBackup {
    const val KEY = "sources"
    private const val FORMAT = 1
    private val BOOK_FILES = listOf("meta.json", "origin.json", "chapters.json", "index.json")

    fun export(ctx: Context, s: Sources): JsonObject = buildJsonObject {
        put("format", FORMAT)
        put("repos", buildJsonArray { s.repos.repoUrls().filter { it != PluginRepositories.BUILTIN_URL }.forEach { add(JsonPrimitive(it)) } })
        put("plugins", buildJsonArray {
            s.repos.installed().values.forEach { i ->
                add(buildJsonObject { put("id", i.info.id); put("repo", i.repo); put("version", i.info.version) })
            }
        })
        put("storage", buildJsonObject {
            s.repos.storageDir.listFiles()?.filter { it.name.endsWith(".json") }?.forEach { f ->
                runCatching { json.parseToJsonElement(f.readText()) }.getOrNull()?.let { put(f.name.removeSuffix(".json"), it) }
            }
        })
        put("books", buildJsonArray {
            File(ctx.filesDir, "srcbooks").listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
                pluginDir.listFiles()?.filter { it.isDirectory }?.forEach { bookDir ->
                    add(buildJsonObject {
                        put("plugin", pluginDir.name); put("id", bookDir.name)
                        for (name in BOOK_FILES) {
                            val f = File(bookDir, name)
                            if (f.exists()) runCatching { json.parseToJsonElement(f.readText()) }.getOrNull()?.let { put(name, it) }
                        }
                    })
                }
            }
        })
    }

    /** Restores the section; plugins are reinstalled from their repositories (or the APK when offline). Returns problems. */
    suspend fun restore(ctx: Context, s: Sources, section: JsonObject): List<String> {
        val problems = mutableListOf<String>()
        if ((section["format"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0) > FORMAT) return listOf("копия из более новой версии приложения: источники пропущены")

        // plugin books first: they show in the library even before their plugins are back
        for (b in section["books"]?.jsonArray.orEmpty()) {
            val o = b.jsonObject
            val plugin = o["plugin"]?.jsonPrimitive?.contentOrNull ?: continue
            val id = o["id"]?.jsonPrimitive?.contentOrNull ?: continue
            if (!SAFE.matches(plugin) || !SAFE.matches(id)) continue
            val dir = TextBooks.dir(ctx, pluginSourceUrl(plugin), id)
            for (name in BOOK_FILES) o[name]?.let { TextBooks.atomicWrite(File(dir, name), it.toString()) }
        }
        // storage (no secrets in it by contract, §5.3)
        section["storage"]?.jsonObject?.forEach { (id, v) ->
            if (SAFE.matches(id)) TextBooks.atomicWrite(File(s.repos.storageDir, "$id.json"), v.toString())
        }
        // repositories: only ones still signed by our keys get back in
        for (url in section["repos"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull }) {
            runCatching { s.addRepo(url) }.onFailure { problems += "репозиторий $url: ${it.message}" }
        }
        // plugins: the version from the repository now (code is never taken from the backup)
        s.repos.ensureBuiltIns()
        val wanted = section["plugins"]?.jsonArray.orEmpty().map { it.jsonObject }
        val have = s.repos.installed()
        if (wanted.any { it["id"]?.jsonPrimitive?.contentOrNull !in have }) {
            val repos = runCatching { s.repos.repos() }.getOrDefault(emptyList()).associateBy { it.url }
            for (p in wanted) {
                val id = p["id"]?.jsonPrimitive?.contentOrNull ?: continue
                if (id in s.repos.installed()) continue
                val repo = p["repo"]?.jsonPrimitive?.contentOrNull ?: continue
                val info = repos[repo]?.compatible?.find { it.id == id }
                if (info == null) { problems += "расширение $id: нет в репозитории или нет связи"; continue }
                runCatching { s.install(repo, info) }.onFailure { problems += "расширение $id: ${it.message}" }
            }
        }
        s.changes.value++
        return problems
    }

    private val SAFE = Regex("^[a-z0-9-]{1,64}$")
    private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
}

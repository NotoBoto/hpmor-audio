package io.github.notoboto.unison.data

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * Text books voiced on the phone — imported files (`local://`) and books of source plugins (`src://<plugin>/`),
 * SOURCES_DESIGN.md §8.3. Both keep the same files: meta.json, index.json (chapter list), tNNN.json (title + paragraphs);
 * a plugin book also has origin.json and chapters.json (the chapterId → n table, §19.5).
 */
const val SRC_SCHEME = "src://"

fun isTextSource(sourceUrl: String) = sourceUrl == LOCAL_URL || sourceUrl.startsWith(SRC_SCHEME)
fun isPluginSource(sourceUrl: String) = sourceUrl.startsWith(SRC_SCHEME)
fun pluginSourceUrl(pluginId: String) = "$SRC_SCHEME$pluginId/"
fun pluginIdOf(sourceUrl: String) = sourceUrl.removePrefix(SRC_SCHEME).trimEnd('/')
/** Sources whose catalog is fetched over the network (books.json); the sync file only knows these (decision 22). */
fun isWebSource(sourceUrl: String) = sourceUrl.startsWith("https://") || sourceUrl.startsWith("http://")

object TextBooks {
    fun root(ctx: Context, sourceUrl: String): File =
        if (sourceUrl == LOCAL_URL) Local.dir(ctx) else File(ctx.filesDir, "srcbooks/${pluginIdOf(sourceUrl)}").apply { mkdirs() }

    fun dir(ctx: Context, sourceUrl: String, id: String): File = File(root(ctx, sourceUrl), id).apply { mkdirs() }

    /** Plugin sources that have books on the phone. */
    fun pluginSources(ctx: Context): List<String> =
        File(ctx.filesDir, "srcbooks").listFiles()?.filter { it.isDirectory && (it.listFiles()?.isNotEmpty() == true) }?.map { pluginSourceUrl(it.name) }.orEmpty()

    /** Display name of a plugin source: stored in its books' origin.json (works before the plugin is loaded). */
    fun pluginName(ctx: Context, sourceUrl: String): String = root(ctx, sourceUrl).listFiles()?.firstNotNullOfOrNull { d ->
        runCatching { json.parseToJsonElement(File(d, "origin.json").readText()).jsonObject["name"]?.jsonPrimitive?.content }.getOrNull()
    } ?: pluginIdOf(sourceUrl)

    fun catalog(ctx: Context, sourceUrl: String): List<BookMeta> = root(ctx, sourceUrl).listFiles()?.mapNotNull { d ->
        runCatching { json.decodeFromString(BookMeta.serializer(), File(d, "meta.json").readText()) }.getOrNull()
    }.orEmpty().sortedBy { it.title }

    fun chapters(ctx: Context, sourceUrl: String, id: String): List<ChapterMeta> = runCatching {
        json.decodeFromString(ListSerializer(ChapterMeta.serializer()), File(dir(ctx, sourceUrl, id), "index.json").readText())
    }.getOrDefault(emptyList())

    fun saveIndex(ctx: Context, sourceUrl: String, id: String, list: List<ChapterMeta>) =
        atomicWrite(File(dir(ctx, sourceUrl, id), "index.json"), json.encodeToString(ListSerializer(ChapterMeta.serializer()), list))

    fun textFile(ctx: Context, sourceUrl: String, id: String, n: Int) = File(dir(ctx, sourceUrl, id), "t%03d.json".format(n))
    fun hasText(ctx: Context, sourceUrl: String, id: String, n: Int) = textFile(ctx, sourceUrl, id, n).exists()

    fun chapterParas(ctx: Context, sourceUrl: String, id: String, n: Int): LocalChapter =
        json.decodeFromString(LocalChapter.serializer(), textFile(ctx, sourceUrl, id, n).readText())

    fun saveParas(ctx: Context, sourceUrl: String, id: String, n: Int, c: LocalChapter) =
        atomicWrite(textFile(ctx, sourceUrl, id, n), json.encodeToString(LocalChapter.serializer(), c))

    /** Text-only chapter (no timings) for reading before it is voiced. */
    fun chapterText(ctx: Context, sourceUrl: String, id: String, n: Int): ChapterText {
        val c = chapterParas(ctx, sourceUrl, id, n)
        val paras = listOf(c.title.split(' ').map { Tok(it, null, 0) }) + c.paras.map { p -> p.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { Tok(it, null, 0) } }
        val est = c.paras.sumOf { it.length } / 14.0
        return ChapterText(n, c.title, listOf("Рассказчик"), paras, est)
    }

    fun delete(ctx: Context, sourceUrl: String, id: String) = File(root(ctx, sourceUrl), id).deleteRecursively()

    fun atomicWrite(f: File, text: String) {
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }
}

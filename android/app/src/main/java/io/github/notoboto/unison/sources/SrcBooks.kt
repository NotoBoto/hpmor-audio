package io.github.notoboto.unison.sources

import android.content.Context
import io.github.notoboto.unison.data.BookMeta
import io.github.notoboto.unison.data.ChapterMeta
import io.github.notoboto.unison.data.LocalChapter
import io.github.notoboto.unison.data.TextBooks
import io.github.notoboto.unison.data.json
import io.github.notoboto.unison.data.pluginSourceUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.security.MessageDigest

/**
 * Books of source plugins on the phone (SOURCES_DESIGN.md §8.3, §19.5).
 *
 * A plugin book id may contain "/" and spaces, so on the phone a book is stored under a short internal id
 * ("s" + 12 hex of SHA-1 of the plugin's book id); origin.json keeps the real ids for refreshes and, later, sync v2.
 * chapters.json is the chapterId → n table: a chapter keeps its n forever, new chapters get the next free n,
 * reading order is `order` (from chapters()), chapters gone from the site stay with state "removed".
 */
class SrcBooks(private val context: Context, private val sources: Sources) {
    private val lock = Mutex()

    @Serializable data class Origin(val plugin: String, val book: String, val url: String, val license: String? = null, val lang: String? = null,
                                    val name: String? = null)   // plugin name, for the library before the plugin is loaded

    @Serializable data class Entry(
        val id: String, val n: Int, val order: Int, val title: String, val url: String,
        val volume: String? = null, val date: String? = null, val locked: Boolean = false,
        val state: String = "listed",         // listed | removed | unlisted
        val hash: String? = null,             // §19.5, null until the text is on the phone
        val textChanged: Boolean = false,
    )

    @Serializable data class Table(val format: Int = 1, val plugin: String, val book: String, val chapters: List<Entry>, val checked: Long = 0)

    data class RefreshResult(val added: List<Int>, val removed: List<Int>)

    fun internalId(pluginBookId: String): String =
        "s" + MessageDigest.getInstance("SHA-1").digest(pluginBookId.toByteArray()).joinToString("") { "%02x".format(it) }.take(12)

    fun origin(pluginId: String, internalId: String): Origin? = readJson(file(pluginId, internalId, "origin.json"), Origin.serializer())
    fun table(pluginId: String, internalId: String): Table? = readJson(file(pluginId, internalId, "chapters.json"), Table.serializer())

    /** Creates or refreshes the book on the phone from the plugin's details; returns the internal id. */
    suspend fun add(pluginId: String, d: BookDetails, chapters: List<ChapterStub>): String = lock.withLock {
        val id = internalId(d.id)
        withContext(Dispatchers.IO) {
            val src = pluginSourceUrl(pluginId)
            val pluginName = sources.repos.installed()[pluginId]?.info?.name ?: pluginId
            val meta = BookMeta(id = id, title = d.title, author = d.author, translator = d.translator, cover = d.cover, coverSm = d.cover, data = "",
                total = chapters.size, tags = d.tags + listOfNotNull(pluginName, licenseTag(d.license)),
                description = d.description ?: "Книга с сайта: $pluginName. Главы озвучиваются синтезатором телефона.")
            TextBooks.atomicWrite(File(TextBooks.dir(context, src, id), "meta.json"), json.encodeToString(BookMeta.serializer(), meta))
            TextBooks.atomicWrite(File(TextBooks.dir(context, src, id), "origin.json"),
                json.encodeToString(Origin.serializer(), Origin(pluginId, d.id, d.url, d.license, d.lang, pluginName)))
            merge(pluginId, id, d.id, chapters)
        }
        id
    }

    /** chapters() again: new chapters get the next n, gone ones are marked removed (§8.3, §8.6). */
    suspend fun refresh(pluginId: String, internalId: String): RefreshResult = lock.withLock {
        val o = origin(pluginId, internalId) ?: throw PluginException("NOT_FOUND", "book is not on the phone")
        val list = sources.chapters(pluginId, o.book)
        withContext(Dispatchers.IO) { merge(pluginId, internalId, o.book, list) }
    }

    /**
     * Makes sure the text of chapter [n] is on the phone (tNNN.json); fetches it through the plugin if needed.
     * With [refetch], downloads again and marks the chapter as changed when its hash differs (decision 19).
     */
    suspend fun ensureText(pluginId: String, internalId: String, n: Int, refetch: Boolean = false): LocalChapter {
        val src = pluginSourceUrl(pluginId)
        if (!refetch && TextBooks.hasText(context, src, internalId, n)) return TextBooks.chapterParas(context, src, internalId, n)
        val o = origin(pluginId, internalId) ?: throw PluginException("NOT_FOUND", "book is not on the phone")
        val e = table(pluginId, internalId)?.chapters?.find { it.n == n } ?: throw PluginException("NOT_FOUND", "chapter $n")
        if (e.locked) throw PluginException("NEEDS_LOGIN", "chapter is locked", e.url)
        val c = sources.chapter(pluginId, o.book, e.id)
        if (c.kind != "text") throw PluginException("CHANGED_LAYOUT", "audio chapters are not supported yet")
        val text = LocalChapter(c.title.ifBlank { e.title }, c.paras)
        val hash = chapterHash(text.title, text.paras)
        lock.withLock {
            withContext(Dispatchers.IO) {
                TextBooks.saveParas(context, src, internalId, n, text)
                updateEntry(pluginId, internalId, n) { it.copy(hash = hash, textChanged = it.textChanged || (it.hash != null && it.hash != hash)) }
                // a better duration estimate once the text is known (same rule as imported books)
                val idx = TextBooks.chapters(context, src, internalId)
                TextBooks.saveIndex(context, src, internalId, idx.map { if (it.n == n) it.copy(duration = text.paras.sumOf { p -> p.length } / 14.0) else it })
            }
        }
        return text
    }

    fun delete(pluginId: String, internalId: String) = TextBooks.delete(context, pluginSourceUrl(pluginId), internalId)

    // ---- internals ----

    private fun merge(pluginId: String, id: String, bookId: String, list: List<ChapterStub>): RefreshResult {
        val src = pluginSourceUrl(pluginId)
        val old = table(pluginId, id)?.chapters.orEmpty()
        val byId = old.associateBy { it.id }
        var nextN = (old.maxOfOrNull { it.n } ?: 0) + 1
        val added = mutableListOf<Int>()
        val listed = list.mapIndexed { i, c ->
            val prev = byId[c.id]
            val n = prev?.n ?: nextN++.also { added += it }
            (prev ?: Entry(c.id, n, i, c.title, c.url)).copy(order = i, title = c.title, url = c.url, volume = c.volume, date = c.date,
                locked = c.locked, state = "listed")
        }
        val listedIds = list.map { it.id }.toSet()
        val removed = old.filter { it.id !in listedIds && it.state != "removed" }.map { it.n }
        val gone = old.filter { it.id !in listedIds }.map { it.copy(state = "removed", order = Int.MAX_VALUE) }
        val all = listed + gone
        TextBooks.atomicWrite(file(pluginId, id, "chapters.json"),
            json.encodeToString(Table.serializer(), Table(plugin = pluginId, book = bookId, chapters = all, checked = System.currentTimeMillis())))
        // index.json in reading order; removed chapters stay only while they have text on the phone
        val oldIdx = TextBooks.chapters(context, src, id).associateBy { it.n }
        val idx = all.filter { it.state == "listed" || TextBooks.hasText(context, src, id, it.n) }.map { e ->
            val title = if (e.state == "removed") "${e.title} (удалена из источника)" else e.title + if (e.locked) " 🔒" else ""
            ChapterMeta(e.n, title, oldIdx[e.n]?.duration ?: 0.0, 0, null, e.date)
        }
        TextBooks.saveIndex(context, src, id, idx)
        return RefreshResult(added, removed)
    }

    private fun updateEntry(pluginId: String, id: String, n: Int, f: (Entry) -> Entry) {
        val t = table(pluginId, id) ?: return
        TextBooks.atomicWrite(file(pluginId, id, "chapters.json"),
            json.encodeToString(Table.serializer(), t.copy(chapters = t.chapters.map { if (it.n == n) f(it) else it })))
    }

    private fun file(pluginId: String, id: String, name: String) = File(TextBooks.dir(context, pluginSourceUrl(pluginId), id), name)

    private fun <T> readJson(f: File, s: kotlinx.serialization.KSerializer<T>): T? =
        if (!f.exists()) null else runCatching { json.decodeFromString(s, f.readText()) }.getOrNull()

    private fun licenseTag(l: String?) = when (l) { "public-domain" -> "Общественное достояние"; "cc-by" -> "CC BY"; "cc-by-sa" -> "CC BY-SA"; else -> null }

    companion object {
        /** §19.5: first 16 hex of SHA-1 of UTF-8 title + "\n" + paras.join("\n") — the same as core.chapterHash in JS. */
        fun chapterHash(title: String, paras: List<String>): String =
            MessageDigest.getInstance("SHA-1").digest((title + "\n" + paras.joinToString("\n")).toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }.take(16)
    }
}

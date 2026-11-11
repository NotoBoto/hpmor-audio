package io.github.notoboto.unison.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** Catalogs of sources, chapter lists of books, chapter texts. Network first, disk cache for offline. */
class Repo(private val context: Context, private val store: Store) {
    val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val cacheDir = File(context.filesDir, "meta").apply { mkdirs() }

    /** sourceUrl -> books in its catalog */
    private val _catalogs = MutableStateFlow<Map<String, List<BookMeta>>>(emptyMap())
    val catalogs: StateFlow<Map<String, List<BookMeta>>> = _catalogs
    /** bookKey -> chapters */
    private val _chapters = MutableStateFlow<Map<String, List<ChapterMeta>>>(emptyMap())
    val chapters: StateFlow<Map<String, List<ChapterMeta>>> = _chapters
    val refreshing = MutableStateFlow(false)

    /** Text books on the phone: imported files (local://) and books of source plugins (src://<plugin>/), §8.3.
     *  Declared before init: properties and init blocks run in source order. */
    private val textSources = MutableStateFlow(listOf(LOCAL_SOURCE))

    init { loadCached(); reloadLocal() }

    /** Reload text books from disk (imported and plugin books). */
    fun reloadLocal() {
        val srcs = listOf(LOCAL_SOURCE) + TextBooks.pluginSources(context).map { Source(it, TextBooks.pluginName(context, it)) }
        textSources.value = srcs
        for (src in srcs) {
            val list = TextBooks.catalog(context, src.url)
            _catalogs.update { it + (src.url to list) }
            _chapters.update { m -> m + list.associate { bookKey(src.url, it.id) to TextBooks.chapters(context, src.url, it.id) } }
        }
    }
    private val allSources get() = store.s.sources + textSources.value
    private val sources get() = (context.applicationContext as io.github.notoboto.unison.UnisonApp).sources

    private fun cacheFile(name: String) = File(cacheDir, name.replace(Regex("[^A-Za-z0-9_.-]"), "_"))

    private fun loadCached() {
        val cats = mutableMapOf<String, List<BookMeta>>()
        for (src in store.s.sources) {
            val f = cacheFile("books_" + bookKey(src.url, "x") + ".json")
            if (f.exists()) runCatching { cats[src.url] = json.decodeFromString(ListSerializer(BookMeta.serializer()), f.readText()) }
        }
        _catalogs.value = cats
        val chs = mutableMapOf<String, List<ChapterMeta>>()
        cacheDir.listFiles()?.filter { it.name.startsWith("index_") }?.forEach { f ->
            runCatching { chs[f.name.removePrefix("index_").removeSuffix(".json")] = json.decodeFromString(ListSerializer(ChapterMeta.serializer()), f.readText()).sortedBy { it.n } }
        }
        _chapters.value = chs
    }

    suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).cacheControl(CacheControl.FORCE_NETWORK).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body!!.string()
        }
    }

    fun book(key: String): Book? {
        for (src in allSources) for (m in _catalogs.value[src.url].orEmpty())
            if (bookKey(src.url, m.id) == key) return edited(Book(src, m, _chapters.value[key].orEmpty()))
        return null
    }
    fun libraryBooks(): List<Book> = store.s.library.mapNotNull { e ->
        val src = allSources.find { it.url == e.sourceUrl } ?: return@mapNotNull null
        val m = _catalogs.value[src.url]?.find { it.id == e.bookId } ?: return@mapNotNull null
        edited(Book(src, m, _chapters.value[bookKey(src.url, m.id)].orEmpty()))
    }
    /** apply the user's title / cover edits */
    fun edited(b: Book): Book {
        val e = store.s.edits[b.key] ?: return b
        return b.copy(meta = b.meta.copy(title = e.title ?: b.meta.title, short = if (e.title != null) e.title else b.meta.short,
            cover = e.cover ?: b.meta.cover, coverSm = e.cover ?: b.meta.coverSm))
    }
    fun inLibrary(key: String) = store.s.library.any { bookKey(it.sourceUrl, it.bookId) == key }

    suspend fun refreshSource(src: Source): Result<List<BookMeta>> = runCatching {
        val text = get(catalogUrl(src))
        val list = json.decodeFromString(ListSerializer(BookMeta.serializer()), text)
        cacheFile("books_" + bookKey(src.url, "x") + ".json").writeText(text)
        _catalogs.update { it + (src.url to list) }
        list
    }

    /** [pluginMaxAgeMs]: a plugin book checked more recently than this is not asked again (§8.6: at most every 6 h
     *  in the background); 0 = always (a manual refresh). */
    suspend fun refreshBook(b: Book, pluginMaxAgeMs: Long = 0): Result<List<ChapterMeta>> = if (isTextSource(b.source.url)) runCatching {
        if (isPluginSource(b.source.url)) {
            val checked = sources.books.table(pluginIdOf(b.source.url), b.meta.id)?.checked ?: 0
            if (System.currentTimeMillis() - checked >= pluginMaxAgeMs) sources.books.refresh(pluginIdOf(b.source.url), b.meta.id)   // chapters()
        }
        reloadLocal(); TextBooks.chapters(context, b.source.url, b.meta.id)
    } else runCatching {
        val text = get(b.indexUrl)
        val list = json.decodeFromString(ListSerializer(ChapterMeta.serializer()), text).sortedBy { it.n }
        cacheFile("index_" + b.key + ".json").writeText(text)
        _chapters.update { it + (b.key to list) }
        list
    }

    /** Refresh all sources and every library book. Returns number of failures. */
    suspend fun refreshAll(pluginMaxAgeMs: Long = 0): Int = coroutineScope {
        refreshing.value = true
        try {
            // only web sources have a books.json; a stray local:// or src:// entry (old sync imports) is skipped
            val srcFails = store.s.sources.filter { isWebSource(it.url) }.map { async { refreshSource(it).isFailure } }.awaitAll().count { it }
            if (!store.s.firstRunDone) {   // first launch: put the default source's books into the library
                val first = _catalogs.value[DEFAULT_SOURCE.url].orEmpty()
                store.update { st -> st.copy(firstRunDone = true, library = st.library + first.filter { m -> st.library.none { it.bookId == m.id } }.map { LibEntry(DEFAULT_SOURCE.url, it.id) }) }
            }
            val bookFails = libraryBooks().map { async { refreshBook(it, pluginMaxAgeMs).isFailure } }.awaitAll().count { it }
            srcFails + bookFails
        } finally { refreshing.value = false }
    }

    // ---- characters ----
    suspend fun characters(b: Book): List<Character> = withContext(Dispatchers.IO) {
        val f = cacheFile("chars_" + b.key + ".json")
        val text = runCatching { get(b.dataUrl + "characters.json").also { f.writeText(it) } }.getOrNull()
            ?: if (f.exists()) f.readText() else return@withContext emptyList()
        runCatching { json.decodeFromString(ListSerializer(Character.serializer()), text) }.getOrDefault(emptyList())
    }

    private suspend fun extra(b: Book, name: String): String? = withContext(Dispatchers.IO) {
        val f = cacheFile(name + "_" + b.key + ".json")
        runCatching { get(b.dataUrl + name + ".json").also { f.writeText(it) } }.getOrNull() ?: if (f.exists()) f.readText() else null
    }
    private val recapCache = mutableMapOf<String, List<Recap>>()
    private val glossCache = mutableMapOf<String, List<GlossaryEntry>>()
    suspend fun recaps(b: Book): List<Recap> = recapCache[b.key] ?: (if (isTextSource(b.source.url)) emptyList() else
        runCatching { json.decodeFromString(ListSerializer(Recap.serializer()), extra(b, "recaps") ?: "[]") }.getOrDefault(emptyList())).also { recapCache[b.key] = it }
    suspend fun glossary(b: Book): List<GlossaryEntry> = glossCache[b.key] ?: (if (isTextSource(b.source.url)) emptyList() else
        runCatching { json.decodeFromString(ListSerializer(GlossaryEntry.serializer()), extra(b, "glossary") ?: "[]") }.getOrDefault(emptyList())).also { glossCache[b.key] = it }

    // ---- chapter files ----
    private var filesFor: String? = "\u0000"
    private var filesImpl: ChapterFiles = InternalFiles(context)
    /** current storage for downloads (follows the setting) */
    val files: ChapterFiles get() {
        val want = store.s.settings.storageUri
        if (want != filesFor) { filesImpl = want?.let { runCatching { SafFiles(context, it) }.getOrNull() } ?: InternalFiles(context); filesFor = want }
        return filesImpl
    }
    fun deleteLocal(key: String, n: Int) {
        runCatching { files.delete(key, n) }
        book(key)?.images(n)?.forEach { im -> runCatching { files.deleteFile(key, im.localName) } }
        store.unmarkDownloaded(key, n)
    }

    /** Pictures of a chapter for the reader: (after, model for Coil, picture); the downloaded copy when there is one. */
    fun pictures(b: Book, n: Int): List<Pair<String, ChapterImage>> {
        val local = store.isDownloaded(b.key, n)
        return b.images(n).map { im ->
            val uri = if (local) runCatching { if (files.hasFile(b.key, im.localName)) files.fileUri(b.key, im.localName) else null }.getOrNull() else null
            (uri ?: b.imgUrl(im)) to im
        }
    }

    /** Save the pictures of a downloaded chapter that are not on the phone yet (published after the download).
     *  Returns how many were added. */
    suspend fun fillPictures(b: Book, n: Int): Int = withContext(Dispatchers.IO) {
        if (!store.isDownloaded(b.key, n)) return@withContext 0
        var added = 0
        for (im in b.images(n)) {
            if (runCatching { files.hasFile(b.key, im.localName) }.getOrDefault(true)) continue
            runCatching {
                http.newCall(Request.Builder().url(b.imgUrl(im)).build()).execute().use { r ->
                    if (!r.isSuccessful) error("HTTP ${r.code}")
                    files.writeFile(b.key, im.localName) { out -> r.body!!.byteStream().use { it.copyTo(out) } }
                }
                added++
            }
        }
        added
    }

    private val textCache = object : LinkedHashMap<String, ChapterText>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ChapterText>?) = size > 6
    }

    suspend fun chapterText(b: Book, n: Int): ChapterText = withContext(Dispatchers.IO) {
        val k = b.key + ":" + n
        synchronized(textCache) { textCache[k] }?.let { return@withContext it }
        val voiced = store.isDownloaded(b.key, n) && runCatching { files.exists(b.key, n, "json") }.getOrDefault(false)
        if (!voiced && isTextSource(b.source.url)) {
            // a plugin chapter is fetched through the plugin the first time it is opened (§8.3)
            if (isPluginSource(b.source.url) && !TextBooks.hasText(context, b.source.url, b.meta.id, n)) {
                sources.books.ensureText(pluginIdOf(b.source.url), b.meta.id, n)
                reloadLocal()   // the chapter now has a duration estimate
            }
            return@withContext TextBooks.chapterText(context, b.source.url, b.meta.id, n)
        }
        val text = if (voiced) files.readText(b.key, n, "json") else get(b.chUrl(n, "json"))
        val t = parseChapter(text)
        synchronized(textCache) { textCache[k] = t }
        t
    }

    fun audioUri(b: Book, n: Int): String {
        if (b.source.url == LOCAL_URL && Local.isAudio(context, b.meta.id)) return Local.audioFiles(context, b.meta.id).getOrNull(n - 1)?.uri.orEmpty()
        return if (store.isDownloaded(b.key, n) && runCatching { files.exists(b.key, n, "ogg") }.getOrDefault(false)) files.playUri(b.key, n) else b.streamUri(n)
    }

    companion object {
        /** How often plugin books are asked for new chapters outside a manual refresh (§8.6). */
        const val PLUGIN_CHECK_MS = 6 * 3600_000L

        fun parseChapter(text: String): ChapterText {
            val o = json.parseToJsonElement(text).jsonObject
            val roles = o["roles"]!!.jsonArray.map { it.jsonPrimitive.content }
            val paras = o["paras"]!!.jsonArray.map { p ->
                p.jsonArray.map { tk ->
                    val a = tk.jsonArray
                    val t = a.getOrNull(1)?.let { if (it is JsonNull) null else it.jsonPrimitive.double }
                    Tok(a[0].jsonPrimitive.content, t, a.getOrNull(2)?.jsonPrimitive?.int ?: 0)
                }
            }
            return ChapterText(o["n"]!!.jsonPrimitive.int, o["title"]!!.jsonPrimitive.content, roles, paras, o["duration"]!!.jsonPrimitive.double)
        }
    }
}

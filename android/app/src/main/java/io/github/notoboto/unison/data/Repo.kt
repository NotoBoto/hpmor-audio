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
    val dlRoot = File(context.filesDir, "dl").apply { mkdirs() }

    /** sourceUrl -> books in its catalog */
    private val _catalogs = MutableStateFlow<Map<String, List<BookMeta>>>(emptyMap())
    val catalogs: StateFlow<Map<String, List<BookMeta>>> = _catalogs
    /** bookKey -> chapters */
    private val _chapters = MutableStateFlow<Map<String, List<ChapterMeta>>>(emptyMap())
    val chapters: StateFlow<Map<String, List<ChapterMeta>>> = _chapters
    val refreshing = MutableStateFlow(false)

    init { loadCached() }

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
        for (src in store.s.sources) for (m in _catalogs.value[src.url].orEmpty())
            if (bookKey(src.url, m.id) == key) return Book(src, m, _chapters.value[key].orEmpty())
        return null
    }
    fun libraryBooks(): List<Book> = store.s.library.mapNotNull { e ->
        val src = store.s.sources.find { it.url == e.sourceUrl } ?: return@mapNotNull null
        val m = _catalogs.value[src.url]?.find { it.id == e.bookId } ?: return@mapNotNull null
        Book(src, m, _chapters.value[bookKey(src.url, m.id)].orEmpty())
    }
    fun inLibrary(key: String) = store.s.library.any { bookKey(it.sourceUrl, it.bookId) == key }

    suspend fun refreshSource(src: Source): Result<List<BookMeta>> = runCatching {
        val text = get(src.url + "books.json")
        val list = json.decodeFromString(ListSerializer(BookMeta.serializer()), text)
        cacheFile("books_" + bookKey(src.url, "x") + ".json").writeText(text)
        _catalogs.update { it + (src.url to list) }
        list
    }

    suspend fun refreshBook(b: Book): Result<List<ChapterMeta>> = runCatching {
        val text = get(b.indexUrl)
        val list = json.decodeFromString(ListSerializer(ChapterMeta.serializer()), text).sortedBy { it.n }
        cacheFile("index_" + b.key + ".json").writeText(text)
        _chapters.update { it + (b.key to list) }
        list
    }

    /** Refresh all sources and every library book. Returns number of failures. */
    suspend fun refreshAll(): Int = coroutineScope {
        refreshing.value = true
        try {
            val srcFails = store.s.sources.map { async { refreshSource(it).isFailure } }.awaitAll().count { it }
            if (!store.s.firstRunDone) {   // first launch: put the default source's books into the library
                val first = _catalogs.value[DEFAULT_SOURCE.url].orEmpty()
                store.update { st -> st.copy(firstRunDone = true, library = st.library + first.filter { m -> st.library.none { it.bookId == m.id } }.map { LibEntry(DEFAULT_SOURCE.url, it.id) }) }
            }
            val bookFails = libraryBooks().map { async { refreshBook(it).isFailure } }.awaitAll().count { it }
            srcFails + bookFails
        } finally { refreshing.value = false }
    }

    // ---- chapter files ----
    fun dlDir(key: String) = File(dlRoot, key).apply { mkdirs() }
    fun localJson(key: String, n: Int) = File(dlDir(key), "ch%03d.json".format(n))
    fun localOgg(key: String, n: Int) = File(dlDir(key), "ch%03d.ogg".format(n))
    fun deleteLocal(key: String, n: Int) { localJson(key, n).delete(); localOgg(key, n).delete(); store.unmarkDownloaded(key, n) }

    private val textCache = object : LinkedHashMap<String, ChapterText>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ChapterText>?) = size > 6
    }

    suspend fun chapterText(b: Book, n: Int): ChapterText = withContext(Dispatchers.IO) {
        val k = b.key + ":" + n
        synchronized(textCache) { textCache[k] }?.let { return@withContext it }
        val f = localJson(b.key, n)
        val text = if (f.exists() && store.isDownloaded(b.key, n)) f.readText() else get(b.chUrl(n, "json"))
        val t = parseChapter(text)
        synchronized(textCache) { textCache[k] = t }
        t
    }

    fun audioUri(b: Book, n: Int): String {
        val f = localOgg(b.key, n)
        return if (f.exists() && store.isDownloaded(b.key, n)) f.toURI().toString() else b.chUrl(n, "ogg")
    }

    companion object {
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

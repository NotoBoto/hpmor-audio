package io.github.notoboto.unison.data

import kotlinx.serialization.Serializable
import java.security.MessageDigest

/** One entry of a source's books.json (same format the website uses). */
@Serializable
data class BookMeta(
    val id: String,
    val title: String,
    val short: String? = null,
    val author: String? = null,
    val translator: String? = null,
    val cover: String? = null,
    val coverSm: String? = null,
    val data: String = "data/",
    val total: Int? = null,
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val credits: String? = null,
)

/** One entry of a book's index.json. */
@Serializable
data class ChapterMeta(
    val n: Int,
    val title: String,
    val duration: Double,
    val bytes: Long = 0,
    val v: String? = null,
    val date: String? = null,
)

@Serializable
data class Source(val url: String, val name: String)

@Serializable
data class LibEntry(val sourceUrl: String, val bookId: String, val added: Long = System.currentTimeMillis())

@Serializable
data class Progress(val pos: Double = 0.0, val done: Boolean = false)

@Serializable
data class HistEntry(val book: String, val n: Int, val ts: Long)

@Serializable
data class Settings(
    val theme: String = "dark",          // dark | light | system
    val dynamicColor: Boolean = false,
    val amoled: Boolean = false,
    val fontSize: Int = 19,
    val speed: Float = 1f,
    val keepScreenOn: Boolean = true,
    val fullTitles: Boolean = true,
    val sortDesc: Boolean = true,
    val wifiOnly: Boolean = false,
    val autoDownloadNext: Int = 0,       // keep N chapters ahead downloaded
    val autoDelete: Boolean = false,     // delete a chapter after it was listened to
    val autoUpdate: Boolean = true,      // re-download re-voiced chapters
    val skipBack: Int = 15,
    val skipForward: Int = 15,
    val autoBackupUri: String? = null,   // SAF folder for daily backups
    val lastUpdateCheck: Long = 0,
    val readerMode: String = "text",     // text | player
    // library display (like Mihon): compact | comfortable | cover | list
    val libDisplay: String = "compact",
    val libColumns: Int = 0,             // 0 = automatic
    val badgeUnread: Boolean = true,
    val badgeDownloaded: Boolean = true,
    val libContinueButton: Boolean = false,
    val libSort: String = "title",       // title | last | unread | added | chapters
    val libSortDesc: Boolean = false,
    val filterDownloaded: Boolean = false,
    val filterUnread: Boolean = false,
)

@Serializable
data class AppState(
    val sources: List<Source> = listOf(DEFAULT_SOURCE),
    val library: List<LibEntry> = emptyList(),
    val progress: Map<String, Map<Int, Progress>> = emptyMap(),   // bookKey -> chapter -> progress
    val last: Map<String, Int> = emptyMap(),                       // bookKey -> last opened chapter
    val downloaded: Map<String, Map<Int, String>> = emptyMap(),    // bookKey -> chapter -> version
    val history: List<HistEntry> = emptyList(),
    val settings: Settings = Settings(),
    val seenUpdates: String = "",
    val firstRunDone: Boolean = false,
)

val DEFAULT_SOURCE = Source("https://notoboto.github.io/unison/", "Unison")

fun normalizeSource(url: String): String {
    var u = url.trim()
    if (!u.startsWith("http")) u = "https://$u"
    u = u.removeSuffix("books.json")
    if (!u.endsWith("/")) u += "/"
    return u
}

fun bookKey(sourceUrl: String, id: String): String {
    val h = MessageDigest.getInstance("SHA-1").digest(sourceUrl.toByteArray()).joinToString("") { "%02x".format(it) }
    return h.take(8) + "_" + id
}

/** A book from a concrete source, with its chapter list (may be empty until loaded). */
data class Book(val source: Source, val meta: BookMeta, val chapters: List<ChapterMeta> = emptyList()) {
    val key: String get() = bookKey(source.url, meta.id)
    fun abs(path: String?): String? = path?.let { if (it.startsWith("http")) it else source.url + it }
    val coverUrl: String? get() = abs(meta.cover)
    val coverSmUrl: String? get() = abs(meta.coverSm ?: meta.cover)
    val indexUrl: String get() = source.url + meta.data + "index.json"
    fun chUrl(n: Int, ext: String) = source.url + meta.data + "ch%03d.%s".format(n, ext)
    val shortTitle: String get() = meta.short ?: meta.title
}

/** Parsed chapter text with word timings. */
data class Tok(val word: String, val t: Double?, val role: Int)
data class ChapterText(val n: Int, val title: String, val roles: List<String>, val paras: List<List<Tok>>, val duration: Double) {
    /** words with timings in time order: (time, paragraph, token) */
    val timeline: List<Triple<Double, Int, Int>> by lazy {
        paras.flatMapIndexed { pi, toks -> toks.mapIndexedNotNull { ti, tk -> tk.t?.let { Triple(it, pi, ti) } } }.sortedBy { it.first }
    }
    fun wordAt(time: Double): Int {
        var lo = 0; var hi = timeline.size - 1; var ans = -1
        while (lo <= hi) { val m = (lo + hi) / 2; if (timeline[m].first <= time + 0.05) { ans = m; lo = m + 1 } else hi = m - 1 }
        return ans
    }
}

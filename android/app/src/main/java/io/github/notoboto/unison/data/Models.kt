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
    /** sizes of the byte pieces chNNN.ogg.1, .2, ... when the audio is published split (host file-size cap) */
    val parts: List<Long>? = null,
    /** illustrations between paragraphs (Unison Studio books) */
    val images: List<ChapterImage> = emptyList(),
)

/** A picture after paragraph [after] of the chapter (index in "paras", 0 = title); [src] is relative to the data folder. */
@Serializable
data class ChapterImage(val after: Int, val src: String, val w: Int = 0, val h: Int = 0, val cap: String? = null) {
    /** file name of the downloaded copy, next to the chapter files */
    val localName: String get() = "img_" + src.substringAfterLast('/')
}

@Serializable
data class Source(val url: String, val name: String)

@Serializable
data class LibEntry(val sourceUrl: String, val bookId: String, val added: Long = System.currentTimeMillis())

@Serializable
data class Progress(val pos: Double = 0.0, val done: Boolean = false, val ts: Long = 0)

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
    val catCounts: Boolean = false,       // number of books in the category tabs (off, as in Mihon)
    val libContinueButton: Boolean = false,
    val libSort: String = "title",       // title | last | unread | added | chapters
    val libSortDesc: Boolean = false,
    val filterDownloaded: Boolean = false,
    val filterUnread: Boolean = false,
    // modes from "More"
    val downloadedOnly: Boolean = false,
    val incognito: Boolean = false,
    // chapter list
    val chFilterDownloaded: Boolean = false,
    val chFilterUnread: Boolean = false,
    val chFilterBookmarked: Boolean = false,
    val swipeRight: String = "read",     // read | download | none
    val swipeLeft: String = "download",
    // reader tap zones: off | double | single; width in % of the screen on each side
    val tapZones: String = "off",
    val tapZoneWidth: Int = 25,
    // background library check
    val autoCheckHours: Int = 0,         // 0 = off
    val autoCheckWifi: Boolean = true,
    val autoCheckCharging: Boolean = false,
    val autoDownloadNew: Boolean = false,
    val notifyDownloads: Boolean = true,
    // player extras
    val perBookSpeed: Boolean = false,
    val bookSpeed: Map<String, Float> = emptyMap(),
    val autoRewindAfterMin: Int = 0,     // 0 = off
    val autoRewindSec: Int = 10,
    val sleepScheduleOn: Boolean = false,
    val sleepFrom: Int = 23 * 60,        // minutes of the day
    val sleepTo: Int = 6 * 60,
    val sleepScheduleMin: Int = 30,
    // reading
    val fontFamily: String = "sans",     // sans | serif | mono
    val lineHeight: Float = 1.6f,
    val margins: Int = 18,
    val readerBg: String = "theme",      // theme | sepia | black | white
    val highlight: String = "yellow",    // yellow | pink | blue | green | none
    val sentenceHighlight: Boolean = false,
    val castButton: Boolean = true,
    // phone speech synthesizer for imported books
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val ttsLang: String = "ru-RU",
    val ttsEngine: String = "system",    // system | piper
    val piperNarrator: String = "dmitri",
    val piperMale: String = "ruslan",
    val piperFemale: String = "irina",
    val piperRoles: Boolean = true,      // different voices for dialogue
    val liveSubtitles: Boolean = true,   // current sentence in the notification / big widget
    // habits
    val goalMin: Int = 0,                // daily listening goal, 0 = off
    val reminderOn: Boolean = false,
    val reminderAt: Int = 20 * 60,       // minutes of the day
    // sound
    val skipSilence: Boolean = false,
    val voiceBoost: Int = 0,             // 0 = off, else gain in dB
    val autoStartHeadphones: Boolean = false,
    val recapAfterHours: Int = 12,       // show «Ранее в книге» after a break this long, 0 = never
    val glossaryHints: Boolean = true,   // «Кто это?» from the glossary
    val storageLimitGb: Int = 0,         // 0 = no limit; otherwise listened chapters are removed first
    val appLock: Boolean = false,
    val lockAfterMin: Int = 0,           // lock again after the app was in the background this long
    val hideInRecents: Boolean = false,
    // where chapters are stored: null = app storage, else SAF tree uri (e.g. SD card)
    val storageUri: String? = null,
)

@Serializable
data class Category(val id: Int, val name: String)

@Serializable
data class Bookmark(val n: Int, val pos: Double, val quote: String, val note: String = "", val ts: Long = System.currentTimeMillis())

/** A highlighted passage: paragraph [p], tokens [a]..[b], colour name, optional note. */
@Serializable
data class Highlight(val n: Int, val p: Int, val a: Int, val b: Int, val color: String = "yellow", val note: String = "",
                     val text: String = "", val pos: Double = 0.0, val ts: Long = System.currentTimeMillis())

@Serializable
data class BookEdit(val title: String? = null, val cover: String? = null)

@Serializable
data class AppState(
    val sources: List<Source> = listOf(DEFAULT_SOURCE),
    val library: List<LibEntry> = emptyList(),
    val progress: Map<String, Map<Int, Progress>> = emptyMap(),   // bookKey -> chapter -> progress
    val last: Map<String, Int> = emptyMap(),                       // bookKey -> last opened chapter
    val lastTs: Map<String, Long> = emptyMap(),
    val downloaded: Map<String, Map<Int, String>> = emptyMap(),    // bookKey -> chapter -> version
    val history: List<HistEntry> = emptyList(),
    val settings: Settings = Settings(),
    val seenUpdates: String = "",
    val firstRunDone: Boolean = false,
    val onboardingDone: Boolean = false,
    val categories: List<Category> = emptyList(),
    val bookCategories: Map<String, List<Int>> = emptyMap(),       // bookKey -> category ids
    val bookmarks: Map<String, List<Bookmark>> = emptyMap(),       // bookKey -> bookmarks
    val highlights: Map<String, List<Highlight>> = emptyMap(),     // bookKey -> highlights
    val edits: Map<String, BookEdit> = emptyMap(),                 // bookKey -> user edits
    val statsDays: Map<String, Long> = emptyMap(),                 // yyyy-MM-dd -> ms listened
    val statsBooks: Map<String, Long> = emptyMap(),                // bookKey -> ms listened
    val knownChapters: Map<String, Int> = emptyMap(),              // bookKey -> chapter count seen by the background check
)

val DEFAULT_SOURCE = Source("https://notoboto.github.io/unison/", "Unison")

/** The built-in source's list of books lives on Cloudflare (uploaded by Unison Studio; the app repo holds only the
 *  apps). The source keeps its old address as its identity, so book keys — progress, downloads — stay the same. */
const val DEFAULT_CATALOG = "https://unison-catalog-7q7.pages.dev/books.json"

fun catalogUrl(src: Source): String = if (src.url == DEFAULT_SOURCE.url) DEFAULT_CATALOG else src.url + "books.json"

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
    fun abs(path: String?): String? = path?.let { if (it.contains("://")) it else source.url + it }
    val coverUrl: String? get() = abs(meta.cover)
    val coverSmUrl: String? get() = abs(meta.coverSm ?: meta.cover)
    /** chapter data folder; may live on another host (absolute URL in books.json "data") */
    val dataUrl: String get() = abs(meta.data)!!
    val indexUrl: String get() = dataUrl + "index.json"
    fun chUrl(n: Int, ext: String) = dataUrl + "ch%03d.%s".format(n, ext)
    /** URLs to fetch the chapter audio from: the pieces when it is published split, else the one file */
    fun oggUrls(n: Int): List<String> = chapters.find { it.n == n }?.parts?.indices?.map { chUrl(n, "ogg") + "." + (it + 1) }
        ?: listOf(chUrl(n, "ogg"))
    /** URI for streaming; a split chapter carries its piece sizes for [io.github.notoboto.unison.player.PartsDataSource] */
    fun streamUri(n: Int): String = chapters.find { it.n == n }?.parts?.let { chUrl(n, "ogg") + "#parts=" + it.joinToString(",") }
        ?: chUrl(n, "ogg")
    val shortTitle: String get() = meta.short ?: meta.title
    fun images(n: Int): List<ChapterImage> = chapters.find { it.n == n }?.images.orEmpty()
    fun imgUrl(im: ChapterImage): String = if (im.src.contains("://")) im.src else dataUrl + im.src
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

@Serializable
data class Character(val name: String, val voice: String = "", val narrator: Boolean = false, val chapters: List<Int> = emptyList(),
                     val lines: Int = 0, val sample: String? = null, val sampleText: String? = null)

@Serializable
data class Recap(val n: Int, val recap: String)

@Serializable
data class GlossaryEntry(val term: String, val aliases: List<String> = emptyList(), val kind: String = "", val since: Int = 0, val short: String = "")

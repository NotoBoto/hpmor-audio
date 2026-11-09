package io.github.notoboto.unison.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }

/** All user state in one JSON file; every change is persisted (debounced). */
class Store(context: Context, scope: CoroutineScope) {
    private val file = File(context.filesDir, Profiles.stateFile(context))
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state
    val s: AppState get() = _state.value

    init {
        @OptIn(FlowPreview::class)
        scope.launch(Dispatchers.IO) { _state.drop(1).debounce(400).collect { write(it) } }
    }

    private fun load(): AppState = try {
        if (file.exists()) json.decodeFromString(AppState.serializer(), file.readText()) else AppState()
    } catch (e: Exception) { AppState() }

    @Synchronized private fun write(st: AppState) {
        val tmp = File(file.parentFile, "state.json.tmp")
        tmp.writeText(json.encodeToString(AppState.serializer(), st))
        tmp.renameTo(file)
    }
    fun flush() = write(_state.value)

    fun update(f: (AppState) -> AppState) = _state.update(f)
    fun settings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings)) }

    fun exportJson(): String = json.encodeToString(AppState.serializer(), s)
    fun importJson(text: String) {
        val st = json.decodeFromString(AppState.serializer(), text)
        // keep what is actually on this phone's disk; everything else comes from the backup
        _state.update { cur -> st.copy(downloaded = cur.downloaded) }
        flush()
    }

    // ---- progress helpers ----
    fun progress(book: String, n: Int): Progress = s.progress[book]?.get(n) ?: Progress()
    private val incognito get() = s.settings.incognito

    fun setPos(book: String, n: Int, pos: Double, duration: Double) = if (incognito) Unit else update { st ->
        val m = st.progress[book].orEmpty().toMutableMap()
        val old = m[n] ?: Progress()
        m[n] = old.copy(pos = pos, done = old.done || (duration > 0 && pos >= duration * 0.97), ts = System.currentTimeMillis())
        st.copy(progress = st.progress + (book to m))
    }
    fun setDone(book: String, ns: Collection<Int>, done: Boolean) = update { st ->
        val m = st.progress[book].orEmpty().toMutableMap()
        val now = System.currentTimeMillis()
        ns.forEach { n -> m[n] = if (done) (m[n] ?: Progress()).copy(done = true, ts = now) else Progress(ts = now) }
        st.copy(progress = st.progress + (book to m))
    }
    /** listening time for statistics */
    fun addListened(book: String, ms: Long) = if (incognito || ms <= 0) Unit else update { st ->
        val day = java.time.LocalDate.now().toString()
        st.copy(statsDays = st.statsDays + (day to ((st.statsDays[day] ?: 0) + ms)),
            statsBooks = st.statsBooks + (book to ((st.statsBooks[book] ?: 0) + ms)))
    }
    fun addHighlight(book: String, h: Highlight) = update { st ->
        st.copy(highlights = st.highlights + (book to (st.highlights[book].orEmpty().filterNot { it.n == h.n && it.p == h.p && it.a == h.a && it.b == h.b } + h)
            .sortedWith(compareBy({ it.n }, { it.p }, { it.a }))))
    }
    fun removeHighlight(book: String, h: Highlight) = update { st -> st.copy(highlights = st.highlights + (book to (st.highlights[book].orEmpty() - h))) }
    fun editHighlight(book: String, h: Highlight, note: String) = update { st ->
        st.copy(highlights = st.highlights + (book to st.highlights[book].orEmpty().map { if (it == h) it.copy(note = note) else it }))
    }
    fun addBookmark(book: String, b: Bookmark) = update { st -> st.copy(bookmarks = st.bookmarks + (book to (st.bookmarks[book].orEmpty() + b).sortedWith(compareBy({ it.n }, { it.pos })))) }
    fun removeBookmark(book: String, b: Bookmark) = update { st -> st.copy(bookmarks = st.bookmarks + (book to (st.bookmarks[book].orEmpty() - b))) }
    fun editBookmark(book: String, b: Bookmark, note: String) = update { st ->
        st.copy(bookmarks = st.bookmarks + (book to st.bookmarks[book].orEmpty().map { if (it == b) it.copy(note = note) else it }))
    }

    fun opened(book: String, n: Int) = if (incognito) update { it.copy(last = it.last + (book to n)) } else update { st ->
        st.copy(last = st.last + (book to n), lastTs = st.lastTs + (book to System.currentTimeMillis()),
            history = (listOf(HistEntry(book, n, System.currentTimeMillis())) + st.history.filterNot { it.book == book && it.n == n }).take(300))
    }
    fun isDownloaded(book: String, n: Int) = s.downloaded[book]?.containsKey(n) == true
    fun markDownloaded(book: String, n: Int, v: String?) = update { st ->
        st.copy(downloaded = st.downloaded + (book to (st.downloaded[book].orEmpty() + (n to (v ?: "1")))))
    }
    fun unmarkDownloaded(book: String, n: Int) = update { st ->
        st.copy(downloaded = st.downloaded + (book to (st.downloaded[book].orEmpty() - n)))
    }
}

/** Several people on one phone: each profile has its own state file (library, progress, settings). Downloads are shared. */
object Profiles {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("profiles", Context.MODE_PRIVATE)
    fun list(ctx: Context): List<String> = prefs(ctx).getString("list", null)?.split('\u0001')?.filter { it.isNotBlank() } ?: listOf("Основной")
    fun current(ctx: Context): String = prefs(ctx).getString("current", "Основной")!!
    fun stateFile(ctx: Context): String = list(ctx).indexOf(current(ctx)).let { if (it <= 0) "state.json" else "state_p$it.json" }
    fun add(ctx: Context, name: String) { val l = list(ctx); if (name !in l) prefs(ctx).edit().putString("list", (l + name).joinToString("\u0001")).apply() }
    fun remove(ctx: Context, name: String) {
        val l = list(ctx); val i = l.indexOf(name); if (i <= 0) return
        File(ctx.filesDir, "state_p$i.json").delete()
        // keep file indexes stable for the others: rename the ones after it
        for (j in i + 1 until l.size) File(ctx.filesDir, "state_p$j.json").renameTo(File(ctx.filesDir, "state_p${j - 1}.json"))
        prefs(ctx).edit().putString("list", (l - name).joinToString("\u0001")).apply()
        if (current(ctx) == name) prefs(ctx).edit().putString("current", "Основной").apply()
    }
    /** switch and restart the process so every component reloads its state */
    fun switchTo(activity: android.app.Activity, name: String) {
        prefs(activity).edit().putString("current", name).commit()
        val i = activity.packageManager.getLaunchIntentForPackage(activity.packageName)!!
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(i)
        Runtime.getRuntime().exit(0)
    }
}

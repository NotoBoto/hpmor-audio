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
    private val file = File(context.filesDir, "state.json")
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
    fun setPos(book: String, n: Int, pos: Double, duration: Double) = update { st ->
        val m = st.progress[book].orEmpty().toMutableMap()
        val old = m[n] ?: Progress()
        m[n] = old.copy(pos = pos, done = old.done || (duration > 0 && pos >= duration * 0.97))
        st.copy(progress = st.progress + (book to m))
    }
    fun setDone(book: String, ns: Collection<Int>, done: Boolean) = update { st ->
        val m = st.progress[book].orEmpty().toMutableMap()
        ns.forEach { n -> m[n] = if (done) (m[n] ?: Progress()).copy(done = true) else Progress() }
        st.copy(progress = st.progress + (book to m))
    }
    fun opened(book: String, n: Int) = update { st ->
        st.copy(last = st.last + (book to n),
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

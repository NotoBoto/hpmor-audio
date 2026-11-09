package io.github.notoboto.unison.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import io.github.notoboto.unison.app
import io.github.notoboto.unison.data.Progress
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(nav: NavController) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val chaptersMap by app.repo.chapters.collectAsState()
    val dlJobs by app.downloads.states.collectAsState()
    val refreshing by app.repo.refreshing.collectAsState()
    val scope = rememberCoroutineScope()
    val items = remember(st.library, chaptersMap, st.settings.downloadedOnly, st.downloaded) {
        app.repo.libraryBooks().flatMap { b -> b.chapters.filter { !st.settings.downloadedOnly || st.downloaded[b.key]?.containsKey(it.n) == true }
            .mapNotNull { c -> parseDate(c.date)?.let { Triple(b, c, it) } } }
            .sortedWith(compareByDescending<Triple<*, *, LocalDateTime>> { it.third }).take(500)
    }
    LaunchedEffect(items) { items.firstOrNull()?.second?.date?.let { d -> if (d > st.seenUpdates) app.store.update { it.copy(seenUpdates = d) } } }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Обновления") }, actions = {
            IconButton(onClick = { scope.launch { app.repo.refreshAll() } }) { Icon(Icons.Filled.Refresh, "Обновить") }
        })
    }) { pad ->
        PullToRefreshBox(refreshing, onRefresh = { scope.launch { app.repo.refreshAll() } }, Modifier.padding(pad).fillMaxSize()) {
            if (items.isEmpty()) EmptyState("🔔", "Пока нет обновлений.")
            else LazyColumn(Modifier.fillMaxSize()) {
                var lastDay = ""
                items.forEach { (b, c, t) ->
                    val d = dayLabel(t.toLocalDate())
                    if (d != lastDay) { lastDay = d; item(key = "h$d") { GroupHeader(d) } }
                    item(key = "${b.key}:${c.n}") {
                        val p = st.progress[b.key]?.get(c.n) ?: Progress()
                        ChapterRow(chName(c, st.settings.fullTitles), "${b.shortTitle} • ${hhmm(t)}", p.done, false, app.downloads.state(b, c.n, dlJobs),
                            onClick = { app.player.open(b, c.n, false); nav.navigate("reader/${b.key}/${c.n}") }, onLongClick = null,
                            onDownload = { app.downloads.enqueue(b, listOf(c.n)) }, onCancel = { app.downloads.cancel(b, c.n) },
                            onDelete = { app.downloads.delete(b, listOf(c.n)) }, onUpdate = { app.downloads.enqueue(b, listOf(c.n), true) },
                            thumb = b.coverSmUrl)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(nav: NavController) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val chaptersMap by app.repo.chapters.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    val entries = remember(st.history, chaptersMap) {
        st.history.mapNotNull { h -> val b = app.repo.book(h.book) ?: return@mapNotNull null; val c = b.chapters.find { it.n == h.n } ?: return@mapNotNull null; Triple(h, b, c) }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("История") }, actions = {
            if (entries.isNotEmpty()) IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.DeleteSweep, "Очистить историю") }
        })
    }) { pad ->
        if (entries.isEmpty()) EmptyState("🕮", "Здесь появятся главы, которые вы слушали.", Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            var lastDay = ""
            entries.forEach { (h, b, c) ->
                val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(h.ts), ZoneId.systemDefault())
                val d = dayLabel(t.toLocalDate())
                if (d != lastDay) { lastDay = d; item(key = "h$d") { GroupHeader(d) } }
                item(key = "${h.book}:${h.n}") {
                    val p = st.progress[b.key]?.get(c.n) ?: Progress()
                    val pct = (progressOf(p, c) * 100).toInt()
                    ChapterRow(chName(c, st.settings.fullTitles), "${hhmm(t)} • ${if (pct >= 100) "прослушано" else "$pct%"} • ${b.shortTitle}", false, false, io.github.notoboto.unison.download.DlState.None,
                        onClick = { app.player.open(b, c.n, false); nav.navigate("reader/${b.key}/${c.n}") }, onLongClick = null,
                        onDownload = {}, onCancel = {}, onDelete = {}, onUpdate = {}, thumb = b.coverSmUrl,
                        trailing = { IconButton(onClick = { app.store.update { s -> s.copy(history = s.history - h) } }) { Icon(Icons.Filled.Delete, "Удалить из истории") } })
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Очистить историю?") },
        text = { Text("Список прослушанного будет очищен. Прогресс глав сохранится.") },
        confirmButton = { TextButton(onClick = { app.store.update { it.copy(history = emptyList()) }; confirm = false }) { Text("Очистить") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
}

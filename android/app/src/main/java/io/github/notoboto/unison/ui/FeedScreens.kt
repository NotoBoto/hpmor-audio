package io.github.notoboto.unison.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var removing by remember { mutableStateOf<io.github.notoboto.unison.data.Book?>(null) }
    var query by remember { mutableStateOf<String?>(null) }
    // Mihon-style: one row per book — its most recently listened chapter (the store keeps every chapter for "continue" and recaps)
    val entries = remember(st.history, chaptersMap) {
        st.history.distinctBy { it.book }.mapNotNull { h ->
            val b = app.repo.book(h.book) ?: return@mapNotNull null; val c = b.chapters.find { it.n == h.n } ?: return@mapNotNull null; Triple(h, b, c)
        }
    }
    val q = query?.trim()?.lowercase().orEmpty()
    val shown = if (q.isEmpty()) entries else entries.filter { (_, b, _) -> b.meta.title.lowercase().contains(q) || b.shortTitle.lowercase().contains(q) }
    Scaffold(topBar = {
        TopAppBar(title = {
            if (query == null) Text("История")
            else androidx.compose.material3.TextField(query!!, { query = it }, placeholder = { Text("Поиск по истории") }, singleLine = true,
                colors = androidx.compose.material3.TextFieldDefaults.colors(focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent), modifier = Modifier.fillMaxWidth())
        }, navigationIcon = {
            if (query != null) IconButton(onClick = { query = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Закрыть поиск") }
        }, actions = {
            if (query == null && entries.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Search, "Поиск") }
            if (entries.isNotEmpty()) IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.DeleteSweep, "Очистить историю") }
        })
    }) { pad ->
        if (entries.isEmpty()) EmptyState("🕮", "Здесь появятся книги, которые вы слушали.", Modifier.padding(pad))
        else if (shown.isEmpty()) EmptyState("🔍", "Ничего не найдено.", Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
            var lastDay = ""
            shown.forEach { (h, b, c) ->
                val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(h.ts), ZoneId.systemDefault())
                val d = dayLabel(t.toLocalDate())
                if (d != lastDay) { lastDay = d; item(key = "h$d") { GroupHeader(d) } }
                item(key = b.key) {
                    val p = st.progress[b.key]?.get(c.n) ?: Progress()
                    val pct = (progressOf(p, c) * 100).toInt()
                    val sub = "Гл. ${c.n} • ${hhmm(t)}" + if (pct in 1..99) " • $pct%" else ""
                    HistoryRow(b.meta.title, sub, b.coverSmUrl,
                        onClick = { app.player.open(b, c.n, false); nav.navigate("reader/${b.key}/${c.n}") },
                        onCover = { nav.navigate("book/${b.key}") },
                        onDelete = { removing = b })
                }
            }
        }
    }
    removing?.let { b ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Убрать из истории?") },
            text = { Text("«${b.meta.title}» пропадёт из истории. Прогресс глав сохранится.") },
            confirmButton = { TextButton(onClick = { app.store.update { s -> s.copy(history = s.history.filter { it.book != b.key }) }; removing = null }) { Text("Убрать") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Отмена") } })
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Очистить историю?") },
        text = { Text("История будет очищена. Прогресс глав сохранится.") },
        confirmButton = { TextButton(onClick = { app.store.update { it.copy(history = emptyList()) }; confirm = false }) { Text("Очистить") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
}

/** History row (Mihon-like): large cover (opens the book), book title, chapter and time; tap continues listening. */
@Composable
private fun HistoryRow(title: String, sub: String, cover: String?, onClick: () -> Unit, onCover: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Cover(cover, Modifier.width(56.dp).height(80.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onCover))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 21.sp)
            Spacer(Modifier.height(4.dp))
            Text(sub, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, "Убрать из истории", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

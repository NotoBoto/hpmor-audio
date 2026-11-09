package io.github.notoboto.unison.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import io.github.notoboto.unison.app
import io.github.notoboto.unison.data.Book
import io.github.notoboto.unison.data.LibEntry
import io.github.notoboto.unison.download.DlState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookScreen(nav: NavController, key: String) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val catalogs by app.repo.catalogs.collectAsState()
    val chaptersMap by app.repo.chapters.collectAsState()
    val dlJobs by app.downloads.states.collectAsState()
    val book: Book = remember(catalogs, chaptersMap) { app.repo.book(key) } ?: run { EmptyState("📕", "Книга не найдена"); return }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(key) { if (book.chapters.isEmpty() || !app.repo.inLibrary(key)) app.repo.refreshBook(book) }

    val s = st.settings
    val prog = st.progress[key].orEmpty()
    val list = if (s.sortDesc) book.chapters.reversed() else book.chapters
    var sel by remember { mutableStateOf(setOf<Int>()) }
    var anchor by remember { mutableStateOf<Int?>(null) }
    var descOpen by remember { mutableStateOf(false) }
    var filterSheet by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var dlMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val lazy = rememberLazyListState()
    val scrolled by remember { derivedStateOf { lazy.firstVisibleItemIndex > 0 || lazy.firstVisibleItemScrollOffset > 300 } }
    val inLib = st.library.any { io.github.notoboto.unison.data.bookKey(it.sourceUrl, it.bookId) == key }

    fun dl(n: Int) = app.downloads.state(book, n, dlJobs)
    fun continueTarget(): Int? {
        val last = st.last[key]
        var ch = book.chapters.find { it.n == last }
        if (ch != null && prog[ch.n]?.done == true) ch = book.chapters.firstOrNull { it.n > ch!!.n } ?: ch
        return (ch ?: book.chapters.firstOrNull { prog[it.n]?.done != true } ?: book.chapters.firstOrNull())?.n
    }
    fun toggle(n: Int) { sel = if (n in sel) sel - n else { anchor = n; sel + n } }
    fun longPress(n: Int) {
        val a = anchor
        if (sel.isNotEmpty() && a != null && n !in sel) {
            val order = list.map { it.n }; val i = order.indexOf(a); val j = order.indexOf(n)
            sel = sel + order.subList(minOf(i, j), maxOf(i, j) + 1); anchor = n
        } else toggle(n)
    }
    fun open(n: Int) { app.player.open(book, n, autoplay = false); nav.navigate("reader/$key/$n") }

    Scaffold(
        topBar = {
            if (sel.isNotEmpty()) TopAppBar(
                navigationIcon = { IconButton(onClick = { sel = emptySet() }) { Icon(Icons.Filled.Close, "Отменить выбор") } },
                title = { Text("${sel.size}") },
                actions = {
                    IconButton(onClick = { sel = book.chapters.map { it.n }.toSet() }) { Icon(Icons.Filled.SelectAll, "Выбрать все") }
                    IconButton(onClick = { sel = book.chapters.map { it.n }.toSet() - sel }) { Icon(Icons.Filled.FlipToBack, "Инвертировать") }
                })
            else TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (scrolled) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent),
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = { if (scrolled) Text(book.meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    Box {
                        IconButton(onClick = { dlMenu = true }) { Icon(Icons.Filled.Download, "Скачать") }
                        DropdownMenu(dlMenu, { dlMenu = false }) {
                            val from = continueTarget() ?: 0
                            val ahead = book.chapters.filter { it.n >= from && !app.store.isDownloaded(key, it.n) }.map { it.n }
                            fun q(ns: List<Int>) { dlMenu = false; val k = app.downloads.enqueue(book, ns); scope.launch { snack.showSnackbar(if (k > 0) "В очереди: ${chapters(k)}" else "Уже скачано") } }
                            DropdownMenuItem({ Text("Следующую главу") }, { q(ahead.take(1)) })
                            DropdownMenuItem({ Text("Следующие 5") }, { q(ahead.take(5)) })
                            DropdownMenuItem({ Text("Следующие 10") }, { q(ahead.take(10)) })
                            DropdownMenuItem({ Text("Непрослушанные") }, { q(book.chapters.filter { prog[it.n]?.done != true }.map { it.n }) })
                            DropdownMenuItem({ Text("Все") }, { q(book.chapters.map { it.n }) })
                        }
                    }
                    IconButton(onClick = { filterSheet = true }) { Icon(Icons.Filled.FilterList, "Сортировка") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Меню") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Удалить все скачанные") }, { menu = false; confirmDelete = true })
                            val old = app.downloads.outdated(listOf(book)).size
                            DropdownMenuItem({ Text("Обновить скачанные ($old)") }, { menu = false; app.downloads.updateOutdated(listOf(book)) }, enabled = old > 0)
                            DropdownMenuItem({ Text("Настройки") }, { menu = false; nav.navigate("settings") })
                        }
                    }
                })
        },
        bottomBar = {
            AnimatedVisibility(sel.isNotEmpty(), enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                BottomAppBar {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        SelAction(Icons.Filled.DoneAll, "Прослушано") { app.store.setDone(key, sel, true); sel = emptySet() }
                        SelAction(Icons.Filled.RemoveDone, "Не прослушано") { app.store.setDone(key, sel, false); sel = emptySet() }
                        SelAction(Icons.AutoMirrored.Filled.PlaylistAddCheck, "Предыдущие") {
                            val mx = sel.max(); app.store.setDone(key, book.chapters.filter { it.n < mx }.map { it.n }, true); sel = emptySet()
                        }
                        SelAction(Icons.Filled.Download, "Скачать") { app.downloads.enqueue(book, sel); sel = emptySet() }
                        SelAction(Icons.Filled.Delete, "Удалить") { app.downloads.delete(book, sel); sel = emptySet() }
                    }
                }
            }
        },
        floatingActionButton = {
            if (sel.isEmpty()) continueTarget()?.let { n ->
                val started = prog.isNotEmpty()
                ExtendedFloatingActionButton(onClick = { app.player.open(book, n, autoplay = true); nav.navigate("reader/$key/$n") },
                    expanded = !lazy.isScrollInProgress || lazy.firstVisibleItemIndex == 0,
                    icon = { Icon(Icons.Filled.PlayArrow, null) }, text = { Text(if (started) "Продолжить" else "Начать") },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            }
        },
        snackbarHost = { SnackbarHost(snack) },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        LazyColumn(state = lazy, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = pad.calculateBottomPadding() + 96.dp)) {
            item(key = "hero") {
                Box {
                    AsyncImage(book.coverUrl, null, Modifier.matchParentSize().blur(18.dp), contentScale = ContentScale.Crop, alpha = .45f)
                    Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
                    Row(Modifier.statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        Cover(book.coverUrl, Modifier.width(112.dp).height(162.dp))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(book.meta.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 25.sp)
                            Spacer(Modifier.height(8.dp))
                            book.meta.author?.let { Meta(Icons.Outlined.Person, it) }
                            book.meta.translator?.let { Meta(Icons.Outlined.Translate, it) }
                            val total = book.chapters.sumOf { it.duration }
                            Meta(Icons.Outlined.Schedule, "${hours(total)} • озвучено ${book.chapters.size}" + (book.meta.total?.let { " из $it" } ?: ""))
                        }
                    }
                }
            }
            item(key = "actions") {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = {
                        app.store.update { s2 -> if (inLib) s2.copy(library = s2.library.filter { io.github.notoboto.unison.data.bookKey(it.sourceUrl, it.bookId) != key })
                            else s2.copy(library = s2.library + LibEntry(book.source.url, book.meta.id)) }
                    }) {
                        Icon(if (inLib) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (inLib) "В библиотеке" else "Добавить в библиотеку")
                    }
                }
            }
            item(key = "desc") {
                Column(Modifier.padding(horizontal = 16.dp).clickable { descOpen = !descOpen }) {
                    Text(book.meta.description.orEmpty(), fontSize = 14.5.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (descOpen) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                    Icon(if (descOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.align(Alignment.CenterHorizontally))
                }
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    book.meta.tags.forEach { AssistChip(onClick = {}, label = { Text(it, fontSize = 13.sp) }) }
                }
            }
            item(key = "count") {
                val dled = st.downloaded[key].orEmpty()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(chapters(book.chapters.size), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    if (dled.isNotEmpty()) Text("скачано ${dled.size} • ${mb(book.chapters.filter { it.n in dled }.sumOf { it.bytes })}",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(list, key = { it.n }) { ch ->
                val p = prog[ch.n] ?: io.github.notoboto.unison.data.Progress()
                ChapterRow(chName(ch, s.fullTitles), subline(p, ch), read = p.done, selected = ch.n in sel, dl = dl(ch.n),
                    onClick = { if (sel.isNotEmpty()) toggle(ch.n) else open(ch.n) }, onLongClick = { longPress(ch.n) },
                    onDownload = { app.downloads.enqueue(book, listOf(ch.n)) }, onCancel = { app.downloads.cancel(book, ch.n) },
                    onDelete = { app.downloads.delete(book, listOf(ch.n)) }, onUpdate = { app.downloads.enqueue(book, listOf(ch.n), force = true) })
            }
        }
    }

    if (filterSheet) ModalBottomSheet(onDismissRequest = { filterSheet = false }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle("Сортировка")
            Choice("Сначала последние", s.sortDesc) { app.store.settings { it.copy(sortDesc = true) } }
            Choice("По порядку, с первой главы", !s.sortDesc) { app.store.settings { it.copy(sortDesc = false) } }
            SheetTitle("Отображение")
            Choice("Название главы", s.fullTitles) { app.store.settings { it.copy(fullTitles = true) } }
            Choice("«Глава N»", !s.fullTitles) { app.store.settings { it.copy(fullTitles = false) } }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Удалить скачанные?") },
        text = { Text("Все скачанные главы «${book.shortTitle}» будут удалены с телефона. Прогресс прослушивания сохранится.") },
        confirmButton = { TextButton(onClick = { app.downloads.delete(book, book.chapters.map { it.n }); confirmDelete = false }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } })
}

@Composable
private fun Meta(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) =
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

@Composable
private fun SelAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) =
    Column(Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, label)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

@Composable
fun SheetTitle(t: String) = Text(t, Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick); Text(label, fontSize = 16.sp)
    }

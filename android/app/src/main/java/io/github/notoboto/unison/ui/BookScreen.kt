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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val marks = st.bookmarks[key].orEmpty()
    val dledMap = st.downloaded[key].orEmpty()
    val list = (if (s.sortDesc) book.chapters.reversed() else book.chapters).filter { c ->
        (!s.chFilterDownloaded && !s.downloadedOnly || c.n in dledMap) && (!s.chFilterUnread || prog[c.n]?.done != true) &&
            (!s.chFilterBookmarked || marks.any { it.n == c.n })
    }
    var catDialog by remember { mutableStateOf(false) }
    var editDialog by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
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

    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val header: @Composable () -> Unit = {
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
                        Meta(Icons.Outlined.Schedule, if (book.source.url == io.github.notoboto.unison.data.LOCAL_URL) "${chapters(book.chapters.size)} • озвучено ${st.downloaded[key].orEmpty().size}" else "${hours(total)} • озвучено ${book.chapters.size}" + (book.meta.total?.let { " из $it" } ?: ""))
                    }
                }
            }
            Row(Modifier.padding(horizontal = 8.dp)) {
                TextButton(onClick = {
                    app.store.update { s2 -> if (inLib) s2.copy(library = s2.library.filter { io.github.notoboto.unison.data.bookKey(it.sourceUrl, it.bookId) != key })
                        else s2.copy(library = s2.library + LibEntry(book.source.url, book.meta.id)) }
                }) {
                    Icon(if (inLib) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (inLib) "В библиотеке" else "Добавить в библиотеку")
                }
            }
            Column(Modifier.padding(horizontal = 16.dp).clickable { descOpen = !descOpen }) {
                Text(book.meta.description.orEmpty(), fontSize = 14.5.sp, lineHeight = 21.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (descOpen) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                Icon(if (descOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.align(Alignment.CenterHorizontally))
            }
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                book.meta.tags.forEach { AssistChip(onClick = {}, label = { Text(it, fontSize = 13.sp) }) }
            }

    }
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
                            if (book.source.url == io.github.notoboto.unison.data.LOCAL_URL) Text("Озвучить синтезатором:", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            DropdownMenuItem({ Text("Закладки (${marks.size})") }, { menu = false; nav.navigate("bookmarks/$key") })
                            DropdownMenuItem({ Text("Выделения (${st.highlights[key].orEmpty().size})") }, { menu = false; nav.navigate("highlights/$key") })
                            DropdownMenuItem({ Text("Действующие лица") }, { menu = false; nav.navigate("characters/$key") })
                            DropdownMenuItem({ Text("Глоссарий") }, { menu = false; nav.navigate("glossary/$key") })
                            DropdownMenuItem({ Text("Поиск по книге") }, { menu = false; nav.navigate("search/$key") })
                            DropdownMenuItem({ Text("Категории…") }, { menu = false; catDialog = true })
                            DropdownMenuItem({ Text("Изменить…") }, { menu = false; editDialog = true })
                            DropdownMenuItem({ Text("Поделиться") }, { menu = false; shareBook(ctx, book) })
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
        Row(Modifier.fillMaxSize()) {
        if (wide) Column(Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState())) { header() }
        LazyColumn(state = lazy, modifier = Modifier.weight(if (wide) 0.58f else 1f).fillMaxHeight(),
            contentPadding = PaddingValues(top = if (wide) 72.dp else 0.dp, bottom = pad.calculateBottomPadding() + 96.dp)) {
            if (!wide) item(key = "header") { header() }
            item(key = "count") {
                val dled = st.downloaded[key].orEmpty()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(chapters(book.chapters.size), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.weight(1f))
                    if (dled.isNotEmpty()) Text("скачано ${dled.size} • ${mb(book.chapters.filter { it.n in dled }.sumOf { it.bytes })}",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (list.isEmpty()) item { Text(if (book.chapters.isEmpty()) "Загружаю список глав…" else "Нет глав под выбранный фильтр",
                Modifier.fillMaxWidth().padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list, key = { it.n }) { ch ->
                val p = prog[ch.n] ?: io.github.notoboto.unison.data.Progress()
                val bm = marks.count { it.n == ch.n }
                SwipeRow(s.swipeLeft, s.swipeRight, read = p.done, downloaded = ch.n in dledMap, enabled = sel.isEmpty(),
                    onAction = { a -> when (a) {
                        "read" -> app.store.setDone(key, listOf(ch.n), !p.done)
                        "download" -> if (ch.n in dledMap) app.downloads.delete(book, listOf(ch.n)) else app.downloads.enqueue(book, listOf(ch.n))
                    } }) {
                ChapterRow(chName(ch, s.fullTitles), subline(p, ch) + (if (bm > 0) " • закладок: $bm" else ""), read = p.done, selected = ch.n in sel, dl = dl(ch.n),
                    onClick = { if (sel.isNotEmpty()) toggle(ch.n) else open(ch.n) }, onLongClick = { longPress(ch.n) },
                    onDownload = { app.downloads.enqueue(book, listOf(ch.n)) }, onCancel = { app.downloads.cancel(book, ch.n) },
                    onDelete = { app.downloads.delete(book, listOf(ch.n)) }, onUpdate = { app.downloads.enqueue(book, listOf(ch.n), force = true) })
                }
            }
        }
        }
    }

    if (filterSheet) ModalBottomSheet(onDismissRequest = { filterSheet = false }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle("Фильтр")
            FilterCheck("Скачанные", s.chFilterDownloaded) { v -> app.store.settings { it.copy(chFilterDownloaded = v) } }
            FilterCheck("Непрослушанные", s.chFilterUnread) { v -> app.store.settings { it.copy(chFilterUnread = v) } }
            FilterCheck("С закладками", s.chFilterBookmarked) { v -> app.store.settings { it.copy(chFilterBookmarked = v) } }
            SheetTitle("Сортировка")
            Choice("Сначала последние", s.sortDesc) { app.store.settings { it.copy(sortDesc = true) } }
            Choice("По порядку, с первой главы", !s.sortDesc) { app.store.settings { it.copy(sortDesc = false) } }
            SheetTitle("Отображение")
            Choice("Название главы", s.fullTitles) { app.store.settings { it.copy(fullTitles = true) } }
            Choice("«Глава N»", !s.fullTitles) { app.store.settings { it.copy(fullTitles = false) } }
        }
    }
    if (catDialog) BookCategoriesDialog(key, onDismiss = { catDialog = false }, onManage = { catDialog = false; nav.navigate("categories") })
    if (editDialog) EditBookDialog(book) { editDialog = false }
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

fun shareBook(ctx: android.content.Context, book: Book) {
    val url = book.source.url + "#/book/" + book.meta.id
    val text = book.meta.title + (book.meta.author?.let { " — $it" } ?: "") + "\n" + url
    ctx.startActivity(android.content.Intent.createChooser(
        android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), null))
}

@Composable
private fun FilterCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(checked, onChange); Text(label, fontSize = 16.sp)
    }

/** Mihon-like swipe on a chapter row: the action runs and the row springs back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeRow(left: String, right: String, read: Boolean, downloaded: Boolean, enabled: Boolean, onAction: (String) -> Unit, content: @Composable () -> Unit) {
    if (!enabled || (left == "none" && right == "none")) { content(); return }
    val state = androidx.compose.material3.rememberSwipeToDismissBoxState(confirmValueChange = { v ->
        when (v) {
            androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> onAction(right)
            androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> onAction(left)
            else -> {}
        }
        false
    }, positionalThreshold = { it * 0.3f })
    androidx.compose.material3.SwipeToDismissBox(state, enableDismissFromStartToEnd = right != "none", enableDismissFromEndToStart = left != "none",
        backgroundContent = {
            val dir = state.dismissDirection
            val action = if (dir == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) right else left
            val icon = when (action) { "read" -> if (read) Icons.Filled.RemoveDone else Icons.Filled.DoneAll; else -> if (downloaded) Icons.Filled.Delete else Icons.Filled.Download }
            val label = when (action) { "read" -> if (read) "Не прослушано" else "Прослушано"; else -> if (downloaded) "Удалить" else "Скачать" }
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 24.dp),
                horizontalArrangement = if (dir == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Spacer(Modifier.width(8.dp))
                Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }) { Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() } }
}

@Composable
private fun EditBookDialog(book: Book, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val cur = app.store.s.edits[book.key]
    val original = remember(book.key) { app.repo.book(book.key)?.let { b -> app.store.s.edits[b.key]; b } }
    var title by remember { mutableStateOf(cur?.title ?: book.meta.title) }
    var cover by remember { mutableStateOf(cur?.cover) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val f = java.io.File(java.io.File(ctx.filesDir, "covers").apply { mkdirs() }, "${book.key}_${System.currentTimeMillis()}.jpg")
            ctx.contentResolver.openInputStream(it)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            cover = f.toURI().toString()
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Изменить книгу") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.OutlinedTextField(title, { title = it }, label = { Text("Название") })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(cover ?: book.coverSmUrl, Modifier.width(60.dp).height(86.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    TextButton(onClick = { pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Выбрать обложку") }
                    TextButton(onClick = { app.store.update { s -> s.copy(edits = s.edits - book.key) }; onDismiss() }) { Text("Вернуть исходные") }
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            val orig = original?.let { app.repo.catalogs.value[it.source.url]?.find { m -> m.id == it.meta.id }?.title } ?: book.meta.title
            app.store.update { s -> s.copy(edits = s.edits + (book.key to io.github.notoboto.unison.data.BookEdit(title.trim().takeIf { it.isNotBlank() && it != orig }, cover))) }
            onDismiss()
        }) { Text("Сохранить") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

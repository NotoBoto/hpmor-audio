package io.github.notoboto.unison.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.notoboto.unison.app
import io.github.notoboto.unison.data.Book
import io.github.notoboto.unison.data.Source
import io.github.notoboto.unison.data.bookKey
import io.github.notoboto.unison.data.normalizeSource
import io.github.notoboto.unison.data.AppState
import io.github.notoboto.unison.data.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun BookTile(b: Book, unread: Int, downloaded: Int, inLib: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
        Cover(b.coverSmUrl, Modifier.fillMaxSize())
        Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
            .padding(start = 8.dp, end = 8.dp, top = 26.dp, bottom = 7.dp)) {
            Text(b.meta.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
        }
        Row(Modifier.padding(6.dp).clip(RoundedCornerShape(6.dp))) {
            if (unread > 0) Text("$unread", Modifier.background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (downloaded > 0) Text("$downloaded", Modifier.background(Color(0xFF3F8DE0)).padding(horizontal = 6.dp, vertical = 2.dp),
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            if (inLib) Text("В библиотеке", Modifier.background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 2.dp),
                color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(nav: NavController) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val catalogs by app.repo.catalogs.collectAsState()
    val chapters by app.repo.chapters.collectAsState()
    val refreshing by app.repo.refreshing.collectAsState()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf(false) }
    val s = st.settings
    val all = remember(st.library, catalogs, chapters) { app.repo.libraryBooks() }
    fun unread(b: Book) = b.chapters.count { st.progress[b.key]?.get(it.n)?.done != true }
    fun dled(b: Book) = st.downloaded[b.key].orEmpty().size
    var query by remember { mutableStateOf<String?>(null) }
    var catTab by remember { mutableStateOf(0) }
    // tabs: categories (+ "По умолчанию" for books without a category, if any)
    val cats = st.categories
    val uncategorized = all.filter { st.bookCategories[it.key].isNullOrEmpty() }
    val tabs: List<Pair<String, List<Book>>> = if (cats.isEmpty()) listOf("" to all) else
        (if (uncategorized.isNotEmpty()) listOf("По умолчанию" to uncategorized) else emptyList()) +
            cats.map { c -> c.name to all.filter { c.id in st.bookCategories[it.key].orEmpty() } }
    val tabIdx = catTab.coerceIn(0, (tabs.size - 1).coerceAtLeast(0))
    val inTab = tabs.getOrNull(tabIdx)?.second ?: all
    val q = query?.trim()?.lowercase().orEmpty()
    val books = inTab.filter { (!s.filterDownloaded || dled(it) > 0) && (!s.filterUnread || unread(it) > 0) && (!s.downloadedOnly || dled(it) > 0) &&
        (q.isEmpty() || it.meta.title.lowercase().contains(q) || it.meta.author.orEmpty().lowercase().contains(q)) }.let { l ->
        val sorted = when (s.libSort) {
            "last" -> l.sortedBy { b -> -(st.history.firstOrNull { it.book == b.key }?.ts ?: 0L) }
            "unread" -> l.sortedBy { -unread(it) }
            "added" -> l.sortedBy { b -> -(st.library.find { bookKey(it.sourceUrl, it.bookId) == b.key }?.added ?: 0L) }
            "chapters" -> l.sortedBy { -it.chapters.size }
            else -> l.sortedBy { it.meta.title.lowercase() }
        }
        if (s.libSortDesc) sorted.reversed() else sorted
    }
    val filtered = s.filterDownloaded || s.filterUnread
    val open = { b: Book -> nav.navigate("book/${b.key}") }
    val cont = { b: Book -> continueTarget(b, st)?.let { n -> app.player.open(b, n, autoplay = true); nav.navigate("reader/${b.key}/$n") }; Unit }

    Scaffold(topBar = {
        Column { TopAppBar(title = {
            if (query == null) Text("Библиотека")
            else androidx.compose.material3.TextField(query!!, { query = it }, placeholder = { Text("Поиск по библиотеке") }, singleLine = true,
                colors = androidx.compose.material3.TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), modifier = Modifier.fillMaxWidth())
        }, navigationIcon = { if (query != null) IconButton(onClick = { query = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Закрыть поиск") } },
        actions = {
            if (query == null) IconButton(onClick = { query = "" }) { Icon(androidx.compose.material.icons.Icons.Filled.Search, "Поиск") }
            IconButton(onClick = { sheet = true }) {
                Icon(Icons.Filled.FilterList, "Фильтр, сортировка, вид", tint = if (filtered) MaterialTheme.colorScheme.primary else LocalContentColor.current)
            }
            IconButton(onClick = { scope.launch { val f = app.repo.refreshAll(); snack.showSnackbar(if (f == 0) "Библиотека обновлена" else "Не удалось обновить: $f") } }) {
                Icon(Icons.Filled.Refresh, "Обновить")
            }
        })
        if (tabs.size > 1) androidx.compose.material3.PrimaryScrollableTabRow(tabIdx, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, (name, list) -> Tab(i == tabIdx, { catTab = i }, text = { Text("$name (${list.size})") }) }
        } }
    }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        PullToRefreshBox(refreshing, onRefresh = { scope.launch { app.repo.refreshAll() } }, Modifier.padding(pad).fillMaxSize()) {
            if (books.isEmpty()) EmptyState("📚", when { refreshing -> "Загружаю…"; filtered || q.isNotEmpty() || s.downloadedOnly -> "Ничего не найдено."
                else -> "Библиотека пуста.\nДобавьте книги во вкладке «Обзор»." })
            else if (s.libDisplay == "list") LazyColumn(Modifier.fillMaxSize()) {
                items(books, key = { it.key }) { b -> LibItem(b, "list", unread(b), dled(b), s, { open(b) }, { cont(b) }) }
            }
            else LazyVerticalGrid(if (s.libColumns > 0) GridCells.Fixed(s.libColumns) else GridCells.Adaptive(108.dp), contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(books, key = { it.key }) { b -> LibItem(b, s.libDisplay, unread(b), dled(b), s, { open(b) }, { cont(b) }) }
            }
        }
    }
    if (sheet) LibrarySheet { sheet = false }
}

fun continueTarget(b: Book, st: AppState): Int? {
    val prog = st.progress[b.key].orEmpty()
    var ch = b.chapters.find { it.n == st.last[b.key] }
    if (ch != null && prog[ch.n]?.done == true) ch = b.chapters.firstOrNull { it.n > ch!!.n } ?: ch
    return (ch ?: b.chapters.firstOrNull { prog[it.n]?.done != true } ?: b.chapters.firstOrNull())?.n
}

@Composable
private fun Badges(unread: Int, downloaded: Int, s: Settings, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(6.dp))) {
        if (s.badgeUnread && unread > 0) Text("$unread", Modifier.background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (s.badgeDownloaded && downloaded > 0) Text("$downloaded", Modifier.background(Color(0xFF3F8DE0)).padding(horizontal = 6.dp, vertical = 2.dp),
            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ContinueButton(modifier: Modifier, onClick: () -> Unit) =
    FilledIconButton(onClick = onClick, modifier = modifier.size(34.dp),
        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .92f))) {
        Icon(Icons.Filled.PlayArrow, "Продолжить", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }

@Composable
private fun LibItem(b: Book, mode: String, unread: Int, downloaded: Int, s: Settings, onClick: () -> Unit, onContinue: () -> Unit) {
    when (mode) {
        "list" -> Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(b.coverSmUrl, Modifier.size(width = 40.dp, height = 56.dp))
            Spacer(Modifier.size(12.dp))
            Text(b.meta.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
            Badges(unread, downloaded, s)
            if (s.libContinueButton) { Spacer(Modifier.size(8.dp)); ContinueButton(Modifier, onContinue) }
        }
        "comfortable" -> Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
            Box(Modifier.aspectRatio(2f / 3f)) {
                Cover(b.coverSmUrl, Modifier.fillMaxSize())
                Badges(unread, downloaded, s, Modifier.padding(6.dp))
                if (s.libContinueButton) ContinueButton(Modifier.align(Alignment.BottomEnd).padding(6.dp), onContinue)
            }
            Text(b.meta.title, Modifier.padding(horizontal = 4.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
        }
        else -> Box(Modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick)) {
            Cover(b.coverSmUrl, Modifier.fillMaxSize())
            if (mode == "compact") Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xD9000000))))
                .padding(start = 8.dp, end = if (s.libContinueButton) 44.dp else 8.dp, top = 26.dp, bottom = 7.dp)) {
                Text(b.meta.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
            }
            Badges(unread, downloaded, s, Modifier.padding(6.dp))
            if (s.libContinueButton) ContinueButton(Modifier.align(Alignment.BottomEnd).padding(6.dp), onContinue)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LibrarySheet(onClose: () -> Unit) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val s = st.settings
    fun set(f: (Settings) -> Settings) = app.store.settings(f)
    var tab by remember { mutableStateOf(2) }
    ModalBottomSheet(onDismissRequest = onClose) {
        PrimaryTabRow(tab) { listOf("Фильтр", "Сортировка", "Вид").forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) } }
        Column(Modifier.navigationBarsPadding().padding(vertical = 8.dp)) {
            when (tab) {
                0 -> {
                    CheckRow("Скачанные", s.filterDownloaded) { v -> set { it.copy(filterDownloaded = v) } }
                    CheckRow("Есть непрослушанные главы", s.filterUnread) { v -> set { it.copy(filterUnread = v) } }
                }
                1 -> listOf("title" to "По алфавиту", "last" to "Последние прослушанные", "unread" to "Непрослушанных глав",
                    "chapters" to "Всего глав", "added" to "Дата добавления").forEach { (v, l) ->
                    Row(Modifier.fillMaxWidth().clickable { set { if (it.libSort == v) it.copy(libSortDesc = !it.libSortDesc) else it.copy(libSort = v, libSortDesc = false) } }
                        .padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp)) {
                            if (s.libSort == v) Icon(if (s.libSortDesc) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.size(16.dp)); Text(l, fontSize = 16.sp)
                    }
                }
                else -> {
                    SheetTitle("Режим отображения")
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("compact" to "Компактная сетка", "comfortable" to "Удобная сетка", "cover" to "Только обложки", "list" to "Список").forEach { (v, l) ->
                            FilterChip(s.libDisplay == v, { set { it.copy(libDisplay = v) } }, label = { Text(l) })
                        }
                    }
                    if (s.libDisplay != "list") {
                        SheetTitle("Обложек в ряду: " + if (s.libColumns == 0) "авто" else "${s.libColumns}")
                        Slider(s.libColumns.toFloat(), { v -> set { it.copy(libColumns = v.roundToInt()) } }, Modifier.padding(horizontal = 20.dp), valueRange = 0f..10f, steps = 9)
                    }
                    SheetTitle("Значки")
                    CheckRow("Непрослушанные главы", s.badgeUnread) { v -> set { it.copy(badgeUnread = v) } }
                    CheckRow("Скачанные главы", s.badgeDownloaded) { v -> set { it.copy(badgeDownloaded = v) } }
                    SheetTitle("Прочее")
                    CheckRow("Кнопка «Продолжить» на обложке", s.libContinueButton) { v -> set { it.copy(libContinueButton = v) } }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange); Text(label, fontSize = 16.sp)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(nav: NavController) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val catalogs by app.repo.catalogs.collectAsState()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Source?>(null) }
    val snack = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    val audioLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { io.github.notoboto.unison.data.Local.importAudio(ctx, uris) } }
                .onSuccess { m ->
                    app.repo.reloadLocal()
                    val k = bookKey(io.github.notoboto.unison.data.LOCAL_URL, m.id)
                    app.repo.book(k)?.chapters?.forEach { app.store.markDownloaded(k, it.n, "audio") }
                    app.store.update { s -> s.copy(library = s.library + io.github.notoboto.unison.data.LibEntry(io.github.notoboto.unison.data.LOCAL_URL, m.id)) }
                    nav.navigate("book/$k")
                }.onFailure { snack.showSnackbar("Не удалось открыть файлы: ${it.message}") }
        }
    }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> scope.launch {
            val r = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { io.github.notoboto.unison.data.Local.import(ctx, u) } }
            r.onSuccess { m ->
                app.repo.reloadLocal()
                app.store.update { s -> s.copy(library = s.library + io.github.notoboto.unison.data.LibEntry(io.github.notoboto.unison.data.LOCAL_URL, m.id)) }
                nav.navigate("book/" + bookKey(io.github.notoboto.unison.data.LOCAL_URL, m.id))
            }.onFailure { snack.showSnackbar("Не удалось прочитать файл: ${it.message}") }
        } }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Обзор") }, actions = { IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Добавить источник") } })
    }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        var gq by remember { mutableStateOf("") }
        LazyColumn(Modifier.padding(pad)) {
            item {
                OutlinedTextField(gq, { gq = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true,
                    placeholder = { Text("Поиск по всем источникам") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (gq.isNotEmpty()) IconButton(onClick = { gq = "" }) { Icon(androidx.compose.material.icons.Icons.Filled.Close, "Очистить") } })
            }
            if (gq.isNotBlank()) {
                val qq = gq.trim().lowercase()
                val hits = st.sources.flatMap { src -> catalogs[src.url].orEmpty().filter { m ->
                    listOf(m.title, m.author.orEmpty(), m.description.orEmpty(), m.tags.joinToString(" ")).any { it.lowercase().contains(qq) } }.map { src to it } }
                item { GroupHeader(if (hits.isEmpty()) "Ничего не найдено" else "Найдено: ${hits.size}") }
                items(hits, key = { (src, m) -> bookKey(src.url, m.id) }) { (src, m) ->
                    val b = Book(src, m)
                    ListItem(headlineContent = { Text(m.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(listOfNotNull(m.author, src.name).joinToString(" • ")) },
                        leadingContent = { Cover(b.coverSmUrl, Modifier.size(width = 40.dp, height = 56.dp)) },
                        modifier = Modifier.clickable { nav.navigate("book/${b.key}") })
                }
                return@LazyColumn
            }
            item {
                ListItem(headlineContent = { Text("Импортировать книгу") }, supportingContent = { Text("EPUB, FB2 или TXT с телефона — озвучка синтезатором речи") },
                    leadingContent = { Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/epub+zip", "application/x-fictionbook+xml", "text/plain", "application/octet-stream", "*/*")) })
                ListItem(headlineContent = { Text("Импортировать аудиокнигу") }, supportingContent = { Text("MP3, M4B, M4A, OGG — один или несколько файлов, без текста") },
                    leadingContent = { Icon(Icons.Filled.Headphones, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { audioLauncher.launch(arrayOf("audio/*")) })
                val local = catalogs[io.github.notoboto.unison.data.LOCAL_URL].orEmpty()
                if (local.isNotEmpty()) ListItem(headlineContent = { Text("На телефоне") }, supportingContent = { Text("Импортированные книги: ${local.size}") },
                    leadingContent = { Icon(Icons.Filled.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable { nav.navigate("catalog/-1") })
            }
            item { GroupHeader("Источники") }
            itemsIndexed(st.sources) { i, src ->
                val n = catalogs[src.url]?.size
                ListItem(headlineContent = { Text(src.name) },
                    supportingContent = { Text(src.url.removePrefix("https://").removeSuffix("/") + (n?.let { " • ${it} ${plural(it, "книга", "книги", "книг")}" } ?: "")) },
                    leadingContent = { Icon(Icons.Filled.Public, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { if (i > 0) IconButton(onClick = { removing = src }) { Icon(Icons.Filled.Delete, "Удалить источник") } },
                    modifier = Modifier.clickable { nav.navigate("catalog/$i") })
            }
            item {
                Text("Источник — это сайт с файлом books.json в формате Unison. Книги из источника можно добавить в библиотеку.",
                    Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (adding) {
        var url by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { adding = false }, title = { Text("Новый источник") }, text = {
            Column {
                OutlinedTextField(url, { url = it }, label = { Text("Адрес") }, placeholder = { Text("https://example.github.io/books/") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Название (необязательно)") }, singleLine = true)
            }
        }, confirmButton = {
            TextButton(onClick = {
                val src = Source(normalizeSource(url), name.ifBlank { normalizeSource(url).removePrefix("https://").substringBefore('/') })
                adding = false
                scope.launch {
                    val r = app.repo.refreshSource(src)
                    if (r.isSuccess) { app.store.update { s -> if (s.sources.any { it.url == src.url }) s else s.copy(sources = s.sources + src) }
                        snack.showSnackbar("Источник добавлен: ${r.getOrNull()?.size} книг") }
                    else snack.showSnackbar("Не нашёл books.json по этому адресу")
                }
            }, enabled = url.isNotBlank()) { Text("Добавить") }
        }, dismissButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } })
    }
    removing?.let { src ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Удалить источник?") },
            text = { Text("«${src.name}» и его книги будут убраны из библиотеки. Скачанные главы останутся, пока вы их не удалите.") },
            confirmButton = { TextButton(onClick = { app.store.update { s -> s.copy(sources = s.sources - src, library = s.library.filter { it.sourceUrl != src.url }) }; removing = null }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Отмена") } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(nav: NavController, srcIdx: Int) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val catalogs by app.repo.catalogs.collectAsState()
    val src = if (srcIdx == -1) io.github.notoboto.unison.data.LOCAL_SOURCE else st.sources.getOrNull(srcIdx) ?: return
    val list = catalogs[src.url].orEmpty()
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { BackBar(nav, src.name) { IconButton(onClick = { scope.launch { app.repo.refreshSource(src) } }) { Icon(Icons.Filled.Refresh, "Обновить") } } }) { pad ->
        if (list.isEmpty()) EmptyState("🌐", "Каталог пуст или нет интернета.", Modifier.padding(pad))
        else LazyVerticalGrid(GridCells.Adaptive(108.dp), Modifier.padding(pad), contentPadding = PaddingValues(12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { m ->
                val b = Book(src, m)
                BookTile(b, 0, 0, inLib = st.library.any { bookKey(it.sourceUrl, it.bookId) == b.key }) { nav.navigate("book/${b.key}") }
            }
        }
    }
}

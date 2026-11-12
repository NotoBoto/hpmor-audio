package io.github.notoboto.unison.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import io.github.notoboto.unison.app
import io.github.notoboto.unison.sources.BookDetails
import io.github.notoboto.unison.sources.BookStub
import io.github.notoboto.unison.sources.ChapterStub
import io.github.notoboto.unison.sources.PluginException
import io.github.notoboto.unison.sources.PluginInfo
import io.github.notoboto.unison.sources.PluginRepositories
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

// «Обзор» for book-source plugins (SOURCES_DESIGN.md §8.2): extensions, a source, global search, a plugin book card.

fun errorText(e: Throwable): String = (e as? PluginException)?.userMessage ?: (e.message ?: e.toString())

private fun encodeArg(s: String) = Uri.encode(s)

@Composable
fun PluginIcon(id: String, modifier: Modifier = Modifier.size(40.dp)) {
    val f = LocalContext.current.app().sources.repos.iconFile(id)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (f.exists()) AsyncImage(f, null, Modifier.fillMaxSize())
        else Icon(Icons.Filled.Extension, null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** Installed plugins as rows for «Обзор». */
@Composable
fun InstalledSourcesRows(nav: NavController) {
    val app = LocalContext.current.app()
    val changes by app.sources.changes.collectAsState()
    val st by app.store.state.collectAsState()
    val installed = remember(changes) { app.sources.repos.installed().values.sortedBy { it.info.name } }
    installed.filter { st.settings.showNsfw || !it.info.nsfw }.forEach { inst ->
        val p = inst.info
        ListItem(headlineContent = { Text(p.name) },
            supportingContent = { Text(listOf(p.lang.uppercase(), "версия ${p.version}").joinToString(" • ")) },
            leadingContent = { PluginIcon(p.id) },
            modifier = Modifier.clickable { nav.navigate("source/${p.id}") })
    }
    ListItem(headlineContent = { Text("Расширения") }, supportingContent = { Text("Установить, обновить, добавить репозиторий") },
        leadingContent = { Icon(Icons.Filled.Extension, null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable { nav.navigate("extensions") })
}

// ---------- extensions ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(nav: NavController) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val changes by app.sources.changes.collectAsState()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var repos by remember { mutableStateOf<List<PluginRepositories.Repo>?>(null) }
    val errors = remember { mutableStateMapOf<String, String>() }
    var refreshing by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableStateOf(0) }
    val installed = remember(changes) { app.sources.repos.installed() }

    LaunchedEffect(refreshKey, changes) {
        refreshing = true
        errors.clear()
        repos = runCatching { app.sources.repos.repos(errors) }.getOrDefault(emptyList())
        refreshing = false
    }

    fun act(label: String, block: suspend () -> Unit) = scope.launch {
        busy = label
        runCatching { block() }.onFailure { snack.showSnackbar(errorText(it)) }
        busy = null
    }

    Scaffold(topBar = {
        BackBar(nav, "Расширения") {
            IconButton(onClick = { refreshKey++ }) { Icon(Icons.Filled.Refresh, "Проверить обновления") }
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Добавить репозиторий") }
        }
    }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                ListItem(headlineContent = { Text("Обновлять автоматически") }, supportingContent = { Text("Раз в сутки, в фоне") },
                    trailingContent = { Switch(st.settings.pluginAutoUpdate, { v -> app.store.settings { it.copy(pluginAutoUpdate = v) } }) })
                ListItem(headlineContent = { Text("Показывать 18+") }, supportingContent = { Text("Источники и книги для взрослых") },
                    trailingContent = { Switch(st.settings.showNsfw, { v -> app.store.settings { it.copy(showNsfw = v) } }) })
                if (refreshing) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
            }
            val all = repos.orEmpty()
            for (repo in all) {
                item {
                    GroupHeader(repo.index.repo.name + if (repo.fromCache) " (нет связи, сохранённый список)" else "")
                }
                val list = repo.compatible.filter { st.settings.showNsfw || !it.nsfw }
                items(list, key = { repo.url + it.id }) { p ->
                    val have = installed[p.id]
                    val newer = have != null && have.repo == repo.url && PluginRepositories.compareVersions(p.version, have.info.version) > 0
                    ListItem(
                        headlineContent = { Text(p.name + if (p.nsfw) " 18+" else "") },
                        supportingContent = {
                            Text(listOfNotNull(p.lang.uppercase(), "версия ${p.version}", have?.let { if (newer) "установлена ${it.info.version}" else "установлено" }).joinToString(" • "))
                        },
                        leadingContent = { PluginIcon(p.id) },
                        trailingContent = {
                            when {
                                busy == p.id -> CircularProgressIndicator(Modifier.size(24.dp))
                                have == null -> TextButton(onClick = { act(p.id) { app.sources.install(repo.url, p) } }) { Text("Установить") }
                                newer -> TextButton(onClick = { act(p.id) { app.sources.install(repo.url, p) } }) { Text("Обновить") }
                                else -> IconButton(onClick = { act(p.id) { app.sources.uninstall(p.id) } }) { Icon(Icons.Filled.Delete, "Удалить") }
                            }
                        },
                        modifier = Modifier.clickable(enabled = have != null) { nav.navigate("source/${p.id}") })
                }
                if (!repo.builtIn) item {
                    TextButton(onClick = { act("repo") { app.sources.removeRepo(repo.url) } }, Modifier.padding(horizontal = 8.dp)) { Text("Удалить репозиторий") }
                }
            }
            if (errors.isNotEmpty()) item {
                GroupHeader("Не удалось открыть")
                errors.forEach { (url, err) -> ListItem(headlineContent = { Text(url) }, supportingContent = { Text(err) }) }
            }
            item {
                Text("Плагины ставятся только из репозиториев, подписанных ключом Unison. Контент сайтов принадлежит правообладателям: используйте его только для себя.",
                    Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (adding) {
        var url by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { adding = false }, title = { Text("Добавить репозиторий") }, text = {
            Column {
                Text("Контент сайтов принадлежит правообладателям. Использование — только личное.", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(url, { url = it }, label = { Text("Адрес") }, placeholder = { Text("https://…/") }, singleLine = true)
            }
        }, confirmButton = {
            TextButton(onClick = {
                adding = false
                act("repo") {
                    val r = app.sources.addRepo(url)
                    snack.showSnackbar("Репозиторий добавлен: ${r.index.repo.name}, ${r.compatible.size} расширений")
                }
            }, enabled = url.isNotBlank()) { Text("Добавить") }
        }, dismissButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } })
    }
}

// ---------- one source ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceScreen(nav: NavController, pluginId: String) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val inst = remember { app.sources.repos.installed()[pluginId] } ?: run { EmptyState("🧩", "Расширение не установлено"); return }
    val p = inst.info
    val tabs = buildList {
        if ("popular" in p.features) add("popular" to "Популярное")
        if ("latest" in p.features) add("latest" to "Новое")
        if ("search" in p.features) add("search" to "Поиск")
    }
    var tab by remember { mutableStateOf(tabs.firstOrNull()?.first ?: "search") }
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<BookStub>() }
    var page by remember { mutableStateOf(0) }
    var hasNext by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    val scope = rememberCoroutineScope()
    val authChanges by app.sources.auth.changes.collectAsState()
    val loggedIn = remember(authChanges) { app.sources.auth.loggedIn(p) }

    fun loadMore() {
        if (loading || !hasNext) return
        if (tab == "search" && submitted.isBlank()) return
        loading = true; error = null
        scope.launch {
            val next = page + 1
            runCatching {
                when (tab) {
                    "popular" -> app.sources.popular(p.id, next)
                    "latest" -> app.sources.latest(p.id, next)
                    else -> app.sources.search(p.id, submitted, next)
                }
            }.onSuccess { r ->
                items += r.items.filter { st.settings.showNsfw || !it.nsfw }.filter { n -> items.none { it.id == n.id } }
                hasNext = r.hasNext && r.items.isNotEmpty(); page = next
            }.onFailure { error = it }
            loading = false
        }
    }
    fun reset() { items.clear(); page = 0; hasNext = true; error = null; loadMore() }
    LaunchedEffect(tab, submitted, authChanges) { reset() }

    Scaffold(topBar = {
        BackBar(nav, p.name) {
            if (p.login != null) TextButton(onClick = { if (loggedIn) sourceLogout(nav.context, p) else nav.navigate(loginRoute(p.id)) }) {
                Text(if (loggedIn) "Выйти" else "Войти")
            }
            IconButton(onClick = { nav.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(p.site))) }) { Icon(Icons.Filled.OpenInBrowser, "Открыть сайт") } }
    }) { pad ->
        Column(Modifier.padding(pad)) {
            if (tabs.size > 1) PrimaryTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == tab }.coerceAtLeast(0)) {
                tabs.forEach { (k, l) -> Tab(tab == k, { tab = k }, text = { Text(l) }) }
            }
            if (tab == "search") OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true, placeholder = { Text("Название или автор") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { submitted = query.trim() }),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search))
            LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { b ->
                    BookStubRow(b) { nav.navigate("srcbook/${p.id}/${encodeArg(b.id)}") }
                    if (b === items.last() && hasNext && !loading) LaunchedEffect(items.size) { loadMore() }
                }
                item {
                    when {
                        loading -> Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        error != null -> SourceError(nav, p, error!!) { loadMore() }
                        items.isEmpty() && tab == "search" && submitted.isBlank() -> Text("Введите запрос", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        items.isEmpty() -> Text("Ничего не найдено", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BookStubRow(b: BookStub, onClick: () -> Unit) =
    ListItem(headlineContent = { Text(b.title + if (b.nsfw) " 18+" else "", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = b.author?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Cover(b.cover, Modifier.size(width = 40.dp, height = 56.dp)) },
        modifier = Modifier.clickable(onClick = onClick))

// ---------- global search ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(nav: NavController, initial: String) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    var query by remember { mutableStateOf(initial) }
    var submitted by remember { mutableStateOf(initial) }
    val results = remember { mutableStateMapOf<String, Result<List<BookStub>>?>() }
    val plugins = remember {
        app.sources.repos.installed().values.map { it.info }.filter { "search" in it.features }.sortedBy { it.name }
    }.filter { st.settings.showNsfw || !it.nsfw }

    LaunchedEffect(submitted) {
        results.clear()
        if (submitted.isBlank()) return@LaunchedEffect
        plugins.forEach { results[it.id] = null }
        val gate = Semaphore(3)                                   // at most 3 sources at once (§8.2)
        coroutineScope {
            plugins.map { p ->
                async {
                    gate.withPermit {
                        results[p.id] = runCatching { app.sources.search(p.id, submitted, 1).items.filter { st.settings.showNsfw || !it.nsfw } }
                    }
                }
            }.awaitAll()
        }
    }

    Scaffold(topBar = { BackBar(nav, "Поиск по расширениям") }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { submitted = query.trim() }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search))
                if (plugins.isEmpty()) Text("Нет установленных расширений с поиском", Modifier.padding(16.dp))
            }
            items(plugins, key = { it.id }) { p ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { nav.navigate("source/${p.id}") }.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        PluginIcon(p.id, Modifier.size(24.dp)); Spacer(Modifier.width(8.dp))
                        Text(p.name, fontWeight = FontWeight.SemiBold)
                    }
                    when (val r = results[p.id]) {
                        null -> if (submitted.isNotBlank()) CircularProgressIndicator(Modifier.padding(horizontal = 16.dp).size(20.dp))
                        else -> r.fold(
                            onSuccess = { list ->
                                if (list.isEmpty()) Text("Ничего не найдено", Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                                else Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    list.take(12).forEach { b ->
                                        Column(Modifier.width(96.dp).clickable { nav.navigate("srcbook/${p.id}/${encodeArg(b.id)}") }) {
                                            Cover(b.cover, Modifier.size(width = 96.dp, height = 136.dp))
                                            Text(b.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                        }
                                    }
                                }
                            },
                            onFailure = { Text(errorText(it), Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, fontSize = 14.sp) })
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

// ---------- a book of a plugin ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginBookScreen(nav: NavController, pluginId: String, bookId: String) {
    val app = LocalContext.current.app()
    var details by remember { mutableStateOf<BookDetails?>(null) }
    var chapters by remember { mutableStateOf<List<ChapterStub>?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var retry by remember { mutableStateOf(0) }
    val plugin = remember { app.sources.repos.installed()[pluginId]?.info }
    val name = plugin?.name ?: pluginId
    val authChanges by app.sources.auth.changes.collectAsState()
    LaunchedEffect(retry, authChanges) {
        error = null
        runCatching { details = app.sources.details(pluginId, bookId); chapters = app.sources.chapters(pluginId, bookId) }
            .onFailure { error = it }
    }
    val d = details
    Scaffold(topBar = {
        BackBar(nav, d?.title ?: name) {
            d?.url?.let { u -> IconButton(onClick = { nav.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))) }) { Icon(Icons.Filled.OpenInBrowser, "Открыть на сайте") } }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Row(Modifier.padding(16.dp)) {
                    Cover(d?.cover, Modifier.size(width = 100.dp, height = 144.dp))
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(d?.title ?: "…", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        d?.author?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        d?.let {
                            val status = when (it.status) { "ongoing" -> "Выходит"; "completed" -> "Завершена"; else -> null }
                            val lic = when (it.license) { "public-domain" -> "Общественное достояние"; "cc-by" -> "CC BY"; "cc-by-sa" -> "CC BY-SA"; else -> null }
                            Text(listOfNotNull(status, lic, chapters?.let { c -> "${c.size} ${plural(c.size, "глава", "главы", "глав")}" }).joinToString(" • "), fontSize = 14.sp)
                        }
                    }
                }
                if (d != null && chapters != null) {
                    val st by app.store.state.collectAsState()
                    val src = io.github.notoboto.unison.data.pluginSourceUrl(pluginId)
                    val key = io.github.notoboto.unison.data.bookKey(src, app.sources.books.internalId(d.id))
                    val inLib = st.library.any { io.github.notoboto.unison.data.bookKey(it.sourceUrl, it.bookId) == key }
                    var adding by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    Button(onClick = {
                        adding = true
                        scope.launch {
                            runCatching { app.sources.books.add(pluginId, d, chapters!!) }.onSuccess { id ->
                                app.repo.reloadLocal()
                                app.store.update { s -> if (s.library.any { it.sourceUrl == src && it.bookId == id }) s
                                    else s.copy(library = s.library + io.github.notoboto.unison.data.LibEntry(src, id)) }
                                nav.navigate("book/$key")
                            }.onFailure { error = it }
                            adding = false
                        }
                    }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), enabled = !adding) {
                        Text(if (inLib) "Открыть в библиотеке" else "В библиотеку")
                    }
                    Spacer(Modifier.height(8.dp))
                }
                d?.description?.let { Text(it, Modifier.padding(horizontal = 16.dp), fontSize = 15.sp) }
                if (d?.tags?.isNotEmpty() == true) Text(d.tags.joinToString(" • "), Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { SourceError(nav, plugin, it) { retry++ } }
                if (d == null && error == null) Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
            }
            chapters?.let { list ->
                var lastVolume: String? = null
                list.forEachIndexed { i, c ->
                    if (c.volume != null && c.volume != lastVolume) { lastVolume = c.volume; item(key = "v$i") { GroupHeader(c.volume) } }
                    item(key = "c$i") {
                        ListItem(headlineContent = { Text(c.title + if (c.locked) " 🔒" else "", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = c.date?.let { { Text(it.take(10)) } },
                            modifier = Modifier.clickable { nav.context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(c.url))) })
                    }
                }
            }
        }
    }
}

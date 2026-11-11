package io.github.notoboto.unison.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.imageLoader
import io.github.notoboto.unison.R
import io.github.notoboto.unison.app
import io.github.notoboto.unison.data.Bookmark
import io.github.notoboto.unison.data.Category
import io.github.notoboto.unison.data.InternalFiles
import io.github.notoboto.unison.data.SafFiles
import io.github.notoboto.unison.scheduleAutoBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

// ---------------- categories ----------------
@Composable
fun CategoriesScreen(nav: NavController) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    var edit by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    Scaffold(topBar = { BackBar(nav, "Категории") }, floatingActionButton = {
        FloatingActionButton(onClick = { creating = true }) { Icon(Icons.Filled.Add, "Новая категория") }
    }) { pad ->
        if (st.categories.isEmpty()) EmptyState("🗂", "Категорий пока нет.\nКатегории становятся вкладками в библиотеке.", Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad)) {
            itemsIndexed(st.categories, key = { _, c -> c.id }) { i, c ->
                val count = st.bookCategories.values.count { c.id in it }
                ListItem(headlineContent = { Text(c.name) }, supportingContent = { Text("Книг: $count") }, trailingContent = {
                    Row {
                        IconButton(onClick = { move(app, i, -1) }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, "Выше") }
                        IconButton(onClick = { move(app, i, 1) }, enabled = i < st.categories.size - 1) { Icon(Icons.Filled.ArrowDownward, "Ниже") }
                        IconButton(onClick = { edit = c }) { Icon(Icons.Filled.Edit, "Переименовать") }
                        IconButton(onClick = { deleting = c }) { Icon(Icons.Filled.Delete, "Удалить") }
                    }
                })
            }
        }
    }
    if (creating || edit != null) NameDialog(if (creating) "Новая категория" else "Переименовать", edit?.name ?: "", onDismiss = { creating = false; edit = null }) { name ->
        app.store.update { s ->
            if (edit != null) s.copy(categories = s.categories.map { if (it.id == edit!!.id) it.copy(name = name) else it })
            else s.copy(categories = s.categories + Category((s.categories.maxOfOrNull { it.id } ?: 0) + 1, name))
        }
        creating = false; edit = null
    }
    deleting?.let { c ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Удалить «${c.name}»?") }, text = { Text("Книги останутся в библиотеке.") },
            confirmButton = { TextButton(onClick = {
                app.store.update { s -> s.copy(categories = s.categories - c, bookCategories = s.bookCategories.mapValues { (_, v) -> v - c.id }) }; deleting = null
            }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } })
    }
}

private fun move(app: io.github.notoboto.unison.UnisonApp, i: Int, d: Int) = app.store.update { s ->
    val l = s.categories.toMutableList(); val j = i + d
    if (j !in l.indices) s else { val t = l[i]; l[i] = l[j]; l[j] = t; s.copy(categories = l) }
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(v, { v = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onOk(v.trim()) }, enabled = v.isNotBlank()) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

/** Checkbox dialog: which categories a book belongs to. */
@Composable
fun BookCategoriesDialog(key: String, onDismiss: () -> Unit, onManage: () -> Unit) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    var sel by remember { mutableStateOf(st.bookCategories[key].orEmpty().toSet()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Категории") }, text = {
        if (st.categories.isEmpty()) Text("Категорий пока нет. Создайте их в «Ещё → Категории».")
        else Column { st.categories.forEach { c ->
            Row(Modifier.fillMaxWidth().clickable { sel = if (c.id in sel) sel - c.id else sel + c.id }, verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Checkbox(c.id in sel, { sel = if (c.id in sel) sel - c.id else sel + c.id }); Text(c.name)
            }
        } }
    }, confirmButton = {
        TextButton(onClick = { app.store.update { s -> s.copy(bookCategories = s.bookCategories + (key to sel.toList())) }; onDismiss() }) { Text("Сохранить") }
    }, dismissButton = { TextButton(onClick = onManage) { Text("Изменить список") } })
}

// ---------------- statistics ----------------
@Composable
fun StatsScreen(nav: NavController) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val books = remember(st.library) { app.repo.libraryBooks() }
    val totalMs = st.statsDays.values.sum()
    val doneCh = st.progress.values.sumOf { m -> m.values.count { it.done } }
    val doneSec = books.sumOf { b -> b.chapters.filter { st.progress[b.key]?.get(it.n)?.done == true }.sumOf { it.duration } }
    val finishedBooks = books.count { b -> b.chapters.isNotEmpty() && b.chapters.all { st.progress[b.key]?.get(it.n)?.done == true } }
    val started = books.count { b -> st.progress[b.key].orEmpty().isNotEmpty() }
    val used = remember(st.downloaded) { app.downloads.usedBytes() }
    Scaffold(topBar = { BackBar(nav, "Статистика") }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp)) {
            item {
                SectionTitle("Прослушивание")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Время в приложении", hours(totalMs / 1000.0), Modifier.weight(1f))
                    StatCard("Прослушано глав", "$doneCh", Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("Объём прослушанного", hours(doneSec), Modifier.weight(1f))
                    StatCard("Книги: начато / дослушано", "$started / $finishedBooks", Modifier.weight(1f))
                }
            }
            item {
                val goal = st.settings.goalMin
                val today = ((st.statsDays[LocalDate.now().toString()] ?: 0L) / 60000).toInt()
                val streak = io.github.notoboto.unison.Habits.streak(app)
                SectionTitle("Сегодня")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(progress = { if (goal > 0) (today.toFloat() / goal).coerceAtMost(1f) else 1f },
                            Modifier.size(84.dp), strokeWidth = 8.dp, trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        Text(if (goal > 0) "$today/$goal" else "$today", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(if (goal > 0) (if (today >= goal) "Цель на сегодня выполнена 🎉" else "Цель: $goal мин в день") else "Минут сегодня", fontWeight = FontWeight.Medium)
                        Text("Серия: $streak ${plural(streak, "день", "дня", "дней")} подряд", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    }
                }
                val ym = LocalDate.now().toString().take(7); val y = LocalDate.now().year.toString()
                val month = st.statsDays.filterKeys { it.startsWith(ym) }.values.sum(); val year = st.statsDays.filterKeys { it.startsWith(y) }.values.sum()
                val best = st.statsDays.maxByOrNull { it.value }
                SectionTitle("Итоги")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard("В этом месяце", hours(month / 1000.0), Modifier.weight(1f))
                    StatCard("В этом году", hours(year / 1000.0), Modifier.weight(1f))
                }
                if (best != null) Text("Рекорд: ${hours(best.value / 1000.0)} — ${best.key}", Modifier.padding(top = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            item {
                SectionTitle("Последние 14 дней")
                val days = (13 downTo 0).map { LocalDate.now().minusDays(it.toLong()) }
                val vals = days.map { (st.statsDays[it.toString()] ?: 0L) / 60000f }
                val max = (vals.maxOrNull() ?: 0f).coerceAtLeast(1f)
                val bar = MaterialTheme.colorScheme.primary; val track = MaterialTheme.colorScheme.surfaceContainerHigh
                Canvas(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 8.dp)) {
                    val w = size.width / vals.size
                    vals.forEachIndexed { i, v ->
                        val h = size.height * (v / max)
                        drawRoundRect(track, Offset(i * w + w * 0.15f, 0f), Size(w * 0.7f, size.height), CornerRadius(6f))
                        drawRoundRect(bar, Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h), CornerRadius(6f))
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    Text(days.first().let { "${it.dayOfMonth}.${"%02d".format(it.monthValue)}" }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("сегодня: ${(vals.last()).toInt()} мин", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                SectionTitle("По книгам")
            }
            items(books.sortedByDescending { st.statsBooks[it.key] ?: 0 }) { b ->
                val done = b.chapters.count { st.progress[b.key]?.get(it.n)?.done == true }
                ListItem(headlineContent = { Text(b.meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = {
                        Column {
                            Text("${hours((st.statsBooks[b.key] ?: 0) / 1000.0)} • $done из ${b.chapters.size} глав")
                            LinearProgressIndicator(progress = { if (b.chapters.isEmpty()) 0f else done.toFloat() / b.chapters.size }, Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                    }, leadingContent = { Cover(b.coverSmUrl, Modifier.size(width = 40.dp, height = 56.dp)) })
            }
            item {
                SectionTitle("Хранилище")
                Text("Скачано: ${chapters(st.downloaded.values.sumOf { it.size })} • ${mb(used)}", Modifier.padding(bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) =
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp)) {
            Text(value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

// ---------------- storage ----------------
@Composable
fun StorageScreen(nav: NavController) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val st by app.store.state.collectAsState()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var moving by remember { mutableStateOf<Float?>(null) }
    var tick by remember { mutableStateOf(0) }
    val books = remember(st.library) { app.repo.libraryBooks() }
    val sizes = remember(st.downloaded, tick) { books.associate { it.key to runCatching { app.repo.files.bookBytes(it.key) }.getOrDefault(0L) } }

    // move every downloaded chapter to the new place, then switch the setting
    fun relocate(newUri: String?) = scope.launch {
        val from = app.repo.files
        val to = newUri?.let { SafFiles(ctx, it) } ?: InternalFiles(ctx)
        val all = st.downloaded.flatMap { (k, m) -> m.keys.map { k to it } }
        moving = 0f
        withContext(Dispatchers.IO) {
            all.forEachIndexed { i, (k, n) ->
                runCatching {
                    for (ext in listOf("json", "ogg")) to.write(k, n, ext) { out -> from.open(k, n, ext).use { it.copyTo(out) } }
                    from.delete(k, n)
                }
                moving = (i + 1f) / all.size.coerceAtLeast(1)
            }
        }
        app.store.settings { it.copy(storageUri = newUri) }
        moving = null; tick++
        snack.showSnackbar("Перенесено глав: ${all.size}")
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            relocate(it.toString())
        }
    }
    Scaffold(topBar = { BackBar(nav, "Хранилище") }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                SheetTitle("Где хранить главы")
                ListItem(headlineContent = { Text("Память приложения") }, supportingContent = { Text("Удаляется вместе с приложением") },
                    leadingContent = { Icon(Icons.Filled.PhoneAndroid, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { androidx.compose.material3.RadioButton(st.settings.storageUri == null, { if (st.settings.storageUri != null) relocate(null) }) },
                    modifier = Modifier.clickable { if (st.settings.storageUri != null) relocate(null) })
                ListItem(headlineContent = { Text("Своя папка") },
                    supportingContent = { Text(st.settings.storageUri?.let { Uri.parse(it).lastPathSegment?.replace("primary:", "Телефон: ") } ?: "Например, на SD-карте") },
                    leadingContent = { Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = { androidx.compose.material3.RadioButton(st.settings.storageUri != null, { pick.launch(null) }) },
                    modifier = Modifier.clickable { pick.launch(null) })
                moving?.let { p ->
                    Text("Переношу скачанные главы…", Modifier.padding(horizontal = 16.dp))
                    LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().padding(16.dp))
                }
                ListItem(headlineContent = { Text("Очистить кэш") }, supportingContent = { Text("Обложки и временные файлы. Скачанные главы не затрагиваются") },
                    leadingContent = { Icon(Icons.Filled.CleaningServices, null, tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        scope.launch {
                            withContext(Dispatchers.IO) { ctx.imageLoader.diskCache?.clear(); ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                            ctx.imageLoader.memoryCache?.clear(); snack.showSnackbar("Кэш очищен")
                        }
                    })
                SheetTitle("Занято по книгам")
            }
            items(books.filter { (sizes[it.key] ?: 0) > 0 }) { b ->
                ListItem(headlineContent = { Text(b.meta.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text("${chapters(st.downloaded[b.key].orEmpty().size)} • ${mb(sizes[b.key] ?: 0)}") },
                    leadingContent = { Cover(b.coverSmUrl, Modifier.size(width = 34.dp, height = 48.dp)) },
                    trailingContent = { IconButton(onClick = { app.downloads.delete(b, st.downloaded[b.key].orEmpty().keys); tick++ }) { Icon(Icons.Filled.Delete, "Удалить скачанное") } })
            }
        }
    }
}

// ---------------- bookmarks ----------------
@Composable
fun BookmarksScreen(nav: NavController, key: String) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val book = remember(key) { app.repo.book(key) } ?: return
    val list = st.bookmarks[key].orEmpty()
    var editing by remember { mutableStateOf<Bookmark?>(null) }
    Scaffold(topBar = { BackBar(nav, "Закладки") }) { pad ->
        if (list.isEmpty()) EmptyState("🔖", "Закладок пока нет.\nДобавляйте их кнопкой с флажком на экране главы.", Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad)) {
            items(list, key = { "${it.n}:${it.pos}:${it.ts}" }) { bm ->
                val ch = book.chapters.find { it.n == bm.n }
                ListItem(
                    overlineContent = { Text("${ch?.let { chName(it, st.settings.fullTitles) } ?: "Глава ${bm.n}"} • ${fmt(bm.pos)}") },
                    headlineContent = { Text("«${bm.quote}»", maxLines = 3, overflow = TextOverflow.Ellipsis) },
                    supportingContent = if (bm.note.isNotBlank()) ({ Text(bm.note, color = MaterialTheme.colorScheme.primary) }) else null,
                    leadingContent = { Icon(Icons.Filled.Bookmark, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { editing = bm }) { Icon(Icons.Filled.Edit, "Заметка") }
                            IconButton(onClick = { app.store.removeBookmark(key, bm) }) { Icon(Icons.Filled.Delete, "Удалить") }
                        }
                    },
                    modifier = Modifier.clickable {
                        app.player.open(book, bm.n, autoplay = false); app.player.seekTo(bm.pos); app.player.play()
                        nav.navigate("reader/$key/${bm.n}")
                    })
            }
        }
    }
    editing?.let { bm -> NameDialog("Заметка", bm.note, { editing = null }) { app.store.editBookmark(key, bm, it); editing = null } }
}

// ---------------- first launch ----------------
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val st by app.store.state.collectAsState()
    val s = st.settings
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            app.store.settings { x -> x.copy(autoBackupUri = it.toString()) }; scheduleAutoBackup(ctx)
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Image(painterResource(R.drawable.ic_launcher_fg), null, Modifier.size(110.dp).align(Alignment.CenterHorizontally))
        Text("Добро пожаловать в Unison", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("Книги в несколько голосов: каждого персонажа читает свой голос, а текст подсвечивается вместе с речью.",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("1. Тема")
        val opts = listOf("dark" to "Тёмная", "light" to "Светлая", "system" to "Система")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            opts.forEachIndexed { i, (v, l) -> SegmentedButton(s.theme == v, { app.store.settings { it.copy(theme = v) } }, SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(l) } }
        }
        SectionTitle("2. Уведомления")
        Text("Нужны для плеера в шторке, загрузок и новостей о новых главах.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        OutlinedButton(onClick = { if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS) }, Modifier.fillMaxWidth()) { Text("Разрешить уведомления") }
        SectionTitle("3. Резервные копии")
        Text("Раз в день Unison может сохранять библиотеку и прогресс в выбранную папку.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        OutlinedButton(onClick = { folder.launch(null) }, Modifier.fillMaxWidth()) { Text(if (s.autoBackupUri != null) "Папка выбрана ✓" else "Выбрать папку (необязательно)") }
        SectionTitle("4. Источники")
        Text("Книги берутся из источников — сайтов с каталогом в формате Unison. Основной источник уже подключён; свои можно добавить во вкладке «Обзор».",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onDone, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Начать", fontSize = 17.sp) }
    }
}

@Composable
private fun SectionTitle(t: String) = Text(t, Modifier.padding(top = 12.dp, bottom = 6.dp), color = MaterialTheme.colorScheme.primary,
    fontWeight = FontWeight.SemiBold, fontSize = 14.sp)

// ---------------- characters ----------------
@Composable
fun CharactersScreen(nav: NavController, key: String) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val book = remember(key) { app.repo.book(key) } ?: return
    var list by remember { mutableStateOf<List<io.github.notoboto.unison.data.Character>?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    val mp = remember { android.media.MediaPlayer() }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { runCatching { mp.release() } } }
    androidx.compose.runtime.LaunchedEffect(key) { list = app.repo.characters(book) }
    fun play(c: io.github.notoboto.unison.data.Character) {
        runCatching { mp.reset() }
        if (playing == c.name) { playing = null; return }
        val url = book.dataUrl + (c.sample ?: return)
        runCatching {
            mp.setDataSource(url); mp.setOnPreparedListener { it.start() }; mp.setOnCompletionListener { playing = null }; mp.prepareAsync()
            playing = c.name
        }
    }
    Scaffold(topBar = { BackBar(nav, "Действующие лица") }) { pad ->
        val l = list
        when {
            l == null -> EmptyState("⏳", "Загружаю…", Modifier.padding(pad))
            l.isEmpty() -> EmptyState("🎭", "Для этой книги нет списка действующих лиц или нет интернета.", Modifier.padding(pad))
            else -> LazyColumn(Modifier.padding(pad)) {
                item { Text("Нажмите ▶, чтобы услышать голос. В тексте главы нажатие на имя в списке ролей подсвечивает реплики персонажа.",
                    Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(l, key = { it.name }) { c ->
                    val range = if (c.chapters.isEmpty()) "" else if (c.chapters.size == 1) "глава ${c.chapters[0]}" else "главы ${c.chapters.first()}–${c.chapters.last()} (${c.chapters.size})"
                    ListItem(headlineContent = { Text(c.name, fontWeight = FontWeight.Medium) },
                        supportingContent = { Text("${c.voice}\nРеплик: ${c.lines} • $range") },
                        trailingContent = {
                            if (c.sample != null) IconButton(onClick = { play(c) }) {
                                Icon(if (playing == c.name) Icons.Filled.Stop else Icons.Filled.PlayArrow, "Образец голоса", tint = MaterialTheme.colorScheme.primary)
                            }
                        })
                }
            }
        }
    }
}

// ---------------- Piper voices ----------------
@Composable
fun VoicesScreen(nav: NavController) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val st by app.store.state.collectAsState()
    val s = st.settings
    val prog by io.github.notoboto.unison.data.Piper.progress.collectAsState()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var tick by remember { mutableStateOf(0) }
    fun inst(id: String) = tick >= 0 && io.github.notoboto.unison.data.Piper.installed(ctx, id)
    var testing by remember { mutableStateOf<String?>(null) }
    fun set(f: (io.github.notoboto.unison.data.Settings) -> io.github.notoboto.unison.data.Settings) = app.store.settings(f)
    Scaffold(topBar = { BackBar(nav, "Голоса для своих книг") }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Text("Импортированные книги озвучиваются прямо на телефоне. Системный синтезатор — один голос; нейросетевые голоса Piper звучат естественнее, " +
                    "работают без интернета и могут читать по ролям: рассказчик, мужские и женские реплики.",
                    Modifier.padding(16.dp), fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SheetTitle("Движок")
                Choice("Системный синтезатор (Google и др.)", s.ttsEngine != "piper") { set { it.copy(ttsEngine = "system") } }
                Choice("Нейросетевые голоса Piper", s.ttsEngine == "piper") {
                    if (inst(s.piperNarrator)) set { it.copy(ttsEngine = "piper") } else scope.launch { snack.showSnackbar("Сначала скачайте голос рассказчика") }
                }
                SheetTitle("Голоса Piper")
                // the engine is a separate one-time download; shown when voices are there but the engine is not (e.g. after an update)
                val ep = prog[io.github.notoboto.unison.data.Piper.ENGINE_KEY]
                val engineOk = tick >= 0 && io.github.notoboto.unison.data.Piper.engineInstalled(ctx)
                if (ep != null || (!engineOk && io.github.notoboto.unison.data.Piper.voices.any { inst(it.id) })) ListItem(
                    headlineContent = { Text("Движок Piper") },
                    supportingContent = {
                        if (ep != null) LinearProgressIndicator(progress = { ep }, Modifier.fillMaxWidth().padding(top = 6.dp))
                        else Text("Нужен для скачанных голосов, ~${io.github.notoboto.unison.data.Piper.ENGINE_SIZE_MB} МБ один раз")
                    },
                    trailingContent = {
                        IconButton(onClick = {
                            scope.launch {
                                runCatching { io.github.notoboto.unison.data.Piper.downloadEngine(ctx, app.repo.http) }
                                    .onSuccess { tick++ }.onFailure { snack.showSnackbar("Не удалось скачать: ${it.message}") }
                            }
                        }, enabled = ep == null) { Icon(Icons.Filled.Download, "Скачать") }
                    })
            }
            items(io.github.notoboto.unison.data.Piper.voices, key = { it.id }) { v ->
                val p = prog[v.id]
                ListItem(headlineContent = { Text(v.title + if (v.female) " (женский)" else " (мужской)") },
                    supportingContent = {
                        if (p != null) LinearProgressIndicator(progress = { p }, Modifier.fillMaxWidth().padding(top = 6.dp))
                        else Text(if (inst(v.id)) "Скачан" else if (io.github.notoboto.unison.data.Piper.engineInstalled(ctx)) "~21 МБ"
                            else "~21 МБ + движок ~${io.github.notoboto.unison.data.Piper.ENGINE_SIZE_MB} МБ")
                    },
                    trailingContent = {
                        Row {
                            if (inst(v.id)) {
                                IconButton(onClick = {
                                    testing = v.id
                                    scope.launch {
                                        runCatching {
                                            val (rate, pcm) = kotlinx.coroutines.withContext(Dispatchers.Default) {
                                                io.github.notoboto.unison.data.Piper.speak(ctx, v.id, "Здравствуйте! Так звучит мой голос.", s.ttsRate)
                                            }
                                            val t = android.media.AudioTrack.Builder().setAudioFormat(android.media.AudioFormat.Builder()
                                                .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                                                .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
                                                .setBufferSizeInBytes(pcm.size * 2).setTransferMode(android.media.AudioTrack.MODE_STATIC).build()
                                            t.write(pcm, 0, pcm.size); t.play()
                                            kotlinx.coroutines.delay(pcm.size * 1000L / rate + 300)
                                            t.release()
                                        }.onFailure { snack.showSnackbar("Не удалось: ${it.message}") }
                                        testing = null
                                    }
                                }, enabled = testing == null) { Icon(Icons.Filled.PlayArrow, "Послушать") }
                                IconButton(onClick = { io.github.notoboto.unison.data.Piper.delete(ctx, v.id); tick++ }) { Icon(Icons.Filled.Delete, "Удалить") }
                            } else IconButton(onClick = {
                                scope.launch {
                                    runCatching { io.github.notoboto.unison.data.Piper.download(ctx, v.id, app.repo.http) }
                                        .onSuccess { tick++ }.onFailure { snack.showSnackbar("Не удалось скачать: ${it.message}") }
                                }
                            }, enabled = p == null) { Icon(Icons.Filled.Download, "Скачать") }
                        }
                    })
            }
            item {
                SheetTitle("Кто каким голосом читает")
                val opts = io.github.notoboto.unison.data.Piper.voices.map { it.id to it.title }
                PickRowX("Рассказчик", s.piperNarrator, opts) { v -> set { it.copy(piperNarrator = v) } }
                Row(Modifier.fillMaxWidth().clickable { set { it.copy(piperRoles = !it.piperRoles) } }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Читать по ролям")
                        Text("Реплики персонажей — отдельными голосами; пол угадывается по тексту («сказала»)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(s.piperRoles, { v -> set { it.copy(piperRoles = v) } })
                }
                if (s.piperRoles) {
                    PickRowX("Мужские реплики", s.piperMale, opts) { v -> set { it.copy(piperMale = v) } }
                    PickRowX("Женские реплики", s.piperFemale, opts) { v -> set { it.copy(piperFemale = v) } }
                }
                Text("Уже озвученные главы не меняются: удалите главу и озвучьте заново, чтобы применить новые голоса.",
                    Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PickRowX(title: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(options.find { it.first == value }?.second ?: value) },
        modifier = Modifier.clickable { open = true })
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = {
        Column { options.forEach { (v, l) -> Choice(l, v == value) { onPick(v); open = false } } }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text("Закрыть") } })
}

// ---------------- car mode ----------------
@Composable
fun CarScreen(nav: NavController) {
    val app = LocalContext.current.app()
    val p = app.player
    val now by p.now.collectAsState()
    val playing by p.isPlaying.collectAsState()
    val pos by p.position.collectAsState()
    val sentence by p.sentence.collectAsState()
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    Column(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).statusBarsPadding().navigationBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }, Modifier.size(64.dp)) {
                Icon(Icons.Filled.Close, "Выйти из режима", Modifier.size(36.dp), tint = androidx.compose.ui.graphics.Color.White)
            }
            Text(now?.title ?: "Нечего играть", color = androidx.compose.ui.graphics.Color.White, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
        }
        Text(sentence, Modifier.weight(1f).fillMaxWidth().padding(vertical = 16.dp), color = androidx.compose.ui.graphics.Color(0xFFDDDDDD),
            fontSize = 26.sp, lineHeight = 34.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
        LinearProgressIndicator(progress = { now?.let { (pos / it.duration).toFloat().coerceIn(0f, 1f) } ?: 0f }, Modifier.fillMaxWidth().height(6.dp))
        Text(fmt(pos) + " / " + fmt(now?.duration ?: 0.0), color = androidx.compose.ui.graphics.Color.Gray, fontSize = 18.sp, modifier = Modifier.padding(vertical = 8.dp))
        val big = Modifier.weight(1f).height(120.dp)
        val c = MaterialTheme.colorScheme
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.FilledTonalButton(onClick = { p.skip(-30) }, big, shape = RoundedCornerShape(24.dp)) { Text("−30", fontSize = 28.sp) }
            androidx.compose.material3.Button(onClick = { p.toggleOrResume() }, big, shape = RoundedCornerShape(24.dp)) {
                Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, null, Modifier.size(64.dp))
            }
            androidx.compose.material3.FilledTonalButton(onClick = { p.skip(30) }, big, shape = RoundedCornerShape(24.dp)) { Text("+30", fontSize = 28.sp) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = { p.neighbour(-1)?.let { m -> now?.let { p.open(it.book, m, true) } } }, Modifier.weight(1f).height(80.dp)) { Text("‹ Глава", fontSize = 22.sp) }
            androidx.compose.material3.OutlinedButton(onClick = { p.neighbour(1)?.let { m -> now?.let { p.open(it.book, m, true) } } }, Modifier.weight(1f).height(80.dp)) { Text("Глава ›", fontSize = 22.sp) }
        }
    }
}

// ---------------- profiles ----------------
@Composable
fun ProfilesScreen(nav: NavController) {
    val ctx = LocalContext.current
    var tick by remember { mutableStateOf(0) }
    val list = remember(tick) { io.github.notoboto.unison.data.Profiles.list(ctx) }
    val cur = io.github.notoboto.unison.data.Profiles.current(ctx)
    var adding by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { BackBar(nav, "Профили") }, floatingActionButton = {
        FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Filled.Add, "Новый профиль") }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item { Text("У каждого профиля своя библиотека, прогресс, история, закладки и настройки. Скачанные главы общие. При переключении приложение перезапустится.",
                Modifier.padding(16.dp), fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list, key = { it }) { p ->
                ListItem(headlineContent = { Text(p, fontWeight = if (p == cur) FontWeight.SemiBold else null) },
                    supportingContent = { if (p == cur) Text("Текущий") },
                    leadingContent = { androidx.compose.material3.RadioButton(p == cur, { if (p != cur) io.github.notoboto.unison.data.Profiles.switchTo(ctx as android.app.Activity, p) }) },
                    trailingContent = { if (p != "Основной") IconButton(onClick = { removing = p }) { Icon(Icons.Filled.Delete, "Удалить") } },
                    modifier = Modifier.clickable { if (p != cur) io.github.notoboto.unison.data.Profiles.switchTo(ctx as android.app.Activity, p) })
            }
        }
    }
    if (adding) NameDialog("Новый профиль", "", { adding = false }) { io.github.notoboto.unison.data.Profiles.add(ctx, it); adding = false; tick++ }
    removing?.let { p ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("Удалить профиль «$p»?") }, text = { Text("Его прогресс и настройки будут удалены.") },
            confirmButton = { TextButton(onClick = { io.github.notoboto.unison.data.Profiles.remove(ctx, p); removing = null; tick++ }) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Отмена") } })
    }
}

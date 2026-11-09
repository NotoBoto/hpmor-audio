package io.github.notoboto.unison.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.github.notoboto.unison.BuildConfig
import io.github.notoboto.unison.R
import io.github.notoboto.unison.app
import io.github.notoboto.unison.backupFileName
import io.github.notoboto.unison.data.Settings
import io.github.notoboto.unison.download.DlState
import io.github.notoboto.unison.scheduleAutoBackup
import io.github.notoboto.unison.writeBackup
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(nav: NavController, title: String, actions: @Composable () -> Unit = {}) =
    TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
        actions = { actions() })

@Composable
fun MoreScreen(nav: NavController) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val n = st.downloaded.values.sumOf { it.size }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().padding(top = 48.dp, bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.ic_launcher_fg), null, Modifier.size(96.dp))
            Text("Unison", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text("Книги в несколько голосов", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
        HorizontalDivider()
        Row1(Icons.Filled.Download, "Загрузки", "${chapters(n)} на телефоне") { nav.navigate("downloads") }
        Row1(Icons.Filled.Settings, "Настройки", "Тема, плеер, загрузки, резервная копия") { nav.navigate("settings") }
        Row1(Icons.Filled.Info, "О программе", "Версия ${BuildConfig.VERSION_NAME}, обновления, лицензии") { nav.navigate("about") }
    }
}

@Composable
private fun Row1(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String?, onClick: () -> Unit) =
    ListItem(headlineContent = { Text(title) }, supportingContent = sub?.let { { Text(it) } },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }, modifier = Modifier.clickable(onClick = onClick))

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) =
    ListItem(headlineContent = { Text(title) }, supportingContent = sub?.let { { Text(it) } },
        trailingContent = { Switch(checked, onChange) }, modifier = Modifier.clickable { onChange(!checked) })

@Composable
private fun <T> PickRow(title: String, value: T, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(options.find { it.first == value }?.second ?: "$value") },
        modifier = Modifier.clickable { open = true })
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text(title) }, text = {
        Column { options.forEach { (v, l) -> Choice(l, v == value) { onPick(v); open = false } } }
    }, confirmButton = { TextButton(onClick = { open = false }) { Text("Закрыть") } })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val st by app.store.state.collectAsState()
    val s = st.settings
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun set(f: (Settings) -> Settings) = app.store.settings(f)

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { runCatching { writeBackup(ctx, it) }.onSuccess { scope.launch { snack.showSnackbar("Резервная копия сохранена") } } }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> runCatching { app.store.importJson(ctx.contentResolver.openInputStream(u)!!.bufferedReader().readText()) }
            .onSuccess { scope.launch { app.repo.refreshAll(); snack.showSnackbar("Восстановлено") } }
            .onFailure { scope.launch { snack.showSnackbar("Это не резервная копия Unison") } } }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            set { x -> x.copy(autoBackupUri = it.toString()) }; scheduleAutoBackup(ctx)
        }
    }

    Scaffold(topBar = { BackBar(nav, "Настройки") }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                SheetTitle("Внешний вид")
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Тема", fontSize = 16.sp); Spacer(Modifier.height(8.dp))
                    val opts = listOf("dark" to "Тёмная", "light" to "Светлая", "system" to "Система")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        opts.forEachIndexed { i, (v, l) ->
                            SegmentedButton(s.theme == v, { set { it.copy(theme = v) } }, SegmentedButtonDefaults.itemShape(i, opts.size)) { Text(l) }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= 31) SwitchRow("Цвета из обоев", "Material You", s.dynamicColor) { v -> set { it.copy(dynamicColor = v) } }
                SwitchRow("Чёрный фон", "Для AMOLED-экранов, в тёмной теме", s.amoled) { v -> set { it.copy(amoled = v) } }
                PickRow("Размер текста", s.fontSize, (14..30 step 1).map { it to "$it" }) { v -> set { it.copy(fontSize = v) } }
                SwitchRow("Полные названия глав", "Иначе — «Глава N»", s.fullTitles) { v -> set { it.copy(fullTitles = v) } }
            }
            item {
                SheetTitle("Плеер")
                PickRow("Скорость по умолчанию", s.speed, listOf(0.8f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f).map { it to "${it}×".replace('.', ',') }) { v -> app.player.setSpeed(v) }
                PickRow("Перемотка назад", s.skipBack, listOf(5, 10, 15, 30, 60).map { it to "$it с" }) { v -> set { it.copy(skipBack = v) } }
                PickRow("Перемотка вперёд", s.skipForward, listOf(5, 10, 15, 30, 60).map { it to "$it с" }) { v -> set { it.copy(skipForward = v) } }
                SwitchRow("Не гасить экран", "Пока открыта глава и идёт воспроизведение", s.keepScreenOn) { v -> set { it.copy(keepScreenOn = v) } }
            }
            item {
                SheetTitle("Загрузки")
                SwitchRow("Только по Wi‑Fi", "Без Wi‑Fi загрузки ждут в очереди", s.wifiOnly) { v -> set { it.copy(wifiOnly = v) } }
                PickRow("Скачивать наперёд", s.autoDownloadNext, listOf(0 to "Выключено", 1 to "1 главу", 2 to "2 главы", 3 to "3 главы", 5 to "5 глав", 10 to "10 глав")) { v -> set { it.copy(autoDownloadNext = v) } }
                SwitchRow("Удалять прослушанные", "Глава удаляется с телефона, когда дослушана", s.autoDelete) { v -> set { it.copy(autoDelete = v) } }
                SwitchRow("Обновлять переозвученные", "Скачанная глава заменяется новой версией сама", s.autoUpdate) { v -> set { it.copy(autoUpdate = v) } }
            }
            item {
                SheetTitle("Резервная копия")
                Row1(Icons.Filled.Backup, "Создать резервную копию", "Библиотека, прогресс, история, настройки, источники") { exportLauncher.launch(backupFileName()) }
                Row1(Icons.Filled.Refresh, "Восстановить из файла", null) { importLauncher.launch(arrayOf("application/json", "*/*")) }
                SwitchRow("Автоматически раз в день", s.autoBackupUri?.let { "Папка: " + Uri.parse(it).lastPathSegment } ?: "Выберите папку", s.autoBackupUri != null) { on ->
                    if (on) folderLauncher.launch(null) else { set { it.copy(autoBackupUri = null) }; scheduleAutoBackup(ctx) }
                }
            }
        }
    }
}

@Composable
fun DownloadsScreen(nav: NavController) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val jobs by app.downloads.states.collectAsState()
    val chaptersMap by app.repo.chapters.collectAsState()
    var confirm by remember { mutableStateOf(false) }
    val books = remember(st.library, chaptersMap) { app.repo.libraryBooks() }
    val used = remember(st.downloaded) { app.downloads.usedBytes() }
    val old = app.downloads.outdated(books)
    Scaffold(topBar = { BackBar(nav, "Загрузки") }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                ListItem(headlineContent = { Text("На телефоне: ${chapters(st.downloaded.values.sumOf { it.size })}") }, supportingContent = { Text(mb(used)) })
                Row1(Icons.Filled.Refresh, "Обновить скачанные", if (old.isEmpty()) "Все скачанные главы актуальны" else "Есть новая озвучка у ${chapters(old.size)}") {
                    app.downloads.updateOutdated(books)
                }
                Row1(Icons.Filled.Delete, "Удалить все скачанные", "Прогресс прослушивания сохранится") { confirm = true }
                GroupHeader(if (jobs.isEmpty()) "Очередь пуста" else "Очередь: ${jobs.size}")
            }
            items(jobs.entries.toList(), key = { it.key }) { (k, state) ->
                val bookKey = k.substringBeforeLast(':'); val n = k.substringAfterLast(':').toInt()
                val b = app.repo.book(bookKey); val c = b?.chapters?.find { it.n == n }
                ListItem(headlineContent = { Text(c?.let { chName(it, st.settings.fullTitles) } ?: "Глава $n") },
                    overlineContent = { b?.let { Text(it.shortTitle) } },
                    supportingContent = {
                        if (state is DlState.Running) LinearProgressIndicator(progress = { state.p }, Modifier.fillMaxWidth().padding(top = 6.dp))
                        else Text(if (st.settings.wifiOnly) "Ждёт Wi‑Fi или очереди" else "В очереди")
                    },
                    trailingContent = { IconButton(onClick = { b?.let { app.downloads.cancel(it, n) } }) { Icon(Icons.Filled.Close, "Отменить") } })
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Удалить всё скачанное?") },
        text = { Text("Все главы будут удалены с телефона. Прогресс прослушивания сохранится.") },
        confirmButton = { TextButton(onClick = { app.downloads.cancelAll(); books.forEach { b -> app.downloads.delete(b, st.downloaded[b.key].orEmpty().keys) }; confirm = false }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } })
}

@Composable
fun AboutScreen(nav: NavController) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val catalogs by app.repo.catalogs.collectAsState()
    Scaffold(topBar = { BackBar(nav, "О программе") }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            ListItem(headlineContent = { Text("Версия") }, supportingContent = { Text(BuildConfig.VERSION_NAME) })
            Row1(Icons.Filled.SystemUpdate, "Проверить обновления", null) {
                scope.launch { if (app.updater.check(force = true) == null) snack.showSnackbar("У вас последняя версия") }
            }
            Row1(Icons.Filled.Code, "GitHub", "github.com/NotoBoto/unison") {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/NotoBoto/unison")))
            }
            HorizontalDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val txt = MaterialTheme.colorScheme.onSurfaceVariant
                Text("Unison — читалка аудиокниг, озвученных по ролям: у каждого персонажа свой голос, а текст подсвечивается синхронно с речью.", color = txt)
                catalogs.values.flatten().forEach { m -> m.credits?.let { Text("${m.title}: $it", color = txt, fontSize = 14.sp) } }
                Text("Синтез речи — XTTS-v2 (Coqui Public Model License, некоммерческое использование). Часть голосов клонирована по записям волонтёров LibriVox (общественное достояние). Синхронизация слов — faster-whisper.", color = txt, fontSize = 14.sp)
                Text("Harry Potter принадлежит J. K. Rowling. Проект некоммерческий и не связан с правообладателями.", color = txt, fontSize = 14.sp)
            }
        }
    }
}

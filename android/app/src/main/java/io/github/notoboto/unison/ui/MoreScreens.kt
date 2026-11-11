package io.github.notoboto.unison.ui

import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Upload
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
        SwitchRow("Только скачанное", "Показывать только скачанные главы и книги", st.settings.downloadedOnly) { v -> app.store.settings { it.copy(downloadedOnly = v) } }
        SwitchRow("Инкогнито", "Не сохранять историю, прогресс и статистику", st.settings.incognito) { v -> app.store.settings { it.copy(incognito = v) } }
        HorizontalDivider()
        Row1(Icons.Filled.Download, "Загрузки", "${chapters(n)} на телефоне") { nav.navigate("downloads") }
        Row1(Icons.AutoMirrored.Filled.Label, "Категории", "Вкладки библиотеки") { nav.navigate("categories") }
        Row1(Icons.Filled.QueryStats, "Статистика", "Время прослушивания, главы, книги") { nav.navigate("stats") }
        Row1(Icons.Filled.Storage, "Хранилище", "Где хранить главы, кэш, место по книгам") { nav.navigate("storage") }
        Row1(Icons.Filled.People, "Профили", io.github.notoboto.unison.data.Profiles.current(LocalContext.current)) { nav.navigate("profiles") }
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
fun SettingsScreen(nav: NavController, section: String? = null) {
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
        uri?.let { u -> runCatching {
                io.github.notoboto.unison.restoreBackup(ctx, ctx.contentResolver.openInputStream(u)!!.bufferedReader().readText()) { problems ->
                    if (problems.isNotEmpty()) scope.launch { snack.showSnackbar("Источники: ${problems.first()}") }
                } }
            .onSuccess { scope.launch { app.repo.refreshAll(); snack.showSnackbar("Восстановлено") } }
            .onFailure { scope.launch { snack.showSnackbar("Это не резервная копия Unison") } } }
    }
    val syncOut = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { u -> runCatching { ctx.contentResolver.openOutputStream(u, "wt")!!.use { it.write(io.github.notoboto.unison.data.Sync.export(app.store, app.repo).toByteArray()) } }
            .onSuccess { scope.launch { snack.showSnackbar("Файл прогресса сохранён") } } }
    }
    val syncIn = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u -> runCatching { io.github.notoboto.unison.data.Sync.import(app.store, ctx.contentResolver.openInputStream(u)!!.bufferedReader().readText()) }
            .onSuccess { r -> scope.launch { app.repo.refreshAll(); snack.showSnackbar("Слито: книг ${r.books}, глав обновлено ${r.chapters}, новых закладок ${r.bookmarks}") } }
            .onFailure { scope.launch { snack.showSnackbar("Это не файл прогресса Unison") } } }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            ctx.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            set { x -> x.copy(autoBackupUri = it.toString()) }; scheduleAutoBackup(ctx)
        }
    }

    val sections = listOf(
        Triple("appearance", "Внешний вид", "Тема, цвета, названия глав") to Icons.Filled.Palette,
        Triple("library", "Библиотека", "Свайпы по главам, проверка новых глав") to Icons.AutoMirrored.Filled.LibraryBooks,
        Triple("player", "Плеер", "Скорость, перемотка, звук, таймер сна") to Icons.Filled.Headphones,
        Triple("reader", "Читалка", "Экран, зоны касания, подсказки") to Icons.AutoMirrored.Filled.MenuBook,
        Triple("downloads", "Загрузки", "Wi‑Fi, скачивание наперёд, место") to Icons.Filled.Download,
        Triple("tts", "Синтезатор речи", "Озвучка своих книг, голоса Piper") to Icons.Filled.RecordVoiceOver,
        Triple("habit", "Привычка", "Цель на день, напоминание") to Icons.Filled.EmojiEvents,
        Triple("data", "Данные и хранение", "Резервные копии, синхронизация прогресса") to Icons.Filled.Storage,
        Triple("security", "Безопасность", "Блокировка приложения, скрытие в недавних") to Icons.Filled.Security,
    )
    val title = sections.find { it.first.first == section }?.first?.second ?: "Настройки"
    Scaffold(topBar = { BackBar(nav, title) }, snackbarHost = { SnackbarHost(snack) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                when (section) {
                    "appearance" -> {
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
                    "library" -> {
                SheetTitle("Главы")
                val sw = listOf("read" to "Прослушано / не прослушано", "download" to "Скачать / удалить", "none" to "Ничего")
                PickRow("Свайп вправо", s.swipeRight, sw) { v -> set { it.copy(swipeRight = v) } }
                PickRow("Свайп влево", s.swipeLeft, sw) { v -> set { it.copy(swipeLeft = v) } }
                SheetTitle("Обновление библиотеки")
                PickRow("Проверять новые главы", s.autoCheckHours, listOf(0 to "Вручную", 6 to "Каждые 6 часов", 12 to "Каждые 12 часов",
                    24 to "Раз в день", 48 to "Раз в 2 дня", 168 to "Раз в неделю")) { v -> set { it.copy(autoCheckHours = v) }; io.github.notoboto.unison.scheduleLibraryCheck(ctx) }
                if (s.autoCheckHours > 0) {
                    SwitchRow("Только по Wi‑Fi", null, s.autoCheckWifi) { v -> set { it.copy(autoCheckWifi = v) }; io.github.notoboto.unison.scheduleLibraryCheck(ctx) }
                    SwitchRow("Только на зарядке", null, s.autoCheckCharging) { v -> set { it.copy(autoCheckCharging = v) }; io.github.notoboto.unison.scheduleLibraryCheck(ctx) }
                    SwitchRow("Скачивать новые главы", "Сразу после проверки", s.autoDownloadNew) { v -> set { it.copy(autoDownloadNew = v) } }
                }
                    }
                    "player" -> {
                PickRow("Скорость по умолчанию", s.speed, listOf(0.8f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f).map { it to "${it}×".replace('.', ',') }) { v -> app.player.setSpeed(v) }
                PickRow("Перемотка назад", s.skipBack, listOf(5, 10, 15, 30, 60).map { it to "$it с" }) { v -> set { it.copy(skipBack = v) } }
                PickRow("Перемотка вперёд", s.skipForward, listOf(5, 10, 15, 30, 60).map { it to "$it с" }) { v -> set { it.copy(skipForward = v) } }
                SwitchRow("Пропускать паузы", "Длинная тишина между фразами сокращается", s.skipSilence) { v -> set { it.copy(skipSilence = v) } }
                PickRow("Усиление голоса", s.voiceBoost, listOf(0 to "Выключено", 3 to "+3 дБ", 6 to "+6 дБ", 9 to "+9 дБ", 12 to "+12 дБ")) { v -> set { it.copy(voiceBoost = v) } }
                PickRow("Музыка из других приложений", s.otherAudio, listOf("pause" to "Ставить на паузу", "duck" to "Приглушать, пока звучит книга",
                    "mix" to "Не трогать — звучит вместе с книгой")) { v -> set { it.copy(otherAudio = v) } }
                if (s.otherAudio == "mix") PickRow("Музыка тише книги", s.mixBalance, listOf(0 to "Нет, одинаково", 1 to "На 1 деление громкости",
                    2 to "На 2 деления", 3 to "На 3 деления", 4 to "На 4 деления", 5 to "На 5 делений")) { v -> set { it.copy(mixBalance = v) } }
                SwitchRow("Старт при подключении наушников", "Проводные, Bluetooth и автомобиль — продолжить с последнего места", s.autoStartHeadphones) { v -> set { it.copy(autoStartHeadphones = v) } }
                SwitchRow("Живые субтитры", "Звучащая фраза в шторке, на экране блокировки и в большом виджете", s.liveSubtitles) { v -> set { it.copy(liveSubtitles = v) } }
                SwitchRow("Кнопка трансляции", "Google Cast: колонки и телевизоры в той же сети", s.castButton) { v -> set { it.copy(castButton = v) } }
                SwitchRow("Своя скорость для каждой книги", "Скорость запоминается отдельно для книги", s.perBookSpeed) { v -> set { it.copy(perBookSpeed = v) } }
                PickRow("Перемотка после паузы", s.autoRewindAfterMin, listOf(0 to "Выключено", 1 to "Пауза дольше 1 мин", 2 to "Пауза дольше 2 мин",
                    5 to "Пауза дольше 5 мин", 15 to "Пауза дольше 15 мин")) { v -> set { it.copy(autoRewindAfterMin = v) } }
                if (s.autoRewindAfterMin > 0) PickRow("Насколько отматывать", s.autoRewindSec, listOf(5, 10, 15, 30).map { it to "$it с назад" }) { v -> set { it.copy(autoRewindSec = v) } }
                SwitchRow("Таймер сна по расписанию", "Включается сам, если начать слушать в это время", s.sleepScheduleOn) { v -> set { it.copy(sleepScheduleOn = v) } }
                if (s.sleepScheduleOn) {
                    val hh = (0 until 24).flatMap { h -> listOf(h * 60, h * 60 + 30) }.map { it to "%02d:%02d".format(it / 60, it % 60) }
                    PickRow("С", s.sleepFrom, hh) { v -> set { it.copy(sleepFrom = v) } }
                    PickRow("До", s.sleepTo, hh) { v -> set { it.copy(sleepTo = v) } }
                    PickRow("Длительность таймера", s.sleepScheduleMin, listOf(10, 15, 20, 30, 45, 60, 90).map { it to "$it мин" }) { v -> set { it.copy(sleepScheduleMin = v) } }
                }
                    }
                    "reader" -> {
                SwitchRow("Не гасить экран", "Пока открыта глава и идёт воспроизведение", s.keepScreenOn) { v -> set { it.copy(keepScreenOn = v) } }
                PickRow("«Ранее в книге»", s.recapAfterHours, listOf(0 to "Не показывать", 6 to "После перерыва от 6 ч", 12 to "После перерыва от 12 ч",
                    24 to "После перерыва от суток", 72 to "После перерыва от 3 дней")) { v -> set { it.copy(recapAfterHours = v) } }
                PickRow("Автопрокрутка текста", s.follow, listOf("page" to "Скачками, как страницы", "center" to "Плавно, строка посередине экрана",
                    "off" to "Выключена")) { v -> set { it.copy(follow = v) } }
                if (s.follow == "page") {
                    PickRow("Когда перелистывать", s.followBottom, (0..5).map { it to if (it == 0) "Когда строка дойдёт до плеера" else "За ${lines(it)} до низа" }) { v -> set { it.copy(followBottom = v) } }
                    PickRow("Отступ сверху после скачка", s.followTop, (0..5).map { it to if (it == 0) "Строка у самого верха" else lines(it).replaceFirstChar { c -> c.uppercase() } + " над ней" }) { v -> set { it.copy(followTop = v) } }
                }
                SwitchRow("Подсказки глоссария", "«Кто это?» в меню долгого нажатия на слово", s.glossaryHints) { v -> set { it.copy(glossaryHints = v) } }
                PickRow("Зоны касания при чтении", s.tapZones, listOf("off" to "Выключены", "single" to "Касание у края — перемотка",
                    "double" to "Двойное касание у края — перемотка")) { v -> set { it.copy(tapZones = v) } }
                if (s.tapZones != "off") PickRow("Ширина зоны", s.tapZoneWidth, listOf(15, 20, 25, 33, 40).map { it to "$it% у каждого края" }) { v -> set { it.copy(tapZoneWidth = v) } }
                    }
                    "downloads" -> {
                SwitchRow("Только по Wi‑Fi", "Без Wi‑Fi загрузки ждут в очереди", s.wifiOnly) { v -> set { it.copy(wifiOnly = v) } }
                PickRow("Скачивать наперёд", s.autoDownloadNext, listOf(0 to "Выключено", 1 to "1 главу", 2 to "2 главы", 3 to "3 главы", 5 to "5 глав", 10 to "10 глав")) { v -> set { it.copy(autoDownloadNext = v) } }
                SwitchRow("Удалять прослушанные", "Глава удаляется с телефона, когда дослушана", s.autoDelete) { v -> set { it.copy(autoDelete = v) } }
                SwitchRow("Обновлять переозвученные", "Скачанная глава заменяется новой версией сама", s.autoUpdate) { v -> set { it.copy(autoUpdate = v) } }
                SwitchRow("Уведомлять о завершении", "«Скачано N глав» по окончании очереди", s.notifyDownloads) { v -> set { it.copy(notifyDownloads = v) } }
                Row1(Icons.Filled.Storage, "Хранилище", "Где хранить главы, очистка кэша") { nav.navigate("storage") }
                PickRow("Лимит места", s.storageLimitGb, listOf(0 to "Без лимита", 1 to "1 ГБ", 2 to "2 ГБ", 5 to "5 ГБ", 10 to "10 ГБ", 20 to "20 ГБ")) { v ->
                    set { it.copy(storageLimitGb = v) }; app.downloads.enforceLimit() }
                    }
                    "tts" -> {
                Row1(Icons.Filled.RecordVoiceOver, "Голоса Piper и чтение по ролям", if (s.ttsEngine == "piper") "Включены нейросетевые голоса" else "Сейчас: системный синтезатор") { nav.navigate("voices") }
                PickRow("Скорость речи", s.ttsRate, listOf(0.7f, 0.85f, 1f, 1.15f, 1.3f, 1.5f).map { it to "${it}×".replace('.', ',') }) { v -> set { it.copy(ttsRate = v) } }
                PickRow("Высота голоса", s.ttsPitch, listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f).map { it to "${it}".replace('.', ',') }) { v -> set { it.copy(ttsPitch = v) } }
                Row1(Icons.Filled.RecordVoiceOver, "Голос и движок", "Системные настройки синтеза речи") {
                    runCatching { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
                    }
                    "habit" -> {
                PickRow("Цель на день", s.goalMin, listOf(0 to "Без цели", 10 to "10 мин", 15 to "15 мин", 20 to "20 мин", 30 to "30 мин", 45 to "45 мин", 60 to "1 час", 90 to "1,5 часа")) { v -> set { it.copy(goalMin = v) } }
                SwitchRow("Напоминание", "Если сегодня ещё не слушали или цель не выполнена", s.reminderOn) { v -> set { it.copy(reminderOn = v) }; io.github.notoboto.unison.Habits.schedule(ctx) }
                if (s.reminderOn) PickRow("Время напоминания", s.reminderAt, (6 * 2 until 24 * 2).map { it * 30 to "%02d:%02d".format(it * 30 / 60, it * 30 % 60) }) { v ->
                    set { it.copy(reminderAt = v) }; io.github.notoboto.unison.Habits.schedule(ctx) }
                    }
                    "data" -> {
                SheetTitle("Резервная копия")
                Row1(Icons.Filled.Backup, "Создать резервную копию", "Библиотека, прогресс, история, настройки, источники") { exportLauncher.launch(backupFileName()) }
                Row1(Icons.Filled.Refresh, "Восстановить из файла", null) { importLauncher.launch(arrayOf("application/json", "*/*")) }
                SwitchRow("Автоматически раз в день", s.autoBackupUri?.let { "Папка: " + Uri.parse(it).lastPathSegment } ?: "Выберите папку", s.autoBackupUri != null) { on ->
                    if (on) folderLauncher.launch(null) else { set { it.copy(autoBackupUri = null) }; scheduleAutoBackup(ctx) }
                }
                SheetTitle("Синхронизация")
                Text("Перенос прогресса между телефоном, планшетом и сайтом: сохраните файл на одном устройстве и загрузите на другом. Данные сливаются: остаётся самое свежее.",
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row1(Icons.Filled.Upload, "Сохранить файл прогресса", "Прогресс, закладки, история, категории") { syncOut.launch("unison-progress_" + java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm", java.util.Locale.US).format(java.util.Date()) + ".json") }
                Row1(Icons.Filled.Download, "Загрузить файл прогресса", "С сайта или другого устройства") { syncIn.launch(arrayOf("application/json", "*/*")) }
                SheetTitle("Хранилище")
                Row1(Icons.Filled.Storage, "Хранилище", "Где хранить главы, кэш, место по книгам") { nav.navigate("storage") }
                    }
                    "security" -> {
                SwitchRow("Блокировка приложения", "Отпечаток, лицо или PIN телефона при входе", s.appLock) { v -> set { it.copy(appLock = v) } }
                if (s.appLock) PickRow("Блокировать снова", s.lockAfterMin, listOf(0 to "Сразу после выхода", 1 to "Через 1 мин", 5 to "Через 5 мин", 15 to "Через 15 мин", 60 to "Через час")) { v -> set { it.copy(lockAfterMin = v) } }
                SwitchRow("Скрывать в недавних", "Содержимое не видно в списке приложений и на скриншотах", s.hideInRecents) { v -> set { it.copy(hideInRecents = v) } }
                    }
                    else -> sections.forEach { (t, icon) ->
                        ListItem(headlineContent = { Text(t.second) }, supportingContent = { Text(t.third) },
                            leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable { nav.navigate("settings/${t.first}") }.padding(vertical = 4.dp))
                    }
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

private fun lines(n: Int) = "$n " + when { n % 10 == 1 && n % 100 != 11 -> "строку"; n % 10 in 2..4 && n % 100 !in 12..14 -> "строки"; else -> "строк" }

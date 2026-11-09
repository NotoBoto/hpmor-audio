package io.github.notoboto.unison.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import io.github.notoboto.unison.data.Settings
import io.github.notoboto.unison.data.Tok
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.input.pointer.PointerInputScope
import io.github.notoboto.unison.data.Bookmark
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation.NavController
import io.github.notoboto.unison.app
import io.github.notoboto.unison.data.ChapterText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HL = Color(0xFFFFD54A)
private val HL_FG = Color(0xFF15131B)

/** Paragraph with char offsets of each token (to map taps to words). */
private class ParaText(val text: AnnotatedString, val starts: IntArray)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(nav: NavController, key: String, n: Int) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val player = app.player
    val st by app.store.state.collectAsState()
    val now by player.now.collectAsState()
    val playing by player.isPlaying.collectAsState()
    val pos by player.position.collectAsState()
    val speed by player.speed.collectAsState()
    val sleep by player.sleep.collectAsState()
    val s = st.settings
    val scope = rememberCoroutineScope()

    // open the requested chapter if the player holds something else
    LaunchedEffect(key, n) {
        if (now?.book?.key != key || now?.n != n) app.repo.book(key)?.let { player.open(it, n, autoplay = false) }
    }
    val np = now?.takeIf { it.book.key == key } ?: now
    var text by remember { mutableStateOf<ChapterText?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(np?.book?.key, np?.n) {
        val cur = np ?: return@LaunchedEffect
        text = null; error = null
        runCatching { app.repo.chapterText(cur.book, cur.n) }.onSuccess { text = it }.onFailure { error = "Глава не скачана, а интернета нет" }
    }

    // keep the screen on while playing
    val view = LocalView.current
    DisposableEffect(playing, s.keepScreenOn) { view.keepScreenOn = playing && s.keepScreenOn; onDispose { view.keepScreenOn = false } }

    var immersive by remember { mutableStateOf(false) }
    DisposableEffect(immersive) {
        val w = (ctx as Activity).window
        val c = WindowCompat.getInsetsController(w, view)
        if (immersive) { c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE; c.hide(WindowInsetsCompat.Type.systemBars()) }
        else c.show(WindowInsetsCompat.Type.systemBars())
        onDispose { c.show(WindowInsetsCompat.Type.systemBars()) }
    }

    var sleepSheet by remember { mutableStateOf(false) }
    val zones = Zones(s.tapZones, s.tapZoneWidth) { dir -> player.skip(if (dir < 0) -s.skipBack else s.skipForward) }
    var bmMenu by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var readingSheet by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var focusRole by remember(np?.n) { mutableStateOf(-1) }
    var readOnly by remember { mutableStateOf(false) }
    var visibleTime by remember { mutableStateOf<Double?>(null) }
    var wordMenu by remember { mutableStateOf<WordHit?>(null) }
    val look = rememberLook(s)
    var recapText by remember(np?.n) { mutableStateOf<String?>(null) }
    var gloss by remember { mutableStateOf<List<io.github.notoboto.unison.data.GlossaryEntry>>(emptyList()) }
    LaunchedEffect(np?.book?.key, np?.n) {
        val cur = np ?: return@LaunchedEffect
        gloss = app.repo.glossary(cur.book)
        // «Ранее в книге»: after a break, remind what happened in the previous chapter
        val lastTs = st.history.firstOrNull()?.takeIf { true }?.ts ?: 0L
        val gapH = (System.currentTimeMillis() - (st.history.drop(if (st.history.firstOrNull()?.n == cur.n) 1 else 0).firstOrNull()?.ts ?: lastTs)) / 3_600_000.0
        if (s.recapAfterHours > 0 && gapH >= s.recapAfterHours && app.store.progress(cur.book.key, cur.n).pos < 30) {
            val prev = cur.book.chapters.lastOrNull { it.n < cur.n } ?: return@LaunchedEffect
            app.repo.recaps(cur.book).find { it.n == prev.n }?.let { recapText = "${prev.title}\n\n${it.recap}" }
        }
    }
    val playerMode = s.readerMode == "player"

    Box(Modifier.fillMaxSize().background(if (playerMode) MaterialTheme.colorScheme.background else look.bg)) {
        val t = text
        when {
            error != null -> EmptyState("📡", error!!)
            t == null -> EmptyState("⏳", "Загружаю главу…")
            playerMode || t.paras.size <= 1 -> PlayerMode(t, np?.book?.coverUrl, pos, look, zones, onToggleUi = { immersive = !immersive })
            else -> TextMode(t, pos, look, immersive, zones, focusRole, { focusRole = it }, readOnly, { visibleTime = it },
                onTapEmpty = { immersive = !immersive }, onSeek = { player.seekTo(it); player.play() }, onLong = { wordMenu = it },
                marks = np?.let { st.highlights[it.book.key] }.orEmpty())
        }

        recapText?.let { r ->
            androidx.compose.material3.Card(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 70.dp, start = 12.dp, end = 12.dp).fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Ранее в книге", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(r, fontSize = 14.5.sp, color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(vertical = 6.dp).heightIn(max = 300.dp).verticalScroll(rememberScrollState()))
                    TextButton(onClick = { recapText = null }, Modifier.align(Alignment.End)) { Text("Понятно") }
                }
            }
        }
        AnimatedVisibility(!immersive, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .97f)),
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = { Text(np?.let { if (s.fullTitles) it.title else "Глава ${it.n}" } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp) },
                actions = {
                    if (s.castButton) androidx.compose.ui.viewinterop.AndroidView(factory = { c ->
                        app.player.initCast()
                        androidx.mediarouter.app.MediaRouteButton(android.view.ContextThemeWrapper(c, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)).also {
                            runCatching { com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(c.applicationContext, it) }
                        }
                    }, modifier = Modifier.size(48.dp))
                    IconButton(onClick = { readingSheet = true }) { Icon(Icons.Outlined.FormatSize, "Оформление текста") }
                    Box {
                        val cur = np
                        val chMarks = cur?.let { c -> st.bookmarks[c.book.key].orEmpty().filter { it.n == c.n } }.orEmpty()
                        IconButton(onClick = { bmMenu = true }) {
                            Icon(if (chMarks.isNotEmpty()) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, "Закладки",
                                tint = if (chMarks.isNotEmpty()) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                        }
                        DropdownMenu(bmMenu, { bmMenu = false }) {
                            DropdownMenuItem({ Text("Добавить закладку здесь") }, leadingIcon = { Icon(Icons.Filled.BookmarkAdd, null) }, onClick = {
                                bmMenu = false
                                val tt = text
                                if (cur != null && tt != null) {
                                    app.store.addBookmark(cur.book.key, Bookmark(cur.n, pos, sentenceAt(tt, pos)))
                                    android.widget.Toast.makeText(ctx, "Закладка добавлена", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            })
                            chMarks.forEach { bm ->
                                DropdownMenuItem({ Text(fmt(bm.pos) + "  " + bm.quote, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp) },
                                    onClick = { bmMenu = false; player.seekTo(bm.pos) }, modifier = Modifier.widthIn(max = 320.dp))
                            }
                            if (cur != null) DropdownMenuItem({ Text("Все закладки книги") }, onClick = { bmMenu = false; nav.navigate("bookmarks/${cur.book.key}") })
                        }
                    }
                    IconButton(onClick = { sleepSheet = true }) {
                        if (sleep != null) Icon(Icons.Filled.Bedtime, "Таймер сна", tint = MaterialTheme.colorScheme.primary) else Icon(Icons.Outlined.Bedtime, "Таймер сна")
                    }
                    Box {
                        IconButton(onClick = { moreMenu = true }) { Icon(Icons.Filled.MoreVert, "Ещё") }
                        DropdownMenu(moreMenu, { moreMenu = false }) {
                            DropdownMenuItem({ Text(if (playerMode) "Режим текста" else "Режим плеера") },
                                leadingIcon = { Icon(if (playerMode) Icons.AutoMirrored.Outlined.Article else Icons.Filled.Headphones, null) },
                                onClick = { moreMenu = false; app.store.settings { it.copy(readerMode = if (playerMode) "text" else "player") } })
                            if (!playerMode) DropdownMenuItem({ Text("Только читать") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, null) },
                                trailingIcon = { if (readOnly) Icon(Icons.Filled.Check, null) }, onClick = {
                                    moreMenu = false
                                    if (!readOnly) { player.pause(); readOnly = true }
                                    else { readOnly = false; visibleTime?.let { player.seekTo(it) } }
                                })
                            DropdownMenuItem({ Text("В машине") }, leadingIcon = { Icon(Icons.Filled.DirectionsCar, null) }, onClick = { moreMenu = false; nav.navigate("car") })
                            np?.let { cur -> DropdownMenuItem({ Text("Пересказ прошлой главы") }, leadingIcon = { Icon(Icons.Filled.History, null) }, onClick = {
                                moreMenu = false
                                scope.launch {
                                    val prev = cur.book.chapters.lastOrNull { it.n < cur.n }
                                    recapText = prev?.let { pc -> app.repo.recaps(cur.book).find { it.n == pc.n }?.let { "${pc.title}\n\n${it.recap}" } } ?: "Для этой главы пересказа нет."
                                }
                            }) }
                            np?.let { cur -> DropdownMenuItem({ Text("Глоссарий") }, leadingIcon = { Icon(Icons.Filled.Info, null) }, onClick = { moreMenu = false; nav.navigate("glossary/${cur.book.key}") }) }
                            if (!playerMode) DropdownMenuItem({ Text("Поиск в главе") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, onClick = { moreMenu = false; searchOpen = true })
                            np?.let { cur ->
                                DropdownMenuItem({ Text("Поиск по книге") }, leadingIcon = { Icon(Icons.Filled.ManageSearch, null) }, onClick = { moreMenu = false; nav.navigate("search/${cur.book.key}") })
                                DropdownMenuItem({ Text("Действующие лица") }, leadingIcon = { Icon(Icons.Filled.Groups, null) }, onClick = { moreMenu = false; nav.navigate("characters/${cur.book.key}") })
                            }
                        }
                    }
                })
        }

        if (readOnly && !immersive) androidx.compose.material3.ExtendedFloatingActionButton(onClick = { readOnly = false; visibleTime?.let { player.seekTo(it) }; player.play() },
            icon = { Icon(Icons.Filled.PlayArrow, null) }, text = { Text("Слушать отсюда") },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp))
        AnimatedVisibility(!immersive && !readOnly, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
                Column(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    val dur = np?.duration ?: 1.0
                    var drag by remember { mutableStateOf<Float?>(null) }
                    Slider(value = drag ?: (pos / dur).toFloat().coerceIn(0f, 1f), onValueChange = { drag = it },
                        onValueChangeFinished = { drag?.let { player.seekTo(it * dur) }; drag = null })
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(fmt(drag?.let { it * dur } ?: pos), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        sleep?.let { Text(if (it < 0) "до конца главы" else "сон ${fmt(it / 1000.0)}", fontSize = 12.5.sp, color = MaterialTheme.colorScheme.primary) }
                        Spacer(Modifier.weight(1f))
                        Text("−" + fmt(dur - pos), fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { player.neighbour(-1)?.let { m -> np?.let { player.open(it.book, m, playing) } } }, enabled = player.neighbour(-1) != null) {
                            Icon(Icons.Filled.SkipPrevious, "Предыдущая глава") }
                        IconButton(onClick = { player.skip(-s.skipBack) }) { Icon(Icons.Filled.Replay10, "Назад ${s.skipBack} с") }
                        FilledIconButton(onClick = { player.toggle() }, Modifier.size(64.dp), shape = RoundedCornerShape(20.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Пауза" else "Играть", Modifier.size(34.dp))
                        }
                        IconButton(onClick = { player.skip(s.skipForward) }) { Icon(Icons.Filled.Forward10, "Вперёд ${s.skipForward} с") }
                        IconButton(onClick = { player.neighbour(1)?.let { m -> np?.let { player.open(it.book, m, playing) } } }, enabled = player.neighbour(1) != null) {
                            Icon(Icons.Filled.SkipNext, "Следующая глава") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Box {
                            TextButton(onClick = { speedMenu = true }) { Text("Скорость ${speed}×".replace('.', ','), fontSize = 13.sp) }
                            DropdownMenu(speedMenu, { speedMenu = false }) {
                                listOf(0.8f, 0.9f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f).forEach { v ->
                                    DropdownMenuItem({ Text("${v}×".replace('.', ','), fontWeight = if (v == speed) FontWeight.Bold else null) }, { player.setSpeed(v); speedMenu = false })
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (readingSheet) ReadingSheet(s, { f -> app.store.settings(f) }) { readingSheet = false }
    if (searchOpen) text?.let { tt -> ChapterSearchDialog(tt, onPick = { time -> searchOpen = false; player.seekTo(time) }) { searchOpen = false } }
    wordMenu?.let { hit -> WordDialog(hit, np?.book?.meta?.title ?: "", np?.book?.coverUrl,
        glossary = if (s.glossaryHints) lookupGlossary(gloss, hit.word, np?.n ?: 0) else null, onHighlight = { color, whole ->
        np?.let { cur ->
            val h = if (whole) io.github.notoboto.unison.data.Highlight(cur.n, hit.p, 0, hit.lastTok, color, "", hit.paragraph, hit.pos)
                    else io.github.notoboto.unison.data.Highlight(cur.n, hit.p, hit.a, hit.b, color, "", hit.sentence, hit.pos)
            app.store.addHighlight(cur.book.key, h)
            android.widget.Toast.makeText(ctx, "Выделено", android.widget.Toast.LENGTH_SHORT).show()
        }
    }) { wordMenu = null } }
    if (sleepSheet) ModalBottomSheet(onDismissRequest = { sleepSheet = false }) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle("Таймер сна")
            Text("Встряхните телефон, чтобы продлить таймер.", Modifier.padding(horizontal = 20.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(5, 15, 30, 45, 60, 90).forEach { m -> Choice("$m мин", false) { player.startSleep(m * 60_000L); sleepSheet = false } }
            Choice("До конца главы", sleep == -1L) { player.startSleep(-1); sleepSheet = false }
            if (sleep != null) Choice("Выключить", false) { player.cancelSleep(); sleepSheet = false }
        }
    }
}

/** Reader appearance, from the settings. */
data class Look(val fs: Int, val family: FontFamily, val lh: Float, val margin: Int, val bg: Color, val fg: Color, val hl: Color?, val sentence: Boolean, val lightBg: Boolean)

@Composable
fun rememberLook(s: Settings): Look {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (s.readerBg) {
        "sepia" -> Color(0xFFF4ECD8) to Color(0xFF5B4636)
        "black" -> Color.Black to Color(0xFFDDDDDD)
        "white" -> Color.White to Color(0xFF222222)
        else -> scheme.background to scheme.onBackground
    }
    val hl = when (s.highlight) { "pink" -> Color(0xFFFF8AB3); "blue" -> Color(0xFF8AC7FF); "green" -> Color(0xFF9BE39B); "none" -> null; else -> HL }
    val fam = when (s.fontFamily) { "serif" -> FontFamily.Serif; "mono" -> FontFamily.Monospace; else -> FontFamily.SansSerif }
    return Look(s.fontSize, fam, s.lineHeight, s.margins, bg, fg, hl, s.sentenceHighlight, bg.luminance() > 0.5f)
}

val HL_COLORS = mapOf("yellow" to Color(0xFFFFD54A), "green" to Color(0xFF9BE39B), "blue" to Color(0xFF8AC7FF), "pink" to Color(0xFFFF8AB3), "purple" to Color(0xFFC9A6FF))

private val sentenceEnd = { w: String -> w.trimEnd('»', '"', ')', '—').let { it.endsWith('.') || it.endsWith('!') || it.endsWith('?') || it.endsWith('…') } }

private fun buildPara(toks: List<Tok>, colors: List<Color>, active: Int, look: Look?, focusRole: Int = -1,
                      marks: List<io.github.notoboto.unison.data.Highlight> = emptyList()): ParaText {
    val starts = IntArray(toks.size)
    var a = active; var b = active
    if (active >= 0 && look?.sentence == true) {
        while (a > 0 && !sentenceEnd(toks[a - 1].word)) a--
        while (b < toks.size - 1 && !sentenceEnd(toks[b].word)) b++
    }
    val hl = if (look == null) HL else look.hl
    val text = buildAnnotatedString {
        toks.forEachIndexed { i, tk ->
            if (i > 0) {
                val m = marks.firstOrNull { (i - 1) in it.a..it.b && i in it.a..it.b }
                if (m != null) withStyle(SpanStyle(background = (HL_COLORS[m.color] ?: HL).copy(alpha = .38f))) { append(' ') } else append(' ')
            }
            starts[i] = length
            var c = if (tk.role > 0) colors[(tk.role - 1) % colors.size] else Color.Unspecified
            if (focusRole >= 0 && tk.role != focusRole) c = (if (c == Color.Unspecified) (look?.fg ?: Color.Gray) else c).copy(alpha = 0.28f)
            val on = active >= 0 && i in a..b
            val mark = marks.firstOrNull { i in it.a..it.b }
            if (!on && mark != null) { withStyle(SpanStyle(background = (HL_COLORS[mark.color] ?: HL).copy(alpha = .38f), color = c)) { append(tk.word) }; return@forEachIndexed }
            when {
                on && hl != null -> withStyle(SpanStyle(background = hl, color = HL_FG)) { append(tk.word) }
                on -> withStyle(SpanStyle(color = c, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append(tk.word) }
                else -> withStyle(SpanStyle(color = c)) { append(tk.word) }
            }
        }
    }
    return ParaText(text, starts)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextMode(t: ChapterText, pos: Double, look: Look, immersive: Boolean, zones: Zones, focusRole: Int, onFocus: (Int) -> Unit,
                     readOnly: Boolean, onVisible: (Double) -> Unit, onTapEmpty: () -> Unit, onSeek: (Double) -> Unit,
                     onLong: (WordHit) -> Unit, marks: List<io.github.notoboto.unison.data.Highlight> = emptyList()) {
    val colors = if (look.lightBg) roleColorsLight else roleColorsDark
    val wi = t.wordAt(pos)
    val activePara = if (wi >= 0) t.timeline[wi].second else -1
    val activeTok = if (wi >= 0) t.timeline[wi].third else -1
    val lazy = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val layouts = remember(t) { mutableStateMapOf<Int, TextLayoutResult>() }
    var userScrollUntil by remember { mutableLongStateOf(0L) }
    var programmatic by remember { mutableStateOf(false) }
    val viewport = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val headerItems = 2   // title + legend
    fun itemOf(pi: Int) = if (pi <= 0) 0 else pi - 1 + headerItems
    // in "read only" mode the screen edges turn pages instead of skipping audio
    val activeZones = if (readOnly) Zones("single", 25) { dir -> scope.launch { lazy.animateScrollBy(dir * viewport * 0.82f) } } else zones

    LaunchedEffect(lazy.isScrollInProgress) { if (lazy.isScrollInProgress && !programmatic) userScrollUntil = System.currentTimeMillis() + 8000 }
    LaunchedEffect(lazy.firstVisibleItemIndex) {
        val pi = (lazy.firstVisibleItemIndex - headerItems + 1).coerceIn(1, t.paras.size - 1)
        t.paras.getOrNull(pi)?.firstOrNull { it.t != null }?.t?.let(onVisible)
    }
    // follow the spoken word
    LaunchedEffect(activePara, activeTok / 6, readOnly) {
        if (readOnly || activePara < 0 || System.currentTimeMillis() < userScrollUntil) return@LaunchedEffect
        val lineTop = layouts[activePara]?.let { lay -> runCatching { lay.getLineTop(lay.getLineForOffset(buildParaStart(t, activePara, activeTok))) }.getOrDefault(0f) } ?: 0f
        val info = lazy.layoutInfo.visibleItemsInfo.find { it.index == itemOf(activePara) }
        val wordY = info?.let { it.offset + lineTop }
        if (wordY != null && wordY > viewport * 0.15f && wordY < viewport * 0.62f) return@LaunchedEffect
        programmatic = true
        lazy.animateScrollToItem(itemOf(activePara), (lineTop - viewport * 0.3f).toInt().coerceAtLeast(-(viewport * 0.3f).toInt()))
        programmatic = false
    }

    val fs = look.fs
    LazyColumn(state = lazy, modifier = Modifier.fillMaxSize().background(look.bg).pointerInput(activeZones) { activeZones.detect(this, onTap = { onTapEmpty() }) },
        contentPadding = PaddingValues(start = look.margin.dp, end = look.margin.dp, top = if (immersive) 24.dp else 110.dp, bottom = if (readOnly) 80.dp else 260.dp)) {
        item {
            Text(t.title, Modifier.statusBarsPadding(), fontSize = (fs * 1.15).sp, fontWeight = FontWeight.Bold, lineHeight = (fs * 1.45).sp,
                fontFamily = look.family, color = look.fg)
        }
        item {
            FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                t.roles.forEachIndexed { i, r ->
                    if (i > 0) Text("● $r", Modifier.clip(RoundedCornerShape(6.dp)).clickable { onFocus(if (focusRole == i) -1 else i) }
                        .background(if (focusRole == i) colors[(i - 1) % colors.size].copy(alpha = .18f) else Color.Transparent).padding(horizontal = 4.dp, vertical = 2.dp),
                        fontSize = 13.sp, color = colors[(i - 1) % colors.size], fontWeight = if (focusRole == i) FontWeight.Bold else null)
                }
            }
        }
        itemsIndexed(t.paras.drop(1)) { idx, toks ->
            val pi = idx + 1
            val act = if (pi == activePara && !readOnly) activeTok else -1
            val pm = marks.filter { it.n == t.n && it.p == pi }
            val pt = remember(t, pi, act, colors, look, focusRole, pm) { buildPara(toks, colors, act, look, focusRole, pm) }
            fun tokAt(off: androidx.compose.ui.geometry.Offset): Int {
                val lay = layouts[pi] ?: return -1
                val ch = lay.getOffsetForPosition(off)
                return pt.starts.indexOfLast { it <= ch }
            }
            Text(pt.text, Modifier.widthIn(max = 720.dp).padding(bottom = (fs * 0.7).dp)
                .pointerInput(pt, activeZones) {
                    activeZones.detect(this, onTap = { off ->
                        var k = tokAt(off)
                        while (k >= 0 && toks[k].t == null) k--
                        val tt = toks.getOrNull(k)?.t
                        if (tt != null && !readOnly) onSeek(tt) else onTapEmpty()
                    }, onLong = { off ->
                        val k = tokAt(off)
                        if (k >= 0) {
                            var a = k; while (a > 0 && !sentenceEnd(toks[a - 1].word)) a--
                            var b = k; while (b < toks.size - 1 && !sentenceEnd(toks[b].word)) b++
                            onLong(WordHit(toks[k].word, toks.subList(a, b + 1).joinToString(" ") { it.word }, pi, a, b,
                                toks.subList(a, b + 1).firstOrNull { it.t != null }?.t ?: 0.0, toks.joinToString(" ") { it.word }, toks.size - 1))
                        }
                    })
                },
                fontSize = fs.sp, lineHeight = (fs * look.lh).sp, fontFamily = look.family, color = look.fg,
                style = MaterialTheme.typography.bodyLarge.copy(textIndent = TextIndent(firstLine = (fs * 1.2).sp)),
                onTextLayout = { layouts[pi] = it })
        }
    }
    // "back to current place" chip while the user scrolls away
    Box(Modifier.fillMaxSize()) {
        if (!readOnly && System.currentTimeMillis() < userScrollUntil && !immersive) {
            Button(onClick = { userScrollUntil = 0; scope.launch { lazy.animateScrollToItem(itemOf(activePara)) } },
                Modifier.align(Alignment.BottomCenter).padding(bottom = 210.dp)) { Text("↧ К текущему месту") }
        }
    }
}

private fun buildParaStart(t: ChapterText, pi: Int, tok: Int): Int {
    var o = 0
    t.paras[pi].forEachIndexed { i, tk -> if (i == tok) return o; o += tk.word.length + 1 }
    return o
}

@Composable
private fun PlayerMode(t: ChapterText, cover: String?, pos: Double, look: Look, zones: Zones, onToggleUi: () -> Unit) {
    val colors = if (LocalIsDark.current) roleColorsDark else roleColorsLight
    val wi = t.wordAt(pos)
    val pi = if (wi >= 0) t.timeline[wi].second else 1
    val ti = if (wi >= 0) t.timeline[wi].third else -1
    val para = t.paras.getOrNull(pi).orEmpty()
    val role = para.getOrNull(ti.coerceAtLeast(0))?.role ?: 0
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 72.dp, bottom = 250.dp, start = 24.dp, end = 24.dp)
        .pointerInput(zones) { zones.detect(this, onTap = { onToggleUi() }) },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(cover, Modifier.fillMaxWidth(0.62f).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(18.dp))
        Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        if (role > 0) Text(t.roles.getOrNull(role) ?: "", color = colors[(role - 1) % colors.size], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        // only the sentence being spoken
        val a = ti.coerceAtLeast(0)
        var from = a; while (from > 0 && !sentenceEnd(para[from - 1].word)) from--
        var to = a; while (to < para.size - 1 && !sentenceEnd(para[to].word)) to++
        val sentence = if (para.isEmpty()) emptyList() else para.subList(from, (to + 1).coerceAtMost(para.size))
        val pt = remember(t, pi, from, ti, colors, look) { buildPara(sentence, colors, ti - from, look.copy(sentence = false)) }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Text(pt.text, fontSize = 19.sp, lineHeight = 28.sp, textAlign = TextAlign.Center, fontFamily = look.family, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Reader tap zones (Mihon-like): taps or double taps at the screen edges skip back / forward. */
class Zones(val mode: String, val widthPct: Int, val onSkip: (Int) -> Unit) {
    private fun zone(x: Float, w: Int): Int {
        val f = x / w.coerceAtLeast(1); val z = widthPct / 100f
        return if (f < z) -1 else if (f > 1 - z) 1 else 0
    }
    suspend fun detect(scope: PointerInputScope, onTap: (androidx.compose.ui.geometry.Offset) -> Unit,
                       onLong: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null) = with(scope) {
        when (mode) {
            "single" -> detectTapGestures(onLongPress = onLong, onTap = { off -> val z = zone(off.x, size.width); if (z != 0) onSkip(z) else onTap(off) })
            "double" -> detectTapGestures(onLongPress = onLong, onDoubleTap = { off -> val z = zone(off.x, size.width); if (z != 0) onSkip(z) }, onTap = onTap)
            else -> detectTapGestures(onLongPress = onLong, onTap = onTap)
        }
    }
    override fun equals(other: Any?) = other is Zones && other.mode == mode && other.widthPct == widthPct
    override fun hashCode() = mode.hashCode() * 31 + widthPct
}

// ---------------- reading settings sheet ----------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ReadingSheet(s: Settings, set: ((Settings) -> Settings) -> Unit, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Размер", Modifier.weight(1f))
                TextButton(onClick = { set { it.copy(fontSize = (it.fontSize - 1).coerceAtLeast(14)) } }) { Text("A−", fontSize = 18.sp) }
                Text("${s.fontSize}", Modifier.width(32.dp), textAlign = TextAlign.Center)
                TextButton(onClick = { set { it.copy(fontSize = (it.fontSize + 1).coerceAtMost(32)) } }) { Text("A+", fontSize = 18.sp) }
            }
            Text("Шрифт", fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("sans" to "Без засечек", "serif" to "С засечками", "mono" to "Моноширинный").forEach { (v, l) ->
                    FilterChip(s.fontFamily == v, { set { it.copy(fontFamily = v) } }, label = { Text(l) })
                }
            }
            Text("Межстрочный интервал: ${"%.1f".format(s.lineHeight)}", fontWeight = FontWeight.Medium)
            Slider(s.lineHeight, { v -> set { it.copy(lineHeight = (v * 10).roundToInt() / 10f) } }, valueRange = 1.2f..2.2f, steps = 9)
            Text("Поля: ${s.margins} dp", fontWeight = FontWeight.Medium)
            Slider(s.margins.toFloat(), { v -> set { it.copy(margins = v.roundToInt()) } }, valueRange = 8f..48f, steps = 9)
            Text("Фон", fontWeight = FontWeight.Medium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("theme" to "Как тема", "sepia" to "Сепия", "black" to "Чёрный", "white" to "Белый").forEach { (v, l) ->
                    FilterChip(s.readerBg == v, { set { it.copy(readerBg = v) } }, label = { Text(l) })
                }
            }
            Text("Цвет подсветки", fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("yellow" to HL, "pink" to Color(0xFFFF8AB3), "blue" to Color(0xFF8AC7FF), "green" to Color(0xFF9BE39B), "none" to Color.Transparent).forEach { (v, c) ->
                    Box(Modifier.size(38.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c)
                        .border(if (s.highlight == v) 3.dp else 1.dp, if (s.highlight == v) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                        .clickable { set { it.copy(highlight = v) } }, contentAlignment = Alignment.Center) { if (v == "none") Text("U", textDecoration = TextDecoration.Underline) }
                }
            }
            Row(Modifier.fillMaxWidth().clickable { set { it.copy(sentenceHighlight = !it.sentenceHighlight) } }, verticalAlignment = Alignment.CenterVertically) {
                Text("Подсвечивать предложение, а не слово", Modifier.weight(1f))
                Switch(s.sentenceHighlight, { v -> set { it.copy(sentenceHighlight = v) } })
            }
        }
    }
}

// ---------------- word actions ----------------
@Composable
private fun WordDialog(hit: WordHit, bookTitle: String, cover: String?, glossary: io.github.notoboto.unison.data.GlossaryEntry? = null,
                       onHighlight: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    val word = hit.word; val sentence = hit.sentence
    var whole by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val clean = word.trim { !it.isLetterOrDigit() && it != '-' }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(clean) }, text = {
        Column {
            glossary?.let { g ->
                androidx.compose.material3.Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.padding(bottom = 10.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(g.term + if (g.kind.isNotBlank()) " · ${g.kind}" else "", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(g.short, fontSize = 14.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            Text("«$sentence»", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            Text("Выделить ${if (whole) "абзац" else "предложение"}:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                HL_COLORS.forEach { (name, c) ->
                    Box(Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c).clickable { onHighlight(name, whole); onDismiss() })
                }
            }
            TextButton(onClick = { whole = !whole }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(if (whole) "Выделять предложение" else "Выделять весь абзац", fontSize = 13.sp) }
            listOf<Pair<String, () -> Unit>>(
                "Копировать предложение" to { clip.setText(AnnotatedString(sentence)); android.widget.Toast.makeText(ctx, "Скопировано", android.widget.Toast.LENGTH_SHORT).show() },
                "Поделиться цитатой" to {
                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, "«$sentence»\n— $bookTitle"), null))
                },
                "Поделиться картинкой" to { ctx.app().scope.launch { runCatching { shareQuoteImage(ctx, sentence, bookTitle, cover) } } },
                "Найти в словаре" to { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://ru.wiktionary.org/wiki/" + android.net.Uri.encode(clean.lowercase())))) },
            ).forEach { (label, act) ->
                Text(label, Modifier.fillMaxWidth().clickable { act(); onDismiss() }.padding(vertical = 12.dp), fontSize = 16.sp)
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } })
}

/** Renders a quote card (cover + quote + title) and opens the share sheet. */
suspend fun shareQuoteImage(ctx: android.content.Context, quote: String, title: String, cover: String?) {
    val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val w = 1080; val h = 1350
        val b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val bg = android.graphics.Paint().apply { shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF2A1630.toInt(), 0xFF120E16.toInt(), android.graphics.Shader.TileMode.CLAMP) }
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bg)
        val coverBmp = cover?.let {
            runCatching {
                val r = coil.request.ImageRequest.Builder(ctx).data(it).allowHardware(false).build()
                (coil.ImageLoader(ctx).execute(r).drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            }.getOrNull()
        }
        coverBmp?.let { c.drawBitmap(it, null, android.graphics.RectF(80f, 80f, 80f + 200f, 80f + 290f), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
        val qp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt(); textSize = 58f; typeface = android.graphics.Typeface.SERIF }
        val text = "«$quote»"
        val lay = android.text.StaticLayout.Builder.obtain(text, 0, text.length, qp, w - 160).setLineSpacing(10f, 1f).setMaxLines(12)
            .setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        c.save(); c.translate(80f, ((h - lay.height) / 2f).coerceAtLeast(420f)); lay.draw(c); c.restore()
        val tp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8417A.toInt(); textSize = 40f; isFakeBoldText = true }
        val tl = android.text.StaticLayout.Builder.obtain(title, 0, title.length, tp, w - 160).setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        c.save(); c.translate(80f, h - 120f - tl.height); tl.draw(c); c.restore()
        val sp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = 0x99FFFFFF.toInt(); textSize = 30f }
        c.drawText("Unison", 80f, h - 70f, sp)
        b
    }
    val f = java.io.File(java.io.File(ctx.cacheDir, "share").apply { mkdirs() }, "quote.png")
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("image/png")
        .putExtra(android.content.Intent.EXTRA_STREAM, uri).addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
}

// ---------------- chapter search ----------------
@Composable
private fun ChapterSearchDialog(t: ChapterText, onPick: (Double) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val hits = remember(q, t) {
        val qq = q.trim().lowercase()
        if (qq.length < 2) emptyList() else t.paras.mapIndexedNotNull { pi, toks ->
            val txt = toks.joinToString(" ") { it.word }; val i = txt.lowercase().indexOf(qq); if (i < 0) return@mapIndexedNotNull null
            var o = 0; var ti = 0
            for ((k, tk) in toks.withIndex()) { if (o + tk.word.length >= i) { ti = k; break }; o += tk.word.length + 1 }
            val time = toks.drop(ti).firstOrNull { it.t != null }?.t ?: return@mapIndexedNotNull null
            Triple(pi, txt.substring((i - 40).coerceAtLeast(0), (i + qq.length + 60).coerceAtMost(txt.length)), time)
        }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Поиск в главе") }, text = {
        Column {
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Слово или фраза") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            if (q.trim().length >= 2) Text("Найдено: ${hits.size}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(hits.size) { i ->
                    val (_, snip, time) = hits[i]
                    Text("${fmt(time)}  …$snip…", Modifier.fillMaxWidth().clickable { onPick(time) }.padding(vertical = 10.dp), fontSize = 14.sp)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } })
}

// ---------------- book search ----------------
@Composable
fun BookSearchScreen(nav: NavController, key: String) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val book = remember(key) { app.repo.book(key) } ?: return
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var progress by remember { mutableStateOf<Float?>(null) }
    val hits = remember { mutableStateListOf<Triple<Int, String, Double>>() }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun run() {
        val qq = q.trim().lowercase(); if (qq.length < 2) return
        job?.cancel(); hits.clear()
        job = scope.launch {
            val chs = book.chapters.filter { !st.settings.downloadedOnly || app.store.isDownloaded(key, it.n) }
            chs.forEachIndexed { i, ch ->
                progress = i.toFloat() / chs.size.coerceAtLeast(1)
                val t = runCatching { app.repo.chapterText(book, ch.n) }.getOrNull() ?: return@forEachIndexed
                t.paras.forEach { toks ->
                    val txt = toks.joinToString(" ") { it.word }; val idx = txt.lowercase().indexOf(qq)
                    if (idx >= 0) {
                        var o = 0; var ti = 0
                        for ((k, tk) in toks.withIndex()) { if (o + tk.word.length >= idx) { ti = k; break }; o += tk.word.length + 1 }
                        toks.drop(ti).firstOrNull { it.t != null }?.t?.let { time ->
                            hits += Triple(ch.n, txt.substring((idx - 50).coerceAtLeast(0), (idx + qq.length + 70).coerceAtMost(txt.length)), time)
                        }
                    }
                }
            }
            progress = null
        }
    }
    Scaffold(topBar = { BackBar(nav, "Поиск по книге") }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp), singleLine = true, placeholder = { Text("Слово или фраза") },
                    trailingIcon = { IconButton(onClick = { run() }) { Icon(Icons.Filled.Search, "Искать") } },
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { run() }),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search))
                progress?.let { LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp)) }
                if (progress == null && hits.isEmpty() && job != null) Text("Ничего не найдено", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hits.isNotEmpty()) GroupHeader("Найдено: ${hits.size}")
            }
            items(hits.size) { i ->
                val (n, snip, time) = hits[i]
                val ch = book.chapters.find { it.n == n }
                ListItem(overlineContent = { Text("${ch?.let { chName(it, st.settings.fullTitles) } ?: "Глава $n"} • ${fmt(time)}") },
                    headlineContent = { Text("…$snip…", fontSize = 14.sp) },
                    modifier = Modifier.clickable {
                        app.player.open(book, n, autoplay = false); app.player.seekTo(time); nav.navigate("reader/$key/$n")
                    })
            }
        }
    }
}

/** The sentence being spoken at [pos] (used as the bookmark quote). */
fun sentenceAt(t: ChapterText, pos: Double): String {
    val wi = t.wordAt(pos); if (wi < 0) return t.title
    val (_, pi, ti) = t.timeline[wi]; val para = t.paras[pi]
    val ends = { w: String -> w.trimEnd('»', '"', ')').let { it.endsWith('.') || it.endsWith('!') || it.endsWith('?') || it.endsWith('…') } }
    var a = ti; while (a > 0 && !ends(para[a - 1].word)) a--
    var b = ti; while (b < para.size - 1 && !ends(para[b].word)) b++
    return para.subList(a, b + 1).joinToString(" ") { it.word }.take(300)
}

/** What was long-pressed: the word, its sentence (tokens a..b) in paragraph p, and the whole paragraph. */
data class WordHit(val word: String, val sentence: String, val p: Int, val a: Int, val b: Int, val pos: Double, val paragraph: String, val lastTok: Int)

// ---------------- highlights list + export ----------------
@Composable
fun HighlightsScreen(nav: NavController, key: String) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val st by app.store.state.collectAsState()
    val book = remember(key) { app.repo.book(key) } ?: return
    val list = st.highlights[key].orEmpty()
    var editing by remember { mutableStateOf<io.github.notoboto.unison.data.Highlight?>(null) }
    fun markdown(): String = buildString {
        append("# ${book.meta.title}\n"); book.meta.author?.let { append("*$it*\n") }; append("\n")
        list.groupBy { it.n }.forEach { (n, hs) ->
            append("## ${book.chapters.find { it.n == n }?.title ?: "Глава $n"}\n\n")
            hs.forEach { h -> append("> ${h.text}\n\n"); if (h.note.isNotBlank()) append("${h.note}\n\n") }
        }
    }
    val save = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri?.let { u -> runCatching { ctx.contentResolver.openOutputStream(u, "wt")!!.use { it.write(markdown().toByteArray()) } } }
    }
    Scaffold(topBar = { BackBar(nav, "Выделения") {
        if (list.isNotEmpty()) {
            IconButton(onClick = { ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, markdown()), null)) }) { Icon(Icons.Filled.Share, "Поделиться") }
            IconButton(onClick = { save.launch(book.shortTitle + " — выделения.md") }) { Icon(Icons.Filled.Save, "Сохранить .md") }
        }
    } }) { pad ->
        if (list.isEmpty()) EmptyState("🖍", "Выделений пока нет.\nДолгое нажатие на слово в тексте → выберите цвет.", Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad)) {
            list.groupBy { it.n }.forEach { (n, hs) ->
                item(key = "h$n") { GroupHeader(book.chapters.find { it.n == n }?.let { chName(it, st.settings.fullTitles) } ?: "Глава $n") }
                items(hs, key = { "${it.n}:${it.p}:${it.a}:${it.ts}" }) { h ->
                    Row(Modifier.fillMaxWidth().clickable {
                        app.player.open(book, h.n, autoplay = false); app.player.seekTo(h.pos); nav.navigate("reader/$key/${h.n}")
                    }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Box(Modifier.padding(top = 4.dp).size(width = 4.dp, height = 40.dp).background(HL_COLORS[h.color] ?: HL))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.text, maxLines = 6, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                            if (h.note.isNotBlank()) Text(h.note, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        IconButton(onClick = { editing = h }) { Icon(Icons.Filled.Edit, "Заметка") }
                        IconButton(onClick = { app.store.removeHighlight(key, h) }) { Icon(Icons.Filled.Delete, "Удалить") }
                    }
                }
            }
        }
    }
    editing?.let { h -> NameDialog("Заметка", h.note, { editing = null }) { app.store.editHighlight(key, h, it); editing = null } }
}

/** Glossary entry for a word (by term or alias stem), only if it was already introduced by chapter [n] — no spoilers. */
fun lookupGlossary(list: List<io.github.notoboto.unison.data.GlossaryEntry>, word: String, n: Int): io.github.notoboto.unison.data.GlossaryEntry? {
    fun norm(x: String) = x.lowercase().replace('ё', 'е').trim { !it.isLetterOrDigit() }
    val w = norm(word)
    if (w.length < 3) return null
    // Russian endings: compare stems (drop up to 2 trailing letters on longer words)
    fun stem(x: String) = norm(x).let { if (it.length > 5) it.dropLast(2) else if (it.length > 4) it.dropLast(1) else it }
    val ws = stem(w)
    data class Hit(val e: io.github.notoboto.unison.data.GlossaryEntry, val score: Int)
    val hits = list.filter { it.since <= n }.mapNotNull { e ->
        val names = listOf(e.term) + e.aliases
        var best = 0
        for (nm in names) {
            val parts = nm.trim().split(' ').map { norm(it) }.filter { it.length >= 3 }
            val single = parts.size == 1
            // first word (Драко) or, for people, the surname (Малфой)
            for (part in listOfNotNull(parts.firstOrNull(), parts.lastOrNull().takeIf { e.kind == "персонаж" && !single })) {
                when {
                    part == w -> best = maxOf(best, if (single) 4 else 3)
                    stem(part) == ws || (ws.length >= 4 && part.startsWith(ws)) || (stem(part).length >= 4 && w.startsWith(stem(part))) ->
                        best = maxOf(best, if (single) 2 else 1)
                }
            }
        }
        if (best == 0) null else Hit(e, best * 1000 + (if (e.kind == "персонаж") 200 else 0) - e.since)
    }
    return hits.maxByOrNull { it.score }?.takeIf { it.score >= 1000 }?.e
}

// ---------------- glossary ----------------
@Composable
fun GlossaryScreen(nav: NavController, key: String) {
    val app = LocalContext.current.app()
    val st by app.store.state.collectAsState()
    val book = remember(key) { app.repo.book(key) } ?: return
    var list by remember { mutableStateOf<List<io.github.notoboto.unison.data.GlossaryEntry>?>(null) }
    var q by remember { mutableStateOf("") }
    var all by remember { mutableStateOf(false) }
    LaunchedEffect(key) { list = app.repo.glossary(book) }
    // no spoilers: only what appeared up to the furthest chapter you opened
    val reach = maxOf(st.progress[key].orEmpty().keys.maxOrNull() ?: 0, st.last[key] ?: 0)
    Scaffold(topBar = { BackBar(nav, "Глоссарий") }) { pad ->
        val l = list
        if (l == null) EmptyState("⏳", "Загружаю…", Modifier.padding(pad))
        else if (l.isEmpty()) EmptyState("📖", "Для этой книги глоссария нет.", Modifier.padding(pad))
        else LazyColumn(Modifier.padding(pad)) {
            item {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp), singleLine = true, placeholder = { Text("Поиск") })
                Row(Modifier.fillMaxWidth().clickable { all = !all }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Показывать всё, включая будущие главы (спойлеры)", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(all, { all = it })
                }
            }
            val shown = l.filter { all || it.since <= reach }.filter { q.isBlank() || (listOf(it.term) + it.aliases).any { a -> a.contains(q, true) } || it.short.contains(q, true) }
                .sortedBy { it.term.lowercase() }
            items(shown, key = { it.term + it.since }) { e ->
                ListItem(headlineContent = { Text(e.term, fontWeight = FontWeight.Medium) },
                    overlineContent = { Text(listOfNotNull(e.kind.ifBlank { null }, "с главы ${e.since}").joinToString(" · ")) },
                    supportingContent = { Text(e.short + if (e.aliases.isNotEmpty()) "\nТакже: " + e.aliases.joinToString(", ") else "") })
            }
        }
    }
}

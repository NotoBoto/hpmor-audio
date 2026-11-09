package io.github.notoboto.unison.ui

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
    var fontMenu by remember { mutableStateOf(false) }
    val playerMode = s.readerMode == "player"

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val t = text
        when {
            error != null -> EmptyState("📡", error!!)
            t == null -> EmptyState("⏳", "Загружаю главу…")
            playerMode -> PlayerMode(t, np?.book?.coverUrl, pos, playing, zones, onToggleUi = { immersive = !immersive })
            else -> TextMode(t, pos, s.fontSize, immersive, zones, onTapEmpty = { immersive = !immersive }, onSeek = { player.seekTo(it); player.play() })
        }

        AnimatedVisibility(!immersive, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .97f)),
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = { Text(np?.let { if (s.fullTitles) it.title else "Глава ${it.n}" } ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 17.sp) },
                actions = {
                    Box {
                        IconButton(onClick = { fontMenu = true }) { Icon(Icons.Outlined.FormatSize, "Размер текста") }
                        DropdownMenu(fontMenu, { fontMenu = false }) {
                            Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { app.store.settings { it.copy(fontSize = (it.fontSize - 1).coerceAtLeast(14)) } }) { Text("A−", fontSize = 18.sp) }
                                Text("${s.fontSize}", Modifier.width(32.dp), textAlign = TextAlign.Center)
                                TextButton(onClick = { app.store.settings { it.copy(fontSize = (it.fontSize + 1).coerceAtMost(30)) } }) { Text("A+", fontSize = 18.sp) }
                            }
                        }
                    }
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
                    IconButton(onClick = { app.store.settings { it.copy(readerMode = if (playerMode) "text" else "player") } }) {
                        Icon(if (playerMode) Icons.AutoMirrored.Outlined.Article else Icons.Filled.Headphones, if (playerMode) "Текст" else "Плеер")
                    }
                })
        }

        AnimatedVisibility(!immersive, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
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

private fun buildPara(toks: List<io.github.notoboto.unison.data.Tok>, colors: List<Color>, active: Int): ParaText {
    val starts = IntArray(toks.size)
    val text = buildAnnotatedString {
        toks.forEachIndexed { i, tk ->
            if (i > 0) append(' ')
            starts[i] = length
            val c = if (tk.role > 0) colors[(tk.role - 1) % colors.size] else Color.Unspecified
            if (i == active) withStyle(SpanStyle(background = HL, color = HL_FG)) { append(tk.word) }
            else withStyle(SpanStyle(color = c)) { append(tk.word) }
        }
    }
    return ParaText(text, starts)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextMode(t: ChapterText, pos: Double, fontSize: Int, immersive: Boolean, zones: Zones, onTapEmpty: () -> Unit, onSeek: (Double) -> Unit) {
    val colors = if (LocalIsDark.current) roleColorsDark else roleColorsLight
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

    LaunchedEffect(lazy.isScrollInProgress) { if (lazy.isScrollInProgress && !programmatic) userScrollUntil = System.currentTimeMillis() + 8000 }
    // follow the spoken word
    LaunchedEffect(activePara, activeTok / 6) {
        if (activePara < 0 || System.currentTimeMillis() < userScrollUntil) return@LaunchedEffect
        val lineTop = layouts[activePara]?.let { lay -> runCatching { lay.getLineTop(lay.getLineForOffset(buildParaStart(t, activePara, activeTok))) }.getOrDefault(0f) } ?: 0f
        val info = lazy.layoutInfo.visibleItemsInfo.find { it.index == itemOf(activePara) }
        val wordY = info?.let { it.offset + lineTop }
        if (wordY != null && wordY > viewport * 0.15f && wordY < viewport * 0.62f) return@LaunchedEffect
        programmatic = true
        lazy.animateScrollToItem(itemOf(activePara), (lineTop - viewport * 0.3f).toInt().coerceAtLeast(-(viewport * 0.3f).toInt()))
        programmatic = false
    }

    LazyColumn(state = lazy, modifier = Modifier.fillMaxSize().pointerInput(zones) { zones.detect(this) { onTapEmpty() } },
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = if (immersive) 24.dp else 110.dp, bottom = 260.dp)) {
        item {
            Text(t.title, Modifier.statusBarsPadding(), fontSize = (fontSize * 1.15).sp, fontWeight = FontWeight.Bold, lineHeight = (fontSize * 1.45).sp)
        }
        item {
            FlowRow(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                t.roles.forEachIndexed { i, r -> if (i > 0) Text("● $r", fontSize = 13.sp, color = colors[(i - 1) % colors.size]) }
            }
        }
        itemsIndexed(t.paras.drop(1)) { idx, toks ->
            val pi = idx + 1
            val pt = remember(t, pi, if (pi == activePara) activeTok else -1, colors) { buildPara(toks, colors, if (pi == activePara) activeTok else -1) }
            Text(pt.text, Modifier.widthIn(max = 720.dp).padding(bottom = (fontSize * 0.7).dp)
                .pointerInput(pt, zones) {
                    zones.detect(this) { off ->
                        val lay = layouts[pi] ?: return@detect
                        val ch = lay.getOffsetForPosition(off)
                        var k = pt.starts.indexOfLast { it <= ch }
                        while (k >= 0 && toks[k].t == null) k--
                        val tt = toks.getOrNull(k)?.t
                        if (tt != null) onSeek(tt) else onTapEmpty()
                    }
                },
                fontSize = fontSize.sp, lineHeight = (fontSize * 1.6).sp, style = MaterialTheme.typography.bodyLarge.copy(textIndent = TextIndent(firstLine = (fontSize * 1.2).sp)),
                onTextLayout = { layouts[pi] = it })
        }
    }
    // "back to current place" chip while the user scrolls away
    Box(Modifier.fillMaxSize()) {
        if (System.currentTimeMillis() < userScrollUntil && !immersive) {
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
private fun PlayerMode(t: ChapterText, cover: String?, pos: Double, playing: Boolean, zones: Zones, onToggleUi: () -> Unit) {
    val colors = if (LocalIsDark.current) roleColorsDark else roleColorsLight
    val wi = t.wordAt(pos)
    val pi = if (wi >= 0) t.timeline[wi].second else 1
    val ti = if (wi >= 0) t.timeline[wi].third else -1
    val para = t.paras.getOrNull(pi).orEmpty()
    val role = para.getOrNull(ti.coerceAtLeast(0))?.role ?: 0
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 72.dp, bottom = 250.dp, start = 24.dp, end = 24.dp)
        .pointerInput(zones) { zones.detect(this) { onToggleUi() } },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(cover, Modifier.fillMaxWidth(0.62f).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(18.dp))
        Text(t.title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        if (role > 0) Text(t.roles.getOrNull(role) ?: "", color = colors[(role - 1) % colors.size], fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        // only the sentence being spoken
        val endsSentence = { w: String -> w.trimEnd('»', '"', ')', '—').let { it.endsWith('.') || it.endsWith('!') || it.endsWith('?') || it.endsWith('…') } }
        val a = ti.coerceAtLeast(0)
        var from = a; while (from > 0 && !endsSentence(para[from - 1].word)) from--
        var to = a; while (to < para.size - 1 && !endsSentence(para[to].word)) to++
        val sentence = if (para.isEmpty()) emptyList() else para.subList(from, (to + 1).coerceAtMost(para.size))
        val pt = remember(t, pi, from, ti, colors) { buildPara(sentence, colors, ti - from) }
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
            Text(pt.text, fontSize = 19.sp, lineHeight = 28.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Reader tap zones (Mihon-like): taps or double taps at the screen edges skip back / forward. */
class Zones(val mode: String, val widthPct: Int, val onSkip: (Int) -> Unit) {
    private fun zone(x: Float, w: Int): Int {
        val f = x / w.coerceAtLeast(1); val z = widthPct / 100f
        return if (f < z) -1 else if (f > 1 - z) 1 else 0
    }
    suspend fun detect(scope: PointerInputScope, onTap: (androidx.compose.ui.geometry.Offset) -> Unit) = with(scope) {
        when (mode) {
            "single" -> detectTapGestures { off -> val z = zone(off.x, size.width); if (z != 0) onSkip(z) else onTap(off) }
            "double" -> detectTapGestures(onDoubleTap = { off -> val z = zone(off.x, size.width); if (z != 0) onSkip(z) }, onTap = onTap)
            else -> detectTapGestures(onTap = onTap)
        }
    }
    override fun equals(other: Any?) = other is Zones && other.mode == mode && other.widthPct == widthPct
    override fun hashCode() = mode.hashCode() * 31 + widthPct
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

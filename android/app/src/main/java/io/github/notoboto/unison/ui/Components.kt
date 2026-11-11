package io.github.notoboto.unison.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.notoboto.unison.data.Book
import io.github.notoboto.unison.data.ChapterMeta
import io.github.notoboto.unison.data.Progress
import io.github.notoboto.unison.download.DlState
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

fun fmt(sec: Double): String {
    val s = sec.coerceAtLeast(0.0).toInt(); val h = s / 3600; val m = s / 60 % 60; val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}
fun hours(sec: Double) = if (sec >= 3600) "${(sec / 3600).roundToInt()} ч" else "${(sec / 60).roundToInt()} мин"
fun mb(b: Long) = if (b > 100 shl 20) "${b shr 20} МБ" else "%.1f МБ".format(b / 1048576.0)
fun plural(n: Int, a: String, b: String, c: String): String { val m = n % 100; val k = n % 10
    return if (m in 11..19) c else if (k == 1) a else if (k in 2..4) b else c }
fun chapters(n: Int) = "$n ${plural(n, "глава", "главы", "глав")}"

private val RU = Locale.forLanguageTag("ru")
fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) { today -> "Сегодня"; today.minusDays(1) -> "Вчера"
        else -> d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "d MMMM" else "d MMMM yyyy", RU)) }
}
fun parseDate(s: String?): LocalDateTime? = s?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
fun hhmm(t: LocalDateTime) = t.format(DateTimeFormatter.ofPattern("HH:mm"))

// duration is 0 for a chapter whose text is not on the phone yet (plugin books): no division by zero → NaN
fun progressOf(p: Progress, ch: ChapterMeta) = if (p.done) 1f else if (ch.duration <= 0) 0f else (p.pos / ch.duration).toFloat().coerceIn(0f, 1f)
fun subline(p: Progress, ch: ChapterMeta): String {
    val f = progressOf(p, ch)
    return fmt(ch.duration) + when {
        f >= 1f -> " • прослушано"
        f > 0.005f -> " • прослушано ${(f * 100).roundToInt()}% • осталось ${hours(ch.duration * (1 - f))}"
        else -> ""
    }
}
fun chName(ch: ChapterMeta, full: Boolean) = if (full) ch.title else "Глава ${ch.n}"

@Composable
fun Cover(url: String?, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        Icon(Icons.AutoMirrored.Outlined.MenuBook, null, tint = MaterialTheme.colorScheme.outline)
        AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** Tachiyomi-style download indicator: arrow → progress ring → filled check (or refresh when re-voiced). */
@Composable
fun DlButton(st: DlState, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit, onUpdate: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = {
            when (st) { DlState.None -> onDownload(); DlState.Queued, is DlState.Running -> onCancel(); else -> menu = true }
        }) {
            when (st) {
                DlState.None -> Box(Modifier.size(26.dp).clip(CircleShape).background(Color.Transparent), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { 1f }, Modifier.size(26.dp), color = MaterialTheme.colorScheme.outlineVariant, strokeWidth = 2.dp)
                    Icon(Icons.Outlined.ArrowDownward, "Скачать", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DlState.Queued -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.outline)
                is DlState.Running -> Box(contentAlignment = Alignment.Center) {
                    val p by animateFloatAsState(st.p, label = "dl")
                    CircularProgressIndicator(progress = { p }, Modifier.size(26.dp), strokeWidth = 2.5.dp, trackColor = MaterialTheme.colorScheme.outlineVariant)
                    Icon(Icons.Outlined.Stop, "Отменить", Modifier.size(14.dp))
                }
                DlState.Done -> Icon(Icons.Filled.CheckCircle, "Скачано", Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                DlState.Outdated -> Box(Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Refresh, "Есть новая озвучка", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        DropdownMenu(menu, { menu = false }) {
            if (st == DlState.Outdated) DropdownMenuItem({ Text("Скачать новую версию") }, { menu = false; onUpdate() })
            DropdownMenuItem({ Text("Удалить с телефона") }, { menu = false; onDelete() })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChapterRow(
    title: String, sub: String, read: Boolean, selected: Boolean, dl: DlState,
    onClick: () -> Unit, onLongClick: (() -> Unit)?, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit, onUpdate: () -> Unit,
    thumb: String? = null, trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumb != null) { Cover(thumb, Modifier.width(40.dp).height(56.dp)); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f).alpha(if (read) .5f else 1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!read) { Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)); Spacer(Modifier.width(7.dp)) }
                Text(title, fontSize = 15.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(3.dp))
            Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing() else DlButton(dl, onDownload, onCancel, onDelete, onUpdate)
    }
}

@Composable
fun GroupHeader(text: String) = Text(text, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun EmptyState(emoji: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(emoji, fontSize = 46.sp)
        Spacer(Modifier.height(10.dp))
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

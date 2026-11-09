package io.github.notoboto.unison

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.notoboto.unison.ui.BookScreen
import io.github.notoboto.unison.ui.BrowseScreen
import io.github.notoboto.unison.ui.CatalogScreen
import io.github.notoboto.unison.ui.Cover
import io.github.notoboto.unison.ui.DownloadsScreen
import io.github.notoboto.unison.ui.HistoryScreen
import io.github.notoboto.unison.ui.LibraryScreen
import io.github.notoboto.unison.ui.MoreScreen
import io.github.notoboto.unison.ui.AboutScreen
import io.github.notoboto.unison.ui.ReaderScreen
import io.github.notoboto.unison.ui.SettingsScreen
import io.github.notoboto.unison.ui.UnisonTheme
import io.github.notoboto.unison.ui.UpdatesScreen
import io.github.notoboto.unison.ui.fadeThroughIn
import io.github.notoboto.unison.ui.fadeThroughOut
import io.github.notoboto.unison.ui.sharedAxisXIn
import io.github.notoboto.unison.ui.sharedAxisXOut
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var pendingOpenPlayer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingOpenPlayer = intent?.getBooleanExtra("openPlayer", false) == true
        if (Build.VERSION.SDK_INT >= 33)
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            val st by app().store.state.collectAsState()
            UnisonTheme(st.settings) { AppRoot(openPlayer = pendingOpenPlayer.also { pendingOpenPlayer = false }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("openPlayer", false)) openPlayerRequest.value = true
    }
    override fun onStop() { super.onStop(); app().player.saveNow(); app().store.flush() }

    companion object { val openPlayerRequest = kotlinx.coroutines.flow.MutableStateFlow(false) }
}

private data class Tab(val route: String, val label: String, val on: ImageVector, val off: ImageVector)
private val TABS = listOf(
    Tab("library", "Библиотека", Icons.AutoMirrored.Filled.LibraryBooks, Icons.AutoMirrored.Outlined.LibraryBooks),
    Tab("updates", "Обновления", Icons.Filled.NewReleases, Icons.Outlined.NewReleases),
    Tab("history", "История", Icons.Filled.History, Icons.Outlined.History),
    Tab("browse", "Обзор", Icons.Filled.Explore, Icons.Outlined.Explore),
    Tab("more", "Ещё", Icons.Filled.MoreHoriz, Icons.Filled.MoreHoriz),
)

@Composable
fun AppRoot(openPlayer: Boolean) {
    val nav = rememberNavController()
    val app = androidx.compose.ui.platform.LocalContext.current.app()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "library"
    val topLevel = TABS.any { it.route == route }
    val scope = rememberCoroutineScope()
    val update by app.updater.available.collectAsState()
    val upProgress by app.updater.progress.collectAsState()
    val st by app.store.state.collectAsState()
    val chapters by app.repo.chapters.collectAsState()

    LaunchedEffect(Unit) {
        app.repo.refreshAll()
        app.updater.check()
        if (app.store.s.settings.autoUpdate) app.downloads.updateOutdated(app.repo.libraryBooks())
    }
    val openReq by MainActivity.openPlayerRequest.collectAsState()
    LaunchedEffect(openPlayer, openReq) {
        if (openPlayer || openReq) app.player.now.value?.let { nav.navigate("reader/${it.book.key}/${it.n}") { launchSingleTop = true } }
        MainActivity.openPlayerRequest.value = false
    }

    // unseen updates badge
    val newCount = run {
        val seen = st.seenUpdates
        app.repo.libraryBooks().sumOf { b -> b.chapters.count { (it.date ?: "") > seen } }.takeIf { seen.isNotEmpty() } ?: 0
    }.also { chapters.size }

    Scaffold(
        bottomBar = {
            if (topLevel) Column {
                MiniPlayer(nav)
                NavigationBar {
                    TABS.forEach { t ->
                        val sel = route == t.route
                        NavigationBarItem(sel, onClick = {
                            if (!sel) nav.navigate(t.route) { popUpTo("library") { saveState = true }; launchSingleTop = true; restoreState = true }
                        }, icon = {
                            if (t.route == "updates" && newCount > 0) BadgedBox(badge = { Badge { Text(if (newCount > 99) "99+" else "$newCount") } }) { Icon(if (sel) t.on else t.off, null) }
                            else Icon(if (sel) t.on else t.off, null)
                        }, label = { Text(t.label, maxLines = 1, fontSize = 11.5.sp) })
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(bottom = if (topLevel) pad.calculateBottomPadding() else 0.dp)) { NavGraph(nav) }
    }

    update?.let { rel ->
        AlertDialog(onDismissRequest = { app.updater.available.value = null },
            title = { Text("Доступна версия ${rel.version}") },
            text = {
                Column {
                    Text(rel.notes.ifBlank { "Новая версия Unison." }, maxLines = 12, overflow = TextOverflow.Ellipsis)
                    upProgress?.let { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(progress = { it }, Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { app.updater.install(rel) } }, enabled = upProgress == null) { Text("Обновить") } },
            dismissButton = { TextButton(onClick = { app.updater.available.value = null }) { Text("Позже") } })
    }
}

@Composable
private fun NavGraph(nav: NavHostController) {
    val tabs = TABS.map { it.route }.toSet()
    fun both(a: androidx.navigation.NavBackStackEntry, b: androidx.navigation.NavBackStackEntry) = a.destination.route in tabs && b.destination.route in tabs
    NavHost(nav, startDestination = "library",
        enterTransition = { if (both(initialState, targetState)) fadeThroughIn() else sharedAxisXIn(true) },
        exitTransition = { if (both(initialState, targetState)) fadeThroughOut() else sharedAxisXOut(true) },
        popEnterTransition = { if (both(initialState, targetState)) fadeThroughIn() else sharedAxisXIn(false) },
        popExitTransition = { if (both(initialState, targetState)) fadeThroughOut() else sharedAxisXOut(false) },
    ) {
        composable("library") { LibraryScreen(nav) }
        composable("updates") { UpdatesScreen(nav) }
        composable("history") { HistoryScreen(nav) }
        composable("browse") { BrowseScreen(nav) }
        composable("more") { MoreScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("downloads") { DownloadsScreen(nav) }
        composable("about") { AboutScreen(nav) }
        composable("catalog/{src}") { CatalogScreen(nav, it.arguments?.getString("src")!!.toInt()) }
        composable("book/{key}") { BookScreen(nav, it.arguments?.getString("key")!!) }
        composable("reader/{key}/{n}") { ReaderScreen(nav, it.arguments?.getString("key")!!, it.arguments?.getString("n")!!.toInt()) }
    }
}

@Composable
private fun MiniPlayer(nav: NavHostController) {
    val app = androidx.compose.ui.platform.LocalContext.current.app()
    val now by app.player.now.collectAsState()
    val playing by app.player.isPlaying.collectAsState()
    val pos by app.player.position.collectAsState()
    AnimatedVisibility(now != null) {
        val np = now ?: return@AnimatedVisibility
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            LinearProgressIndicator(progress = { (pos / np.duration).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().height(2.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            Row(Modifier.fillMaxWidth().clickable { nav.navigate("reader/${np.book.key}/${np.n}") }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Cover(np.book.coverSmUrl, Modifier.size(width = 34.dp, height = 48.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (app.store.state.collectAsState().value.settings.fullTitles) np.title else "Глава ${np.n}", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, fontSize = 14.5.sp)
                    Text(np.book.shortTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { app.player.toggle() }) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (playing) "Пауза" else "Играть", Modifier.size(30.dp))
                }
            }
        }
    }
}

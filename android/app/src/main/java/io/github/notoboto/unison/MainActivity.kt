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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val locked = kotlinx.coroutines.flow.MutableStateFlow(false)
    private var backgroundAt = 0L

    override fun onStart() {
        super.onStart()
        val s = app().store.s.settings
        if (s.hideInRecents) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        if (s.appLock && (backgroundAt == 0L || System.currentTimeMillis() - backgroundAt >= s.lockAfterMin * 60_000L)) { locked.value = true; unlock() }
    }

    fun unlock() {
        val mgr = androidx.biometric.BiometricManager.from(this)
        val auth = if (Build.VERSION.SDK_INT >= 30) androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                   else androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (mgr.canAuthenticate(auth) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) { locked.value = false; return }   // no lock screen set up
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder().setTitle("Unison").setSubtitle("Разблокируйте, чтобы продолжить")
            .setAllowedAuthenticators(auth).apply { if (Build.VERSION.SDK_INT < 30) setNegativeButtonText("Отмена") }.build()
        androidx.biometric.BiometricPrompt(this, androidx.core.content.ContextCompat.getMainExecutor(this), object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) { locked.value = false }
        }).authenticate(info)
    }

    private var pendingOpenPlayer = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Phones: portrait only (the reader has no landscape layout yet). Tablets (smallest side ≥ 600 dp) rotate freely;
        // Android 16 ignores orientation locks on large screens anyway.
        requestedOrientation = if (resources.configuration.smallestScreenWidthDp < 600) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingOpenPlayer = intent?.getBooleanExtra("openPlayer", false) == true
        if (intent?.getBooleanExtra("openUpdates", false) == true) openUpdatesRequest.value = true
        handleShortcut(intent)
        setContent {
            val st by app().store.state.collectAsState()
            UnisonTheme(st.settings) {
                val isLocked by locked.collectAsState()
                if (isLocked) androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.Button(onClick = { unlock() }) { Text("🔒 Разблокировать") }
                    }
                } else if (!st.onboardingDone) androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) { io.github.notoboto.unison.ui.OnboardingScreen { app().store.update { it.copy(onboardingDone = true) } } }
                else AppRoot(openPlayer = pendingOpenPlayer.also { pendingOpenPlayer = false })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("openUpdates", false)) openUpdatesRequest.value = true
        handleShortcut(intent)
        if (intent.getBooleanExtra("openPlayer", false)) openPlayerRequest.value = true
    }
    /** App-icon shortcuts: continue, 30-minute session, car mode. */
    private fun handleShortcut(i: Intent?) {
        val p = app().player
        when (i?.action) {
            "io.github.notoboto.unison.RESUME" -> { p.toggleOrResume(); openPlayerRequest.value = true }
            "io.github.notoboto.unison.SLEEP30" -> { if (!p.isPlaying.value) p.toggleOrResume(); p.startSleep(30 * 60_000L); openPlayerRequest.value = true }
            "io.github.notoboto.unison.CAR" -> { if (!p.isPlaying.value) p.toggleOrResume(); openCarRequest.value = true }
        }
    }

    override fun onStop() { backgroundAt = System.currentTimeMillis(); super.onStop(); app().player.saveNow(); app().store.flush() }

    companion object {
        val openPlayerRequest = kotlinx.coroutines.flow.MutableStateFlow(false)
        val openUpdatesRequest = kotlinx.coroutines.flow.MutableStateFlow(false)
        val openCarRequest = kotlinx.coroutines.flow.MutableStateFlow(false)
    }
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
        app.repo.refreshAll(pluginMaxAgeMs = io.github.notoboto.unison.data.Repo.PLUGIN_CHECK_MS)   // app start: plugins at most every 6 h
        if (app.store.s.knownChapters.isEmpty()) rememberChapterCounts(app)
        scheduleLibraryCheck(app)
        app.updater.check()
        if (app.store.s.settings.autoUpdate) app.downloads.updateOutdated(app.repo.libraryBooks())
    }
    val openReq by MainActivity.openPlayerRequest.collectAsState()
    val openUpd by MainActivity.openUpdatesRequest.collectAsState()
    val openCar by MainActivity.openCarRequest.collectAsState()
    LaunchedEffect(openCar) { if (openCar) { nav.navigate("car") { launchSingleTop = true }; MainActivity.openCarRequest.value = false } }
    LaunchedEffect(openUpd) { if (openUpd) { nav.navigate("updates") { launchSingleTop = true }; MainActivity.openUpdatesRequest.value = false } }
    LaunchedEffect(openPlayer, openReq) {
        if (openPlayer || openReq) app.player.now.value?.let { nav.navigate("reader/${it.book.key}/${it.n}") { launchSingleTop = true } }
        MainActivity.openPlayerRequest.value = false
    }

    // unseen updates badge
    val newCount = run {
        val seen = st.seenUpdates
        app.repo.libraryBooks().sumOf { b -> b.chapters.count { (it.date ?: "") > seen } }.takeIf { seen.isNotEmpty() } ?: 0
    }.also { chapters.size }

    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600
    val goTab = { t: String -> nav.navigate(t) { popUpTo("library") { saveState = true }; launchSingleTop = true; restoreState = true } }
    Scaffold(
        bottomBar = {
            if (topLevel) Column {
                MiniPlayer(nav)
                ModeStrip(st.settings)
                if (!wide) NavigationBar {
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
        // The bottom bar already covers the system navigation bar: mark that inset as consumed, otherwise every tab's own
        // Scaffold adds it again and its list is cut off above an empty strip.
        val bottom = if (topLevel) pad.calculateBottomPadding() else 0.dp
        Row(Modifier.padding(bottom = bottom).consumeWindowInsets(PaddingValues(bottom = bottom))) {
            if (wide && topLevel) androidx.compose.material3.NavigationRail(Modifier.padding(top = 24.dp)) {
                TABS.forEach { t ->
                    val sel = route == t.route
                    androidx.compose.material3.NavigationRailItem(sel, onClick = { if (!sel) goTab(t.route) },
                        icon = { Icon(if (sel) t.on else t.off, null) }, label = { Text(t.label, fontSize = 11.5.sp) })
                }
            }
            Box(Modifier.weight(1f)) { NavGraph(nav) }
        }
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
        composable("extensions") { io.github.notoboto.unison.ui.ExtensionsScreen(nav) }
        composable("source/{id}") { io.github.notoboto.unison.ui.SourceScreen(nav, it.arguments?.getString("id")!!) }
        composable("srcbook/{id}/{book}") { io.github.notoboto.unison.ui.PluginBookScreen(nav, it.arguments?.getString("id")!!, android.net.Uri.decode(it.arguments?.getString("book")!!)) }
        composable("srclogin/{id}") { io.github.notoboto.unison.ui.SourceLoginScreen(nav, it.arguments?.getString("id")!!) }
        composable("srcweb/{id}?url={url}") { io.github.notoboto.unison.ui.SourceBrowserScreen(nav, it.arguments?.getString("id")!!, it.arguments?.getString("url").orEmpty()) }
        composable("gsearch?q={q}") { io.github.notoboto.unison.ui.GlobalSearchScreen(nav, it.arguments?.getString("q").orEmpty()) }
        composable("more") { MoreScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("settings/{section}") { SettingsScreen(nav, it.arguments?.getString("section")) }
        composable("downloads") { DownloadsScreen(nav) }
        composable("about") { AboutScreen(nav) }
        composable("categories") { io.github.notoboto.unison.ui.CategoriesScreen(nav) }
        composable("characters/{key}") { io.github.notoboto.unison.ui.CharactersScreen(nav, it.arguments?.getString("key")!!) }
        composable("search/{key}") { io.github.notoboto.unison.ui.BookSearchScreen(nav, it.arguments?.getString("key")!!) }
        composable("highlights/{key}") { io.github.notoboto.unison.ui.HighlightsScreen(nav, it.arguments?.getString("key")!!) }
        composable("glossary/{key}") { io.github.notoboto.unison.ui.GlossaryScreen(nav, it.arguments?.getString("key")!!) }
        composable("profiles") { io.github.notoboto.unison.ui.ProfilesScreen(nav) }
        composable("car") { io.github.notoboto.unison.ui.CarScreen(nav) }
        composable("voices") { io.github.notoboto.unison.ui.VoicesScreen(nav) }
        composable("stats") { io.github.notoboto.unison.ui.StatsScreen(nav) }
        composable("storage") { io.github.notoboto.unison.ui.StorageScreen(nav) }
        composable("bookmarks/{key}") { io.github.notoboto.unison.ui.BookmarksScreen(nav, it.arguments?.getString("key")!!) }
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
    val st by app.store.state.collectAsState()      // a chapter that gets voiced brings the mini player back
    // only when there is something to play: not for a text chapter that is not voiced yet
    AnimatedVisibility(now?.let { st.downloaded[it.book.key]?.containsKey(it.n) == true || app.player.hasAudio(it.book, it.n) } == true) {
        val np = now ?: return@AnimatedVisibility
        Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            LinearProgressIndicator(progress = { if (np.duration > 0) (pos / np.duration).toFloat().coerceIn(0f, 1f) else 0f }, Modifier.fillMaxWidth().height(2.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            // tap = open the reader, long press = quick controls
            var menu by remember { mutableStateOf(false) }
            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
            Box {
            MiniMenu(menu, onDismiss = { menu = false })
            Row(Modifier.fillMaxWidth().combinedClickable(onClick = { nav.navigate("reader/${np.book.key}/${np.n}") },
                    onLongClick = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menu = true })
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
                if (!playing) IconButton(onClick = { app.player.close() }) {
                    Icon(Icons.Filled.Close, "Закрыть плеер", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            }
        }
    }
}

/** Long-press menu of the mini player. Seek items keep the menu open so they can be tapped repeatedly. */
@Composable
private fun MiniMenu(open: Boolean, onDismiss: () -> Unit) {
    val app = androidx.compose.ui.platform.LocalContext.current.app()
    val s = app.store.state.collectAsState().value.settings
    val sleep by app.player.sleep.collectAsState()
    val p = app.player
    androidx.compose.material3.DropdownMenu(open, onDismiss) {
        @Composable fun item(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, close: Boolean = true, enabled: Boolean = true, act: () -> Unit) =
            androidx.compose.material3.DropdownMenuItem(text = { Text(text) }, leadingIcon = { Icon(icon, null) }, enabled = enabled,
                onClick = { act(); if (close) onDismiss() })
        item("Назад ${s.skipBack} с", Icons.Filled.Replay, close = false) { p.skip(-s.skipBack) }
        item("Вперёд ${s.skipForward} с", Icons.Filled.FastForward, close = false) { p.skip(s.skipForward) }
        item("Предыдущая глава", Icons.Filled.SkipPrevious, enabled = p.neighbour(-1) != null) {
            val np = p.now.value ?: return@item; p.neighbour(-1)?.let { n -> p.open(app.repo.book(np.book.key) ?: np.book, n, autoplay = true) } }
        item("Следующая глава", Icons.Filled.SkipNext, enabled = p.neighbour(1) != null) {
            val np = p.now.value ?: return@item; p.neighbour(1)?.let { n -> p.open(app.repo.book(np.book.key) ?: np.book, n, autoplay = true) } }
        if (sleep != null) item("Выключить таймер сна", Icons.Filled.TimerOff) { p.cancelSleep() }
        else item("Таймер сна: 30 мин", Icons.Filled.Timer) { p.startSleep(30 * 60_000L) }
        androidx.compose.material3.HorizontalDivider()
        item("Убрать плеер", Icons.Filled.Close) { p.close() }
    }
}

/** Mihon-style strip for the global modes. */
@Composable
private fun ModeStrip(s: io.github.notoboto.unison.data.Settings) {
    val parts = listOfNotNull("Только скачанное".takeIf { s.downloadedOnly }, "Инкогнито".takeIf { s.incognito })
    if (parts.isEmpty()) return
    Text(parts.joinToString(" • "), Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary).padding(vertical = 3.dp),
        color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
}

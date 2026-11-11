package io.github.notoboto.unison

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.notoboto.unison.data.Repo
import io.github.notoboto.unison.data.Store
import io.github.notoboto.unison.download.Downloads
import io.github.notoboto.unison.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class UnisonApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var store: Store; private set
    lateinit var repo: Repo; private set
    lateinit var downloads: Downloads; private set
    lateinit var player: PlayerController; private set
    lateinit var updater: Updater; private set
    /** Book-source plugins (SOURCES_DESIGN.md §4–§8): repositories, host and live instances. */
    val sources by lazy { io.github.notoboto.unison.sources.Sources(this, repo.http) }

    override fun onCreate() {
        super.onCreate()
        store = Store(this, scope)
        repo = Repo(this, store)
        downloads = Downloads(this, this, scope)
        player = PlayerController(this, this, scope)
        updater = Updater(this)
        scheduleAutoBackup(this)
        Habits.schedule(this)
        player.watchHeadphones()
        // built-in plugins from the APK (offline), then a daily background update check (decision 16)
        scope.launch(Dispatchers.Default) {
            runCatching { sources.repos.ensureBuiltIns() }
            val s = store.state.value.settings
            if (s.pluginAutoUpdate && System.currentTimeMillis() - s.pluginsChecked > 24 * 3600_000L) {
                runCatching { sources.updateAll() }
                store.settings { it.copy(pluginsChecked = System.currentTimeMillis()) }
            }
        }
    }
}

fun Context.app() = applicationContext as UnisonApp

// ---------- backups ----------
fun backupFileName(): String = "unison_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) + ".json"

fun writeBackup(ctx: Context, uri: Uri) {
    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(backupJson(ctx).toByteArray()) }
}

/** App state plus the book-sources section (SOURCES_DESIGN.md §13). */
fun backupJson(ctx: Context): String {
    val app = ctx.app()
    val state = io.github.notoboto.unison.data.json.parseToJsonElement(app.store.exportJson()) as kotlinx.serialization.json.JsonObject
    val sources = runCatching { io.github.notoboto.unison.sources.SourcesBackup.export(ctx, app.sources) }.getOrNull() ?: return app.store.exportJson()
    return kotlinx.serialization.json.JsonObject(state + (io.github.notoboto.unison.sources.SourcesBackup.KEY to sources)).toString()
}

/** Restores a backup: the app state now, the book-sources section in the background. */
fun restoreBackup(ctx: Context, text: String, done: (problems: List<String>) -> Unit = {}) {
    val app = ctx.app()
    app.store.importJson(text)
    val section = runCatching {
        (io.github.notoboto.unison.data.json.parseToJsonElement(text) as kotlinx.serialization.json.JsonObject)[io.github.notoboto.unison.sources.SourcesBackup.KEY] as? kotlinx.serialization.json.JsonObject
    }.getOrNull()
    app.scope.launch {
        val problems = if (section == null) emptyList() else kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching { io.github.notoboto.unison.sources.SourcesBackup.restore(ctx, app.sources, section) }.getOrElse { listOf(it.message ?: it.toString()) }
        }
        app.repo.reloadLocal()
        done(problems)
    }
}

fun scheduleAutoBackup(ctx: Context) {
    val wm = WorkManager.getInstance(ctx)
    if (ctx.app().store.s.settings.autoBackupUri == null) { wm.cancelUniqueWork("autobackup"); return }
    wm.enqueueUniquePeriodicWork("autobackup", ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build())
}

class AutoBackupWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = runCatching {
        val app = applicationContext.app()
        val tree = DocumentFile.fromTreeUri(applicationContext, Uri.parse(app.store.s.settings.autoBackupUri ?: return Result.success()))!!
        val f = tree.createFile("application/json", backupFileName())!!
        writeBackup(applicationContext, f.uri)
        // keep the 7 newest automatic backups
        tree.listFiles().filter { it.name?.startsWith("unison_") == true }.sortedByDescending { it.name }.drop(7).forEach { it.delete() }
        Result.success()
    }.getOrElse { Result.retry() }
}

/**
 * Imports a progress file (sync v2, SOURCES_DESIGN.md §11). Plugin books of the file that are not on this phone are
 * added in the background through their plugin (when it is installed), then the file is merged again so their
 * progress lands on the right chapters. [added] is called with the number of books added that way.
 */
fun importSyncFile(ctx: Context, text: String, added: (Int) -> Unit = {}): io.github.notoboto.unison.data.Sync.Result {
    val app = ctx.app()
    val r = io.github.notoboto.unison.data.Sync.importV2(app.store, text, app.sources.books)
    if (r.missing.isNotEmpty()) app.scope.launch(Dispatchers.Default) {
        var n = 0
        for ((plugin, bookId) in r.missing) {
            if (plugin !in app.sources.repos.installed()) continue
            runCatching {
                val d = app.sources.details(plugin, bookId)
                val chs = app.sources.chapters(plugin, bookId)
                app.sources.books.add(plugin, d, chs)
            }.onSuccess { n++ }
        }
        if (n > 0) {
            app.repo.reloadLocal()
            io.github.notoboto.unison.data.Sync.importV2(app.store, text, app.sources.books)   // now they are here: merge their progress
        }
        kotlinx.coroutines.withContext(Dispatchers.Main) { added(n) }
    }
    return r.result
}

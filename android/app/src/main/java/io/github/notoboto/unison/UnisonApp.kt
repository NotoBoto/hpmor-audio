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

    override fun onCreate() {
        super.onCreate()
        store = Store(this, scope)
        repo = Repo(this, store)
        downloads = Downloads(this, this, scope)
        player = PlayerController(this, this, scope)
        updater = Updater(this)
        scheduleAutoBackup(this)
    }
}

fun Context.app() = applicationContext as UnisonApp

// ---------- backups ----------
fun backupFileName(): String = "unison_" + SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) + ".json"

fun writeBackup(ctx: Context, uri: Uri) {
    ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(ctx.app().store.exportJson().toByteArray()) }
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

package io.github.notoboto.unison

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Schedules (or cancels) the periodic library check according to the settings. */
fun scheduleLibraryCheck(ctx: Context) {
    val s = ctx.app().store.s.settings
    val wm = WorkManager.getInstance(ctx)
    if (s.autoCheckHours <= 0) { wm.cancelUniqueWork("libcheck"); return }
    val c = Constraints.Builder()
        .setRequiredNetworkType(if (s.autoCheckWifi) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(s.autoCheckCharging).build()
    wm.enqueueUniquePeriodicWork("libcheck", ExistingPeriodicWorkPolicy.UPDATE,
        PeriodicWorkRequestBuilder<LibraryCheckWorker>(s.autoCheckHours.toLong(), TimeUnit.HOURS).setConstraints(c).build())
}

/** Records how many chapters each library book has, so the next check can tell what is new. */
fun rememberChapterCounts(app: UnisonApp) {
    val counts = app.repo.libraryBooks().associate { it.key to (it.chapters.maxOfOrNull { c -> c.n } ?: 0) }
    app.store.update { it.copy(knownChapters = it.knownChapters + counts) }
}

class LibraryCheckWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        val app = applicationContext.app()
        val before = app.store.s.knownChapters
        app.repo.refreshAll()
        val news = app.repo.libraryBooks().mapNotNull { b ->
            val known = before[b.key] ?: return@mapNotNull null          // first time we see the book: no notification
            val fresh = b.chapters.filter { it.n > known }
            if (fresh.isEmpty()) null else b to fresh
        }
        rememberChapterCounts(app)
        if (app.store.s.settings.autoDownloadNew) news.forEach { (b, chs) -> app.downloads.enqueue(b, chs.map { it.n }) }
        if (app.store.s.settings.autoUpdate) app.downloads.updateOutdated(app.repo.libraryBooks())
        if (news.isNotEmpty()) notify(news.map { (b, chs) -> "${b.shortTitle}: ${chs.size} " + if (chs.size == 1) "новая глава" else "новых глав" })
        Result.success()
    }

    private fun notify(lines: List<String>) {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("new_chapters", "Новые главы", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(applicationContext, 2, Intent(applicationContext, MainActivity::class.java).putExtra("openUpdates", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val style = NotificationCompat.InboxStyle().also { st -> lines.forEach { st.addLine(it) } }
        runCatching {
            nm.notify(3998, NotificationCompat.Builder(applicationContext, "new_chapters").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Новые главы").setContentText(lines.joinToString("; ")).setStyle(style)
                .setContentIntent(open).setAutoCancel(true).build())
        }
    }
}

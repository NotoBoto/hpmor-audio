package io.github.notoboto.unison.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.notoboto.unison.R
import io.github.notoboto.unison.UnisonApp
import io.github.notoboto.unison.data.Book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.File

sealed interface DlState {
    data object None : DlState
    data object Queued : DlState
    data class Running(val p: Float) : DlState
    data object Done : DlState
    data object Outdated : DlState
}

class Downloads(private val ctx: Context, private val app: UnisonApp, scope: CoroutineScope) {
    private val wm = WorkManager.getInstance(ctx)

    /** "bookKey:n" -> running/queued work */
    private val jobs: StateFlow<Map<String, DlState>> = wm.getWorkInfosByTagFlow(TAG)
        .map { infos ->
            infos.filter { !it.state.isFinished }.associate { info ->
                val k = info.tags.first { it.startsWith("k:") }.removePrefix("k:")
                k to if (info.state == WorkInfo.State.RUNNING) DlState.Running(info.progress.getFloat("p", 0f)) else DlState.Queued
            }
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    val states: StateFlow<Map<String, DlState>> = jobs

    fun state(b: Book, n: Int, jobsMap: Map<String, DlState> = jobs.value): DlState {
        jobsMap["${b.key}:$n"]?.let { return it }
        val v = app.store.s.downloaded[b.key]?.get(n) ?: return DlState.None
        val cur = b.chapters.find { it.n == n }?.v
        return if (cur != null && v != cur && v != "1") DlState.Outdated else DlState.Done
    }

    fun enqueue(b: Book, ns: Collection<Int>, force: Boolean = false): Int {
        var added = 0
        val wifi = app.store.s.settings.wifiOnly
        for (n in ns) {
            val st = state(b, n)
            if (!force && (st == DlState.Done || st is DlState.Running || st == DlState.Queued)) continue
            val ch = b.chapters.find { it.n == n } ?: continue
            if (b.source.url == io.github.notoboto.unison.data.LOCAL_URL) {     // imported book: voice it on the phone
                val tr = OneTimeWorkRequestBuilder<TtsWorker>()
                    .setInputData(workDataOf("key" to b.key, "id" to b.meta.id, "n" to n, "title" to "${b.shortTitle} — ${ch.title}"))
                    .addTag(TAG).addTag("k:${b.key}:$n").build()
                wm.enqueueUniqueWork("tts:${b.key}", ExistingWorkPolicy.APPEND_OR_REPLACE, tr)   // one chapter at a time
                added++; continue
            }
            val req = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf("key" to b.key, "n" to n, "json" to b.chUrl(n, "json"), "ogg" to b.oggUrls(n).toTypedArray(),
                    "v" to (ch.v ?: "1"), "title" to "${b.shortTitle} — ${ch.title}"))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifi) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .addTag(TAG).addTag("k:${b.key}:$n")
                .build()
            wm.enqueueUniqueWork("dl:${b.key}:$n", if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
            added++
        }
        return added
    }

    fun cancel(b: Book, n: Int) { wm.cancelUniqueWork("dl:${b.key}:$n"); wm.cancelAllWorkByTag("k:${b.key}:$n") }
    fun cancelAll() = wm.cancelAllWorkByTag(TAG)

    fun delete(b: Book, ns: Collection<Int>) { ns.forEach { cancel(b, it); app.repo.deleteLocal(b.key, it) } }

    fun outdated(books: List<Book>): List<Pair<Book, Int>> =
        books.flatMap { b -> b.chapters.filter { state(b, it.n) == DlState.Outdated }.map { b to it.n } }

    fun updateOutdated(books: List<Book>): Int = outdated(books).groupBy({ it.first }, { it.second }).entries.sumOf { (b, ns) -> enqueue(b, ns, force = true) }

    /** Keep the next N chapters after [n] downloaded. */
    fun autoAhead(b: Book, n: Int) {
        val k = app.store.s.settings.autoDownloadNext
        if (k <= 0) return
        enqueue(b, b.chapters.filter { it.n > n }.take(k).map { it.n })
    }

    fun usedBytes(): Long = runCatching { app.repo.files.totalBytes() }.getOrDefault(0L)

    // ---- "downloads finished" notification ----
    private var okCount = 0; private var failCount = 0
    @Synchronized fun finished(ok: Boolean) { if (ok) okCount++ else failCount++; if (ok) enforceLimit() }

    /** Keep downloads under the limit: delete listened chapters first, oldest progress first. Never the one playing. */
    fun enforceLimit() {
        val gb = app.store.s.settings.storageLimitGb; if (gb <= 0) return
        val limit = gb * 1024L * 1024 * 1024
        var used = usedBytes(); if (used <= limit) return
        val playing = app.player.now.value
        val candidates = app.store.s.downloaded.flatMap { (k, m) -> m.keys.map { k to it } }
            .filter { (k, n) -> app.store.progress(k, n).done && !(playing?.book?.key == k && playing.n == n) }
            .sortedBy { (k, n) -> app.store.progress(k, n).ts }
        for ((k, n) in candidates) {
            if (used <= limit) break
            val b = app.repo.book(k) ?: continue
            val sz = b.chapters.find { it.n == n }?.bytes ?: 0
            app.repo.deleteLocal(k, n); used -= sz
        }
    }
    init {
        scope.launch {
            var wasBusy = false
            jobs.collect { m ->
                if (m.isNotEmpty()) wasBusy = true
                else if (wasBusy) { wasBusy = false; notifyDone() }
            }
        }
    }
    @Synchronized private fun notifyDone() {
        val ok = okCount; val fail = failCount; okCount = 0; failCount = 0
        if (!app.store.s.settings.notifyDownloads || ok + fail == 0) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("dl_done", "Загрузки завершены", NotificationManager.IMPORTANCE_DEFAULT))
        val open = android.app.PendingIntent.getActivity(ctx, 1, android.content.Intent(ctx, io.github.notoboto.unison.MainActivity::class.java),
            android.app.PendingIntent.FLAG_IMMUTABLE)
        val text = "Скачано глав: $ok" + if (fail > 0) " • с ошибкой: $fail" else ""
        runCatching {
            nm.notify(3999, NotificationCompat.Builder(ctx, "dl_done").setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Загрузки завершены").setContentText(text).setContentIntent(open).setAutoCancel(true).build())
        }
    }

    companion object { const val TAG = "dl" }
}

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val app = ctx.applicationContext as UnisonApp

    override suspend fun getForegroundInfo(): ForegroundInfo = foreground(0f)

    private fun foreground(p: Float): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CH, "Загрузки", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, CH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Скачивание")
            .setContentText(inputData.getString("title"))
            .setProgress(100, (p * 100).toInt(), p == 0f)
            .setOngoing(true).setSilent(true).build()
        val id = 4000 + (inputData.getString("key").hashCode() + inputData.getInt("n", 0)).mod(1000)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id, n)
    }

    override suspend fun doWork(): Result {
        val key = inputData.getString("key")!!; val n = inputData.getInt("n", 0)
        runCatching { setForeground(foreground(0f)) }
        return try {
            val files = app.repo.files
            val tmp = File(applicationContext.cacheDir, "dl_${key}_$n").apply { mkdirs() }
            val js = File(tmp, "json"); val og = File(tmp, "ogg")
            fetch(inputData.getString("json")!!, js, false) { }
            var last = 0L
            // one URL, or the byte pieces of a split chapter: appended in order they give back the original file
            val urls = inputData.getStringArray("ogg") ?: arrayOf(inputData.getString("ogg")!!)
            og.delete()
            urls.forEachIndexed { i, url ->
                fetch(url, og, true) { p0 ->
                    val p = (i + p0) / urls.size
                    if (System.currentTimeMillis() - last > 400) {
                        last = System.currentTimeMillis()
                        setProgress(Data.Builder().putFloat("p", p).build())
                        runCatching { setForeground(foreground(p)) }
                    }
                }
            }
            files.write(key, n, "json") { out -> js.inputStream().use { it.copyTo(out) } }
            files.write(key, n, "ogg") { out -> og.inputStream().use { it.copyTo(out) } }
            tmp.deleteRecursively()
            app.store.markDownloaded(key, n, inputData.getString("v"))
            app.downloads.finished(ok = true)
            Result.success()
        } catch (e: Exception) {
            if (isStopped) Result.failure() else if (runAttemptCount < 3) Result.retry() else { app.downloads.finished(ok = false); Result.failure() }
        }
    }

    private suspend fun fetch(url: String, to: File, append: Boolean, onProgress: suspend (Float) -> Unit) {
        app.repo.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val body = r.body!!; val total = body.contentLength().coerceAtLeast(1)
            var got = 0L
            java.io.FileOutputStream(to, append).use { out ->
                body.byteStream().use { inp ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (isStopped) error("stopped")
                        val k = inp.read(buf); if (k < 0) break
                        out.write(buf, 0, k); got += k
                        onProgress((got.toFloat() / total).coerceAtMost(1f))
                    }
                }
            }
        }
    }

    companion object { const val CH = "downloads" }
}

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
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

    // WorkManager starts every queued chapter at once (dozens). Over one HTTP/2 connection each stream then
    // buffers megabytes in the heap faster than it is written to disk -> OutOfMemoryError. So: at most
    // MAX_PARALLEL network downloads at a time, over HTTP/1.1 (backpressure stays in the OS socket buffer).
    val gate = kotlinx.coroutines.sync.Semaphore(MAX_PARALLEL)
    val http: okhttp3.OkHttpClient by lazy { app.repo.http.newBuilder().protocols(listOf(okhttp3.Protocol.HTTP_1_1)).build() }

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

    /** Chapters queued for voicing by «Озвучивать наперёд» ("bookKey:n", tag "bg"): a chapter the user asks for goes first. */
    private val bgVoice: StateFlow<Set<String>> = wm.getWorkInfosByTagFlow(TAG)
        .map { infos -> infos.filter { !it.state.isFinished && "bg" in it.tags }.mapNotNull { i -> i.tags.firstOrNull { it.startsWith("k:") }?.removePrefix("k:") }.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    private fun enqueueTts(b: Book, n: Int, title: String, background: Boolean) {
        val s = app.store.s.settings
        val plugin = io.github.notoboto.unison.data.isPluginSource(b.source.url)
        val tr = OneTimeWorkRequestBuilder<TtsWorker>()
            .setInputData(workDataOf("key" to b.key, "id" to b.meta.id, "src" to b.source.url, "n" to n, "title" to title))
            .addTag(TAG).addTag("k:${b.key}:$n").apply { if (background) addTag("bg") }
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
            .apply {
                if (background || plugin) setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(when { !plugin -> NetworkType.NOT_REQUIRED; background && s.voiceAheadWifi -> NetworkType.UNMETERED; else -> NetworkType.CONNECTED })
                    .setRequiresCharging(background && s.voiceAheadCharging).build())
            }.build()
        wm.enqueueUniqueWork("tts:${b.key}", ExistingWorkPolicy.APPEND_OR_REPLACE, tr)   // one chapter at a time
    }

    /**
     * Audio of chapters: downloads it (web books) or voices the text on the phone (imported and plugin books).
     * [background]: automatic voicing ahead — waits for the «charging» / «Wi‑Fi» conditions of the settings.
     * Chapters the user asks to voice go before the ones waiting in the queue.
     */
    fun enqueue(b: Book, ns: Collection<Int>, force: Boolean = false, background: Boolean = false): Int {
        var added = 0
        val wifi = app.store.s.settings.wifiOnly
        val textBook = io.github.notoboto.unison.data.isTextSource(b.source.url)
        // a manual request: take the waiting chapters of this book off the queue and put them back after it
        val requeue = if (textBook && !background) jobs.value.filter { (k, v) -> k.startsWith("${b.key}:") && v == DlState.Queued }.keys
            .mapNotNull { it.substringAfterLast(':').toIntOrNull() } else emptyList()
        requeue.forEach { wm.cancelAllWorkByTag("k:${b.key}:$it") }
        for (n in ns) {
            val st = state(b, n)
            if (!force && n !in requeue && (st == DlState.Done || st is DlState.Running || st == DlState.Queued)) continue
            val ch = b.chapters.find { it.n == n } ?: continue
            if (textBook) {     // imported or plugin book: voice it on the phone
                enqueueTts(b, n, "${b.shortTitle} — ${ch.title}", background)
                added++; continue
            }
            val req = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(workDataOf("key" to b.key, "n" to n, "json" to b.chUrl(n, "json"), "ogg" to b.oggUrls(n).toTypedArray(),
                    "img" to ch.images.map { b.imgUrl(it) }.toTypedArray(), "imgName" to ch.images.map { it.localName }.toTypedArray(),
                    "v" to (ch.v ?: "1"), "title" to "${b.shortTitle} — ${ch.title}"))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifi) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
                .addTag(TAG).addTag("k:${b.key}:$n")
                .build()
            wm.enqueueUniqueWork("dl:${b.key}:$n", if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
            added++
        }
        for (n in requeue.filter { it !in ns }) {
            val ch = b.chapters.find { it.n == n } ?: continue
            enqueueTts(b, n, "${b.shortTitle} — ${ch.title}", background = "${b.key}:$n" in bgVoice.value)
        }
        return added
    }

    // ---- text of chapters, separately from the audio ----
    // Plugin books: the chapter text through the plugin (tNNN.json). Web books: the chapter's json (text with timings)
    // without the audio, for reading offline. Imported books always have their text. Small and quick: an in-app queue.

    private val textJobs = kotlinx.coroutines.flow.MutableStateFlow<Map<String, DlState>>(emptyMap())
    val textStates: StateFlow<Map<String, DlState>> = textJobs
    /** Bumped when texts are deleted (the queue does not change then). */
    val textRev = kotlinx.coroutines.flow.MutableStateFlow(0)
    private val textQueue = kotlinx.coroutines.channels.Channel<Pair<Book, Int>>(kotlinx.coroutines.channels.Channel.UNLIMITED)
    private val textKnown = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val textMissing = java.util.concurrent.ConcurrentHashMap<String, Long>()   // book rows redraw often: no file check each time

    /** Whether the chapter's text is on the phone. Only «yes» is cached: the reader and voicing also fetch texts. */
    fun hasText(b: Book, n: Int): Boolean = textKnown["${b.key}:$n"] ?: textMissing["${b.key}:$n"]?.takeIf { System.currentTimeMillis() - it < 5_000 }?.let { false } ?: run {
        when {
            b.source.url == io.github.notoboto.unison.data.LOCAL_URL -> true
            io.github.notoboto.unison.data.isPluginSource(b.source.url) -> io.github.notoboto.unison.data.TextBooks.hasText(ctx, b.source.url, b.meta.id, n)
            else -> runCatching { app.repo.files.exists(b.key, n, "json") }.getOrDefault(false)
        }.also { if (it) textKnown["${b.key}:$n"] = true else textMissing["${b.key}:$n"] = System.currentTimeMillis() }
    }

    fun textState(b: Book, n: Int, jobsMap: Map<String, DlState> = textJobs.value): DlState =
        jobsMap["${b.key}:$n"] ?: if (hasText(b, n)) DlState.Done else DlState.None

    /** Queues the text of chapters; returns how many were added. */
    fun enqueueText(b: Book, ns: Collection<Int>): Int {
        var added = 0
        for (n in ns) {
            val k = "${b.key}:$n"
            if (textJobs.value.containsKey(k) || hasText(b, n)) continue
            textJobs.update { it + (k to DlState.Queued) }
            textQueue.trySend(b to n); added++
        }
        return added
    }

    fun cancelText(b: Book, n: Int) { textJobs.update { it - "${b.key}:$n" } }

    /** Deletes the text of chapters (not their audio: a voiced or downloaded chapter keeps its own copy). */
    fun deleteText(b: Book, ns: Collection<Int>) {
        for (n in ns) {
            cancelText(b, n)
            when {
                b.source.url == io.github.notoboto.unison.data.LOCAL_URL -> {}
                io.github.notoboto.unison.data.isPluginSource(b.source.url) ->
                    io.github.notoboto.unison.data.TextBooks.textFile(ctx, b.source.url, b.meta.id, n).delete()
                !app.store.isDownloaded(b.key, n) -> runCatching { app.repo.files.delete(b.key, n) }
            }
            textKnown.remove("${b.key}:$n")
        }
        textRev.value++     // screens redraw
    }

    private suspend fun fetchText(b: Book, n: Int) {
        if (io.github.notoboto.unison.data.isPluginSource(b.source.url)) {
            app.sources.books.ensureText(io.github.notoboto.unison.data.pluginIdOf(b.source.url), b.meta.id, n)
            app.repo.reloadLocal()
        } else if (b.source.url != io.github.notoboto.unison.data.LOCAL_URL) {
            val text = app.repo.get(b.chUrl(n, "json"))
            app.repo.files.write(b.key, n, "json") { it.write(text.toByteArray()) }
        }
    }

    fun cancel(b: Book, n: Int) { wm.cancelUniqueWork("dl:${b.key}:$n"); wm.cancelAllWorkByTag("k:${b.key}:$n") }
    fun cancelAll() = wm.cancelAllWorkByTag(TAG)

    /** Deletes the audio of chapters (for web books that is the chapter's json with it). */
    fun delete(b: Book, ns: Collection<Int>) {
        ns.forEach { cancel(b, it); app.repo.deleteLocal(b.key, it); textKnown.remove("${b.key}:$it") }
        textRev.value++
    }

    fun outdated(books: List<Book>): List<Pair<Book, Int>> =
        books.flatMap { b -> b.chapters.filter { state(b, it.n) == DlState.Outdated }.map { b to it.n } }

    fun updateOutdated(books: List<Book>): Int = outdated(books).groupBy({ it.first }, { it.second }).entries.sumOf { (b, ns) -> enqueue(b, ns, force = true) }

    /**
     * After chapter [n] is opened: «Скачивать вперёд» keeps the next chapters on the phone (the audio of web books, the
     * text of plugin books), «Озвучивать вперёд» voices the next chapters of text books.
     */
    fun autoAhead(b: Book, n: Int) {
        val s = app.store.s.settings
        val after = b.chaptersAfter(n).map { it.n }
        if (io.github.notoboto.unison.data.isTextSource(b.source.url)) {
            if (s.autoDownloadNext > 0 && io.github.notoboto.unison.data.isPluginSource(b.source.url)) enqueueText(b, after.take(s.autoDownloadNext))
            if (s.voiceAhead > 0) enqueue(b, after.take(s.voiceAhead), background = true)
        } else if (s.autoDownloadNext > 0) enqueue(b, after.take(s.autoDownloadNext))
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
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            for ((b, n) in textQueue) {
                val k = "${b.key}:$n"
                if (!textJobs.value.containsKey(k)) continue          // cancelled while waiting
                textJobs.update { it + (k to DlState.Running(0f)) }
                val ok = runCatching { fetchText(b, n) }.onFailure { android.util.Log.w("Downloads", "text $k failed", it) }.isSuccess
                textKnown.remove(k); textMissing.remove(k)
                textJobs.update { it - k }
                if (!ok) synchronized(this@Downloads) { failCount++ }
            }
        }
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

    companion object { const val TAG = "dl"; const val MAX_PARALLEL = 3 }
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
        // wait here (shown as queued at 0%) until one of the few download slots is free
        return try { app.downloads.gate.withPermit { download(key, n) } } catch (e: Exception) {
            if (isStopped) Result.failure() else if (runAttemptCount < 3) Result.retry() else { app.downloads.finished(ok = false); Result.failure() }
        }
    }

    private suspend fun download(key: String, n: Int): Result {
        return run {
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
            // illustrations: small next to the audio; one that fails is fetched later by the reader (Repo.fillPictures)
            val imgs = inputData.getStringArray("img").orEmpty(); val names = inputData.getStringArray("imgName").orEmpty()
            imgs.forEachIndexed { i, url ->
                val name = names.getOrNull(i) ?: return@forEachIndexed
                runCatching {
                    val f = File(tmp, "img$i"); fetch(url, f, false) { }
                    files.writeFile(key, name) { out -> f.inputStream().use { it.copyTo(out) } }
                }
            }
            files.write(key, n, "json") { out -> js.inputStream().use { it.copyTo(out) } }
            files.write(key, n, "ogg") { out -> og.inputStream().use { it.copyTo(out) } }
            tmp.deleteRecursively()
            app.store.markDownloaded(key, n, inputData.getString("v"))
            app.downloads.finished(ok = true)
            Result.success()
        }
    }

    private suspend fun fetch(url: String, to: File, append: Boolean, onProgress: suspend (Float) -> Unit) {
        app.downloads.http.newCall(Request.Builder().url(url).build()).execute().use { r ->
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

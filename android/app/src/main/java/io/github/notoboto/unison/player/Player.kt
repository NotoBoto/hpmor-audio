package io.github.notoboto.unison.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.SessionToken
import io.github.notoboto.unison.MainActivity
import io.github.notoboto.unison.UnisonApp
import io.github.notoboto.unison.data.Book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** Background playback: MediaSession + notification + lock screen + headset buttons. */
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as UnisonApp
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("openPlayer", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaLibrarySession.Builder(this, app.player.exo, AutoLibrary(app)).setSessionActivity(open).build()
        app.player.session = session
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }
    override fun onDestroy() {
        (application as UnisonApp).player.saveNow()
        session?.release(); session = null
        super.onDestroy()
    }
}

data class NowPlaying(val book: Book, val n: Int, val title: String, val duration: Double)

/** App-wide player state and commands. Owns the ExoPlayer used by [PlaybackService]. */
class PlayerController(private val ctx: Context, private val app: UnisonApp, private val scope: CoroutineScope) {
    val exo: ExoPlayer = ExoPlayer.Builder(ctx)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(15_000).setSeekForwardIncrementMs(15_000)
        .build()
    private var controller: MediaController? = null
    var session: MediaSession? = null

    // ---- Google Cast: the session switches between the phone player and the Cast device ----
    private var cast: androidx.media3.cast.CastPlayer? = null
    val casting = MutableStateFlow(false)
    val active: Player get() = if (casting.value) cast ?: exo else exo
    fun initCast() {
        if (cast != null) return
        runCatching {
            val c = androidx.media3.cast.CastPlayer(com.google.android.gms.cast.framework.CastContext.getSharedInstance(ctx))
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) { if (casting.value) { isPlaying.value = playing; if (playing) startTicker() } }
                override fun onPlaybackStateChanged(state: Int) { if (casting.value && state == Player.STATE_ENDED) onEnded() }
            })
            c.setSessionAvailabilityListener(object : androidx.media3.cast.SessionAvailabilityListener {
                override fun onCastSessionAvailable() { toCast(c) }
                override fun onCastSessionUnavailable() { fromCast(c) }
            })
            cast = c
        }
    }
    private fun castItem(): MediaItem? {
        val np = now.value ?: return null
        val ch = np.book.chapters.find { it.n == np.n }
        return MediaItem.Builder().setMediaId("${np.book.key}:${np.n}").setUri(np.book.chUrl(np.n, "ogg")).setMimeType("audio/ogg")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(ch?.title ?: np.title).setArtist(np.book.meta.author).setAlbumTitle(np.book.meta.title)
                .setArtworkUri(np.book.coverUrl?.let(Uri::parse)).build()).build()
    }
    private fun toCast(c: androidx.media3.cast.CastPlayer) {
        val item = castItem() ?: run { casting.value = true; session?.player = c; return }
        val pos = exo.currentPosition; val wasPlaying = exo.isPlaying
        exo.pause()
        casting.value = true
        session?.player = c
        c.setMediaItem(item, pos); c.prepare(); c.playWhenReady = wasPlaying
    }
    private fun fromCast(c: androidx.media3.cast.CastPlayer) {
        val pos = c.currentPosition
        casting.value = false
        session?.player = exo
        if (pos > 0) exo.seekTo(pos)
        isPlaying.value = exo.isPlaying
    }

    val now = MutableStateFlow<NowPlaying?>(null)
    /** text of the current chapter (for live subtitles) and the sentence being spoken */
    private var curText: io.github.notoboto.unison.data.ChapterText? = null
    val sentence = MutableStateFlow("")
    private var lastSubtitleAt = 0L
    val isPlaying = MutableStateFlow(false)
    val position = MutableStateFlow(0.0)      // seconds, updated ~5x/s while playing
    val speed = MutableStateFlow(app.store.s.settings.speed)
    /** sleep timer: remaining ms, -1 = until end of chapter, null = off */
    val sleep = MutableStateFlow<Long?>(null)
    private var sleepJob: Job? = null
    private var tickJob: Job? = null

    private var enhancer: android.media.audiofx.LoudnessEnhancer? = null
    /** skip long pauses + make quiet voices louder (settings) */
    fun applySound() {
        val s = app.store.s.settings
        exo.skipSilenceEnabled = s.skipSilence
        runCatching {
            if (s.voiceBoost > 0) {
                if (enhancer == null) enhancer = android.media.audiofx.LoudnessEnhancer(exo.audioSessionId)
                enhancer!!.setTargetGain(s.voiceBoost * 100); enhancer!!.enabled = true
            } else enhancer?.enabled = false
        }
    }

    /** start / continue when headphones or a Bluetooth audio device connects (setting) */
    fun watchHeadphones() {
        val am = ctx.getSystemService(android.media.AudioManager::class.java) ?: return
        val startedAt = System.currentTimeMillis()
        val kinds = setOf(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_USB_HEADSET) +
            (if (android.os.Build.VERSION.SDK_INT >= 31) setOf(android.media.AudioDeviceInfo.TYPE_BLE_HEADSET) else emptySet())
        am.registerAudioDeviceCallback(object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(added: Array<out android.media.AudioDeviceInfo>) {
                if (System.currentTimeMillis() - startedAt < 3000) return          // already connected at app start
                if (!app.store.s.settings.autoStartHeadphones || isPlaying.value) return
                if (added.any { it.isSink && it.type in kinds }) scope.launch { delay(1200); toggleOrResume() }
            }
        }, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    init {
        exo.playbackParameters = exo.playbackParameters.withSpeed(speed.value)
        scope.launch { app.store.state.collect { applySound() } }
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
                Widgets.update(ctx)
                if (casting.value) return
                if (playing) startTicker() else { saveNow(); position.value = exo.currentPosition / 1000.0; pausedAt = System.currentTimeMillis() }
            }
            override fun onPlaybackStateChanged(state: Int) { if (!casting.value && state == Player.STATE_ENDED) onEnded() }
        })
        scope.launch {
            runCatching {
                controller = MediaController.Builder(ctx, SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))).buildAsync().await()
            }
        }
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            var k = 0
            var lastStat = System.currentTimeMillis()
            while (isActive) {
                position.value = active.currentPosition / 1000.0
                updateSubtitle()
                if (++k % 25 == 0) {
                    saveNow()
                    val t = System.currentTimeMillis()
                    now.value?.let { app.store.addListened(it.book.key, t - lastStat) }
                    lastStat = t
                }
                delay(200)
            }
        }
    }

    /** Live subtitles: the sentence being spoken goes into the notification ("artist" line) and the big widget. */
    private fun updateSubtitle() {
        val t = curText ?: return
        val s = io.github.notoboto.unison.ui.sentenceAt(t, position.value)
        if (s == sentence.value) return
        sentence.value = s
        val now0 = System.currentTimeMillis()
        if (now0 - lastSubtitleAt < 1200) return
        lastSubtitleAt = now0
        if (app.store.s.settings.liveSubtitles && !casting.value && exo.mediaItemCount > 0) {
            val cur = exo.currentMediaItem ?: return
            val meta = cur.mediaMetadata.buildUpon().setArtist(s.take(160)).build()
            runCatching { exo.replaceMediaItem(0, cur.buildUpon().setMediaMetadata(meta).build()) }
        }
        Widgets.update(ctx)
    }

    fun saveNow() {
        val np = now.value ?: return
        val p = active.currentPosition / 1000.0
        if (active.mediaItemCount > 0) app.store.setPos(np.book.key, np.n, p, np.duration)
    }

    /** Build the media item for a chapter and make it "current" (start position in seconds). Shared with Android Auto. */
    fun resolve(b: Book, n: Int): Pair<MediaItem, Double>? {
        val ch = b.chapters.find { it.n == n } ?: return null
        saveNow()
        val pr = app.store.progress(b.key, n)
        val start = if (pr.done || pr.pos > ch.duration - 3) 0.0 else pr.pos
        val item = MediaItem.Builder().setMediaId("${b.key}:$n").setUri(app.repo.audioUri(b, n))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.title).setArtist(b.meta.author).setAlbumTitle(b.meta.title)
                .setArtworkUri(b.coverUrl?.let(Uri::parse)).setIsPlayable(true).setIsBrowsable(false).build()).build()
        now.value = NowPlaying(b, n, ch.title, ch.duration)
        applyBookSpeed(b.key)
        curText = null; sentence.value = ""
        scope.launch { curText = runCatching { app.repo.chapterText(b, n) }.getOrNull() }
        position.value = start
        app.store.opened(b.key, n)
        app.downloads.autoAhead(b, n)
        Widgets.update(ctx)
        return item to start
    }

    /** Load a chapter; continue from saved position unless it was finished. */
    fun open(b: Book, n: Int, autoplay: Boolean) {
        if (now.value?.book?.key == b.key && now.value?.n == n && exo.mediaItemCount > 0) { if (autoplay) play(); return }
        if (b.source.url == io.github.notoboto.unison.data.LOCAL_URL && !app.store.isDownloaded(b.key, n) && !io.github.notoboto.unison.data.Local.isAudio(ctx, b.meta.id)) {
            resolve(b, n); exo.clearMediaItems(); return       // text only until the chapter is voiced
        }
        val (item, start) = resolve(b, n) ?: return
        exo.setMediaItem(item, (start * 1000).toLong())
        exo.prepare()
        if (casting.value) cast?.let { c -> castItem()?.let { c.setMediaItem(it, (start * 1000).toLong()); c.prepare() } }
        if (autoplay) play() else active.playWhenReady = false
    }

    /** Play/pause; if nothing is loaded (e.g. after a restart), continue the last chapter from history. */
    fun toggleOrResume() {
        if (now.value != null && exo.mediaItemCount > 0) { toggle(); return }
        val h = app.store.s.history.firstOrNull() ?: return
        val b = app.repo.book(h.book) ?: return
        open(b, h.n, autoplay = true)
    }

    private var pausedAt = 0L
    fun play() {
        val p = active
        if (p.mediaItemCount == 0) {
            now.value?.let { np -> if (np.book.source.url == io.github.notoboto.unison.data.LOCAL_URL) {
                android.widget.Toast.makeText(ctx, "Глава ещё не озвучена: нажмите ⤓ у главы", android.widget.Toast.LENGTH_LONG).show()
                if (!app.store.isDownloaded(np.book.key, np.n)) return
                open(np.book, np.n, autoplay = true); return
            } }
            return
        }
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        val s = app.store.s.settings
        if (s.autoRewindAfterMin > 0 && pausedAt > 0 && System.currentTimeMillis() - pausedAt > s.autoRewindAfterMin * 60_000L)
            p.seekTo((p.currentPosition - s.autoRewindSec * 1000L).coerceAtLeast(0))
        pausedAt = 0
        if (s.sleepScheduleOn && sleep.value == null && inWindow(s.sleepFrom, s.sleepTo)) startSleep(s.sleepScheduleMin * 60_000L)
        p.play()
    }
    private fun inWindow(from: Int, to: Int): Boolean {
        val c = java.util.Calendar.getInstance(); val m = c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
        return if (from <= to) m in from until to else m >= from || m < to
    }
    fun pause() { active.pause(); if (casting.value) pausedAt = System.currentTimeMillis() }
    fun toggle() = if (active.isPlaying) pause() else play()
    fun seekTo(sec: Double) { active.seekTo((sec * 1000).toLong()); position.value = sec }
    fun skip(sec: Int) = seekTo((active.currentPosition / 1000.0 + sec).coerceIn(0.0, now.value?.duration ?: 0.0))
    fun setSpeed(v: Float) {
        speed.value = v; exo.playbackParameters = exo.playbackParameters.withSpeed(v)
        val bk = now.value?.book?.key
        if (app.store.s.settings.perBookSpeed && bk != null) app.store.settings { it.copy(bookSpeed = it.bookSpeed + (bk to v)) }
        else app.store.settings { it.copy(speed = v) }
    }
    private fun applyBookSpeed(key: String) {
        val s = app.store.s.settings
        val v = if (s.perBookSpeed) s.bookSpeed[key] ?: s.speed else s.speed
        speed.value = v; exo.playbackParameters = exo.playbackParameters.withSpeed(v)
    }
    fun neighbour(d: Int): Int? { val np = now.value ?: return null; val l = np.book.chapters; val i = l.indexOfFirst { it.n == np.n }; return l.getOrNull(i + d)?.n }

    private fun onEnded() {
        val np = now.value ?: return
        app.store.setDone(np.book.key, listOf(np.n), true)
        app.store.setPos(np.book.key, np.n, 0.0, np.duration)
        if (app.store.s.settings.autoDelete && app.store.isDownloaded(np.book.key, np.n)) app.downloads.delete(np.book, listOf(np.n))
        if (sleep.value == -1L) { cancelSleep(); return }          // "until end of chapter"
        val next = neighbour(1) ?: return
        val book = app.repo.book(np.book.key) ?: np.book
        open(book, next, autoplay = true)
    }

    // ---------- sleep timer ----------
    private var shake: SensorEventListener? = null
    fun startSleep(ms: Long) {
        sleepJob?.cancel(); sleep.value = ms; listenShake(ms)
        if (ms < 0) return
        sleepJob = scope.launch {
            while (isActive && (sleep.value ?: 0) > 0) {
                delay(1000); if (exo.isPlaying) sleep.value = (sleep.value ?: 0) - 1000
                val left = sleep.value ?: 0
                if (left in 1..10_000) exo.volume = left / 10_000f       // fade out over the last 10 s
            }
            if (isActive) { exo.pause(); exo.volume = 1f; cancelSleep() }
        }
    }
    fun cancelSleep() { sleepJob?.cancel(); sleep.value = null; exo.volume = 1f; stopShake() }

    private fun listenShake(ms: Long) {
        stopShake()
        val sm = ctx.getSystemService(SensorManager::class.java) ?: return
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        var lastShake = 0L
        shake = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val g = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]) / SensorManager.GRAVITY_EARTH
                if (g > 2.3f && System.currentTimeMillis() - lastShake > 1500 && (sleep.value ?: 0) > 0) {
                    lastShake = System.currentTimeMillis()
                    sleep.value = ms; exo.volume = 1f          // shake = restart the timer
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(shake, acc, SensorManager.SENSOR_DELAY_UI)
    }
    private fun stopShake() { shake?.let { ctx.getSystemService(SensorManager::class.java)?.unregisterListener(it) }; shake = null }

    val connected: StateFlow<Boolean> get() = MutableStateFlow(controller != null)
}

/** Android Auto / media browsers: library books -> chapters. */
class AutoLibrary(private val app: UnisonApp) : MediaLibrarySession.Callback {
    private fun browsable(id: String, title: String, sub: String?, art: String?) = MediaItem.Builder().setMediaId(id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(sub).setArtworkUri(art?.let(Uri::parse))
            .setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_AUDIO_BOOK).build()).build()

    override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: MediaLibraryService.LibraryParams?) =
        com.google.common.util.concurrent.Futures.immediateFuture(androidx.media3.session.LibraryResult.ofItem(browsable("root", "Unison", null, null), params))

    override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int, pageSize: Int,
                               params: MediaLibraryService.LibraryParams?): com.google.common.util.concurrent.ListenableFuture<androidx.media3.session.LibraryResult<com.google.common.collect.ImmutableList<MediaItem>>> {
        val items: List<MediaItem> = if (parentId == "root") {
            val cont = app.store.s.history.firstOrNull()?.let { h -> app.repo.book(h.book)?.let { b ->
                val ch = b.chapters.find { it.n == h.n }
                MediaItem.Builder().setMediaId("${b.key}:${h.n}").setMediaMetadata(MediaMetadata.Builder().setTitle("Продолжить: ${ch?.title ?: "глава " + h.n}")
                    .setSubtitle(b.shortTitle).setArtworkUri(b.coverSmUrl?.let(Uri::parse)).setIsPlayable(true).setIsBrowsable(false).build()).build()
            } }
            listOfNotNull(cont) + app.repo.libraryBooks().map { b -> browsable("book:${b.key}", b.meta.title, b.meta.author, b.coverSmUrl) }
        } else if (parentId.startsWith("book:")) {
            val b = app.repo.book(parentId.removePrefix("book:"))
            b?.chapters?.map { ch ->
                val done = app.store.progress(b.key, ch.n).done
                MediaItem.Builder().setMediaId("${b.key}:${ch.n}").setMediaMetadata(MediaMetadata.Builder().setTitle(ch.title)
                    .setSubtitle(if (done) "прослушано" else io.github.notoboto.unison.ui.fmt(ch.duration)).setArtworkUri(b.coverSmUrl?.let(Uri::parse))
                    .setIsPlayable(true).setIsBrowsable(false).build()).build()
            }.orEmpty()
        } else emptyList()
        return com.google.common.util.concurrent.Futures.immediateFuture(androidx.media3.session.LibraryResult.ofItemList(items, params))
    }

    override fun onSetMediaItems(session: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>, startIndex: Int,
                                 startPositionMs: Long): com.google.common.util.concurrent.ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val id = mediaItems.firstOrNull()?.mediaId.orEmpty()
        val key = id.substringBeforeLast(':'); val n = id.substringAfterLast(':').toIntOrNull()
        val b = app.repo.book(key)
        val r = if (b != null && n != null) app.player.resolve(b, n) else null
        return com.google.common.util.concurrent.Futures.immediateFuture(
            if (r != null) MediaSession.MediaItemsWithStartPosition(listOf(r.first), 0, (r.second * 1000).toLong())
            else MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
    }
}

private fun PlayerController.noop() = Unit

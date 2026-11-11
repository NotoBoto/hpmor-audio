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
        session = MediaLibrarySession.Builder(this, app.player.sessionPlayer, AutoLibrary(app)).setSessionActivity(open).build()
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
    private val audioAttrs = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build()
    private val streams = StreamStore(java.io.File(ctx.cacheDir, "stream"))
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    val exo: ExoPlayer = ExoPlayer.Builder(ctx)
        // split chapters (#parts=...) are stitched from their pieces; network files are fetched whole once
        // (the audio host ignores Range requests); downloaded chapters play from disk as before
        .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            PartsDataSource.Factory(WholeFileDataSource.Factory(androidx.media3.datasource.DefaultDataSource.Factory(ctx), streams))))
        .setAudioAttributes(audioAttrs, app.store.s.settings.otherAudio == "pause")
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(15_000).setSeekForwardIncrementMs(15_000)
        .build()
    private var controller: MediaController? = null
    var session: MediaSession? = null
    /** What the notification / lock screen / Android Auto see: the phone player with live subtitles mixed into its metadata. */
    val sessionPlayer = SubtitlePlayer(exo)

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
        session?.player = sessionPlayer
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
            val gain = s.voiceBoost * 100 + balanceGainMb          // millibels
            if (gain > 0) {
                if (enhancer == null) enhancer = android.media.audiofx.LoudnessEnhancer(exo.audioSessionId)
                enhancer!!.setTargetGain(gain); enhancer!!.enabled = true
            } else enhancer?.enabled = false
        }
    }

    // ---------- other apps' music (settings: otherAudio, mixBalance) ----------
    // pause: ExoPlayer takes full audio focus, music apps pause (the Android default for audiobooks)
    // duck:  we hold "transient, may duck" focus while playing, so the system / the music app turns music down, and back up on pause
    // mix:   no focus at all, both play; optionally the media volume goes down N steps and the book is boosted back by the same dB,
    //        so only the music gets quieter. Calls are caught via the audio mode (Android 12+), since without focus nothing tells us.
    private val am = ctx.getSystemService(android.media.AudioManager::class.java)
    private val prefs = ctx.getSharedPreferences("other_audio", Context.MODE_PRIVATE)
    private var appliedMode = app.store.s.settings.otherAudio
    private var appliedBalance = app.store.s.settings.mixBalance
    private var focusReq: android.media.AudioFocusRequest? = null
    private var resumeOnGain = false
    private var resumeAfterCall = false
    /** media volume steps taken away for the mix balance; persisted so a killed app still gives them back */
    private var lowered = prefs.getInt("lowered", 0)
        set(v) { field = v; prefs.edit().putInt("lowered", v).apply() }
    private var balanceGainMb = 0

    private val focusListener = android.media.AudioManager.OnAudioFocusChangeListener { f ->
        when (f) {
            android.media.AudioManager.AUDIOFOCUS_LOSS -> { resumeOnGain = false; exo.pause(); abandonFocus() }
            android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> if (exo.isPlaying) { resumeOnGain = true; exo.pause() }
            android.media.AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnGain) { resumeOnGain = false; exo.play() }
        }
    }

    private fun requestFocus(): Boolean {
        val m = am ?: return true
        val r = focusReq ?: android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setWillPauseWhenDucked(false).setOnAudioFocusChangeListener(focusListener).build().also { focusReq = it }
        return m.requestAudioFocus(r) != android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED
    }
    private fun abandonFocus() { resumeOnGain = false; focusReq?.let { am?.abandonAudioFocusRequest(it) } }

    /** our phone playback started / stopped */
    private fun onOwnPlaying(playing: Boolean) {
        when (app.store.s.settings.otherAudio) {
            "duck" -> if (playing) { if (!requestFocus()) exo.pause() } else if (!resumeOnGain) abandonFocus()
            "mix" -> if (playing) lowerVolume() else restoreVolume()
        }
    }

    private fun applyOtherAudio() {
        val s = app.store.s.settings
        if (s.otherAudio != appliedMode) {
            appliedMode = s.otherAudio
            if (s.otherAudio != "duck") abandonFocus()
            if (s.otherAudio != "mix") restoreVolume()
            exo.setAudioAttributes(audioAttrs, s.otherAudio == "pause")
            if (exo.isPlaying) onOwnPlaying(true)
        }
        if (s.mixBalance != appliedBalance) {
            appliedBalance = s.mixBalance
            if (lowered > 0 || exo.isPlaying) { restoreVolume(); if (exo.isPlaying && s.otherAudio == "mix") lowerVolume() }
        }
    }

    private fun outputDeviceType(): Int {
        val m = am ?: return android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        if (android.os.Build.VERSION.SDK_INT >= 33)
            m.getAudioDevicesForAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
                .firstOrNull()?.let { return it.type }
        val prefer = listOf(android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_USB_HEADSET)
        val outs = m.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        return prefer.firstOrNull { it in outs } ?: android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
    }

    private fun lowerVolume() {
        val m = am ?: return
        val steps = app.store.s.settings.mixBalance
        if (lowered > 0 || steps <= 0) return
        val st = android.media.AudioManager.STREAM_MUSIC
        val cur = m.getStreamVolume(st)
        val floor = maxOf(1, if (android.os.Build.VERSION.SDK_INT >= 28) m.getStreamMinVolume(st) else 0)
        val n = (cur - floor).coerceIn(0, steps)
        if (n == 0) return
        var db = Float.NaN
        if (android.os.Build.VERSION.SDK_INT >= 28) runCatching {
            val dev = outputDeviceType()
            db = m.getStreamVolumeDb(st, cur, dev) - m.getStreamVolumeDb(st, cur - n, dev)
        }
        if (db.isNaN() || db.isInfinite() || db <= 0f) db = n * 3f            // typical step of a 15-step media volume
        runCatching { m.setStreamVolume(st, cur - n, 0) }.onFailure { return }
        lowered = n
        balanceGainMb = (db.coerceAtMost(20f) * 100).toInt()
        applySound()
    }

    private fun restoreVolume() {
        if (lowered > 0) am?.let { m ->
            val st = android.media.AudioManager.STREAM_MUSIC
            runCatching { m.setStreamVolume(st, minOf(m.getStreamVolume(st) + lowered, m.getStreamMaxVolume(st)), 0) }
        }
        lowered = 0
        if (balanceGainMb != 0) { balanceGainMb = 0; applySound() }
    }

    /** mix mode holds no focus: pause for calls ourselves, continue when the call ends (Android 12+) */
    private fun watchCalls() {
        val m = am ?: return
        if (android.os.Build.VERSION.SDK_INT < 31) return
        m.addOnModeChangedListener(ctx.mainExecutor) { mode ->
            if (app.store.s.settings.otherAudio != "mix" || casting.value) return@addOnModeChangedListener
            val inCall = mode == android.media.AudioManager.MODE_IN_CALL || mode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
                mode == android.media.AudioManager.MODE_RINGTONE || (android.os.Build.VERSION.SDK_INT >= 33 && mode == android.media.AudioManager.MODE_CALL_SCREENING)
            if (inCall && exo.isPlaying) { resumeAfterCall = true; exo.pause() }
            else if (!inCall && mode == android.media.AudioManager.MODE_NORMAL && resumeAfterCall) { resumeAfterCall = false; exo.play() }
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
        restoreVolume()                    // volume left lowered by a mix session the app did not get to finish
        watchCalls()
        scope.launch { app.store.state.collect { applySound(); applyOtherAudio() } }
        // the open chapter of an imported book was text-only; once its voicing finishes, load the audio right away
        scope.launch {
            app.store.state.collect { st ->
                val np = now.value ?: return@collect
                if (!io.github.notoboto.unison.data.isTextSource(np.book.source.url) || exo.mediaItemCount > 0 || casting.value) return@collect
                if (st.downloaded[np.book.key]?.containsKey(np.n) != true) return@collect
                open(app.repo.book(np.book.key) ?: np.book, np.n, autoplay = false)
            }
        }
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
                Widgets.update(ctx)
                if (!playing || !casting.value) onOwnPlaying(playing)
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
        val t = curText
        val s = if (t == null) "" else io.github.notoboto.unison.ui.sentenceAt(t, position.value)
        val changed = s != sentence.value
        sentence.value = s
        val line = s.take(160).takeIf { it.isNotBlank() && app.store.s.settings.liveSubtitles && !casting.value }
        if (line == sessionPlayer.line && !changed) return
        // throttled so the notification is not rebuilt several times a second; a skipped change is picked up on a later tick
        val now0 = System.currentTimeMillis()
        if (line != null && now0 - lastSubtitleAt < 1200) return
        lastSubtitleAt = now0
        sessionPlayer.setLine(line)
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
        curText = null; sentence.value = ""; sessionPlayer.setLine(null)
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
        if (io.github.notoboto.unison.data.isTextSource(b.source.url) && !app.store.isDownloaded(b.key, n) && !(b.source.url == io.github.notoboto.unison.data.LOCAL_URL && io.github.notoboto.unison.data.Local.isAudio(ctx, b.meta.id))) {
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
            now.value?.let { np -> if (io.github.notoboto.unison.data.isTextSource(np.book.source.url)) {
                if (!app.store.isDownloaded(np.book.key, np.n)) {
                    android.widget.Toast.makeText(ctx, "Глава ещё не озвучена: нажмите ⤓ у главы", android.widget.Toast.LENGTH_LONG).show(); return }
                open(app.repo.book(np.book.key) ?: np.book, np.n, autoplay = true); return
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
    /** Hide the mini player: save the position, stop and unload the chapter. Play from history/widget resumes it. */
    fun close() {
        saveNow(); active.pause()
        if (!casting.value) { exo.stop(); exo.clearMediaItems() }
        tickJob?.cancel(); cancelSleep()
        now.value = null; curText = null; sentence.value = ""; sessionPlayer.setLine(null)
        Widgets.update(ctx)
    }
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

/**
 * Live subtitles for the notification, lock screen and car: the sentence being spoken replaces the "artist" line.
 * The audio files carry their own artist/title tags, which ExoPlayer puts above the media item's metadata, so changing the
 * item (the old approach) never showed up; here the metadata the session reads is overridden instead.
 */
class SubtitlePlayer(player: Player) : androidx.media3.common.ForwardingPlayer(player) {
    var line: String? = null; private set
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<Player.Listener>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    init {
        // the wrapped player reports its own (tag) metadata when a file loads: re-announce ours right after it
        player.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) { main.post { announce() } }
        })
    }

    override fun addListener(listener: Player.Listener) { super.addListener(listener); listeners += listener }
    override fun removeListener(listener: Player.Listener) { super.removeListener(listener); listeners -= listener }

    override fun getMediaMetadata(): MediaMetadata {
        val b = super.getMediaMetadata().buildUpon()
        // our own title / book / author win over whatever tags the audio file has
        currentMediaItem?.mediaMetadata?.let { m ->
            m.title?.let(b::setTitle); m.artist?.let(b::setArtist); m.albumTitle?.let(b::setAlbumTitle); m.artworkUri?.let(b::setArtworkUri)
        }
        line?.let { b.setArtist(it).setSubtitle(it) }
        return b.build()
    }

    fun setLine(text: String?) {
        if (text == line) return
        line = text
        announce()
    }

    private fun announce() {
        val m = mediaMetadata
        val ev = Player.Events(androidx.media3.common.FlagSet.Builder().add(Player.EVENT_MEDIA_METADATA_CHANGED).build())
        listeners.forEach { runCatching { it.onMediaMetadataChanged(m); it.onEvents(this, ev) } }
    }
}

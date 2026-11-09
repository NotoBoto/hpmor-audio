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
import androidx.media3.session.MediaSessionService
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
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val app = application as UnisonApp
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("openPlayer", true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, app.player.exo).setSessionActivity(open).build()
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

    val now = MutableStateFlow<NowPlaying?>(null)
    val isPlaying = MutableStateFlow(false)
    val position = MutableStateFlow(0.0)      // seconds, updated ~5x/s while playing
    val speed = MutableStateFlow(app.store.s.settings.speed)
    /** sleep timer: remaining ms, -1 = until end of chapter, null = off */
    val sleep = MutableStateFlow<Long?>(null)
    private var sleepJob: Job? = null
    private var tickJob: Job? = null

    init {
        exo.playbackParameters = exo.playbackParameters.withSpeed(speed.value)
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying.value = playing
                if (playing) startTicker() else { saveNow(); position.value = exo.currentPosition / 1000.0 }
            }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) onEnded() }
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
            while (isActive) {
                position.value = exo.currentPosition / 1000.0
                if (++k % 25 == 0) saveNow()
                delay(200)
            }
        }
    }

    fun saveNow() {
        val np = now.value ?: return
        val p = exo.currentPosition / 1000.0
        if (exo.mediaItemCount > 0) app.store.setPos(np.book.key, np.n, p, np.duration)
    }

    /** Load a chapter; continue from saved position unless it was finished. */
    fun open(b: Book, n: Int, autoplay: Boolean) {
        val ch = b.chapters.find { it.n == n } ?: return
        if (now.value?.book?.key == b.key && now.value?.n == n) { if (autoplay) play(); return }
        saveNow()
        val pr = app.store.progress(b.key, n)
        val start = if (pr.done || pr.pos > ch.duration - 3) 0.0 else pr.pos
        val item = MediaItem.Builder().setMediaId("${b.key}:$n").setUri(app.repo.audioUri(b, n))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(ch.title).setArtist(b.meta.author).setAlbumTitle(b.meta.title)
                .setArtworkUri(b.coverUrl?.let(Uri::parse)).build()).build()
        now.value = NowPlaying(b, n, ch.title, ch.duration)
        position.value = start
        exo.setMediaItem(item, (start * 1000).toLong())
        exo.prepare()
        exo.playWhenReady = autoplay
        app.store.opened(b.key, n)
        app.downloads.autoAhead(b, n)
    }

    fun play() { if (exo.playbackState == Player.STATE_IDLE) exo.prepare(); exo.play() }
    fun pause() = exo.pause()
    fun toggle() = if (exo.isPlaying) pause() else play()
    fun seekTo(sec: Double) { exo.seekTo((sec * 1000).toLong()); position.value = sec }
    fun skip(sec: Int) = seekTo((exo.currentPosition / 1000.0 + sec).coerceIn(0.0, now.value?.duration ?: 0.0))
    fun setSpeed(v: Float) { speed.value = v; exo.playbackParameters = exo.playbackParameters.withSpeed(v); app.store.settings { it.copy(speed = v) } }
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

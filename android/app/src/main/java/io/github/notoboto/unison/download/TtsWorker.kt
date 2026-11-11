package io.github.notoboto.unison.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.notoboto.unison.R
import io.github.notoboto.unison.UnisonApp
import io.github.notoboto.unison.data.Local
import io.github.notoboto.unison.data.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/** Voices a chapter of an imported book with the phone's speech synthesizer → audio + word timings (same format as Unison chapters). */
class TtsWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    private val app = ctx.applicationContext as UnisonApp

    override suspend fun getForegroundInfo() = foreground(0f)
    private fun foreground(p: Float): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(DownloadWorker.CH, "Загрузки", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, DownloadWorker.CH).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Озвучка синтезатором").setContentText(inputData.getString("title"))
            .setProgress(100, (p * 100).toInt(), p == 0f).setOngoing(true).setSilent(true).build()
        val id = 5000 + (inputData.getString("key").hashCode() + inputData.getInt("n", 0)).mod(1000)
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(id, n)
    }

    private val waiting = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()

    private suspend fun initTts(): TextToSpeech = suspendCancellableCoroutine { cont ->
        var tts: TextToSpeech? = null
        tts = TextToSpeech(applicationContext) { status -> if (status == TextToSpeech.SUCCESS) cont.resume(tts!!) else cont.cancel() }
    }

    /**
     * 16-bit mono PCM collected in a temp file. A long chapter is ~100 MB of raw audio: keeping it in a
     * ByteArrayOutputStream (plus the copy toByteArray makes) froze the whole app in GC for minutes.
     */
    private class PcmFile(val file: File) : java.io.Closeable {
        private val out = java.io.BufferedOutputStream(java.io.FileOutputStream(file), 256 * 1024)
        var bytes = 0L; private set
        fun write(b: ByteArray, off: Int = 0, len: Int = b.size) { out.write(b, off, len); bytes += len }
        fun silence(sec: Double, sr: Int) { var n = (sec * sr).toLong() * 2; val z = ByteArray(8192)
            while (n > 0) { val k = minOf(n, z.size.toLong()).toInt(); write(z, 0, k); n -= k } }
        fun seconds(sr: Int) = if (sr <= 0) 0.0 else bytes / 2.0 / sr
        override fun close() = out.close()
    }

    private suspend fun progress(p: Float, pi: Int) {
        setProgress(Data.Builder().putFloat("p", p).build())
        if (pi % 5 == 0) runCatching { setForeground(foreground(p)) }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) { work() }

    private suspend fun work(): Result {
        val key = inputData.getString("key")!!; val id = inputData.getString("id")!!; val n = inputData.getInt("n", 0)
        runCatching { setForeground(foreground(0f)) }
        val s0 = app.store.s.settings
        val usePiper = s0.ttsEngine == "piper" && io.github.notoboto.unison.data.Piper.installed(applicationContext, s0.piperNarrator)
        // the engine is not in the APK: users who had voices before it moved out get it here, once
        if (usePiper) runCatching { io.github.notoboto.unison.data.Piper.downloadEngine(applicationContext, app.repo.http) }
            .onFailure { app.downloads.finished(ok = false); return Result.failure() }
        val tts = if (usePiper) null else runCatching { kotlinx.coroutines.withTimeout(30_000) { withContext(Dispatchers.Main) { initTts() } } }.getOrElse { app.downloads.finished(ok = false); return Result.failure() }
        val raw = File(applicationContext.cacheDir, "tts_${key}_$n.pcm")
        val audio = File(applicationContext.cacheDir, "tts_${key}_$n.m4a")
        try {
            val s = app.store.s.settings
            tts?.language = Locale.forLanguageTag(s.ttsLang)
            tts?.setSpeechRate(s.ttsRate); tts?.setPitch(s.ttsPitch)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(u: String?) {}
                override fun onDone(u: String?) { waiting.remove(u)?.complete(true) }
                @Deprecated("old api") override fun onError(u: String?) { waiting.remove(u)?.complete(false) }
                override fun onError(u: String?, code: Int) { waiting.remove(u)?.complete(false) }
            })
            val ch = Local.chapterParas(applicationContext, id, n)
            val pieces = listOf(ch.title) + ch.paras
            val speakers = io.github.notoboto.unison.data.Speakers.State()
            fun voiceOf(k: io.github.notoboto.unison.data.Speakers.Kind) = when {
                !s.piperRoles -> s.piperNarrator
                k == io.github.notoboto.unison.data.Speakers.Kind.FEMALE && io.github.notoboto.unison.data.Piper.installed(applicationContext, s.piperFemale) -> s.piperFemale
                k == io.github.notoboto.unison.data.Speakers.Kind.MALE && io.github.notoboto.unison.data.Piper.installed(applicationContext, s.piperMale) -> s.piperMale
                else -> s.piperNarrator
            }
            // synthesis takes the first 90% of the progress bar, encoding the rest
            val synthShare = 0.9f
            var sr = 0
            val pcm = PcmFile(raw)
            val parasJson = pcm.use {
                buildJsonArray {
                    pieces.forEachIndexed { pi, para ->
                        if (isStopped) error("stopped")
                        if (usePiper) {
                            // Piper: segment by speaker, each with its own voice; timings per segment
                            val segs = if (pi == 0) listOf(io.github.notoboto.unison.data.Speakers.Seg(para, io.github.notoboto.unison.data.Speakers.Kind.NARRATOR))
                                else io.github.notoboto.unison.data.Speakers.split(para, speakers)
                            val toks = mutableListOf<Triple<String, Double, Int>>()
                            val dialogue = pi > 0 && para.trimStart().let { it.startsWith("—") || it.startsWith("–") || it.startsWith("-") }
                            for (sg in segs) {
                                val role = when (sg.kind) { io.github.notoboto.unison.data.Speakers.Kind.NARRATOR -> 0; io.github.notoboto.unison.data.Speakers.Kind.MALE -> 1; else -> 2 }
                                val t = pcm.seconds(sr)
                                if (dialogue) toks += Triple("—", t, role)
                                val (rate, samples) = io.github.notoboto.unison.data.Piper.speak(applicationContext, voiceOf(sg.kind), sg.text, s.ttsRate)
                                if (samples.isEmpty()) continue
                                if (sr == 0) sr = rate
                                val bytes = ByteArray(samples.size * 2)
                                samples.forEachIndexed { k, v -> bytes[2 * k] = (v.toInt() and 0xff).toByte(); bytes[2 * k + 1] = (v.toInt() shr 8).toByte() }
                                pcm.write(bytes)
                                val dur = samples.size.toDouble() / rate
                                val ws = sg.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                val wts = ws.map { (it.count { c -> c.isLetterOrDigit() } + 1).toDouble() }; val tot = wts.sum().coerceAtLeast(1.0)
                                var acc = 0.0
                                ws.forEachIndexed { k, w -> toks += Triple(w, t + dur * acc / tot, role); acc += wts[k] }
                                pcm.silence(0.18, sr)
                            }
                            if (sr > 0) pcm.silence(if (pi == 0) 0.8 else 0.3, sr)
                            add(buildJsonArray { toks.forEach { (w, tt, r) -> add(buildJsonArray { add(JsonPrimitive(w)); add(JsonPrimitive(Math.round(tt * 100) / 100.0)); add(JsonPrimitive(r)) }) } })
                            progress(synthShare * (pi + 1f) / pieces.size, pi)
                            return@forEachIndexed
                        }
                        val words = para.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        val paraStart = pcm.seconds(sr); var paraDur = 0.0
                        for (part in splitLong(para)) {
                            val f = File(applicationContext.cacheDir, "tts_${key}_$n.wav")
                            val done = CompletableDeferred<Boolean>(); val uid = "u$pi-${part.hashCode()}"
                            waiting[uid] = done
                            if (tts!!.synthesizeToFile(part, null, f, uid) != TextToSpeech.SUCCESS) { waiting.remove(uid); error("synthesis failed") }
                            // a synthesizer that never calls back must not hang the worker forever
                            if (kotlinx.coroutines.withTimeoutOrNull(120_000) { done.await() } != true) error("synthesis failed")
                            val (rate, data) = readWav(f)
                            f.delete()
                            if (data.isEmpty()) continue
                            if (sr == 0) sr = rate
                            pcm.write(data); paraDur += data.size / 2.0 / rate
                        }
                        if (sr > 0) pcm.silence(if (pi == 0) 0.8 else 0.35, sr)   // pause between paragraphs
                        // word timings: proportional to word length inside the paragraph
                        val weights = words.map { (it.count { c -> c.isLetterOrDigit() } + 1).toDouble() }
                        val total = weights.sum().coerceAtLeast(1.0)
                        var acc = 0.0
                        add(buildJsonArray {
                            words.forEachIndexed { k, w ->
                                add(buildJsonArray { add(JsonPrimitive(w)); add(JsonPrimitive(Math.round((paraStart + paraDur * acc / total) * 100) / 100.0)); add(JsonPrimitive(0)) })
                                acc += weights[k]
                            }
                        })
                        progress(synthShare * (pi + 1f) / pieces.size, pi)
                    }
                }
            }
            if (sr == 0) error("synthesizer produced no audio")
            val duration = pcm.seconds(sr)
            var lastP = 0L
            encodeAac(raw, sr, audio) { f ->
                val now = System.currentTimeMillis()
                if (now - lastP > 500) { lastP = now; setProgressAsync(Data.Builder().putFloat("p", synthShare + (1 - synthShare) * f).build()) }
            }
            raw.delete()
            val chJson = buildJsonObject {
                put("n", n); put("title", ch.title); put("roles", buildJsonArray { add(JsonPrimitive("Рассказчик")); if (usePiper) { add(JsonPrimitive("Мужской голос")); add(JsonPrimitive("Женский голос")) } })
                put("paras", parasJson); put("duration", Math.round(duration * 10) / 10.0)
            }
            val files = app.repo.files
            withContext(Dispatchers.IO) {
                files.write(key, n, "json") { it.write(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), chJson).toByteArray()) }
                files.write(key, n, "ogg") { out -> audio.inputStream().use { it.copyTo(out) } }
                audio.delete()
                Local.saveIndex(applicationContext, id, Local.chapters(applicationContext, id).map { if (it.n == n) it.copy(duration = duration, bytes = files.bookBytes(key), v = "tts") else it })
                app.repo.reloadLocal()
            }
            withContext(Dispatchers.Main) { app.store.markDownloaded(key, n, "tts"); app.downloads.finished(ok = true) }
            return Result.success()
        } catch (e: Exception) {
            if (!isStopped) app.downloads.finished(ok = false)
            return Result.failure()
        } finally {
            tts?.shutdown(); if (usePiper) io.github.notoboto.unison.data.Piper.releaseAll()
            raw.delete(); audio.delete()
        }
    }

    /** synthesizeToFile has an input limit (~4000 chars): split long paragraphs at sentence ends. */
    private fun splitLong(p: String): List<String> {
        if (p.length < 3500) return listOf(p)
        val out = mutableListOf<String>(); val sb = StringBuilder()
        for (s in p.split(Regex("(?<=[.!?…])\\s+"))) { if (sb.length + s.length > 3500 && sb.isNotEmpty()) { out += sb.toString(); sb.clear() }; sb.append(s).append(' ') }
        if (sb.isNotBlank()) out += sb.toString()
        return out
    }

    /** returns (sampleRate, 16-bit mono PCM) */
    private fun readWav(f: File): Pair<Int, ByteArray> = RandomAccessFile(f, "r").use { r ->
        val head = ByteArray(12); r.readFully(head)
        var rate = 22050; var channels = 1
        while (r.filePointer < r.length()) {
            val id = ByteArray(4); r.readFully(id)
            val size = Integer.reverseBytes(r.readInt())
            when (String(id)) {
                "fmt " -> { val b = ByteArray(size); r.readFully(b); channels = (b[2].toInt() and 0xff) or ((b[3].toInt() and 0xff) shl 8)
                    rate = (b[4].toInt() and 0xff) or ((b[5].toInt() and 0xff) shl 8) or ((b[6].toInt() and 0xff) shl 16) or ((b[7].toInt() and 0xff) shl 24) }
                "data" -> { val n = minOf(size.toLong(), r.length() - r.filePointer).toInt(); val b = ByteArray(n); r.readFully(b)
                    return@use rate to (if (channels == 2) downmix(b) else b) }
                else -> r.seek(r.filePointer + size)
            }
        }
        rate to ByteArray(0)
    }
    private fun downmix(b: ByteArray): ByteArray { val o = ByteArray(b.size / 2); var i = 0; var j = 0; while (i + 3 < b.size) { o[j] = b[i]; o[j + 1] = b[i + 1]; i += 4; j += 2 }; return o }

    /** 16-bit mono PCM file → AAC in an MP4 container, read in chunks (never the whole chapter in memory). */
    private fun encodeAac(pcmFile: File, sr: Int, out: File, onProgress: (Float) -> Unit) {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 48000); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val total = pcmFile.length().coerceAtLeast(1)
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started = false
        try {
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
            java.io.BufferedInputStream(java.io.FileInputStream(pcmFile), 256 * 1024).use { inp ->
                var track = -1; var off = 0L; var inputDone = false; val info = MediaCodec.BufferInfo()
                val chunk = ByteArray(16384)
                while (true) {
                    if (isStopped) error("stopped")
                    if (!inputDone) {
                        val ii = codec.dequeueInputBuffer(10_000)
                        if (ii >= 0) {
                            val buf = codec.getInputBuffer(ii)!!; buf.clear()
                            val want = minOf(buf.capacity(), chunk.size) and 1.inv()     // whole samples only
                            var got = 0
                            while (got < want) { val k = inp.read(chunk, got, want - got); if (k < 0) break; got += k }
                            val ptsUs = off / 2L * 1_000_000L / sr
                            if (got <= 0) { codec.queueInputBuffer(ii, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                            else { buf.put(chunk, 0, got); codec.queueInputBuffer(ii, 0, got, ptsUs, 0); off += got; onProgress(off.toFloat() / total) }
                        }
                    }
                    val oi = codec.dequeueOutputBuffer(info, 10_000)
                    if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = muxer.addTrack(codec.outputFormat); muxer.start(); started = true }
                    else if (oi >= 0) {
                        val ob = codec.getOutputBuffer(oi)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && track >= 0) { ob.position(info.offset); ob.limit(info.offset + info.size); muxer.writeSampleData(track, ob, info) }
                        codec.releaseOutputBuffer(oi, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }; codec.release()
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }
}

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
import java.io.ByteArrayOutputStream
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

    override suspend fun doWork(): Result {
        val key = inputData.getString("key")!!; val id = inputData.getString("id")!!; val n = inputData.getInt("n", 0)
        runCatching { setForeground(foreground(0f)) }
        val s0 = app.store.s.settings
        val usePiper = s0.ttsEngine == "piper" && io.github.notoboto.unison.data.Piper.installed(applicationContext, s0.piperNarrator)
        val tts = if (usePiper) null else runCatching { initTts() }.getOrElse { return Result.failure() }
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
            val pcm = ByteArrayOutputStream(); var sr = 0
            val parasJson = buildJsonArray {
                var t = 0.0
                pieces.forEachIndexed { pi, para ->
                    if (usePiper) {
                        // Piper: segment by speaker, each with its own voice; timings per segment
                        val segs = if (pi == 0) listOf(io.github.notoboto.unison.data.Speakers.Seg(para, io.github.notoboto.unison.data.Speakers.Kind.NARRATOR))
                            else io.github.notoboto.unison.data.Speakers.split(para, speakers)
                        val toks = mutableListOf<Triple<String, Double, Int>>()
                        for (sg in segs) {
                            val (rate, samples) = io.github.notoboto.unison.data.Piper.speak(applicationContext, voiceOf(sg.kind), sg.text, s.ttsRate)
                            if (sr == 0) sr = rate
                            val bytes = ByteArray(samples.size * 2)
                            samples.forEachIndexed { k, v -> bytes[2 * k] = (v.toInt() and 0xff).toByte(); bytes[2 * k + 1] = (v.toInt() shr 8).toByte() }
                            pcm.write(bytes)
                            val dur = samples.size.toDouble() / rate
                            val ws = sg.text.split(Regex("\\s+")).filter { it.isNotEmpty() }
                            val wts = ws.map { (it.count { c -> c.isLetterOrDigit() } + 1).toDouble() }; val tot = wts.sum().coerceAtLeast(1.0)
                            var acc = 0.0
                            val role = when (sg.kind) { io.github.notoboto.unison.data.Speakers.Kind.NARRATOR -> 0; io.github.notoboto.unison.data.Speakers.Kind.MALE -> 1; else -> 2 }
                            ws.forEachIndexed { k, w -> toks += Triple(w, t + dur * acc / tot, role); acc += wts[k] }
                            t += dur
                            val gapS = ByteArray((0.18 * rate).toInt() * 2); pcm.write(gapS); t += 0.18
                        }
                        val gap = ByteArray(((if (pi == 0) 0.8 else 0.3) * sr).toInt() * 2); pcm.write(gap); t += gap.size / 2.0 / sr
                        add(buildJsonArray { toks.forEach { (w, tt, r) -> add(buildJsonArray { add(JsonPrimitive(w)); add(JsonPrimitive(Math.round(tt * 100) / 100.0)); add(JsonPrimitive(r)) }) } })
                        setProgress(Data.Builder().putFloat("p", (pi + 1f) / pieces.size).build())
                        if (pi % 5 == 0) runCatching { setForeground(foreground((pi + 1f) / pieces.size)) }
                        return@forEachIndexed
                    }
                    val words = para.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    var paraStart = t; var paraDur = 0.0
                    for (part in splitLong(para)) {
                        val f = File(applicationContext.cacheDir, "tts_${key}_$n.wav")
                        val done = CompletableDeferred<Boolean>(); val uid = "u$pi-${part.hashCode()}"
                        waiting[uid] = done
                        tts!!.synthesizeToFile(part, null, f, uid)
                        if (!done.await()) error("synthesis failed")
                        val (rate, data) = readWav(f)
                        if (sr == 0) sr = rate
                        pcm.write(data); paraDur += data.size / 2.0 / rate
                        f.delete()
                    }
                    // pause between paragraphs
                    val gap = ByteArray(((if (pi == 0) 0.8 else 0.35) * sr).toInt() * 2); pcm.write(gap)
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
                    t = paraStart + paraDur + gap.size / 2.0 / sr.coerceAtLeast(1)
                    setProgress(Data.Builder().putFloat("p", (pi + 1f) / pieces.size).build())
                    if (pi % 5 == 0) runCatching { setForeground(foreground((pi + 1f) / pieces.size)) }
                }
            }
            val duration = pcm.size() / 2.0 / sr
            val audio = File(applicationContext.cacheDir, "tts_${key}_$n.m4a")
            withContext(Dispatchers.Default) { encodeAac(pcm.toByteArray(), sr, audio) }
            val chJson = buildJsonObject {
                put("n", n); put("title", ch.title); put("roles", buildJsonArray { add(JsonPrimitive("Рассказчик")); if (usePiper) { add(JsonPrimitive("Мужской голос")); add(JsonPrimitive("Женский голос")) } })
                put("paras", parasJson); put("duration", Math.round(duration * 10) / 10.0)
            }
            val files = app.repo.files
            files.write(key, n, "json") { it.write(json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), chJson).toByteArray()) }
            files.write(key, n, "ogg") { out -> audio.inputStream().use { it.copyTo(out) } }
            audio.delete()
            Local.saveIndex(applicationContext, id, Local.chapters(applicationContext, id).map { if (it.n == n) it.copy(duration = duration, bytes = files.bookBytes(key), v = "tts") else it })
            withContext(Dispatchers.Main) { app.repo.reloadLocal(); app.store.markDownloaded(key, n, "tts"); app.downloads.finished(ok = true) }
            return Result.success()
        } catch (e: Exception) {
            if (!isStopped) app.downloads.finished(ok = false)
            return Result.failure()
        } finally { tts?.shutdown(); if (usePiper) io.github.notoboto.unison.data.Piper.releaseAll() }
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

    /** 16-bit mono PCM → AAC in an MP4 container. */
    private fun encodeAac(pcm: ByteArray, sr: Int, out: File) {
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sr, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 48000); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); codec.start()
        val muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1; var off = 0; var inputDone = false; val info = MediaCodec.BufferInfo()
        while (true) {
            if (!inputDone) {
                val ii = codec.dequeueInputBuffer(10_000)
                if (ii >= 0) {
                    val buf = codec.getInputBuffer(ii)!!; buf.clear()
                    val n = minOf(buf.capacity(), pcm.size - off)
                    val ptsUs = off / 2L * 1_000_000L / sr
                    if (n <= 0) { codec.queueInputBuffer(ii, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                    else { buf.put(pcm, off, n); codec.queueInputBuffer(ii, 0, n, ptsUs, 0); off += n }
                }
            }
            val oi = codec.dequeueOutputBuffer(info, 10_000)
            if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = muxer.addTrack(codec.outputFormat); muxer.start() }
            else if (oi >= 0) {
                val ob = codec.getOutputBuffer(oi)!!
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                if (info.size > 0 && track >= 0) { ob.position(info.offset); ob.limit(info.offset + info.size); muxer.writeSampleData(track, ob, info) }
                codec.releaseOutputBuffer(oi, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        codec.stop(); codec.release(); muxer.stop(); muxer.release()
    }
}

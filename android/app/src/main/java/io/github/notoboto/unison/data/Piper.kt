package io.github.notoboto.unison.data

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Request
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File

/** Offline neural voices (Piper via sherpa-onnx), downloaded on demand. */
object Piper {
    data class Voice(val id: String, val title: String, val female: Boolean)
    val voices = listOf(
        Voice("dmitri", "Дмитрий", false),
        Voice("ruslan", "Руслан", false),
        Voice("denis", "Денис", false),
        Voice("irina", "Ирина", true),
    )
    private fun url(id: String) = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-ru_RU-$id-medium-int8.tar.bz2"
    fun dir(ctx: Context) = File(ctx.filesDir, "piper").apply { mkdirs() }
    private fun voiceDir(ctx: Context, id: String) = File(dir(ctx), id)
    fun installed(ctx: Context, id: String) = File(voiceDir(ctx, id), "ok").exists()

    /** id -> download progress (0..1) while downloading */
    val progress = MutableStateFlow<Map<String, Float>>(emptyMap())

    suspend fun download(ctx: Context, id: String, http: okhttp3.OkHttpClient) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val tmp = File(dir(ctx), "$id.tar.bz2")
        progress.value = progress.value + (id to 0f)
        try {
            http.newCall(Request.Builder().url(url(id)).build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val body = r.body!!; val total = body.contentLength().coerceAtLeast(1); var got = 0L
                tmp.outputStream().use { o -> body.byteStream().use { i ->
                    val buf = ByteArray(64 * 1024)
                    while (true) { val k = i.read(buf); if (k < 0) break; o.write(buf, 0, k); got += k
                        progress.value = progress.value + (id to (got.toFloat() / total * 0.9f)) }
                } }
            }
            val target = voiceDir(ctx, id).apply { deleteRecursively(); mkdirs() }
            TarArchiveInputStream(BZip2CompressorInputStream(tmp.inputStream().buffered())).use { tar ->
                while (true) {
                    val e = tar.nextEntry ?: break
                    // strip the top folder of the archive
                    val rel = e.name.substringAfter('/', "")
                    if (rel.isEmpty()) continue
                    val f = File(target, rel)
                    if (e.isDirectory) f.mkdirs() else { f.parentFile?.mkdirs(); f.outputStream().use { tar.copyTo(it) } }
                }
            }
            File(target, "ok").writeText("1")
        } finally { tmp.delete(); progress.value = progress.value - id }
    }

    @Synchronized fun delete(ctx: Context, id: String) { engines.remove(id)?.release(); voiceDir(ctx, id).deleteRecursively() }

    // All engine access goes through this object's lock: the voice preview and the voicing worker may run at the same time,
    // and releasing a native engine while another thread is generating with it crashes the whole process.
    private val engines = mutableMapOf<String, OfflineTts>()
    @Synchronized private fun engine(ctx: Context, id: String): OfflineTts = engines.getOrPut(id) {
        val d = voiceDir(ctx, id)
        // check the files here: a missing file inside the native engine is a hard crash, not an exception
        val model = d.listFiles()?.firstOrNull { it.name.endsWith(".onnx") } ?: error("голос «$id» не скачан")
        val tokens = File(d, "tokens.txt"); val espeak = File(d, "espeak-ng-data")
        if (!tokens.isFile || !espeak.isDirectory) error("файлы голоса «$id» повреждены — удалите и скачайте заново")
        OfflineTts(null, OfflineTtsConfig(model = OfflineTtsModelConfig(
            vits = OfflineTtsVitsModelConfig(model = model.path, tokens = File(d, "tokens.txt").path, dataDir = File(d, "espeak-ng-data").path),
            numThreads = 2, debug = false, provider = "cpu")))
    }
    @Synchronized fun releaseAll() { engines.values.forEach { it.release() }; engines.clear() }

    /** Synthesizes [text] → 16-bit PCM at the model's sample rate. */
    @Synchronized fun speak(ctx: Context, id: String, text: String, speed: Float): Pair<Int, ShortArray> {
        if (text.none { it.isLetterOrDigit() }) return 22050 to ShortArray(0)     // nothing to say (e.g. "—", "***")
        val a = engine(ctx, id).generate(text, 0, speed)
        val pcm = ShortArray(a.samples.size) { i -> (a.samples[i].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
        return a.sampleRate to pcm
    }
}

/**
 * Who speaks in a paragraph — a light heuristic for Russian prose:
 * dialogue starts with a dash and alternates «— speech — remark — speech»; the remark's verb tells the gender
 * («сказала» → female). Unattributed lines alternate between the last two speakers.
 */
object Speakers {
    enum class Kind { NARRATOR, MALE, FEMALE }
    data class Seg(val text: String, val kind: Kind)

    private val femaleVerb = Regex("\\b\\p{L}+(ла|лась)\\b", RegexOption.IGNORE_CASE)
    private val maleVerb = Regex("\\b\\p{L}+(л|лся|ул|ил|ел|ал)\\b", RegexOption.IGNORE_CASE)
    private val femaleWords = Regex("\\b(она|девочка|девушка|женщина|ведьма|мать|мама|профессор \\p{Lu}\\p{L}+а)\\b", RegexOption.IGNORE_CASE)

    private fun genderOf(remark: String): Kind? {
        val f = femaleVerb.containsMatchIn(remark); val m = maleVerb.containsMatchIn(remark)
        return when { f && !m -> Kind.FEMALE; m && !f -> Kind.MALE; f && m -> if (femaleWords.containsMatchIn(remark)) Kind.FEMALE else Kind.MALE; else -> null }
    }

    class State { var last: Kind = Kind.MALE; var prev: Kind = Kind.FEMALE }

    fun split(para: String, st: State): List<Seg> {
        val p = para.trim()
        if (!(p.startsWith("—") || p.startsWith("–") || p.startsWith("-"))) return listOf(Seg(p, Kind.NARRATOR))
        val parts = p.trimStart('—', '–', '-', ' ').split(Regex("\\s+[—–]\\s+"))
        // even indexes = speech, odd = remarks
        val remarks = parts.filterIndexed { i, _ -> i % 2 == 1 }
        val g = remarks.firstNotNullOfOrNull { genderOf(it) }
        val who = g ?: st.prev            // no remark: the other person answers
        if (who != st.last) { st.prev = st.last; st.last = who }
        return parts.mapIndexed { i, t -> Seg(t.trim(), if (i % 2 == 0) who else Kind.NARRATOR) }.filter { it.text.isNotBlank() }
    }
}

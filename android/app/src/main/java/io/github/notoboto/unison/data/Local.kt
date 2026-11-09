package io.github.notoboto.unison.data

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/** Books imported from the phone (EPUB / FB2 / TXT). Voiced on the device by the system speech synthesizer. */
const val LOCAL_URL = "local://"
val LOCAL_SOURCE = Source(LOCAL_URL, "На телефоне")

@Serializable
data class LocalChapter(val title: String, val paras: List<String>)

object Local {
    fun dir(ctx: Context) = File(ctx.filesDir, "local").apply { mkdirs() }
    private fun bookDir(ctx: Context, id: String) = File(dir(ctx), id).apply { mkdirs() }

    /** Catalog of imported books (BookMeta list) and their chapter lists. */
    fun catalog(ctx: Context): List<BookMeta> = dir(ctx).listFiles()?.mapNotNull { d ->
        runCatching { json.decodeFromString(BookMeta.serializer(), File(d, "meta.json").readText()) }.getOrNull()
    }.orEmpty().sortedBy { it.title }

    fun chapters(ctx: Context, id: String): List<ChapterMeta> = runCatching {
        json.decodeFromString(ListSerializer(ChapterMeta.serializer()), File(bookDir(ctx, id), "index.json").readText())
    }.getOrDefault(emptyList())

    fun saveIndex(ctx: Context, id: String, list: List<ChapterMeta>) =
        File(bookDir(ctx, id), "index.json").writeText(json.encodeToString(ListSerializer(ChapterMeta.serializer()), list))

    fun chapterParas(ctx: Context, id: String, n: Int): LocalChapter =
        json.decodeFromString(LocalChapter.serializer(), File(bookDir(ctx, id), "t%03d.json".format(n)).readText())

    fun delete(ctx: Context, id: String) = File(dir(ctx), id).deleteRecursively()

    /** Text-only chapter (no timings) for reading before it is voiced. */
    fun chapterText(ctx: Context, id: String, n: Int): ChapterText {
        val c = chapterParas(ctx, id, n)
        val paras = listOf(c.title.split(' ').map { Tok(it, null, 0) }) + c.paras.map { p -> p.split(Regex("\\s+")).filter { it.isNotEmpty() }.map { Tok(it, null, 0) } }
        val est = c.paras.sumOf { it.length } / 14.0
        return ChapterText(n, c.title, listOf("Рассказчик"), paras, est)
    }

    // ---------------- audiobook files (no text) ----------------
    @Serializable data class AudioFile(val uri: String, val title: String, val duration: Double)

    fun audioFiles(ctx: Context, id: String): List<AudioFile> = runCatching {
        json.decodeFromString(ListSerializer(AudioFile.serializer()), File(bookDir(ctx, id), "audio.json").readText())
    }.getOrDefault(emptyList())
    fun isAudio(ctx: Context, id: String) = File(bookDir(ctx, id), "audio.json").exists()

    fun importAudio(ctx: Context, uris: List<Uri>): BookMeta {
        require(uris.isNotEmpty())
        val files = uris.map { u ->
            runCatching { ctx.contentResolver.takePersistableUriPermission(u, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val mmr = android.media.MediaMetadataRetriever()
            val (title, dur, album, artist, art) = runCatching {
                mmr.setDataSource(ctx, u)
                listOf(mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE),
                    mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION),
                    mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST), null)
            }.getOrDefault(listOf(null, null, null, null, null))
            val name = android.provider.OpenableColumns.DISPLAY_NAME.let { col ->
                ctx.contentResolver.query(u, arrayOf(col), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            } ?: u.lastPathSegment.orEmpty()
            Triple(AudioFile(u.toString(), title ?: name.substringBeforeLast('.'), (dur?.toDoubleOrNull() ?: 0.0) / 1000), album, artist) to (runCatching { mmr.embeddedPicture }.getOrNull().also { mmr.release() })
        }.sortedBy { it.first.first.title.lowercase().padStart(1) }
        val sorted = files.sortedWith(compareBy { f -> Regex("\\d+").find(f.first.first.title)?.value?.padStart(6, '0') ?: f.first.first.title })
        val id = "a" + System.currentTimeMillis().toString(36)
        val d = bookDir(ctx, id)
        File(d, "audio.json").writeText(json.encodeToString(ListSerializer(AudioFile.serializer()), sorted.map { it.first.first }))
        val coverPath = sorted.firstNotNullOfOrNull { it.second }?.let { File(d, "cover.jpg").apply { writeBytes(it) }.toURI().toString() }
        val first = sorted.first().first
        val meta = BookMeta(id = id, title = first.second ?: first.first.title, author = first.third, cover = coverPath, coverSm = coverPath, data = "",
            total = sorted.size, tags = listOf("Аудиокнига"), description = "Импортированная аудиокнига (${sorted.size} файл.). Текста нет — только плеер.")
        File(d, "meta.json").writeText(json.encodeToString(BookMeta.serializer(), meta))
        saveIndex(ctx, id, sorted.mapIndexed { i, f -> ChapterMeta(i + 1, f.first.first.title, f.first.first.duration, 0, "audio", null) })
        sorted.forEachIndexed { i, f -> File(d, "t%03d.json".format(i + 1)).writeText(json.encodeToString(LocalChapter.serializer(), LocalChapter(f.first.first.title, emptyList()))) }
        return meta
    }

    // ---------------- import ----------------
    data class Parsed(val title: String, val author: String?, val chapters: List<LocalChapter>, val cover: ByteArray?)

    fun import(ctx: Context, uri: Uri): BookMeta {
        val display = runCatching {
            ctx.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()
        val name = display.lowercase()
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val parsed = when {
            bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> epub(bytes)
            name.endsWith(".fb2") || String(bytes, 0, minOf(bytes.size, 400), Charsets.UTF_8).contains("FictionBook") -> fb2(bytes)
            else -> txt(bytes, display.substringAfterLast('/').substringBeforeLast('.'))
        }
        require(parsed.chapters.isNotEmpty()) { "В файле не найден текст" }
        val id = "b" + System.currentTimeMillis().toString(36)
        val d = bookDir(ctx, id)
        parsed.chapters.forEachIndexed { i, c -> File(d, "t%03d.json".format(i + 1)).writeText(json.encodeToString(LocalChapter.serializer(), c)) }
        val coverPath = parsed.cover?.let { File(d, "cover.jpg").apply { writeBytes(it) }.toURI().toString() }
        val meta = BookMeta(id = id, title = parsed.title, author = parsed.author, cover = coverPath, coverSm = coverPath, data = "",
            total = parsed.chapters.size, tags = listOf("Своя книга", "Синтезатор телефона"),
            description = "Импортированная книга. Главы озвучиваются синтезатором речи телефона: нажмите ⤓ у главы или выберите «Озвучить» в меню.")
        File(d, "meta.json").writeText(json.encodeToString(BookMeta.serializer(), meta))
        saveIndex(ctx, id, parsed.chapters.mapIndexed { i, c -> ChapterMeta(i + 1, c.title, c.paras.sumOf { it.length } / 14.0, 0, null, null) })
        return meta
    }

    private fun clean(html: String): List<String> {
        val body = html.substringAfter("<body", html)
        val blocks = Regex("(?is)<(p|h[1-6]|div|li|blockquote)[^>]*>(.*?)</\\1>").findAll(body).map { it.groupValues[2] }.toList()
            .ifEmpty { listOf(body) }
        return blocks.map { b ->
            b.replace(Regex("(?is)<br\\s*/?>"), " ").replace(Regex("(?s)<[^>]+>"), "")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#160;", " ")
                .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toInt().toChar().toString() }
                .replace(Regex("\\s+"), " ").trim()
        }.filter { it.isNotBlank() }
    }

    private fun epub(bytes: ByteArray): Parsed {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z -> while (true) { val e = z.nextEntry ?: break; if (!e.isDirectory) files[e.name] = z.readBytes() } }
        val container = files["META-INF/container.xml"]?.toString(Charsets.UTF_8).orEmpty()
        val opfPath = Regex("full-path=\"([^\"]+)\"").find(container)?.groupValues?.get(1) ?: files.keys.first { it.endsWith(".opf") }
        val opf = files[opfPath]!!.toString(Charsets.UTF_8)
        val base = opfPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val title = Regex("(?s)<dc:title[^>]*>(.*?)</dc:title>").find(opf)?.groupValues?.get(1)?.trim() ?: "Без названия"
        val author = Regex("(?s)<dc:creator[^>]*>(.*?)</dc:creator>").find(opf)?.groupValues?.get(1)?.trim()
        val manifest = Regex("<item\\b[^>]*>").findAll(opf).associate { m ->
            val t = m.value
            (Regex("id=\"([^\"]+)\"").find(t)?.groupValues?.get(1) ?: "") to (Regex("href=\"([^\"]+)\"").find(t)?.groupValues?.get(1) ?: "")
        }
        val coverId = Regex("<meta[^>]*name=\"cover\"[^>]*content=\"([^\"]+)\"").find(opf)?.groupValues?.get(1)
            ?: Regex("<item\\b[^>]*properties=\"cover-image\"[^>]*id=\"([^\"]+)\"").find(opf)?.groupValues?.get(1)
        val cover = coverId?.let { manifest[it] }?.let { files[base + Uri.decode(it)] }
        val spine = Regex("<itemref\\b[^>]*idref=\"([^\"]+)\"").findAll(opf).map { it.groupValues[1] }.toList()
        val chapters = spine.mapNotNull { id ->
            val html = manifest[id]?.let { files[base + Uri.decode(it)] }?.toString(Charsets.UTF_8) ?: return@mapNotNull null
            val paras = clean(html); if (paras.sumOf { it.length } < 200) return@mapNotNull null
            val head = Regex("(?is)<h[1-3][^>]*>(.*?)</h[1-3]>").find(html)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()
            val t = head?.takeIf { it.isNotBlank() && it.length < 120 } ?: paras.first().take(60)
            LocalChapter(t, if (paras.first() == t) paras.drop(1) else paras)
        }
        return Parsed(title, author, chapters, cover)
    }

    private fun fb2(bytes: ByteArray): Parsed {
        val head = String(bytes, 0, minOf(200, bytes.size), Charsets.ISO_8859_1)
        val cs = Regex("encoding=\"([^\"]+)\"").find(head)?.groupValues?.get(1)?.let { runCatching { Charset.forName(it) }.getOrNull() } ?: Charsets.UTF_8
        val x = String(bytes, cs)
        val title = Regex("(?s)<book-title>(.*?)</book-title>").find(x)?.groupValues?.get(1)?.trim() ?: "Без названия"
        val author = Regex("(?s)<author>(.*?)</author>").find(x)?.groupValues?.get(1)?.let { a ->
            listOf("first-name", "last-name").mapNotNull { Regex("<$it>(.*?)</$it>").find(a)?.groupValues?.get(1) }.joinToString(" ")
        }?.ifBlank { null }
        val body = Regex("(?s)<body[^>]*>(.*?)</body>").find(x)?.groupValues?.get(1).orEmpty()
        val sections = Regex("(?s)<section[^>]*>(.*?)</section>").findAll(body).map { it.groupValues[1] }.toList().ifEmpty { listOf(body) }
        val chapters = sections.mapIndexedNotNull { i, sec ->
            val t = Regex("(?s)<title>(.*?)</title>").find(sec)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), " ")?.replace(Regex("\\s+"), " ")?.trim()
            val paras = Regex("(?s)<p>(.*?)</p>").findAll(sec.replace(Regex("(?s)<title>.*?</title>"), "")).map {
                it.groupValues[1].replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()
            }.filter { it.isNotBlank() }.toList()
            if (paras.isEmpty()) null else LocalChapter(t?.ifBlank { null } ?: "Глава ${i + 1}", paras)
        }
        val cover = Regex("(?s)<binary[^>]*content-type=\"image/[^\"]+\"[^>]*>(.*?)</binary>").find(x)?.groupValues?.get(1)?.let {
            runCatching { android.util.Base64.decode(it.trim(), android.util.Base64.DEFAULT) }.getOrNull()
        }
        return Parsed(title, author, chapters, cover)
    }

    private fun txt(bytes: ByteArray, fileName: String): Parsed {
        val utf = String(bytes, Charsets.UTF_8)
        val text = if (utf.contains('�')) String(bytes, Charset.forName("windows-1251")) else utf
        val lines = text.lines().map { it.trim() }
        val head = Regex("^(глава|часть|chapter|пролог|эпилог)\\b.*", RegexOption.IGNORE_CASE)
        val chapters = mutableListOf<LocalChapter>()
        var title = "Начало"; var cur = mutableListOf<String>()
        fun flush() { if (cur.isNotEmpty()) chapters += LocalChapter(title, cur.toList()); cur = mutableListOf() }
        for (l in lines) {
            if (l.isEmpty()) continue
            if (l.length < 80 && head.matches(l)) { flush(); title = l } else cur += l
        }
        flush()
        // no headings found: split into ~15 000 character parts
        val result = if (chapters.size > 1 || chapters.firstOrNull()?.title != "Начало") chapters else {
            val all = chapters.flatMap { it.paras }; val parts = mutableListOf<LocalChapter>(); var acc = mutableListOf<String>(); var size = 0
            for (p in all) { acc += p; size += p.length; if (size > 15000) { parts += LocalChapter("Часть ${parts.size + 1}", acc); acc = mutableListOf(); size = 0 } }
            if (acc.isNotEmpty()) parts += LocalChapter("Часть ${parts.size + 1}", acc)
            parts
        }
        return Parsed(fileName.ifBlank { "Книга" }, null, result, null)
    }
}

private val unusedSerializer = String.serializer()

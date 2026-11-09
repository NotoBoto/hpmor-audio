package io.github.notoboto.unison.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Where downloaded chapters live: app storage or a user-picked folder (SAF, e.g. on an SD card). */
interface ChapterFiles {
    fun exists(key: String, n: Int, ext: String): Boolean
    fun write(key: String, n: Int, ext: String, block: (OutputStream) -> Unit)
    fun readText(key: String, n: Int, ext: String): String
    fun open(key: String, n: Int, ext: String): InputStream
    fun playUri(key: String, n: Int): String
    fun delete(key: String, n: Int)
    fun bookBytes(key: String): Long
    fun totalBytes(): Long
}

private fun name(n: Int, ext: String) = "ch%03d.%s".format(n, ext)

class InternalFiles(context: Context) : ChapterFiles {
    val root = File(context.filesDir, "dl").apply { mkdirs() }
    private fun f(key: String, n: Int, ext: String) = File(File(root, key).apply { mkdirs() }, name(n, ext))
    override fun exists(key: String, n: Int, ext: String) = f(key, n, ext).exists()
    override fun write(key: String, n: Int, ext: String, block: (OutputStream) -> Unit) {
        val target = f(key, n, ext); val part = File(target.parentFile, target.name + ".part")
        part.outputStream().use(block)
        target.delete(); part.renameTo(target)
    }
    override fun readText(key: String, n: Int, ext: String) = f(key, n, ext).readText()
    override fun open(key: String, n: Int, ext: String): InputStream = f(key, n, ext).inputStream()
    override fun playUri(key: String, n: Int) = f(key, n, "ogg").toURI().toString()
    override fun delete(key: String, n: Int) { f(key, n, "json").delete(); f(key, n, "ogg").delete() }
    override fun bookBytes(key: String) = File(root, key).listFiles()?.sumOf { it.length() } ?: 0L
    override fun totalBytes() = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}

class SafFiles(private val context: Context, treeUri: String) : ChapterFiles {
    private val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri))!!
    private val root: DocumentFile get() = tree.findFile("Unison") ?: tree.createDirectory("Unison")!!
    private fun dir(key: String): DocumentFile = root.findFile(key) ?: root.createDirectory(key)!!
    private fun doc(key: String, n: Int, ext: String): DocumentFile? = root.findFile(key)?.findFile(name(n, ext))
    override fun exists(key: String, n: Int, ext: String) = doc(key, n, ext)?.exists() == true
    override fun write(key: String, n: Int, ext: String, block: (OutputStream) -> Unit) {
        val d = dir(key)
        d.findFile(name(n, ext) + ".part")?.delete()
        val part = d.createFile("application/octet-stream", name(n, ext) + ".part")!!
        context.contentResolver.openOutputStream(part.uri, "wt")!!.use(block)
        d.findFile(name(n, ext))?.delete()
        part.renameTo(name(n, ext))
    }
    override fun readText(key: String, n: Int, ext: String) = open(key, n, ext).bufferedReader().use { it.readText() }
    override fun open(key: String, n: Int, ext: String): InputStream = context.contentResolver.openInputStream(doc(key, n, ext)!!.uri)!!
    override fun playUri(key: String, n: Int) = doc(key, n, "ogg")!!.uri.toString()
    override fun delete(key: String, n: Int) { doc(key, n, "json")?.delete(); doc(key, n, "ogg")?.delete() }
    override fun bookBytes(key: String) = root.findFile(key)?.listFiles()?.sumOf { it.length() } ?: 0L
    override fun totalBytes() = root.listFiles().sumOf { d -> d.listFiles().sumOf { it.length() } }
}

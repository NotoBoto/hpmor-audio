package io.github.notoboto.unison.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.concurrent.thread

/**
 * Streaming for hosts without HTTP Range support (Cloudflare Pages answers every request with the
 * whole file, so each seek would re-download from byte 0). Each http(s) file is downloaded once, in
 * full, into [StreamStore]; reads are served from that file as soon as the bytes have arrived, so
 * playback starts at once and seeking back is instant. Other URIs (downloaded chapters) go to [local].
 */
@UnstableApi
class WholeFileDataSource(private val local: DataSource, private val store: StreamStore) : DataSource {
    private var entry: StreamStore.Entry? = null
    private var raf: RandomAccessFile? = null
    private var uri: Uri? = null
    private var pos = 0L
    private var left = 0L
    private var delegated = false

    override fun addTransferListener(transferListener: TransferListener) = local.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val scheme = dataSpec.uri.scheme
        if (scheme != "http" && scheme != "https") { delegated = true; return local.open(dataSpec) }
        delegated = false
        val e = store.acquire(dataSpec.uri.toString()); entry = e
        val total = e.awaitLength()
        if (total >= 0 && dataSpec.position > total) throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
        pos = dataSpec.position
        left = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            total >= 0 -> total - pos
            else -> C.LENGTH_UNSET.toLong()
        }
        raf = RandomAccessFile(e.file, "r")
        return left
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (delegated) return local.read(buffer, offset, length)
        if (length == 0) return 0
        if (left == 0L) return C.RESULT_END_OF_INPUT
        val avail = entry!!.awaitAfter(pos)
        if (avail <= 0) {
            if (left > 0) throw EOFException("${entry!!.url} ended early")
            return C.RESULT_END_OF_INPUT
        }
        var want = minOf(length.toLong(), avail)
        if (left > 0) want = minOf(want, left)
        val r = raf!!
        r.seek(pos)
        val n = r.read(buffer, offset, want.toInt())
        if (n <= 0) throw EOFException("${entry!!.url}: short read")
        pos += n
        if (left > 0) left -= n
        return n
    }

    override fun getUri(): Uri? = uri
    override fun getResponseHeaders(): Map<String, List<String>> = if (delegated) local.responseHeaders else emptyMap()

    override fun close() {
        if (delegated) { delegated = false; local.close(); return }
        runCatching { raf?.close() }; raf = null
        entry?.let { store.release(it) }; entry = null
    }

    class Factory(private val local: DataSource.Factory, private val store: StreamStore) : DataSource.Factory {
        override fun createDataSource(): DataSource = WholeFileDataSource(local.createDataSource(), store)
    }
}

/** Recently streamed files (kept while in use, plus the last [keep]); emptied on app start. */
class StreamStore(dir: File, private val keep: Int = 4) {
    private val dir = dir.apply { deleteRecursively(); mkdirs() }
    private val http by lazy { OkHttpClient() }
    private val entries = LinkedHashMap<String, Entry>(16, 0.75f, true)

    @Synchronized fun acquire(url: String): Entry {
        entries[url]?.let { if (!it.failed) { it.users++; return it } else { entries.remove(url); it.cancel() } }
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
        val e = Entry(url, File(dir, name))
        e.users++
        entries[url] = e
        val it = entries.values.iterator()
        while (entries.size > keep && it.hasNext()) { val x = it.next(); if (x.users == 0) { x.cancel(); it.remove() } }
        e.start()
        return e
    }

    @Synchronized fun release(e: Entry) { e.users-- }

    inner class Entry(val url: String, val file: File) {
        var users = 0                  // guarded by the store
        @Volatile var failed = false
        private val lock = Object()
        private var length = -2L       // -2: not known yet, -1: server sent no length
        private var have = 0L
        private var done = false
        private var error: IOException? = null
        @Volatile private var call: Call? = null

        fun start() {
            file.delete(); file.createNewFile()          // readers may open it before the first byte arrives
            thread(name = "stream-dl", isDaemon = true) { run() }
        }

        private fun run() {
            try {
                val c = http.newCall(Request.Builder().url(url).build()); call = c
                if (failed) return
                c.execute().use { r ->
                    if (!r.isSuccessful) throw IOException("HTTP ${r.code} $url")
                    val body = r.body ?: throw IOException("empty body $url")
                    FileOutputStream(file).use { out ->
                        synchronized(lock) { length = body.contentLength(); lock.notifyAll() }
                        body.byteStream().use { inp ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val k = inp.read(buf); if (k < 0) break
                                out.write(buf, 0, k)
                                synchronized(lock) { have += k; lock.notifyAll() }
                            }
                        }
                    }
                }
                synchronized(lock) { done = true; if (length < 0) length = have; lock.notifyAll() }
            } catch (e: Exception) {
                synchronized(lock) { failed = true; error = e as? IOException ?: IOException(e); lock.notifyAll() }
            }
        }

        fun cancel() { failed = true; call?.cancel(); file.delete() }

        private inline fun waitWhile(cond: () -> Boolean) {
            while (cond()) {
                try { (lock as Object).wait(500) } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt(); throw InterruptedIOException("interrupted")
                }
            }
        }

        /** Total size (-1 if the server did not say), waiting for the response headers. */
        fun awaitLength(): Long = synchronized(lock) {
            waitWhile { length == -2L && !failed }
            if (length == -2L) throw error ?: IOException("cancelled $url")
            length
        }

        /** Bytes already available from [pos], waiting for at least one; 0 at the end of the file. */
        fun awaitAfter(pos: Long): Long = synchronized(lock) {
            waitWhile { have <= pos && !done && !failed }
            when {
                have > pos -> have - pos
                done -> 0L
                else -> throw error ?: IOException("cancelled $url")
            }
        }
    }
}

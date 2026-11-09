package io.github.notoboto.unison.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.EOFException

/**
 * Streams a chapter whose audio is published as byte pieces (`chNNN.ogg.1`, `.2`, ...; the host caps
 * file size). The URI is `.../chNNN.ogg#parts=<size1>,<size2>,...`; reads and seeks are mapped onto
 * the pieces so the player sees one continuous file. Any other URI goes straight to [upstream].
 */
@UnstableApi
class PartsDataSource(private val upstream: DataSource) : DataSource {
    private var parts: List<Long>? = null
    private var base = ""
    private var uri: Uri? = null
    private var idx = 0
    private var leftInPart = 0L
    private var left = 0L
    private var open = false

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val frag = dataSpec.uri.fragment
        if (frag == null || !frag.startsWith("parts=")) {
            parts = null; open = true
            return upstream.open(dataSpec)
        }
        val p = frag.removePrefix("parts=").split(',').map { it.trim().toLong() }
        parts = p
        base = dataSpec.uri.buildUpon().fragment(null).build().toString()
        val total = p.sum()
        var pos = dataSpec.position.coerceAtMost(total)
        left = if (dataSpec.length != C.LENGTH_UNSET.toLong()) minOf(dataSpec.length, total - pos) else total - pos
        idx = 0
        while (idx < p.size - 1 && pos >= p[idx]) { pos -= p[idx]; idx++ }
        if (left > 0) { openPart(pos); open = true } else leftInPart = 0
        if (left == 0L) idx = p.size - 1   // nothing to read: read() reports end of input
        return left
    }

    private fun openPart(offset: Long) {
        val p = parts!!
        upstream.open(DataSpec.Builder().setUri(Uri.parse(base + "." + (idx + 1))).setPosition(offset)
            .setLength(p[idx] - offset).build())
        leftInPart = p[idx] - offset
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val p = parts ?: return upstream.read(buffer, offset, length)
        if (length == 0) return 0
        if (left == 0L) return C.RESULT_END_OF_INPUT
        while (leftInPart == 0L) {
            upstream.close()
            if (idx + 1 >= p.size) { left = 0; return C.RESULT_END_OF_INPUT }
            idx++; openPart(0)
        }
        val n = upstream.read(buffer, offset, minOf(length.toLong(), leftInPart, left).toInt())
        if (n == C.RESULT_END_OF_INPUT) throw EOFException("piece ${idx + 1} of $base ended early")
        leftInPart -= n; left -= n
        return n
    }

    override fun getUri(): Uri? = uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    override fun close() { if (open) { open = false; upstream.close() } }

    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = PartsDataSource(upstream.createDataSource())
    }
}

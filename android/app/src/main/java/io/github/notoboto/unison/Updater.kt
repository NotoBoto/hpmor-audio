package io.github.notoboto.unison

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File

data class Release(val version: String, val notes: String, val apkUrl: String, val size: Long)

/** Checks GitHub Releases of NotoBoto/unison and installs a newer APK. */
class Updater(private val ctx: Context) {
    val available = MutableStateFlow<Release?>(null)
    val progress = MutableStateFlow<Float?>(null)

    suspend fun check(force: Boolean = false): Release? = withContext(Dispatchers.IO) {
        val app = ctx.app()
        if (!force && System.currentTimeMillis() - app.store.s.settings.lastUpdateCheck < 12 * 3600_000L) return@withContext available.value
        runCatching {
            val text = app.repo.get("https://api.github.com/repos/$REPO/releases/latest")
            val o = io.github.notoboto.unison.data.json.parseToJsonElement(text).jsonObject
            val tag = o["tag_name"]!!.jsonPrimitive.content.removePrefix("v")
            val asset = o["assets"]!!.jsonArray.map { it.jsonObject }.first { it["name"]!!.jsonPrimitive.content.endsWith(".apk") }
            app.store.settings { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            val rel = Release(tag, o["body"]?.jsonPrimitive?.content.orEmpty(), asset["browser_download_url"]!!.jsonPrimitive.content,
                asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0)
            if (newer(tag, BuildConfig.VERSION_NAME)) rel.also { available.value = it } else null
        }.getOrNull()
    }

    private fun newer(a: String, b: String): Boolean {
        val x = a.split('.', '-').mapNotNull { it.toIntOrNull() }; val y = b.split('.', '-').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(x.size, y.size)) { val d = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }; if (d != 0) return d > 0 }
        return false
    }

    /** Download the APK and hand it to the system installer. */
    suspend fun install(rel: Release) {
        val f = withContext(Dispatchers.IO) {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "unison-${rel.version}.apk")
            ctx.app().repo.http.newCall(Request.Builder().url(rel.apkUrl).build()).execute().use { r ->
                val body = r.body!!; val total = body.contentLength().coerceAtLeast(1); var got = 0L
                out.outputStream().use { o -> body.byteStream().use { i ->
                    val buf = ByteArray(64 * 1024)
                    while (true) { val k = i.read(buf); if (k < 0) break; o.write(buf, 0, k); got += k; progress.value = got.toFloat() / total }
                } }
            }
            out
        }
        progress.value = null
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    companion object { const val REPO = "NotoBoto/unison" }
}

package io.github.notoboto.unison.sources

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// Book-source plugins: contract api 1 (SOURCES_DESIGN.md §5, §19). Repository: NotoBoto/unison-sources.

/** The engine plugins are built and tested for; the app installs only plugins with this `engine` (§4). */
const val PLUGIN_ENGINE = "qjs-ng-0.12.1"
const val PLUGIN_API = 1

internal val pluginJson = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

@Serializable
data class RateLimit(val rps: Double, val burst: Int)

@Serializable
data class PluginLogin(
    val url: String,
    val kind: String,                // cookies | localStorage
    val key: String? = null,
    val path: String? = null,        // JSON Pointer inside the localStorage value
    val header: String? = null,      // default "Authorization: Bearer {token}"
    val expiresPath: String? = null,
)

/** One plugin as listed in a signed repository index (§19.3) — also embedded in the bundle as `__unison.manifest`. */
@Serializable
data class PluginInfo(
    val id: String,
    val name: String,
    val version: String,
    val api: Int,
    val engine: String = PLUGIN_ENGINE,
    val core: String = "",
    val lang: String,
    val content: List<String> = listOf("text"),
    val nsfw: Boolean = false,
    val studio: String = "none",
    val browser: String = "never",
    val site: String,
    val domains: List<String>,
    val features: List<String> = emptyList(),
    val rateLimit: RateLimit,
    val login: PluginLogin? = null,
    val file: String = "",
    val icon: String = "",
    val sha256: String = "",
    val size: Long = 0,
)

// ---- what plugin functions return (§5.2) ----

@Serializable data class BookStub(val id: String, val title: String, val cover: String? = null, val author: String? = null, val nsfw: Boolean = false)

@Serializable data class BookPage(val items: List<BookStub>, val hasNext: Boolean = false)

@Serializable data class BookDetails(
    val id: String, val title: String, val author: String? = null, val translator: String? = null, val cover: String? = null,
    val description: String? = null, val tags: List<String> = emptyList(), val status: String = "unknown", val lang: String? = null,
    val nsfw: Boolean = false, val url: String, val license: String? = null,
)

@Serializable data class ChapterStub(val id: String, val title: String, val url: String, val volume: String? = null, val date: String? = null, val locked: Boolean = false)

@Serializable data class ChapterImage(val after: Int, val src: String, val w: Int? = null, val h: Int? = null)

/** `chapter()`: text (already through core) or audio. */
@Serializable data class ChapterContent(
    val kind: String, val title: String = "", val paras: List<String> = emptyList(), val images: List<ChapterImage> = emptyList(),
    val url: String? = null, val duration: Double? = null, val mime: String? = null,
)

/** Error codes of §5.2: thrown by plugins, or raised by the host (TIMEOUT, ENGINE, UNAVAILABLE). */
@Serializable
data class PluginErrorInfo(val code: String, val message: String = "", val url: String? = null)

class PluginException(val info: PluginErrorInfo) : Exception("${info.code}: ${info.message}") {
    constructor(code: String, message: String, url: String? = null) : this(PluginErrorInfo(code, message, url))

    /** Text for the user (§8.2). */
    val userMessage: String get() = when (info.code) {
        "NEEDS_BROWSER" -> "Сайт просит пройти проверку в браузере"
        "NEEDS_LOGIN" -> "Нужно войти на сайт"
        "RATE_LIMITED" -> "Сайт ограничил частоту запросов, попробуйте позже"
        "NOT_FOUND" -> "Не найдено"
        "CHANGED_LAYOUT" -> "Сайт изменился, ждите обновления плагина"
        "TIMEOUT" -> "Источник не отвечает"
        "UNAVAILABLE" -> if (info.message.startsWith("network")) "Нет соединения с сайтом" else "Сайт недоступен"
        else -> "Ошибка плагина: ${info.message.take(200)}"
    }
}

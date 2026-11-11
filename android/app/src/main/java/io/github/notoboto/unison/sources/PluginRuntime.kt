package io.github.notoboto.unison.sources

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Host side of the plugin bridge (SOURCES_DESIGN.md §4, §19.1) on quickjs-kt (quickjs-ng 0.12.1).
 *
 * Before the bundle the host defines two synchronous functions:
 *   __host_request(id, op, argsJson)  — puts a request in [requests] and returns at once;
 *   __host_done(callId, resultJson)   — stores the result of a plugin call.
 * Answers go back with `__host_resolve(id, json)` / `__host_reject(id, json)`; each `evaluate` drains the job queue.
 *
 * Calls are serialized: one plugin instance runs one call at a time (§8.1).
 */
class PluginRuntime private constructor(
    private val qjs: QuickJs,
    private val io: HostIo,
) : Closeable {

    data class Limits(
        val memoryBytes: Long = 64L * 1024 * 1024,
        val stackBytes: Long = 256L * 1024,          // above this the wasm test host crashes; same limit everywhere (§4)
        val jsTimeMillis: Long = 30_000,             // time spent running JS only (waits for the host are not counted)
        val wallTimeMillis: Long = 10 * 60_000,      // whole call including rate-limit waits
    )

    private class Request(val id: Int, val op: String, val argsJson: String)

    private val requests = Channel<Request>(Channel.UNLIMITED)
    private val done = ConcurrentHashMap<Int, String>()
    private val callIds = AtomicInteger()
    private val lock = Mutex()
    private var limits = Limits()

    lateinit var manifest: PluginInfo
        private set
    lateinit var coreVersion: String
        private set

    /**
     * Runs a plugin function. [argsJson] is a JSON array of arguments; returns the JSON result.
     * Throws [PluginException] with the plugin's or the host's error code.
     */
    suspend fun call(fn: String, argsJson: String = "[]"): JsonElement = lock.withLock {
        val callId = callIds.incrementAndGet()
        try {
            withTimeout(limits.wallTimeMillis) {
                eval("__unison.call($callId, ${jsString(fn)}, ${jsString(argsJson)})")
                while (true) {
                    done.remove(callId)?.let { return@withTimeout parseResult(it) }
                    val r = requests.receive()
                    val answer = try { Result.success(io.handle(manifest, r.op, r.argsJson)) }
                        catch (e: PluginException) { Result.failure(e) }
                        catch (e: java.io.IOException) { Result.failure(PluginException("UNAVAILABLE", "network: ${e.message}")) }
                    answer.fold(
                        onSuccess = { eval("__host_resolve(${r.id}, ${jsString(it)})") },
                        onFailure = { eval("__host_reject(${r.id}, ${jsString(pluginJson.encodeToString(PluginErrorInfo.serializer(), (it as PluginException).info))})") },
                    )
                }
                @Suppress("UNREACHABLE_CODE") JsonNull
            }
        } catch (e: TimeoutCancellationException) {
            throw PluginException("TIMEOUT", "${manifest.id}.$fn did not finish in ${limits.wallTimeMillis / 1000} s")
        }
    }

    private fun parseResult(json: String): JsonElement {
        val o = pluginJson.parseToJsonElement(json).jsonObject
        o["err"]?.let { throw PluginException(pluginJson.decodeFromJsonElement(PluginErrorInfo.serializer(), it)) }
        return o["ok"] ?: JsonNull
    }

    private suspend fun eval(code: String) {
        try {
            qjs.evaluate<Any?>(code)
        } catch (e: QuickJsInterruptedException) {
            throw PluginException("TIMEOUT", "JavaScript ran longer than ${limits.jsTimeMillis / 1000} s")
        } catch (e: PluginException) {
            throw e
        } catch (e: Exception) {
            throw PluginException("ENGINE", e.message ?: e.toString())
        }
    }

    override fun close() {
        requests.close()
        qjs.close()
    }

    companion object {
        /** Loads [code] (a plugin bundle) into a fresh engine instance. */
        suspend fun load(
            code: String,
            io: HostIo,
            limits: Limits = Limits(),
            dispatcher: CoroutineDispatcher = Dispatchers.Default,
        ): PluginRuntime {
            val qjs = QuickJs.create(dispatcher)
            qjs.memoryLimit = limits.memoryBytes
            qjs.maxStackSize = limits.stackBytes
            qjs.evaluationTimeoutMillis = limits.jsTimeMillis
            val rt = PluginRuntime(qjs, io)
            rt.limits = limits
            try {
                qjs.function("__host_request") { args ->
                    rt.requests.trySend(Request((args[0] as Number).toInt(), args[1] as String, args[2] as String))
                    null
                }
                qjs.function("__host_done") { args ->
                    rt.done[(args[0] as Number).toInt()] = args[1] as String
                    null
                }
                rt.eval(code)
                val info = rt.evalString("JSON.stringify(__unison.manifest)")
                rt.manifest = pluginJson.decodeFromString(PluginInfo.serializer(), info)
                rt.coreVersion = rt.evalString("__unison.core.version")
            } catch (e: Exception) {
                qjs.close()
                throw e as? PluginException ?: PluginException("ENGINE", "cannot load plugin: ${e.message}")
            }
            return rt
        }

        /** A JSON string literal is a valid JS string literal (ES2019+), so it is safe to splice into code. */
        internal fun jsString(s: String): String = JsonPrimitive(s).toString()
    }

    private suspend fun evalString(code: String): String = qjs.evaluate<String>(code)
}

/** The host side of `fetch`, `storage` and `log` for one plugin. */
interface HostIo {
    suspend fun fetch(plugin: PluginInfo, url: String, method: String, headers: Map<String, String>, body: String?): FetchResult
    suspend fun storageGet(plugin: PluginInfo, key: String): JsonElement
    suspend fun storageSet(plugin: PluginInfo, key: String, value: JsonElement)
    fun log(plugin: PluginInfo, level: String, message: String)
}

data class FetchResult(val status: Int, val headers: Map<String, String>, val text: String, val url: String)

/** Dispatches one bridge request (§19.1); returns the JSON answer or throws [PluginException]. */
internal suspend fun HostIo.handle(plugin: PluginInfo, op: String, argsJson: String): String {
    val args = pluginJson.parseToJsonElement(argsJson).jsonArray
    fun str(i: Int) = args.getOrNull(i)?.jsonPrimitive?.contentOrNull ?: throw PluginException("ENGINE", "$op: argument $i")
    return when (op) {
        "fetch" -> {
            val init = (args.getOrNull(1) as? JsonObject) ?: JsonObject(emptyMap())
            val headers = (init["headers"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
            val r = fetch(plugin, str(0), init["method"]?.jsonPrimitive?.contentOrNull ?: "GET", headers, init["body"]?.jsonPrimitive?.contentOrNull)
            JsonObject(mapOf(
                "status" to JsonPrimitive(r.status),
                "headers" to JsonObject(r.headers.mapValues { JsonPrimitive(it.value) }),
                "text" to JsonPrimitive(r.text),
                "url" to JsonPrimitive(r.url),
            )).toString()
        }
        "storage.get" -> storageGet(plugin, str(0)).toString()
        "storage.set" -> { storageSet(plugin, str(0), args.getOrNull(1) ?: JsonNull); "null" }
        "log" -> { log(plugin, str(0), args.getOrNull(1)?.jsonPrimitive?.content ?: ""); "null" }
        else -> throw PluginException("ENGINE", "unknown op $op")
    }
}

package io.github.notoboto.unison.sources

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

/**
 * Live plugin instances (SOURCES_DESIGN.md §8.1): one quickjs-ng instance per plugin, at most [maxLive] at a time
 * (the least recently used is closed when a new one is needed), and an instance idle for [idleMillis] is closed.
 * So seven plugins in a global search never take 7 × 64 MB.
 */
class PluginPool(
    private val loader: suspend (pluginId: String) -> PluginRuntime,
    private val maxLive: Int = 4,
    private val idleMillis: Long = 5 * 60_000,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private class Entry(val runtime: PluginRuntime) {
        var lastUsed = System.nanoTime()
        var busy = 0
        var idleJob: Job? = null
    }

    private val lock = Mutex()
    private val live = LinkedHashMap<String, Entry>()   // insertion order is refreshed on use: first = least recently used

    /** Calls [fn] of plugin [pluginId]; loads the plugin if needed. */
    suspend fun call(pluginId: String, fn: String, argsJson: String = "[]"): JsonElement {
        val e = acquire(pluginId)
        try {
            return e.runtime.call(fn, argsJson)
        } finally {
            release(pluginId, e)
        }
    }

    /** Manifest embedded in the bundle (after loading it). */
    suspend fun manifest(pluginId: String): PluginInfo {
        val e = acquire(pluginId)
        try { return e.runtime.manifest } finally { release(pluginId, e) }
    }

    /** Closes the instance of [pluginId] (after an update or uninstall). */
    suspend fun unload(pluginId: String) = lock.withLock {
        live.remove(pluginId)?.let { it.idleJob?.cancel(); if (it.busy == 0) it.runtime.close() }
    }

    suspend fun unloadAll() = lock.withLock {
        live.values.forEach { it.idleJob?.cancel(); if (it.busy == 0) it.runtime.close() }
        live.clear()
    }

    suspend fun liveIds(): List<String> = lock.withLock { live.keys.toList() }

    private suspend fun acquire(id: String): Entry {
        lock.withLock {
            live.remove(id)?.let { e ->               // move to the end: most recently used
                live[id] = e
                e.busy++; e.lastUsed = System.nanoTime(); e.idleJob?.cancel()
                return e
            }
        }
        val rt = loader(id)                           // outside the lock: loading may take a while
        return lock.withLock {
            live[id]?.let { other -> rt.close(); other.busy++; other.idleJob?.cancel(); return@withLock other }
            while (live.size >= maxLive) {
                val victim = live.entries.firstOrNull { it.value.busy == 0 } ?: break
                live.remove(victim.key)
                victim.value.idleJob?.cancel()
                victim.value.runtime.close()
            }
            Entry(rt).also { it.busy = 1; live[id] = it }
        }
    }

    private suspend fun release(id: String, e: Entry) = lock.withLock {
        e.busy--
        e.lastUsed = System.nanoTime()
        if (live[id] !== e) { if (e.busy == 0) e.runtime.close(); return@withLock }
        if (e.busy == 0) e.idleJob = scope.launch {
            delay(idleMillis)
            lock.withLock { if (live[id] === e && e.busy == 0) { live.remove(id); e.runtime.close() } }
        }
    }
}

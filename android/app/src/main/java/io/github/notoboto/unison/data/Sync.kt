package io.github.notoboto.unison.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToLong

/** Progress file shared with the website (see SYNC_FORMAT.md): export and merge-import. */
object Sync {
    data class Result(val books: Int, val chapters: Int, val bookmarks: Int)

    private fun keyMap(store: Store, repo: Repo): Map<String, Pair<String, String>> {
        val m = mutableMapOf<String, Pair<String, String>>()
        for (src in store.s.sources) for (b in repo.catalogs.value[src.url].orEmpty()) m[bookKey(src.url, b.id)] = src.url to b.id
        store.s.library.forEach { m[bookKey(it.sourceUrl, it.bookId)] = it.sourceUrl to it.bookId }
        return m
    }

    fun export(store: Store, repo: Repo): String {
        val s = store.s
        val keys = keyMap(store, repo)
        // only books of web sources: imported (local://) and plugin (src://) books have device-specific ids
        // until sync v2 (SOURCES_DESIGN.md §11, decision 22)
        val ext = { k: String -> keys[k]?.takeIf { isWebSource(it.first) }?.let { "${it.first}|${it.second}" } }
        val catName = s.categories.associate { it.id to it.name }
        val obj = buildJsonObject {
            put("unison", 1); put("exported", System.currentTimeMillis()); put("device", "Android")
            put("categories", buildJsonArray { s.categories.forEach { add(JsonPrimitive(it.name)) } })
            put("books", buildJsonObject {
                val all = (s.progress.keys + s.library.map { bookKey(it.sourceUrl, it.bookId) } + s.bookmarks.keys + s.last.keys).toSet()
                for (k in all) {
                    val ek = ext(k) ?: continue
                    put(ek, buildJsonObject {
                        val lib = s.library.find { bookKey(it.sourceUrl, it.bookId) == k }
                        put("inLibrary", lib != null); put("added", lib?.added ?: 0)
                        put("categories", buildJsonArray { s.bookCategories[k].orEmpty().mapNotNull { catName[it] }.forEach { add(JsonPrimitive(it)) } })
                        s.last[k]?.let { n -> put("last", buildJsonObject { put("n", n); put("ts", s.lastTs[k] ?: 0) }) }
                        put("progress", buildJsonObject {
                            s.progress[k].orEmpty().forEach { (n, p) -> put("$n", buildJsonObject { put("pos", p.pos); put("done", p.done); put("ts", p.ts) }) }
                        })
                        put("bookmarks", buildJsonArray {
                            s.bookmarks[k].orEmpty().forEach { b -> add(buildJsonObject { put("n", b.n); put("pos", b.pos); put("quote", b.quote); put("note", b.note); put("ts", b.ts) }) }
                        })
                        put("listenedMs", s.statsBooks[k] ?: 0)
                    })
                }
            })
            put("history", buildJsonArray { s.history.forEach { h -> ext(h.book)?.let { add(buildJsonObject { put("book", it); put("n", h.n); put("ts", h.ts) }) } } })
            put("statsDays", buildJsonObject { s.statsDays.forEach { (d, ms) -> put(d, ms) } })
        }
        return json.encodeToString(JsonObject.serializer(), obj)
    }

    fun import(store: Store, text: String): Result {
        val o = json.parseToJsonElement(text).jsonObject
        require(o["unison"] != null) { "not a Unison progress file" }
        var nb = 0; var nc = 0; var nbm = 0
        store.update { s0 ->
            var s = s0
            // global categories: union by name
            val names = o["categories"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            var cats = s.categories
            names.filter { n -> cats.none { it.name == n } }.forEach { n -> cats = cats + Category((cats.maxOfOrNull { it.id } ?: 0) + 1, n) }
            s = s.copy(categories = cats)
            val catId = cats.associate { it.name to it.id }

            val books = o["books"]?.jsonObject.orEmpty()
            for ((ek, v) in books) {
                val bo = v.jsonObject
                val src = ek.substringBeforeLast('|'); val id = ek.substringAfterLast('|')
                if (!isWebSource(src)) continue          // local:// and src:// books are not synced yet (decision 22)
                val k = bookKey(src, id); nb++
                if (s.sources.none { it.url == src }) s = s.copy(sources = s.sources + Source(src, src.removePrefix("https://").substringBefore('/')))
                if (bo["inLibrary"]?.jsonPrimitive?.booleanOrNull == true && s.library.none { bookKey(it.sourceUrl, it.bookId) == k })
                    s = s.copy(library = s.library + LibEntry(src, id, bo["added"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: System.currentTimeMillis()))
                bo["categories"]?.jsonArray?.mapNotNull { catId[it.jsonPrimitive.content] }?.let { ids ->
                    if (ids.isNotEmpty()) s = s.copy(bookCategories = s.bookCategories + (k to (s.bookCategories[k].orEmpty() + ids).distinct()))
                }
                bo["last"]?.jsonObject?.let { l ->
                    val ts = l["ts"]?.jsonPrimitive?.longOrNull ?: 0
                    if (ts >= (s.lastTs[k] ?: 0)) l["n"]?.jsonPrimitive?.intOrNull?.let { n -> s = s.copy(last = s.last + (k to n), lastTs = s.lastTs + (k to ts)) }
                }
                val prog = s.progress[k].orEmpty().toMutableMap()
                bo["progress"]?.jsonObject?.forEach { (ns, pv) ->
                    val n = ns.toIntOrNull() ?: return@forEach
                    val p = pv.jsonObject
                    val inc = Progress(p["pos"]?.jsonPrimitive?.doubleOrNull ?: 0.0, p["done"]?.jsonPrimitive?.booleanOrNull ?: false, p["ts"]?.jsonPrimitive?.longOrNull ?: 0)
                    val cur = prog[n]
                    if (cur == null || inc.ts > cur.ts || (inc.ts == cur.ts && inc.done && !cur.done)) { prog[n] = inc; nc++ }
                }
                s = s.copy(progress = s.progress + (k to prog))
                val marks = s.bookmarks[k].orEmpty().toMutableList()
                bo["bookmarks"]?.jsonArray?.forEach { bv ->
                    val b = bv.jsonObject
                    val bm = Bookmark(b["n"]!!.jsonPrimitive.intOrNull ?: 0, b["pos"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        b["quote"]?.jsonPrimitive?.content.orEmpty(), b["note"]?.jsonPrimitive?.content.orEmpty(), b["ts"]?.jsonPrimitive?.longOrNull ?: 0)
                    val dup = marks.indexOfFirst { it.n == bm.n && it.pos.roundToLong() == bm.pos.roundToLong() && it.quote == bm.quote }
                    if (dup < 0) { marks += bm; nbm++ } else if (marks[dup].note.isBlank() && bm.note.isNotBlank()) marks[dup] = bm
                }
                s = s.copy(bookmarks = s.bookmarks + (k to marks.sortedWith(compareBy({ it.n }, { it.pos }))))
                bo["listenedMs"]?.jsonPrimitive?.longOrNull?.let { ms -> s = s.copy(statsBooks = s.statsBooks + (k to maxOf(ms, s.statsBooks[k] ?: 0))) }
            }
            val hist = s.history.associateBy { it.book to it.n }.toMutableMap()
            o["history"]?.jsonArray?.forEach { hv ->
                val h = hv.jsonObject; val ek = h["book"]?.jsonPrimitive?.content ?: return@forEach
                if (!isWebSource(ek.substringBeforeLast('|'))) return@forEach
                val k = bookKey(ek.substringBeforeLast('|'), ek.substringAfterLast('|'))
                val n = h["n"]?.jsonPrimitive?.intOrNull ?: return@forEach; val ts = h["ts"]?.jsonPrimitive?.longOrNull ?: 0
                if ((hist[k to n]?.ts ?: -1) < ts) hist[k to n] = HistEntry(k, n, ts)
            }
            s = s.copy(history = hist.values.sortedByDescending { it.ts }.take(300))
            o["statsDays"]?.jsonObject?.forEach { (d, v) -> v.jsonPrimitive.longOrNull?.let { ms -> s = s.copy(statsDays = s.statsDays + (d to maxOf(ms, s.statsDays[d] ?: 0))) } }
            s
        }
        store.flush()
        return Result(nb, nc, nbm)
    }
}

private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())

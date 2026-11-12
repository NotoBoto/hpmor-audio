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

    private val TOP_KEYS = setOf("unison", "exported", "device", "categories", "books", "history", "statsDays")
    private val ENTRY_KEYS = setOf("inLibrary", "added", "categories", "last", "progress", "bookmarks", "listenedMs")
    private fun JsonObject.extra(known: Set<String>) = JsonObject(filterKeys { it !in known })
    private fun JsonObject.obj(k: String) = this[k] as? JsonObject ?: JsonObject(emptyMap())

    private fun keyMap(store: Store, repo: Repo): Map<String, Pair<String, String>> {
        val m = mutableMapOf<String, Pair<String, String>>()
        for (src in store.s.sources) for (b in repo.catalogs.value[src.url].orEmpty()) m[bookKey(src.url, b.id)] = src.url to b.id
        store.s.library.forEach { m[bookKey(it.sourceUrl, it.bookId)] = it.sourceUrl to it.bookId }
        return m
    }

    /** Result of an import; [missing]: plugin books (pluginId, book id of the plugin) that are not on this phone yet. */
    data class ImportResult(val result: Result, val missing: List<Pair<String, String>>)

    /**
     * [ids] gives plugin books their place in the file (sync v2, §11): key "src://<plugin>/|<book id of the plugin>",
     * plus "chapterIds" {n: chapterId} for the chapters the entry refers to. Without it (or for imported local://
     * books) such books stay out of the file, as in v1 (decision 22).
     */
    fun export(store: Store, repo: Repo, ids: PluginBookIds? = null): String {
        val s = store.s
        val keys = keyMap(store, repo)
        val ext = { k: String ->
            keys[k]?.let { (src, id) ->
                when {
                    isWebSource(src) -> "$src|$id"
                    ids != null && isPluginSource(src) -> ids.realBookId(pluginIdOf(src), id)?.let { "$src|$it" }
                    else -> null
                }
            }
        }
        val catName = s.categories.associate { it.id to it.name }
        val extra = s.syncExtra
        var v2 = false
        val obj = buildJsonObject {
            extra.obj("top").forEach { (k, v) -> put(k, v) }
            put("unison", 1); put("exported", System.currentTimeMillis()); put("device", "Android")
            put("categories", buildJsonArray { s.categories.forEach { add(JsonPrimitive(it.name)) } })
            put("books", buildJsonObject {
                extra.obj("foreign").forEach { (ek, v) -> put(ek, v) }   // books of sources this phone does not have
                val all = (s.progress.keys + s.library.map { bookKey(it.sourceUrl, it.bookId) } + s.bookmarks.keys + s.last.keys).toSet()
                for (k in all) {
                    val ek = ext(k) ?: continue
                    val (src, id) = keys[k]!!
                    put(ek, buildJsonObject {
                        extra.obj("books").obj(ek).forEach { (f, v) -> put(f, v) }
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
                        if (ids != null && isPluginSource(src)) {
                            v2 = true
                            val used = s.progress[k].orEmpty().keys + listOfNotNull(s.last[k]) + s.bookmarks[k].orEmpty().map { it.n } +
                                s.history.filter { it.book == k }.map { it.n }
                            val map = ids.chapterIds(pluginIdOf(src), id)
                            put("chapterIds", buildJsonObject { used.toSet().sorted().forEach { n -> map[n]?.let { put("$n", it) } } })
                        }
                    })
                }
            })
            put("history", buildJsonArray {
                s.history.forEach { h -> ext(h.book)?.let { add(buildJsonObject { put("book", it); put("n", h.n); put("ts", h.ts) }) } }
                (extra["history"] as? JsonArray)?.forEach { add(it) }
            })
            put("statsDays", buildJsonObject { s.statsDays.forEach { (d, ms) -> put(d, ms) } })
        }
        // v2 only when the file carries plugin books: v1 readers (the website before §11) still accept everything else
        val out = if (v2) JsonObject(obj + ("unison" to JsonPrimitive(2))) else obj
        return json.encodeToString(JsonObject.serializer(), out)
    }

    fun import(store: Store, text: String): Result = importV2(store, text, null).result

    fun importV2(store: Store, text: String, ids: PluginBookIds?): ImportResult {
        val o = json.parseToJsonElement(text).jsonObject
        require(o["unison"] != null) { "not a Unison progress file" }
        var nb = 0; var nc = 0; var nbm = 0
        val missing = mutableListOf<Pair<String, String>>()
        store.update { s0 ->
            var s = s0
            // global categories: union by name
            val names = o["categories"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            var cats = s.categories
            names.filter { n -> cats.none { it.name == n } }.forEach { n -> cats = cats + Category((cats.maxOfOrNull { it.id } ?: 0) + 1, n) }
            s = s.copy(categories = cats)
            val catId = cats.associate { it.name to it.id }

            val ex = s.syncExtra
            val exTop = ex.obj("top").toMutableMap().apply { putAll(o.extra(TOP_KEYS)) }
            val exBooks = ex.obj("books").toMutableMap()
            val exForeign = ex.obj("foreign").toMutableMap()
            val books = o["books"]?.jsonObject.orEmpty()

            /** Where a book of the file lives here: (book key, library id, file n → local n), or null if not here. */
            fun place(ek: String): Triple<String, String, (Int) -> Int?>? {
                val src = ek.substringBeforeLast('|'); val id = ek.substringAfterLast('|')
                if (isWebSource(src)) return Triple(bookKey(src, id), id) { n: Int -> n }
                if (ids == null || !isPluginSource(src)) return null
                val plugin = pluginIdOf(src); val local = ids.internalId(id)
                if (!ids.has(plugin, local)) return null
                val chIds = (books[ek] as? JsonObject)?.obj("chapterIds") ?: JsonObject(emptyMap())
                return Triple(bookKey(src, local), local) { n: Int -> chIds["$n"]?.jsonPrimitive?.content?.let { ids.localN(plugin, local, it) } }
            }

            for ((ek, v) in books) {
                val bo = v.jsonObject
                val src = ek.substringBeforeLast('|')
                val here = place(ek)
                if (here == null) {
                    // not on this phone (yet): kept whole and written back unchanged
                    exForeign[ek] = bo
                    if (ids != null && isPluginSource(src) && bo["inLibrary"]?.jsonPrimitive?.booleanOrNull == true)
                        missing += pluginIdOf(src) to ek.substringAfterLast('|')
                    continue
                }
                exForeign.remove(ek)
                val (k, libId, mapN) = here
                bo.extra(if (isPluginSource(src)) ENTRY_KEYS + "chapterIds" else ENTRY_KEYS).takeIf { it.isNotEmpty() }?.let { exBooks[ek] = JsonObject(exBooks[ek]?.let { o -> (o as JsonObject) + it } ?: it) }
                nb++
                if (isWebSource(src) && s.sources.none { it.url == src }) s = s.copy(sources = s.sources + Source(src, src.removePrefix("https://").substringBefore('/')))
                if (bo["inLibrary"]?.jsonPrimitive?.booleanOrNull == true && s.library.none { bookKey(it.sourceUrl, it.bookId) == k })
                    s = s.copy(library = s.library + LibEntry(src, libId, bo["added"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0 } ?: System.currentTimeMillis()))
                bo["categories"]?.jsonArray?.mapNotNull { catId[it.jsonPrimitive.content] }?.let { cids ->
                    if (cids.isNotEmpty()) s = s.copy(bookCategories = s.bookCategories + (k to (s.bookCategories[k].orEmpty() + cids).distinct()))
                }
                bo["last"]?.jsonObject?.let { l ->
                    val ts = l["ts"]?.jsonPrimitive?.longOrNull ?: 0
                    if (ts >= (s.lastTs[k] ?: 0)) l["n"]?.jsonPrimitive?.intOrNull?.let(mapN)?.let { n -> s = s.copy(last = s.last + (k to n), lastTs = s.lastTs + (k to ts)) }
                }
                val prog = s.progress[k].orEmpty().toMutableMap()
                bo["progress"]?.jsonObject?.forEach { (ns, pv) ->
                    val n = ns.toIntOrNull()?.let(mapN) ?: return@forEach
                    val p = pv.jsonObject
                    val inc = Progress(p["pos"]?.jsonPrimitive?.doubleOrNull ?: 0.0, p["done"]?.jsonPrimitive?.booleanOrNull ?: false, p["ts"]?.jsonPrimitive?.longOrNull ?: 0)
                    val cur = prog[n]
                    if (cur == null || inc.ts > cur.ts || (inc.ts == cur.ts && inc.done && !cur.done)) { prog[n] = inc; nc++ }
                }
                s = s.copy(progress = s.progress + (k to prog))
                val marks = s.bookmarks[k].orEmpty().toMutableList()
                bo["bookmarks"]?.jsonArray?.forEach { bv ->
                    val b = bv.jsonObject
                    val n = (b["n"]?.jsonPrimitive?.intOrNull ?: 0).let(mapN) ?: return@forEach
                    val bm = Bookmark(n, b["pos"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                        b["quote"]?.jsonPrimitive?.content.orEmpty(), b["note"]?.jsonPrimitive?.content.orEmpty(), b["ts"]?.jsonPrimitive?.longOrNull ?: 0)
                    val dup = marks.indexOfFirst { it.n == bm.n && it.pos.roundToLong() == bm.pos.roundToLong() && it.quote == bm.quote }
                    if (dup < 0) { marks += bm; nbm++ } else if (marks[dup].note.isBlank() && bm.note.isNotBlank()) marks[dup] = bm
                }
                s = s.copy(bookmarks = s.bookmarks + (k to marks.sortedWith(compareBy({ it.n }, { it.pos }))))
                bo["listenedMs"]?.jsonPrimitive?.longOrNull?.let { ms -> s = s.copy(statsBooks = s.statsBooks + (k to maxOf(ms, s.statsBooks[k] ?: 0))) }
            }
            val hist = s.history.associateBy { it.book to it.n }.toMutableMap()
            val foreignHist = (ex["history"] as? JsonArray).orEmpty().associateBy { it.jsonObject["book"]?.jsonPrimitive?.content + "#" + it.jsonObject["n"]?.jsonPrimitive?.intOrNull }.toMutableMap()
            o["history"]?.jsonArray?.forEach { hv ->
                val h = hv.jsonObject; val ek = h["book"]?.jsonPrimitive?.content ?: return@forEach
                val fileN = h["n"]?.jsonPrimitive?.intOrNull ?: return@forEach
                val here = place(ek)
                if (here == null) { foreignHist["$ek#$fileN"] = h; return@forEach }
                foreignHist.remove("$ek#$fileN")
                val k = here.first; val n = here.third(fileN) ?: return@forEach
                val ts = h["ts"]?.jsonPrimitive?.longOrNull ?: 0
                if ((hist[k to n]?.ts ?: -1) < ts) hist[k to n] = HistEntry(k, n, ts)
            }
            s = s.copy(history = hist.values.sortedByDescending { it.ts }.take(300))
            s = s.copy(syncExtra = JsonObject(mapOf(
                "top" to JsonObject(exTop), "books" to JsonObject(exBooks), "foreign" to JsonObject(exForeign),
                "history" to JsonArray(foreignHist.values.sortedByDescending { it.jsonObject["ts"]?.jsonPrimitive?.longOrNull ?: 0 }.take(300)))))
            o["statsDays"]?.jsonObject?.forEach { (d, v) -> v.jsonPrimitive.longOrNull?.let { ms -> s = s.copy(statsDays = s.statsDays + (d to maxOf(ms, s.statsDays[d] ?: 0))) } }
            s
        }
        store.flush()
        return ImportResult(Result(nb, nc, nbm), missing.distinct())
    }
}

/** Plugin books for sync v2 (§11); implemented by sources.SrcBooks, kept here so data does not depend on sources. */
interface PluginBookIds {
    /** The plugin's own book id of a book stored under [internalId], or null when it is not on the phone. */
    fun realBookId(pluginId: String, internalId: String): String?
    fun internalId(realBookId: String): String
    fun has(pluginId: String, internalId: String): Boolean
    /** n → chapterId of the book's chapter table. */
    fun chapterIds(pluginId: String, internalId: String): Map<Int, String>
    /** The local n of [chapterId]; an id not in the table yet gets the next free n, state "unlisted" (§11). */
    fun localN(pluginId: String, internalId: String, chapterId: String): Int?
}

private fun JsonArray?.orEmpty() = this ?: JsonArray(emptyList())

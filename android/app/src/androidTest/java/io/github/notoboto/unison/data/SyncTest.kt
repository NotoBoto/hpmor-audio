package io.github.notoboto.unison.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.notoboto.unison.app
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Sync files: v2 is accepted, unknown fields and books of not-synced sources survive a round trip (SYNC_FORMAT.md, §11). */
@RunWith(AndroidJUnit4::class)
class SyncTest {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.app()

    @Test fun pluginBookProgressFollowsChapterIds() = kotlinx.coroutines.runBlocking<Unit> {
        val saved = app.store.s
        val books = app.sources.books
        val plugin = "sync-test"
        val src = pluginSourceUrl(plugin)
        fun ch(id: String) = io.github.notoboto.unison.sources.ChapterStub(id, id.uppercase(), "https://example.org/$id")
        val d = io.github.notoboto.unison.sources.BookDetails(id = "Книга/1", title = "Книга", url = "https://example.org/b")
        try {
            // device A: chapters a, b, c → n 1, 2, 3; listening to "b"
            TextBooks.root(app, src).deleteRecursively()
            val id = books.add(plugin, d, listOf(ch("a"), ch("b"), ch("c")))
            val k = bookKey(src, id)
            app.store.update { it.copy(library = it.library + LibEntry(src, id), progress = it.progress + (k to mapOf(2 to Progress(42.0, false, 1000))),
                last = it.last + (k to 2), lastTs = it.lastTs + (k to 1000), bookmarks = it.bookmarks + (k to listOf(Bookmark(2, 40.0, "цитата")))) }
            val file = Sync.export(app.store, app.repo, books)
            val o = json.parseToJsonElement(file).jsonObject
            assertEquals(2, o["unison"]!!.jsonPrimitive.content.toInt())
            val entry = o["books"]!!.jsonObject["$src|Книга/1"]!!.jsonObject
            assertEquals("b", entry["chapterIds"]!!.jsonObject["2"]!!.jsonPrimitive.content)

            // device B: the same book, but chapter "x" came first → a, b, c are 2, 3, 4 there
            TextBooks.root(app, src).deleteRecursively()
            app.store.update { saved }
            books.add(plugin, d, listOf(ch("x"), ch("a"), ch("b"), ch("c")))
            val r = Sync.importV2(app.store, file, books)
            assertFalse("our book is here", r.missing.any { it.first == plugin })
            assertEquals("progress of \"b\" lands on its local n", 42.0, app.store.s.progress[k]!![3]!!.pos, 0.0)
            assertEquals(3, app.store.s.last[k])
            assertEquals(3, app.store.s.bookmarks[k]!!.single().n)
            assertTrue(app.store.s.library.any { it.sourceUrl == src && it.bookId == id })
            assertFalse("no source entry for src://", app.store.s.sources.any { it.url == src })

            // a book that is not on this phone stays in the file and is reported as missing
            TextBooks.root(app, src).deleteRecursively()
            app.store.update { saved }
            val r2 = Sync.importV2(app.store, file, books)
            assertTrue(r2.missing.contains(plugin to "Книга/1"))
            assertTrue("kept for the next export", "$src|Книга/1" in json.parseToJsonElement(Sync.export(app.store, app.repo, books)).jsonObject["books"]!!.jsonObject)
        } finally {
            TextBooks.root(app, src).deleteRecursively()
            app.store.update { saved }
            app.store.flush()   // the test must not leave its data in the app state on disk
        }
    }

    @Test fun roundTripKeepsUnknownParts() {
        val saved = app.store.s
        try {
            val web = DEFAULT_SOURCE.url + "|hpmor"
            val src = "src://wikisource-ru/|Мёртвые души (Гоголь)"
            val file = """{"unison":2,"exported":1,"device":"Web","future":{"x":1},"categories":[],
              "books":{
                "$web":{"inLibrary":true,"added":5,"progress":{"3":{"pos":12.5,"done":false,"ts":100}},"bookmarks":[],"chapterIds":{"3":"c3"},"newField":"keep"},
                "$src":{"inLibrary":true,"progress":{"1":{"pos":3,"done":false,"ts":50}},"bookmarks":[],"chapterIds":{"1":"x/К читателю"}},
                "local://|b123":{"inLibrary":true,"progress":{},"bookmarks":[]}
              },
              "history":[{"book":"$src","n":1,"ts":50},{"book":"$web","n":3,"ts":100}],"statsDays":{}}"""
            Sync.import(app.store, file)
            assertFalse("no fake sources", app.store.s.sources.any { !isWebSource(it.url) })

            val out = json.parseToJsonElement(Sync.export(app.store, app.repo)).jsonObject
            assertEquals(JsonPrimitive(1), out["future"]!!.jsonObject["x"])
            val books = out["books"]!!.jsonObject
            assertEquals("c3", books[web]!!.jsonObject["chapterIds"]!!.jsonObject["3"]!!.jsonPrimitive.content)
            assertEquals("keep", books[web]!!.jsonObject["newField"]!!.jsonPrimitive.content)
            assertEquals(12.5, books[web]!!.jsonObject["progress"]!!.jsonObject["3"]!!.jsonObject["pos"]!!.jsonPrimitive.content.toDouble(), 0.0)
            assertEquals("x/К читателю", books[src]!!.jsonObject["chapterIds"]!!.jsonObject["1"]!!.jsonPrimitive.content)
            assertTrue("local book kept as foreign", "local://|b123" in books)
            assertTrue("foreign history kept", out["history"]!!.jsonArray.any { it.jsonObject["book"]!!.jsonPrimitive.content == src })
        } finally {
            app.store.update { saved }
            app.store.flush()   // the test must not leave its data in the app state on disk
        }
    }
}

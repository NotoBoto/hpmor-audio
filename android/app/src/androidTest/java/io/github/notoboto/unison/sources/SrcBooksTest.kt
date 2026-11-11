package io.github.notoboto.unison.sources

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.notoboto.unison.data.TextBooks
import io.github.notoboto.unison.data.pluginSourceUrl
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The chapterId → n table of plugin books (SOURCES_DESIGN.md §8.3, §19.5). */
@RunWith(AndroidJUnit4::class)
class SrcBooksTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val books = SrcBooks(ctx, Sources(ctx, OkHttpClient()))
    private val plugin = "test-plugin"
    private fun ch(id: String) = ChapterStub(id, "Глава $id", "https://example.org/$id")
    private val details = BookDetails(id = "Книга / с пробелами", title = "Книга", url = "https://example.org/book")

    @After fun clean() { TextBooks.root(ctx, pluginSourceUrl(plugin)).deleteRecursively() }

    @Test fun chaptersKeepTheirNumbers() = runBlocking<Unit> {
        val id = books.add(plugin, details, listOf(ch("a"), ch("b"), ch("c")))
        assertEquals("s" + id.drop(1), books.internalId(details.id))
        var t = books.table(plugin, id)!!.chapters.associateBy { it.id }
        assertEquals(listOf(1, 2, 3), listOf("a", "b", "c").map { t[it]!!.n })

        // the site inserted "x" before "c" and removed "b"
        books.add(plugin, details, listOf(ch("a"), ch("x"), ch("c")))
        t = books.table(plugin, id)!!.chapters.associateBy { it.id }
        assertEquals(1, t["a"]!!.n); assertEquals(3, t["c"]!!.n)
        assertEquals("new chapter gets the next free n", 4, t["x"]!!.n)
        assertEquals("reading order comes from chapters()", listOf(0, 1, 2), listOf("a", "x", "c").map { t[it]!!.order })
        assertEquals("removed", t["b"]!!.state)

        // index.json: reading order, the removed chapter is gone (no text on the phone)
        val idx = TextBooks.chapters(ctx, pluginSourceUrl(plugin), id)
        assertEquals(listOf(1, 4, 3), idx.map { it.n })
        assertNull(idx.find { it.n == 2 })

        // «Обновления» list chapters by date: a chapter found later has one, the first ones do not
        assertNull(t["a"]!!.date)
        assertTrue("new chapter is dated", t["x"]!!.date != null && idx.find { it.n == 4 }!!.date != null)

        // «next chapter» follows reading order, not n (stage 3): after "a" (n 1) comes "x" (n 4), then "c" (n 3)
        val book = io.github.notoboto.unison.data.Book(io.github.notoboto.unison.data.Source(pluginSourceUrl(plugin), "t"),
            io.github.notoboto.unison.data.BookMeta(id = id, title = "t"), idx)
        assertEquals(listOf(4, 3), book.chaptersAfter(1).map { it.n })
        assertEquals(1, book.chapterBefore(4)!!.n)
        assertEquals(listOf(4, 3), book.chaptersFrom(4).map { it.n })
    }

    @Test fun chapterHashMatchesCore() {
        // the same value as core.chapterHash in unison-sources (tests/unit.mjs)
        assertEquals("first 16 hex of SHA-1", 16, SrcBooks.chapterHash("Глава 1", listOf("Первый абзац.", "— Второй, — сказал он.")).length)
        assertEquals(java.security.MessageDigest.getInstance("SHA-1").digest("Глава 1\nПервый абзац.\n— Второй, — сказал он.".toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16), SrcBooks.chapterHash("Глава 1", listOf("Первый абзац.", "— Второй, — сказал он.")))
    }
}

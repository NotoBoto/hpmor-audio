package io.github.notoboto.unison.sources

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.notoboto.unison.data.TextBooks
import io.github.notoboto.unison.data.pluginSourceUrl
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Book sources in the backup (SOURCES_DESIGN.md §13): export, wipe, restore. */
@RunWith(AndroidJUnit4::class)
class BackupTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun exportWipeRestore() = runBlocking<Unit> {
        val s = Sources(ctx, OkHttpClient())
        File(ctx.filesDir, "srcbooks").deleteRecursively(); File(ctx.filesDir, "sources").deleteRecursively()
        s.repos.ensureBuiltIns()
        val id = s.books.add("wikisource-ru", BookDetails(id = "Тест (Автор)", title = "Тест", url = "https://ru.wikisource.org/wiki/x"),
            listOf(ChapterStub("Тест (Автор)/1", "Первая", "https://ru.wikisource.org/wiki/x/1")))
        s.io.storageSet(s.repos.installed()["wikisource-ru"]!!.info, "seen", JsonPrimitive("yes"))

        val section = SourcesBackup.export(ctx, s)
        val text = section.toString()
        assertFalse("no secrets", Regex("token|cookie|password", RegexOption.IGNORE_CASE).containsMatchIn(text))
        assertEquals(1, section["books"]!!.jsonArray.size)
        assertTrue(section["plugins"]!!.jsonArray.any { it.jsonObject["id"] == JsonPrimitive("wikisource-ru") })

        // a new phone
        File(ctx.filesDir, "srcbooks").deleteRecursively(); File(ctx.filesDir, "sources").deleteRecursively()
        val fresh = Sources(ctx, OkHttpClient())
        val problems = SourcesBackup.restore(ctx, fresh, section)
        assertTrue("problems: $problems", problems.isEmpty())

        assertTrue("plugin is back", "wikisource-ru" in fresh.repos.installed())
        assertTrue("its code is verified", fresh.repos.code("wikisource-ru").isNotEmpty())
        assertEquals("storage is back", JsonPrimitive("yes"), fresh.io.storageGet(fresh.repos.installed()["wikisource-ru"]!!.info, "seen"))
        val src = pluginSourceUrl("wikisource-ru")
        assertEquals("book is back", "Тест", TextBooks.catalog(ctx, src).single().title)
        assertEquals("chapter table is back", 1, fresh.books.table("wikisource-ru", id)!!.chapters.single().n)
        assertEquals("Тест (Автор)", fresh.books.origin("wikisource-ru", id)!!.book)

        File(ctx.filesDir, "srcbooks").deleteRecursively()
    }
}

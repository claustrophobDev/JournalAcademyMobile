package com.claustrophob.journal.data.store

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JsonCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private var now = 1_800_000_000_000L
    private val strings = ListSerializer(String.serializer())

    private fun cache(dir: File = folder.root) = JsonCache(dir, Json, clock = { now })

    private val day = 24L * 60 * 60 * 1000

    @Test
    fun `записали — прочитали`() {
        val cache = cache()
        cache.write("schedule-2026-09-01", strings, listOf("a", "b"))

        val entry = cache.read("schedule-2026-09-01", strings)

        assertEquals(listOf("a", "b"), entry?.value)
        assertEquals(now, entry?.savedAt)
    }

    @Test
    fun `переживает перезапуск — читается с диска`() {
        cache().write("news", strings, listOf("x"))

        val fresh = cache()

        assertEquals(listOf("x"), fresh.read("news", strings)?.value)
    }

    @Test
    fun `после записи не остаётся временных файлов`() {
        cache().write("news", strings, listOf("x"))

        assertTrue(folder.root.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `битый файл не роняет и удаляется`() {
        File(folder.root, "news.json").writeText("{ это не json")

        assertNull(cache().read("news", strings))
        assertFalse(File(folder.root, "news.json").exists())
    }

    @Test
    fun `уборка выкидывает старое и оставляет свежее`() {
        val cache = cache()
        cache.write("schedule-2024-01-01", strings, listOf("old"))
        cache.write("dashboard", strings, listOf("new"))
        File(folder.root, "schedule-2024-01-01.json").setLastModified(now - 90 * day)

        cache.trim(maxAgeMs = 45 * day)

        assertNull(cache().read("schedule-2024-01-01", strings))
        assertNull(cache.read("schedule-2024-01-01", strings))
        assertNotNull(cache.read("dashboard", strings))
    }

    @Test
    fun `уборка держит общий размер, свежие в приоритете`() {
        val cache = cache()
        val big = List(200) { "x".repeat(50) }
        listOf("a", "b", "c").forEachIndexed { index, key ->
            cache.write(key, strings, big)
            File(folder.root, "$key.json").setLastModified(now - (3 - index) * 1000L)
        }
        val one = File(folder.root, "a.json").length()

        cache.trim(maxBytes = one * 2)

        val left = folder.root.listFiles()!!.map { it.name }.toSet()
        assertEquals(setOf("b.json", "c.json"), left)
    }

    @Test
    fun `уборка подметает недописанные файлы`() {
        File(folder.root, "news.json.tmp").writeText("[\"пол")

        cache().trim()

        assertFalse(File(folder.root, "news.json.tmp").exists())
    }

    @Test
    fun `сброс по префиксу трогает только своё`() {
        val cache = cache()
        cache.write("homework-0-3", strings, listOf("a"))
        cache.write("homework-0-1", strings, listOf("b"))
        cache.write("progress", strings, listOf("c"))

        cache.invalidate("homework-")

        assertNull(cache.read("homework-0-3", strings))
        assertNull(cache.read("homework-0-1", strings))
        assertNotNull(cache.read("progress", strings))
    }

    @Test
    fun `полная очистка`() {
        val cache = cache()
        cache.write("a", strings, listOf("1"))
        cache.clear()

        assertNull(cache.read("a", strings))
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }
}

package com.claustrophob.journal.data

import com.claustrophob.journal.data.net.HomeworkDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeworkFeedTest {

    private fun items(vararg ids: Int) = ids.map { HomeworkDto(id = it) }

    private class Pages(val pages: Map<Int, List<HomeworkDto>>) {
        val requested = mutableListOf<Int>()
        suspend fun fetch(page: Int): List<HomeworkDto> {
            requested += page
            return pages[page].orEmpty()
        }
    }

    @Test
    fun `первая страница и конец, если пришло меньше шести`() = runBlocking {
        val pages = Pages(mapOf(1 to items(1, 2, 3)))
        val feed = HomeworkFeed(pageSize = 6, fetch = pages::fetch)

        assertEquals(listOf(1, 2, 3), feed.refresh().map { it.id })
        assertTrue(feed.endReached)
        assertEquals(listOf(1), pages.requested)
    }

    @Test
    fun `догружает по одной странице`() = runBlocking {
        val pages = Pages(
            mapOf(
                1 to items(1, 2, 3, 4, 5, 6),
                2 to items(7, 8, 9, 10, 11, 12),
                3 to items(13),
            ),
        )
        val feed = HomeworkFeed(pageSize = 6, fetch = pages::fetch)

        feed.refresh()
        assertFalse(feed.endReached)
        assertEquals(listOf(1), pages.requested)

        feed.loadMore()
        assertEquals(12, feed.items.size)
        feed.loadMore()
        assertEquals(13, feed.items.size)
        assertTrue(feed.endReached)

        // Дальше не просим.
        feed.loadMore()
        assertEquals(listOf(1, 2, 3), pages.requested)
    }

    @Test
    fun `нумерация с нуля определяется один раз`() = runBlocking {
        val pages = Pages(mapOf(0 to items(1, 2, 3, 4, 5, 6), 1 to emptyList(), 2 to emptyList()))
        val feed = HomeworkFeed(pageSize = 6, fetch = pages::fetch)

        feed.refresh()
        assertEquals(listOf(1, 0), pages.requested)

        feed.refresh()
        assertEquals(listOf(1, 0, 0), pages.requested)
    }

    @Test
    fun `сервер игнорирует номер страницы — не зацикливаемся`() = runBlocking {
        val same = items(1, 2, 3, 4, 5, 6)
        val feed = HomeworkFeed(pageSize = 6) { same }

        feed.refresh()
        feed.loadMore()

        assertTrue(feed.endReached)
        assertEquals(6, feed.items.size)
    }

    @Test
    fun `пусто везде`() = runBlocking {
        val feed = HomeworkFeed(pageSize = 6) { emptyList() }

        assertTrue(feed.refresh().isEmpty())
        assertTrue(feed.endReached)
    }

    @Test
    fun `снимок из кэша виден до первого ответа и заменяется им`() = runBlocking {
        val feed = HomeworkFeed(pageSize = 6) { items(5) }
        feed.seed(items(1, 2))
        assertEquals(listOf(1, 2), feed.items.map { it.id })

        feed.refresh()
        assertEquals(listOf(5), feed.items.map { it.id })
    }

    @Test
    fun `ошибка сети не стирает уже показанное`() = runBlocking {
        var fail = false
        val feed = HomeworkFeed(pageSize = 6) { if (fail) error("offline") else items(1, 2) }
        feed.refresh()

        fail = true
        runCatching { feed.refresh() }

        assertEquals(listOf(1, 2), feed.items.map { it.id })
    }
}

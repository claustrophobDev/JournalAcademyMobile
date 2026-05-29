package com.claustrophob.journal.work

import com.claustrophob.journal.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlannerTest {

    private data class Mark(val key: String, val date: String)

    private val today = "2026-09-11"

    private fun plan(
        items: List<Mark>,
        known: Set<String> = emptySet(),
        baseline: Boolean = false,
    ) = SyncPlanner.plan(
        items = items,
        keyOf = { it.key },
        dateOf = { it.date },
        known = known,
        isBaseline = baseline,
        today = today,
    )

    private fun daysAgo(days: Int) = Dates.shiftDay(today, -days)

    @Test
    fun `первый прогон всё запоминает и молчит`() {
        val marks = listOf(Mark("a", daysAgo(1)), Mark("b", daysAgo(3)))

        val result = plan(marks, baseline = true)

        assertTrue(result.notify.isEmpty())
        assertEquals(2, result.remember.size)
    }

    @Test
    fun `новая оценка звенит один раз`() {
        val old = listOf(Mark("a", daysAgo(2)))
        val first = plan(old, baseline = true)

        val withNew = old + Mark("b", today)
        val second = plan(withNew, known = first.remember)
        assertEquals(listOf("b"), second.notify.map { it.key })

        val third = plan(withNew, known = second.remember)
        assertTrue(third.notify.isEmpty())
    }

    @Test
    fun `оценка прошлого года не звенит даже если её не видели`() {
        val result = plan(listOf(Mark("old", "2024-03-15")), known = setOf("${daysAgo(1)}|x"))

        assertTrue(result.notify.isEmpty())
    }

    // Та самая беда: оценок за пару лет больше, чем влезало в список показанных,
    // и старые каждые три часа всплывали снова.
    @Test
    fun `большая история не всплывает на втором прогоне`() {
        val history = (0 until 2000).map { index -> Mark("m$index", daysAgo(index / 3)) }

        val first = plan(history, baseline = true)
        val second = plan(history, known = first.remember)

        assertTrue(second.notify.isEmpty())
    }

    @Test
    fun `память ограничена окном`() {
        val history = (0 until 2000).map { index -> Mark("m$index", daysAgo(index / 3)) }

        val result = plan(history, baseline = true)

        val oldest = result.remember.minOf { it.substringBefore('|') }
        assertTrue(Dates.daysBetween(oldest, today)!! <= SyncPlanner.REMEMBER_DAYS)
        assertTrue(result.remember.size < history.size)
    }

    @Test
    fun `давно забытое не возвращается пока свежее`() {
        val known = setOf("${daysAgo(SyncPlanner.REMEMBER_DAYS + 5)}|gone")

        val result = plan(emptyList(), known = known)

        assertTrue(result.remember.isEmpty())
    }

    @Test
    fun `исправленная оценка считается новой`() {
        val first = plan(listOf(Mark("lesson1|4", daysAgo(1))), baseline = true)

        val result = plan(listOf(Mark("lesson1|5", daysAgo(1))), known = first.remember)

        assertEquals(listOf("lesson1|5"), result.notify.map { it.key })
    }

    @Test
    fun `граница свежести`() {
        val first = plan(emptyList(), baseline = true)
        val marks = listOf(
            Mark("edge", daysAgo(SyncPlanner.FRESH_DAYS)),
            Mark("stale", daysAgo(SyncPlanner.FRESH_DAYS + 1)),
        )

        val result = plan(marks, known = first.remember)

        assertEquals(listOf("edge"), result.notify.map { it.key })
        // Несвежее всё равно запоминаем, чтобы не думать о нём снова.
        assertEquals(2, result.remember.size)
    }

    @Test
    fun `самое новое первым`() {
        val marks = listOf(
            Mark("mid", daysAgo(3)),
            Mark("new", today),
            Mark("old", daysAgo(7)),
        )

        val result = plan(marks, known = setOf("${daysAgo(1)}|seed"))

        assertEquals(listOf("new", "mid", "old"), result.notify.map { it.key })
    }

    @Test
    fun `кривая дата не ломает и не звенит`() {
        val result = plan(listOf(Mark("x", ""), Mark("y", "вчера")), known = setOf("${today}|seed"))

        assertTrue(result.notify.isEmpty())
        assertEquals(setOf("${today}|seed"), result.remember)
    }

    @Test
    fun `дата со временем понимается`() {
        val result = plan(listOf(Mark("n", "$today 14:30:00")), known = setOf("${today}|seed"))

        assertEquals(listOf("n"), result.notify.map { it.key })
    }
}

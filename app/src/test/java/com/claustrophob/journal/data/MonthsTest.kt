package com.claustrophob.journal.data

import com.claustrophob.journal.data.net.ChartPointDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthsTest {

    private fun point(date: String, value: Double) = ChartPointDto(date = date, points = value)

    @Test
    fun `склеивает графики по месяцу даже с разными днями`() {
        val merged = mergeMonths(
            attendance = listOf(point("2026-09-01", 39.0)),
            average = listOf(point("2026-09-15", 4.5)),
        )

        assertEquals(1, merged.size)
        assertEquals(39.0, merged.single().attendance, 0.0)
        assertEquals(4.5, merged.single().average, 0.0)
    }

    @Test
    fun `месяц только из одного графика не теряется`() {
        val merged = mergeMonths(
            attendance = listOf(point("2026-05-01", 80.0)),
            average = listOf(point("2026-06-01", 4.0)),
        )

        assertEquals(listOf("2026-05-01", "2026-06-01"), merged.map { it.date })
        assertEquals(0.0, merged[0].average, 0.0)
        assertEquals(0.0, merged[1].attendance, 0.0)
    }

    @Test
    fun `порядок по времени, как бы ни пришло`() {
        val merged = mergeMonths(
            attendance = listOf(
                point("2026-03-01", 1.0),
                point("2025-12-01", 2.0),
                point("2026-01-01", 3.0),
            ),
            average = emptyList(),
        )

        assertEquals(listOf("2025-12-01", "2026-01-01", "2026-03-01"), merged.map { it.date })
    }

    @Test
    fun `средний балл пришёл раньше посещаемости`() {
        // Порядок графиков не должен влиять: балл не затирается посещаемостью.
        val merged = mergeMonths(
            attendance = listOf(point("2026-04-02", 70.0), point("2026-04-20", 75.0)),
            average = listOf(point("2026-04-01", 3.8)),
        )

        assertEquals(1, merged.size)
        assertEquals(75.0, merged.single().attendance, 0.0)
        assertEquals(3.8, merged.single().average, 0.0)
    }

    @Test
    fun `мусорные даты пропускаются`() {
        val merged = mergeMonths(
            attendance = listOf(point("", 10.0), point("abc", 20.0), point("2026-02-01", 30.0)),
            average = listOf(point("2026", 4.0)),
        )

        assertEquals(listOf("2026-02-01"), merged.map { it.date })
    }

    @Test
    fun `пусто на входе — пусто на выходе`() {
        assertTrue(mergeMonths(emptyList(), emptyList()).isEmpty())
    }
}

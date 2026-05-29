package com.claustrophob.journal.data

import com.claustrophob.journal.data.net.LessonDto
import com.claustrophob.journal.util.Dates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextUpTest {

    private val today = "2026-09-11"
    private val tomorrow = "2026-09-12"

    private fun lesson(day: String, number: Int, start: String, end: String, subject: String = "Пара $number") =
        LessonDto(date = day, lesson = number, startedAt = start, finishedAt = end, subjectName = subject)

    private val day = listOf(
        lesson(today, 1, "17:10", "18:40", "Алгоритмы"),
        lesson(today, 2, "18:50", "20:20", "Базы данных"),
    )

    private fun at(time: String) = Dates.momentOf(today, time)!!

    @Test
    fun `идёт пара — сколько прошло и сколько осталось`() {
        val state = NextUpResolver.resolve(day, today, at("17:55"))

        assertTrue(state is NextUp.Running)
        state as NextUp.Running
        assertEquals("Алгоритмы", state.lesson.subjectName)
        assertEquals(0.5f, state.progress, 0.01f)
        assertEquals(45, state.minutesLeft)
        assertEquals("Базы данных", state.following?.subjectName)
    }

    @Test
    fun `до первой пары`() {
        val state = NextUpResolver.resolve(day, today, at("16:45"))

        assertTrue(state is NextUp.Upcoming)
        state as NextUp.Upcoming
        assertEquals(25, state.minutesUntil)
        assertTrue(state.isFirst)
    }

    @Test
    fun `перемена между парами`() {
        val state = NextUpResolver.resolve(day, today, at("18:45"))

        assertTrue(state is NextUp.Upcoming)
        state as NextUp.Upcoming
        assertEquals("Базы данных", state.lesson.subjectName)
        assertEquals(5, state.minutesUntil)
        assertTrue(!state.isFirst)
    }

    @Test
    fun `пары кончились — показываем завтрашнюю первую`() {
        val withTomorrow = day + lesson(tomorrow, 2, "10:40", "12:10") + lesson(tomorrow, 1, "09:00", "10:30")

        val state = NextUpResolver.resolve(withTomorrow, today, at("21:00"))

        assertTrue(state is NextUp.DayOver)
        assertEquals("09:00", (state as NextUp.DayOver).tomorrowFirst?.startedAt)
    }

    @Test
    fun `свободный день`() {
        val state = NextUpResolver.resolve(emptyList(), today, at("12:00"))

        assertTrue(state is NextUp.FreeDay)
        assertNull((state as NextUp.FreeDay).tomorrowFirst)
    }

    // Главную открыли без сети, а снимок вчерашний: его "сегодня" — это вчера,
    // а "завтра" — как раз сегодня.
    @Test
    fun `вчерашний снимок не выдаёт вчерашние пары за сегодняшние`() {
        val yesterday = "2026-09-10"
        val snapshot = listOf(
            lesson(yesterday, 1, "09:00", "10:30", "Вчерашняя"),
            lesson(today, 1, "17:10", "18:40", "Сегодняшняя"),
        )

        val state = NextUpResolver.resolve(snapshot, today, at("12:00"))

        assertTrue(state is NextUp.Upcoming)
        assertEquals("Сегодняшняя", (state as NextUp.Upcoming).lesson.subjectName)
    }

    @Test
    fun `пара с непонятным временем пропускается`() {
        val broken = listOf(lesson(today, 1, "", ""), lesson(today, 2, "18:50", "20:20", "Нормальная"))

        val state = NextUpResolver.resolve(broken, today, at("12:00"))

        assertEquals("Нормальная", (state as NextUp.Upcoming).lesson.subjectName)
    }
}

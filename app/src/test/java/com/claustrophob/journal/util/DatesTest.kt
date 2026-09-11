package com.claustrophob.journal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatesTest {

    @Test
    fun `разбор даты и даты со временем`() {
        assertNotNull(Dates.parse("2026-09-11"))
        assertEquals(Dates.parse("2026-09-11"), Dates.parse("2026-09-11 14:30:00"))
        assertNull(Dates.parse(""))
        assertNull(Dates.parse("вчера"))
    }

    @Test
    fun `сдвиг месяца через границу года`() {
        assertEquals("2027-01-01", Dates.shiftMonth("2026-12-15", 1))
        assertEquals("2025-12-01", Dates.shiftMonth("2026-01-31", -1))
        assertEquals("2026-09-01", Dates.shiftMonth("2026-09-30", 0))
    }

    @Test
    fun `сдвиг дня через конец месяца и високосный февраль`() {
        assertEquals("2026-03-01", Dates.shiftDay("2026-02-28", 1))
        assertEquals("2028-02-29", Dates.shiftDay("2028-02-28", 1))
        assertEquals("2025-12-31", Dates.shiftDay("2026-01-01", -1))
    }

    @Test
    fun `дни месяца`() {
        val leap = Dates.daysOfMonth("2028-02-10")
        assertEquals(29, leap.size)
        assertEquals("2028-02-01", leap.first())
        assertEquals("2028-02-29", leap.last())
        assertEquals(28, Dates.daysOfMonth("2026-02-01").size)
        assertEquals(30, Dates.daysOfMonth("2026-09-01").size)
    }

    @Test
    fun `день недели с понедельника`() {
        assertEquals(0, Dates.weekdayIndex("2026-09-07"))
        assertEquals(4, Dates.weekdayIndex("2026-09-11"))
        assertEquals(6, Dates.weekdayIndex("2026-09-13"))
    }

    @Test
    fun `время пары`() {
        val start = Dates.momentOf("2026-09-07", "09:00")!!
        val end = Dates.momentOf("2026-09-07", "10:30:00")!!
        assertEquals(90 * 60_000L, end - start)
    }

    @Test
    fun `кривое время пары не превращается в полночь`() {
        assertNull(Dates.momentOf("2026-09-07", "9:00"))
        assertNull(Dates.momentOf("2026-09-07", "25:00"))
        assertNull(Dates.momentOf("2026-09-07", "09:75"))
        assertNull(Dates.momentOf("2026-09-07", ""))
        assertNull(Dates.momentOf("не дата", "09:00"))
    }

    @Test
    fun `дни между датами`() {
        assertEquals(10, Dates.daysBetween("2026-09-01", "2026-09-11"))
        assertEquals(-10, Dates.daysBetween("2026-09-11", "2026-09-01"))
        assertEquals(0, Dates.daysBetween("2026-09-11 23:59", "2026-09-11"))
        assertEquals(366, Dates.daysBetween("2028-01-01", "2029-01-01"))
        assertNull(Dates.daysBetween("", "2026-09-01"))
    }

    @Test
    fun `относительные подписи и склонения`() {
        val today = Dates.today()
        assertEquals("сегодня", Dates.relativeDayLabel(today))
        assertEquals("завтра", Dates.relativeDayLabel(Dates.shiftDay(today, 1)))
        assertEquals("послезавтра", Dates.relativeDayLabel(Dates.shiftDay(today, 2)))
        assertEquals("через 3 дня", Dates.relativeDayLabel(Dates.shiftDay(today, 3)))
        assertEquals("через 5 дней", Dates.relativeDayLabel(Dates.shiftDay(today, 5)))
        assertEquals("через 21 день", Dates.relativeDayLabel(Dates.shiftDay(today, 21)))
        assertEquals("вчера", Dates.relativeDayLabel(Dates.shiftDay(today, -1)))
        assertEquals("2 дня назад", Dates.relativeDayLabel(Dates.shiftDay(today, -2)))
        assertNull(Dates.relativeDayLabel(Dates.shiftDay(today, 45)))
    }

    @Test
    fun `прошло или нет`() {
        assertTrue(Dates.isPast(Dates.shiftDay(Dates.today(), -1)))
        assertFalse(Dates.isPast(Dates.today()))
        assertTrue(Dates.isToday(Dates.today() + " 10:00:00"))
    }

    @Test
    fun `свежесть кэша`() {
        val now = System.currentTimeMillis()
        assertEquals("", Dates.sinceLabel(0L))
        assertEquals("только что", Dates.sinceLabel(now))
        assertEquals("5 мин назад", Dates.sinceLabel(now - 5 * 60_000L))
        assertEquals("3 ч назад", Dates.sinceLabel(now - 3 * 3_600_000L))
    }

    @Test
    fun `подпись времени`() {
        assertEquals("09:00", Dates.hhmm("09:00:00"))
        assertEquals("", Dates.timestampLabel(""))
        assertTrue(Dates.timestampLabel("2026-09-05 14:30:00").endsWith(", 14:30"))
    }

    @Test
    fun `склонения`() {
        assertEquals("1 задание", pluralRu(1, "задание", "задания", "заданий"))
        assertEquals("2 задания", pluralRu(2, "задание", "задания", "заданий"))
        assertEquals("5 заданий", pluralRu(5, "задание", "задания", "заданий"))
        assertEquals("11 заданий", pluralRu(11, "задание", "задания", "заданий"))
        assertEquals("21 задание", pluralRu(21, "задание", "задания", "заданий"))
        assertEquals("112 заданий", pluralRu(112, "задание", "задания", "заданий"))
        assertEquals("0 заданий", pluralRu(0, "задание", "задания", "заданий"))
    }
}

package com.claustrophob.journal.data

import com.claustrophob.journal.data.net.LessonDto
import com.claustrophob.journal.util.Dates
import kotlin.math.ceil

// Что показывать в красной карточке на главной: какая пара идёт, какая
// следующая или что на сегодня всё. Вынес отдельно, чтобы гонять тестами.
sealed interface NextUp {
    // Идёт пара. progress — сколько прошло, от 0 до 1.
    data class Running(
        val lesson: LessonDto,
        val progress: Float,
        val minutesLeft: Int,
        val following: LessonDto?,
    ) : NextUp

    data class Upcoming(val lesson: LessonDto, val minutesUntil: Int, val isFirst: Boolean) : NextUp

    // Пары сегодня были и кончились.
    data class DayOver(val tomorrowFirst: LessonDto?) : NextUp

    // Сегодня пар нет вовсе.
    data class FreeDay(val tomorrowFirst: LessonDto?) : NextUp
}

object NextUpResolver {

    // lessons — пары на сегодня и завтра вперемешку. Кэш главной бывает
    // вчерашний (открыл без интернета), так что по датам раскладываю сам.
    fun resolve(lessons: List<LessonDto>, today: String, now: Long): NextUp {
        val tomorrow = Dates.shiftDay(today, 1)
        val todays = lessons
            .filter { it.date.take(10).ifBlank { today } == today }
            .mapNotNull { lesson ->
                val start = Dates.momentOf(today, lesson.startedAt) ?: return@mapNotNull null
                val end = Dates.momentOf(today, lesson.finishedAt) ?: return@mapNotNull null
                Slot(lesson, start, end)
            }
            .sortedBy { it.start }
        val tomorrowFirst = lessons
            .filter { it.date.take(10) == tomorrow }
            .minByOrNull { it.startedAt }

        if (todays.isEmpty()) return NextUp.FreeDay(tomorrowFirst)

        val runningIndex = todays.indexOfFirst { now in it.start..it.end }
        if (runningIndex >= 0) {
            val slot = todays[runningIndex]
            val length = (slot.end - slot.start).coerceAtLeast(1L)
            return NextUp.Running(
                lesson = slot.lesson,
                progress = ((now - slot.start).toFloat() / length).coerceIn(0f, 1f),
                minutesLeft = minutesBetween(now, slot.end),
                following = todays.getOrNull(runningIndex + 1)?.lesson,
            )
        }

        val nextIndex = todays.indexOfFirst { it.start > now }
        if (nextIndex >= 0) {
            return NextUp.Upcoming(
                lesson = todays[nextIndex].lesson,
                minutesUntil = minutesBetween(now, todays[nextIndex].start),
                isFirst = nextIndex == 0,
            )
        }

        return NextUp.DayOver(tomorrowFirst)
    }

    private fun minutesBetween(from: Long, to: Long): Int =
        ceil((to - from) / 60_000.0).toInt().coerceAtLeast(0)

    private data class Slot(val lesson: LessonDto, val start: Long, val end: Long)
}

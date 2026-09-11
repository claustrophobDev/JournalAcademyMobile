package com.claustrophob.journal.data

import com.claustrophob.journal.data.net.LessonDto
import com.claustrophob.journal.data.net.VisitDto
import com.claustrophob.journal.util.Dates

// Состояний больше, чем отметок в журнале. Если смотреть только на status_was,
// то все будущие пары станут красными: ноль там значит "не был".
// Поэтому сначала время, и только у прошедшей пары спрашиваем отметку.
enum class LessonState {
    Upcoming,   // ещё не началась
    Running,    // идёт сейчас
    Present,    // был
    Late,       // опоздал
    Absent,     // не был

    // Пара прошла, а препод ещё не заполнил журнал.
    // Тут нельзя показывать "не был", иначе пугаем пропуском на пустом месте.
    Unmarked,
}

// Пара + то, что про неё знает журнал посещений.
data class LessonEntry(
    val lesson: LessonDto,
    val state: LessonState,
    // 0 значит оценки нет
    val mark: Int = 0,
    // тема есть только в журнале, в расписании её не отдают
    val theme: String = "",
) {
    val isProblem: Boolean get() = state == LessonState.Late || state == LessonState.Absent
}

// Короткий итог дня, показываем над списком пар.
data class DaySummary(
    val total: Int,
    val present: Int,
    val late: Int,
    val absent: Int,
    val unmarked: Int,
) {
    val hasFacts: Boolean get() = present + late + absent > 0
}

// Значения status_was, подсмотрел в бандле сайта.
private const val STATUS_ABSENT = 0
private const val STATUS_PRESENT = 1
private const val STATUS_LATE = 2

object Attendance {

    // Склеиваем расписание с посещениями по дате и номеру пары.
    // По названию предмета нельзя: в один день его может быть две штуки.
    fun entriesFor(
        day: String,
        lessons: List<LessonDto>,
        visits: List<VisitDto>,
        now: Long = System.currentTimeMillis(),
    ): List<LessonEntry> {
        val visitsByLesson = visits
            .filter { it.dateVisit.take(10) == day }
            .associateBy { it.lessonNumber }

        return lessons
            .filter { it.date.take(10) == day }
            .sortedBy { it.lesson }
            .map { lesson ->
                val visit = visitsByLesson[lesson.lesson]
                LessonEntry(
                    lesson = lesson,
                    state = stateOf(lesson, visit, now),
                    mark = visit?.let(::markOf) ?: 0,
                    theme = visit?.lessonTheme.orEmpty(),
                )
            }
    }

    fun summarize(entries: List<LessonEntry>): DaySummary = DaySummary(
        total = entries.size,
        present = entries.count { it.state == LessonState.Present },
        late = entries.count { it.state == LessonState.Late },
        absent = entries.count { it.state == LessonState.Absent },
        unmarked = entries.count { it.state == LessonState.Unmarked },
    )

    private fun stateOf(lesson: LessonDto, visit: VisitDto?, now: Long): LessonState {
        val start = Dates.momentOf(lesson.date, lesson.startedAt)
        val end = Dates.momentOf(lesson.date, lesson.finishedAt)

        // Время не разобралось — судим только по отметке, а без неё молчим.
        if (start == null || end == null) {
            return visit?.let(::fromStatus) ?: LessonState.Upcoming
        }

        return when {
            now < start -> LessonState.Upcoming
            now <= end -> LessonState.Running
            visit != null -> fromStatus(visit)
            else -> LessonState.Unmarked
        }
    }

    private fun fromStatus(visit: VisitDto): LessonState = when (visit.statusWas) {
        STATUS_PRESENT -> LessonState.Present
        STATUS_LATE -> LessonState.Late
        STATUS_ABSENT -> LessonState.Absent
        else -> LessonState.Unmarked
    }

    // Оценка может лежать в любом из шести полей, берём первую ненулевую.
    private fun markOf(visit: VisitDto): Int = listOf(
        visit.classWorkMark,
        visit.homeWorkMark,
        visit.labWorkMark,
        visit.controlWorkMark,
        visit.practicalWorkMark,
        visit.finalWorkMark,
    ).firstOrNull { it > 0 } ?: 0
}

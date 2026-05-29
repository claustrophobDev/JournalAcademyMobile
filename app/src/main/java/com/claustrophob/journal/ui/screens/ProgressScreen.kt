package com.claustrophob.journal.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.claustrophob.journal.AppContainer
import com.claustrophob.journal.data.JournalRepository
import com.claustrophob.journal.data.ProgressBundle
import com.claustrophob.journal.data.net.ExamResultDto
import com.claustrophob.journal.data.net.VisitDto
import com.claustrophob.journal.data.net.VisitStatus
import com.claustrophob.journal.ui.LoaderViewModel
import com.claustrophob.journal.ui.components.ChipRow
import com.claustrophob.journal.ui.components.EmptyState
import com.claustrophob.journal.ui.components.ErrorState
import com.claustrophob.journal.ui.components.GradeBadge
import com.claustrophob.journal.ui.components.GroupedList
import com.claustrophob.journal.ui.components.JCard
import com.claustrophob.journal.ui.components.JournalScaffold
import com.claustrophob.journal.ui.components.ListSkeleton
import com.claustrophob.journal.ui.components.RowDivider
import com.claustrophob.journal.ui.components.SectionHeader
import com.claustrophob.journal.ui.components.Segmented
import com.claustrophob.journal.ui.components.StatusBanner
import com.claustrophob.journal.ui.components.SubjectTile
import com.claustrophob.journal.ui.components.formatGrade
import com.claustrophob.journal.ui.components.shortPersonName
import com.claustrophob.journal.ui.journalViewModel
import com.claustrophob.journal.ui.theme.GradeTone
import com.claustrophob.journal.ui.theme.color
import com.claustrophob.journal.ui.theme.container
import com.claustrophob.journal.ui.theme.extra
import com.claustrophob.journal.ui.theme.gradeTone
import com.claustrophob.journal.ui.theme.scaleOf
import com.claustrophob.journal.ui.theme.tabular
import com.claustrophob.journal.util.Dates
import com.claustrophob.journal.util.pluralRu
import kotlin.math.roundToInt

class ProgressViewModel(repository: JournalRepository) :
    LoaderViewModel<ProgressBundle>({ repository.progress() })

private data class SubjectSummary(
    val specId: Int,
    val name: String,
    val teacher: String,
    val marks: List<MarkEntry>,
    val average: Double,
    val attended: Int,
    val late: Int,
    val missed: Int,
    val scale: Int,
) {
    val lessons: Int get() = attended + late + missed
    val attendancePercent: Int
        get() = if (lessons == 0) 0 else ((attended + late) * 100.0 / lessons).roundToInt()
}

private data class MarkEntry(val date: String, val value: Int, val kind: String, val theme: String)

// Сводка: средний балл, сколько оценок, посещаемость и сколько каких оценок.
private data class Overview(
    val average: Double,
    val marks: Int,
    val attendance: Int?,
    val distribution: List<Pair<GradeTone, Int>>,
    val scale: Int,
)

@Composable
fun ProgressScreen() {
    val viewModel: ProgressViewModel = journalViewModel { container: AppContainer ->
        ProgressViewModel(container.repository)
    }
    val state by viewModel.state.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    // В журнале всё обучение сразу, и предметы с прошлых курсов только мешают.
    // Поэтому по умолчанию показываем только этот учебный год.
    var wholeTime by rememberSaveable { mutableStateOf(false) }
    val yearStart = remember { Dates.academicYearStart(Dates.today()) }
    val visits = remember(state.data, wholeTime) {
        val all = state.data?.visits.orEmpty()
        if (wholeTime) all else all.filter { it.dateVisit.take(10) >= yearStart }
    }
    val subjects = remember(visits) { summarize(visits) }
    val overview = remember(visits) { overviewOf(visits) }
    val exams = remember(state.data) {
        state.data?.exams.orEmpty().sortedByDescending { it.date }
    }

    JournalScaffold(
        title = "Успеваемость",
        pinned = {
            Segmented(
                options = listOf("Предметы", "Экзамены"),
                selected = tab,
                onSelect = { tab = it },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
            )
        },
        refreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
    ) { padding ->
        when {
            state.showSkeleton -> Column(Modifier.padding(padding)) { ListSkeleton(rows = 5) }

            state.data == null -> ErrorState(
                message = state.error ?: "Нет данных",
                onRetry = { viewModel.load(refresh = true) },
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "banner") {
                    StatusBanner(
                        message = state.error,
                        offline = true,
                        onRetry = { viewModel.load(refresh = true) },
                        onDismiss = viewModel::dismissError,
                    )
                }

                if (tab == 0) {
                    item(key = "period") {
                        ChipRow(
                            options = listOf("Этот учебный год", "Всё время"),
                            selected = if (wholeTime) 1 else 0,
                            onSelect = { wholeTime = it == 1 },
                            contentPadding = PaddingValues(0.dp),
                        )
                    }
                    if (subjects.isEmpty()) {
                        item(key = "empty") {
                            EmptyState(
                                icon = Icons.Rounded.Grade,
                                title = if (wholeTime) "Оценок пока нет" else "В этом году оценок пока нет",
                                subtitle = "Здесь появятся оценки и посещаемость по предметам",
                                action = if (!wholeTime) {
                                    { TextButton(onClick = { wholeTime = true }) { Text("Показать всё время") } }
                                } else {
                                    null
                                },
                            )
                        }
                    } else {
                        item(key = "overview") { OverviewCard(overview) }
                        item(key = "subjects-header") {
                            SectionHeader("Предметы · ${subjects.size}")
                        }
                        item(key = "subjects") {
                            GroupedList {
                                subjects.forEachIndexed { index, subject ->
                                    SubjectRow(subject)
                                    if (index != subjects.lastIndex) RowDivider(inset = 68)
                                }
                            }
                        }
                    }
                } else {
                    if (exams.isEmpty()) {
                        item(key = "exams-empty") {
                            EmptyState(
                                icon = Icons.Rounded.Grade,
                                title = "Экзаменов нет",
                                subtitle = "Сданные экзамены появятся здесь",
                            )
                        }
                    } else {
                        item(key = "exams") {
                            GroupedList {
                                exams.forEachIndexed { index, exam ->
                                    ExamRow(exam)
                                    if (index != exams.lastIndex) RowDivider(inset = 72)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(overview: Overview) {
    JCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OverviewStat(
                modifier = Modifier.weight(1f),
                value = formatAverage(overview.average),
                label = "средний балл",
                color = gradeTone(overview.average, overview.scale).color,
            )
            VerticalHairline()
            OverviewStat(
                modifier = Modifier.weight(1f),
                value = overview.marks.toString(),
                label = pluralRu(overview.marks, "оценка", "оценки", "оценок").substringAfter(' '),
            )
            VerticalHairline()
            OverviewStat(
                modifier = Modifier.weight(1f),
                value = overview.attendance?.let { "$it%" } ?: "—",
                label = "посещаемость",
            )
        }
        if (overview.distribution.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            val total = overview.distribution.sumOf { it.second }.coerceAtLeast(1)
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                overview.distribution.forEach { (tone, count) ->
                    Box(
                        Modifier
                            .weight(count.toFloat() / total)
                            .fillMaxHeight()
                            .background(tone.color),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                overview.distribution.forEach { (tone, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .background(tone.color, CircleShape),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = "${toneLabel(tone, overview.scale)} · $count",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun toneLabel(tone: GradeTone, scale: Int): String = if (scale > 5) {
    when (tone) {
        GradeTone.Excellent -> "10–12"
        GradeTone.Good -> "7–9"
        GradeTone.Fair -> "4–6"
        else -> "1–3"
    }
} else {
    when (tone) {
        GradeTone.Excellent -> "5"
        GradeTone.Good -> "4"
        GradeTone.Fair -> "3"
        else -> "2"
    }
}

@Composable
private fun OverviewStat(
    modifier: Modifier,
    value: String,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.tabular(),
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VerticalHairline() {
    Box(
        Modifier
            .width(0.8.dp)
            .height(40.dp)
            .background(MaterialTheme.extra.hairline),
    )
}

@Composable
private fun SubjectRow(subject: SubjectSummary) {
    var expanded by rememberSaveable(subject.specId) { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubjectTile(subject.name)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subject.teacher.isNotBlank()) {
                    Text(
                        text = shortPersonName(subject.teacher),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (subject.average > 0.0) {
                Spacer(Modifier.width(12.dp))
                GradeBadge(
                    value = subject.average,
                    scale = subject.scale,
                    text = formatAverage(subject.average),
                    size = 38.dp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.padding(start = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttendanceBar(subject, Modifier.weight(1f))
            Spacer(Modifier.width(10.dp))
            Text(
                text = "${subject.attendancePercent}%",
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (subject.marks.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.padding(start = 52.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val recent = subject.marks.sortedByDescending { it.date }
                recent.take(RECENT_MARKS).forEach { MiniMark(it.value, subject.scale) }
                if (recent.size > RECENT_MARKS) {
                    Text(
                        text = "+${recent.size - RECENT_MARKS}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Свернуть" else "Подробнее",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.padding(start = 52.dp),
                thickness = 0.8.dp,
                color = MaterialTheme.extra.hairline,
            )
            Column(Modifier.padding(start = 52.dp, top = 8.dp)) {
                Text(
                    text = buildString {
                        append("Был: ${subject.attended}")
                        if (subject.late > 0) append(" · опоздал: ${subject.late}")
                        if (subject.missed > 0) append(" · пропустил: ${subject.missed}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (subject.marks.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Оценок по предмету ещё нет",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                subject.marks.sortedByDescending { it.date }.forEach { mark ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MiniMark(mark.value, subject.scale, size = 28.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(text = mark.kind, style = MaterialTheme.typography.bodyMedium)
                            if (mark.theme.isNotBlank()) {
                                Text(
                                    text = mark.theme,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Dates.humanDayShort(mark.date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private const val RECENT_MARKS = 8

// Маленькая оценка-квадратик для ленты последних оценок.
@Composable
private fun MiniMark(value: Int, scale: Int, size: Dp = 22.dp) {
    val tone = gradeTone(value.toDouble(), scale)
    Box(
        modifier = Modifier
            .size(size)
            .background(tone.container, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium.tabular(),
            fontWeight = FontWeight.Bold,
            color = tone.color,
        )
    }
}

@Composable
private fun AttendanceBar(subject: SubjectSummary, modifier: Modifier) {
    val total = subject.lessons.coerceAtLeast(1).toFloat()
    Row(
        modifier = modifier
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        if (subject.attended > 0) {
            Box(
                Modifier
                    .weight(subject.attended / total)
                    .fillMaxHeight()
                    .background(MaterialTheme.extra.success),
            )
        }
        if (subject.late > 0) {
            Box(
                Modifier
                    .weight(subject.late / total)
                    .fillMaxHeight()
                    .background(MaterialTheme.extra.warning),
            )
        }
        if (subject.missed > 0) {
            Box(
                Modifier
                    .weight(subject.missed / total)
                    .fillMaxHeight()
                    .background(MaterialTheme.extra.danger),
            )
        }
    }
}

@Composable
private fun ExamRow(exam: ExamResultDto) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        GradeBadge(value = exam.mark.toDouble(), size = 42.dp, text = formatGrade(exam.mark.toDouble()))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(exam.spec, style = MaterialTheme.typography.titleSmall)
            Text(
                text = listOfNotNull(
                    exam.teacher.takeIf { it.isNotBlank() }?.let(::shortPersonName),
                    exam.date.takeIf { it.isNotBlank() }?.let { Dates.humanDayShortWithYear(it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (exam.commentTeach.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = exam.commentTeach,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun VisitDto.marks(): List<Pair<Int, String>> = listOf(
    classWorkMark to "Работа на паре",
    homeWorkMark to "Домашняя работа",
    labWorkMark to "Лабораторная",
    controlWorkMark to "Контрольная",
    practicalWorkMark to "Практическая",
    finalWorkMark to "Итоговая",
).filter { it.first > 0 }

private fun summarize(visits: List<VisitDto>): List<SubjectSummary> =
    visits.groupBy { it.specId }
        .map { (specId, entries) ->
            val marks = entries.flatMap { visit ->
                visit.marks().map { (value, kind) ->
                    MarkEntry(visit.dateVisit, value, kind, visit.lessonTheme)
                }
            }
            SubjectSummary(
                specId = specId,
                name = entries.firstOrNull { it.specName.isNotBlank() }?.specName.orEmpty(),
                // Преподаватель мог смениться — берём того, кто вёл последним.
                teacher = entries.maxByOrNull { it.dateVisit }?.teacherName.orEmpty(),
                marks = marks,
                average = if (marks.isEmpty()) 0.0 else marks.sumOf { it.value }.toDouble() / marks.size,
                attended = entries.count { it.statusWas == VisitStatus.PRESENT },
                late = entries.count { it.statusWas == VisitStatus.LATE },
                missed = entries.count { it.statusWas == VisitStatus.ABSENT },
                scale = scaleOf(marks.map { it.value }),
            )
        }
        .sortedBy { it.name.lowercase() }

private fun overviewOf(visits: List<VisitDto>): Overview {
    val marks = visits.flatMap { visit -> visit.marks().map { it.first } }
    val scale = scaleOf(marks)
    val attended = visits.count {
        it.statusWas == VisitStatus.PRESENT || it.statusWas == VisitStatus.LATE
    }
    return Overview(
        average = if (marks.isEmpty()) 0.0 else marks.average(),
        marks = marks.size,
        attendance = if (visits.isEmpty()) null else (attended * 100.0 / visits.size).roundToInt(),
        distribution = listOf(GradeTone.Excellent, GradeTone.Good, GradeTone.Fair, GradeTone.Poor)
            .map { tone -> tone to marks.count { gradeTone(it.toDouble(), scale) == tone } }
            .filter { it.second > 0 },
        scale = scale,
    )
}

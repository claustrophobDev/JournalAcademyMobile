package com.claustrophob.journal.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.claustrophob.journal.AppContainer
import com.claustrophob.journal.data.Attendance
import com.claustrophob.journal.data.DaySummary
import com.claustrophob.journal.data.JournalRepository
import com.claustrophob.journal.data.LessonEntry
import com.claustrophob.journal.data.LessonState
import com.claustrophob.journal.data.Res
import com.claustrophob.journal.data.net.LessonDto
import com.claustrophob.journal.data.net.VisitDto
import com.claustrophob.journal.ui.UiState
import com.claustrophob.journal.ui.components.EmptyState
import com.claustrophob.journal.ui.components.GradeBadge
import com.claustrophob.journal.ui.components.GroupedList
import com.claustrophob.journal.ui.components.JCard
import com.claustrophob.journal.ui.components.JournalScaffold
import com.claustrophob.journal.ui.components.ListSkeleton
import com.claustrophob.journal.ui.components.Pill
import com.claustrophob.journal.ui.components.StatusBanner
import com.claustrophob.journal.ui.components.TimelineDot
import com.claustrophob.journal.ui.components.TimelineRow
import com.claustrophob.journal.ui.components.lessonDetails
import com.claustrophob.journal.ui.journalViewModel
import com.claustrophob.journal.ui.theme.extra
import com.claustrophob.journal.ui.theme.tabular
import com.claustrophob.journal.util.Dates
import com.claustrophob.journal.util.pluralRu
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScheduleViewModel(private val repository: JournalRepository) : ViewModel() {

    private val _month = MutableStateFlow(Dates.firstOfMonth(java.util.Date()))
    val month: StateFlow<String> = _month.asStateFlow()

    private val _selectedDay = MutableStateFlow(Dates.today())
    val selectedDay: StateFlow<String> = _selectedDay.asStateFlow()

    private val _state = MutableStateFlow(UiState<List<LessonDto>>(loading = true))
    val state: StateFlow<UiState<List<LessonDto>>> = _state.asStateFlow()

    // Посещения за всё обучение, их около полутора тысяч.
    // Тянем один раз в кэш, а не на каждый открытый день.
    private val _visits = MutableStateFlow<List<VisitDto>>(emptyList())
    val visits: StateFlow<List<VisitDto>> = _visits.asStateFlow()

    private var job: Job? = null
    private var visitsJob: Job? = null

    init {
        load()
        loadVisits()
    }

    private fun loadVisits() {
        visitsJob?.cancel()
        visitsJob = viewModelScope.launch {
            repository.progress().collect { result ->
                // Если отметки не приехали — не беда, расписание живёт и без них.
                if (result is Res.Ok) _visits.value = result.data.visits
            }
        }
    }

    fun load(refresh: Boolean = false) {
        job?.cancel()
        if (refresh) loadVisits()
        _state.value = _state.value.copy(
            loading = !refresh && _state.value.data == null,
            refreshing = refresh,
            error = null,
        )
        job = viewModelScope.launch {
            repository.scheduleForMonth(_month.value).collect { result ->
                _state.value = when (result) {
                    is Res.Ok -> UiState(
                        data = result.data,
                        loading = false,
                        refreshing = false,
                        stale = result.stale,
                        updatedAt = result.savedAt,
                    )

                    is Res.Err -> _state.value.copy(
                        loading = false,
                        refreshing = false,
                        error = result.message,
                    )
                }
            }
        }
    }

    fun shiftMonth(delta: Int) {
        _month.value = Dates.shiftMonth(_month.value, delta)
        _state.value = UiState(loading = true)
        val days = Dates.daysOfMonth(_month.value)
        _selectedDay.value = when {
            days.contains(Dates.today()) -> Dates.today()
            else -> days.firstOrNull() ?: Dates.today()
        }
        load()
    }

    fun jumpToToday() {
        val today = Dates.today()
        val currentMonth = Dates.firstOfMonth(java.util.Date())
        _selectedDay.value = today
        if (_month.value != currentMonth) {
            _month.value = currentMonth
            _state.value = UiState(loading = true)
            load()
        }
    }

    fun selectDay(day: String) {
        _selectedDay.value = day
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}

@Composable
fun ScheduleScreen() {
    val viewModel: ScheduleViewModel = journalViewModel { container: AppContainer ->
        ScheduleViewModel(container.repository)
    }
    val state by viewModel.state.collectAsState()
    val month by viewModel.month.collectAsState()
    val selectedDay by viewModel.selectedDay.collectAsState()
    val visits by viewModel.visits.collectAsState()
    val now = rememberNow(60_000L)

    val lessonsByDay = remember(state.data) {
        state.data.orEmpty().groupBy { it.date.take(10) }
    }

    val entries = remember(selectedDay, state.data, visits, now) {
        Attendance.entriesFor(
            day = selectedDay,
            lessons = lessonsByDay[selectedDay].orEmpty(),
            visits = visits,
            now = now,
        )
    }
    val summary = remember(entries) { Attendance.summarize(entries) }

    // Худшее за день — для точки в календаре. Чтобы пропуск было видно сразу,
    // не заходя в каждый день.
    val dayMarks = remember(lessonsByDay, visits) {
        lessonsByDay.keys.associateWith { day ->
            val dayEntries = Attendance.entriesFor(day, lessonsByDay[day].orEmpty(), visits)
            when {
                dayEntries.any { it.state == LessonState.Absent } -> LessonState.Absent
                dayEntries.any { it.state == LessonState.Late } -> LessonState.Late
                else -> null
            }
        }
    }

    JournalScaffold(
        title = "Расписание",
        actions = {
            // Кнопка нужна только если ушли с сегодня, иначе она просто мозолит глаза.
            if (!Dates.isToday(selectedDay)) {
                TextButton(onClick = viewModel::jumpToToday) { Text("Сегодня") }
            }
        },
        refreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
    ) { padding ->
        LazyColumn(
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

            item(key = "calendar") {
                CalendarCard(
                    month = month,
                    selectedDay = selectedDay,
                    daysWithLessons = lessonsByDay.keys,
                    dayMarks = dayMarks,
                    onPrevious = { viewModel.shiftMonth(-1) },
                    onNext = { viewModel.shiftMonth(1) },
                    onSelect = viewModel::selectDay,
                )
            }

            item(key = "day") { DayHeader(day = selectedDay, summary = summary) }

            when {
                state.showSkeleton -> item(key = "skeleton") { ListSkeleton(rows = 3) }

                entries.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.EventBusy,
                        title = "Пар нет",
                        subtitle = if (Dates.weekdayIndex(selectedDay) >= 5) {
                            "Выходной — занятий не запланировано"
                        } else {
                            "В этот день занятий не запланировано"
                        },
                    )
                }

                else -> item(key = "lessons") {
                    GroupedList {
                        Spacer(Modifier.height(4.dp))
                        entries.forEachIndexed { index, entry ->
                            LessonEntryRow(
                                entry = entry,
                                isFirst = index == 0,
                                isLast = index == entries.lastIndex,
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }

        }
    }
}

// Цвет только у проблемных состояний. Если красить всё подряд, экран
// превращается в светофор и пропуск в нём теряется.
@Composable
private fun stateLabel(state: LessonState): Pair<String, Color>? = when (state) {
    LessonState.Running -> "идёт сейчас" to MaterialTheme.colorScheme.primary
    LessonState.Late -> "опоздание" to MaterialTheme.extra.warning
    LessonState.Absent -> "пропуск" to MaterialTheme.extra.danger
    // Препод ещё не заполнил журнал. Не пугаем пропуском, которого не было.
    LessonState.Unmarked -> "ещё не отмечено" to MaterialTheme.colorScheme.onSurfaceVariant
    else -> null
}

private fun dotOf(state: LessonState): TimelineDot = when (state) {
    LessonState.Upcoming -> TimelineDot.Next
    LessonState.Running -> TimelineDot.Now
    LessonState.Present -> TimelineDot.Done
    LessonState.Late -> TimelineDot.Late
    LessonState.Absent -> TimelineDot.Absent
    LessonState.Unmarked -> TimelineDot.Unmarked
}

// Дата и короткий итог по дню.
@Composable
private fun DayHeader(day: String, summary: DaySummary) {
    Column(Modifier.padding(start = 4.dp, top = 6.dp)) {
        Text(text = dayTitle(day), style = MaterialTheme.typography.titleLarge)

        // У будущего дня итог не нужен: "был на 0 парах" это мусор.
        if (summary.hasFacts) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (summary.present > 0) {
                    Pill(
                        "был на ${pluralRu(summary.present, "паре", "парах", "парах")}",
                        MaterialTheme.extra.success,
                    )
                }
                if (summary.late > 0) Pill("опоздал: ${summary.late}", MaterialTheme.extra.warning)
                if (summary.absent > 0) Pill("пропустил: ${summary.absent}", MaterialTheme.extra.danger)
            }
        } else if (summary.total > 0) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = pluralRu(summary.total, "пара", "пары", "пар"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun dayTitle(day: String): String {
    val today = Dates.today()
    val base = Dates.humanDay(day)
    return when (day) {
        today -> "Сегодня, $base"
        Dates.shiftDay(today, 1) -> "Завтра, $base"
        Dates.shiftDay(today, -1) -> "Вчера, $base"
        else -> {
            val withYear = if (day.take(4) != today.take(4)) Dates.humanFull(day) else base
            "${Dates.weekdayName(day)}, $withYear"
        }
    }
}

// Строка пары: время, точка на линии дня, предмет, справа оценка.
// Тему занятия берём из журнала — в расписании её нет.
@Composable
private fun LessonEntryRow(entry: LessonEntry, isFirst: Boolean, isLast: Boolean) {
    val lesson = entry.lesson
    val label = stateLabel(entry.state)
    TimelineRow(
        start = Dates.hhmm(lesson.startedAt),
        end = Dates.hhmm(lesson.finishedAt),
        title = lesson.subjectName,
        details = lessonDetails(lesson.roomName, lesson.teacherName),
        dot = dotOf(entry.state),
        isFirst = isFirst,
        isLast = isLast,
        trailing = if (entry.mark > 0) {
            { GradeBadge(value = entry.mark.toDouble()) }
        } else {
            null
        },
        extra = if (entry.theme.isNotBlank() || label != null) {
            {
                if (entry.theme.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = entry.theme,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (label != null) {
                    Spacer(Modifier.height(6.dp))
                    Pill(label.first, label.second)
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun CalendarCard(
    month: String,
    selectedDay: String,
    daysWithLessons: Set<String>,
    dayMarks: Map<String, LessonState?>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = remember(month) { Dates.daysOfMonth(month) }
    val leadingBlanks = remember(days) { days.firstOrNull()?.let { Dates.weekdayIndex(it) } ?: 0 }
    val cells = remember(days, leadingBlanks) { List(leadingBlanks) { "" } + days }

    JCard(
        modifier = modifier,
        contentPadding = PaddingValues(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Dates.monthTitle(month),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
            IconButton(onClick = onPrevious) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Предыдущий месяц")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Следующий месяц")
            }
        }
        Row(Modifier.fillMaxWidth().padding(end = 4.dp)) {
            Dates.weekdayHeaders.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().padding(end = 4.dp)) {
                week.forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day.isNotEmpty()) {
                            DayCell(
                                day = day,
                                selected = day == selectedDay,
                                hasLessons = day in daysWithLessons,
                                mark = dayMarks[day],
                                onClick = { onSelect(day) },
                            )
                        }
                    }
                }
                repeat(7 - week.size) {
                    Box(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            LegendItem(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), "есть пары")
            Spacer(Modifier.width(16.dp))
            LegendItem(MaterialTheme.extra.warning, "опоздание")
            Spacer(Modifier.width(16.dp))
            LegendItem(MaterialTheme.extra.danger, "пропуск")
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayCell(
    day: String,
    selected: Boolean,
    hasLessons: Boolean,
    mark: LessonState?,
    onClick: () -> Unit,
) {
    val isToday = Dates.isToday(day)
    val background = when {
        selected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.primary
        hasLessons -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    // Обычный день — серая точка, пропуск и опоздание — цветные. Красить обычный
    // день фирменным нельзя: малиновый рядом с красным "пропустил" не различить.
    val dotColor = when {
        mark == LessonState.Absent -> MaterialTheme.extra.danger
        mark == LessonState.Late -> MaterialTheme.extra.warning
        hasLessons -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> Color.Transparent
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(background)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.takeLast(2).trimStart('0'),
                style = MaterialTheme.typography.bodyMedium.tabular(),
                fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(if (mark != null) 6.dp else 4.dp)
                .background(dotColor, CircleShape),
        )
    }
}

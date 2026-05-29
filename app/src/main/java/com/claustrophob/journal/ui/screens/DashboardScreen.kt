package com.claustrophob.journal.ui.screens

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.claustrophob.journal.AppContainer
import com.claustrophob.journal.data.Dashboard
import com.claustrophob.journal.data.JournalRepository
import com.claustrophob.journal.data.MonthPoint
import com.claustrophob.journal.data.NextUp
import com.claustrophob.journal.data.NextUpResolver
import com.claustrophob.journal.data.net.FutureExamDto
import com.claustrophob.journal.data.net.LessonDto
import com.claustrophob.journal.data.net.UserInfoDto
import com.claustrophob.journal.ui.LoaderViewModel
import com.claustrophob.journal.ui.components.Avatar
import com.claustrophob.journal.ui.components.DeltaPill
import com.claustrophob.journal.ui.components.ErrorState
import com.claustrophob.journal.ui.components.GroupedList
import com.claustrophob.journal.ui.components.IconTile
import com.claustrophob.journal.ui.components.JCard
import com.claustrophob.journal.ui.components.ListRow
import com.claustrophob.journal.ui.components.ListSkeleton
import com.claustrophob.journal.ui.components.Pill
import com.claustrophob.journal.ui.components.RefreshableScreen
import com.claustrophob.journal.ui.components.RingGauge
import com.claustrophob.journal.ui.components.RowDivider
import com.claustrophob.journal.ui.components.SectionHeader
import com.claustrophob.journal.ui.components.Segmented
import com.claustrophob.journal.ui.components.StatusBanner
import com.claustrophob.journal.ui.components.TimelineDot
import com.claustrophob.journal.ui.components.TimelineRow
import com.claustrophob.journal.ui.components.TrendChart
import com.claustrophob.journal.ui.components.lessonDetails
import com.claustrophob.journal.ui.components.pressable
import com.claustrophob.journal.ui.components.shimmer
import com.claustrophob.journal.ui.journalViewModel
import com.claustrophob.journal.ui.theme.extra
import com.claustrophob.journal.ui.theme.tabular
import com.claustrophob.journal.util.Dates
import com.claustrophob.journal.util.pluralRu
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class DashboardViewModel(
    repository: JournalRepository,
    private val badge: MutableStateFlow<Int>,
) : LoaderViewModel<Dashboard>({ repository.dashboard() }) {

    fun publishBadge(count: Int) {
        badge.value = count
    }
}

@Composable
fun DashboardScreen(
    onOpenSchedule: () -> Unit,
    onOpenHomework: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenLeaders: () -> Unit,
) {
    val viewModel: DashboardViewModel = journalViewModel { container: AppContainer ->
        DashboardViewModel(container.repository, container.homeworkBadge)
    }
    val state by viewModel.state.collectAsState()
    val dashboard = state.data

    LaunchedEffect(dashboard?.activeHomework) {
        dashboard?.let { viewModel.publishBadge(it.activeHomework) }
    }

    RefreshableScreen(
        refreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        when {
            state.showSkeleton -> DashboardSkeleton()

            dashboard == null -> ErrorState(
                message = state.error ?: "Нет данных",
                onRetry = { viewModel.load(refresh = true) },
            )

            else -> DashboardContent(
                dashboard = dashboard,
                error = state.error,
                onDismissError = viewModel::dismissError,
                onRetry = { viewModel.load(refresh = true) },
                onOpenSchedule = onOpenSchedule,
                onOpenHomework = onOpenHomework,
                onOpenProgress = onOpenProgress,
                onOpenLeaders = onOpenLeaders,
            )
        }
    }
}

// Текущее время, обновляется само. Без этого карточка с парой застывала
// на том моменте, когда открыл экран.
@Composable
fun rememberNow(periodMs: Long = 30_000L): Long {
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(periodMs)
            value = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun DashboardContent(
    dashboard: Dashboard,
    error: String?,
    onDismissError: () -> Unit,
    onRetry: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenHomework: () -> Unit,
    onOpenProgress: () -> Unit,
    onOpenLeaders: () -> Unit,
) {
    val now = rememberNow()
    val today = remember(now / 60_000L) { Dates.today() }
    val lessons = remember(dashboard) { dashboard.today + dashboard.tomorrow }
    val nextUp = remember(lessons, today, now) { NextUpResolver.resolve(lessons, today, now) }
    val todayLessons = remember(lessons, today) {
        lessons.filter { it.date.take(10) == today }.sortedBy { it.startedAt }
    }

    // На сайте главная показывает текущий месяц, прошлые лежат в графиках.
    // Делаем так же, иначе цифры не сойдутся с журналом.
    val currentKey = today.take(7)
    val months = remember(dashboard, currentKey) { dashboardMonths(dashboard, currentKey) }
    var selected by remember(months) { mutableIntStateOf(months.lastIndex) }
    val point = months.getOrNull(selected)
    val isCurrent = point == null || point.date.take(7) == currentKey
    val scale = dashboard.performance.maxAllowedPoint.takeIf { it >= 1.0 }?.roundToInt() ?: 5
    var chartMetric by rememberSaveable { mutableIntStateOf(0) }

    // Без clipToBounds при прокрутке текст залезал под часы сверху.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "greeting") { Greeting(dashboard.user, today, now) }

        item(key = "banner") {
            StatusBanner(
                message = error,
                offline = true,
                onRetry = onRetry,
                onDismiss = onDismissError,
            )
        }

        item(key = "next-up") { NextUpCard(nextUp, onClick = onOpenSchedule) }

        item(key = "metrics") {
            MetricsCard(
                point = point,
                isCurrent = isCurrent,
                dashboard = dashboard,
                scale = scale,
                onBackToCurrent = { selected = months.lastIndex },
                onClick = onOpenProgress,
            )
        }

        item(key = "shortcuts") {
            Shortcuts(
                dashboard = dashboard,
                onOpenHomework = onOpenHomework,
                onOpenLeaders = onOpenLeaders,
            )
        }

        if (todayLessons.isNotEmpty()) {
            item(key = "today-header") {
                SectionHeader(text = "Сегодня", action = "Расписание", onAction = onOpenSchedule)
            }
            item(key = "today") { TodayCard(todayLessons, today, now) }
        }

        if (months.size > 1) {
            item(key = "trend-header") { SectionHeader("Динамика") }
            item(key = "trend") {
                TrendCard(
                    months = months,
                    selected = selected,
                    onSelect = { selected = it },
                    metric = chartMetric,
                    onMetric = { chartMetric = it },
                    scale = scale,
                )
            }
        }

        if (dashboard.futureExams.isNotEmpty()) {
            item(key = "exams-header") { SectionHeader("Экзамены") }
            item(key = "exams") { ExamsCard(dashboard.futureExams) }
        }
    }
}

private const val MONTHS_SHOWN = 8

// Месяцы для графика: прошлые берём из графиков сайта, текущий — из статистики
// за месяц. Летние месяцы без пар убираем, а то линия падает в ноль.
private fun dashboardMonths(dashboard: Dashboard, currentKey: String): List<MonthPoint> {
    val oldest = Dates.shiftMonth("$currentKey-01", -(MONTHS_SHOWN - 1)).take(7)
    val history = dashboard.months.filter { point ->
        val key = point.date.take(7)
        key >= oldest && key < currentKey && (point.attendance > 0.0 || point.average > 0.0)
    }
    val current = MonthPoint(
        date = "$currentKey-01",
        attendance = dashboard.attendance.statMonth,
        average = dashboard.performance.totalMonth,
    )
    return (history + current).sortedBy { it.date }.takeLast(MONTHS_SHOWN)
}

@Composable
private fun Greeting(user: UserInfoDto, today: String, now: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "${Dates.weekdayName(today)}, ${Dates.humanDay(today)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            val name = firstName(user.fullName)
            Text(
                text = if (name.isBlank()) greetingFor(now) else "${greetingFor(now)}, $name",
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(12.dp))
        Avatar(url = user.photo.takeIf { it.isNotBlank() }, name = user.fullName, size = 48)
    }
}

private fun greetingFor(now: Long): String {
    val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..16 -> "Добрый день"
        in 17..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
}

// ФИО в журнале начинается с фамилии: "Фокин Павел …". Обращаемся по имени.
private fun firstName(full: String): String {
    val parts = full.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return parts.getOrNull(1) ?: parts.firstOrNull().orEmpty()
}

// Красная карточка сверху: какая пара сейчас или какая следующая.
// Ради этого приложение чаще всего и открывают.
@Composable
private fun NextUpCard(state: NextUp, onClick: () -> Unit) {
    val extra = MaterialTheme.extra
    val onHero = extra.onHero

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd)))
            .pressable(onClick),
    ) {
        // Большая полупрозрачная иконка в углу, чтобы карточка не была просто
        // залитым прямоугольником.
        Icon(
            imageVector = Icons.Rounded.School,
            contentDescription = null,
            tint = onHero.copy(alpha = 0.08f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 30.dp, y = 34.dp)
                .size(160.dp),
        )
        Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            when (state) {
                is NextUp.Running -> {
                    HeroLabel(
                        text = "Сейчас идёт",
                        trailing = "${Dates.hhmm(state.lesson.startedAt)} – ${Dates.hhmm(state.lesson.finishedAt)}",
                    )
                    HeroLesson(state.lesson)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(onHero.copy(alpha = 0.25f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(state.progress.coerceIn(0.02f, 1f))
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(onHero),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Text(
                            text = "ещё ${durationLabel(state.minutesLeft)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = onHero,
                            modifier = Modifier.weight(1f),
                        )
                        state.following?.let { next ->
                            Text(
                                text = "дальше в ${Dates.hhmm(next.startedAt)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = onHero.copy(alpha = 0.8f),
                            )
                        }
                    }
                }

                is NextUp.Upcoming -> {
                    HeroLabel(
                        text = if (state.isFirst) "Первая пара" else "Следующая пара",
                        trailing = if (state.minutesUntil <= 12 * 60) {
                            "через ${durationLabel(state.minutesUntil)}"
                        } else {
                            null
                        },
                    )
                    HeroLesson(state.lesson)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "${Dates.hhmm(state.lesson.startedAt)} – ${Dates.hhmm(state.lesson.finishedAt)}",
                        style = MaterialTheme.typography.titleMedium.tabular(),
                        color = onHero,
                    )
                }

                is NextUp.DayOver -> DayOffContent("На сегодня всё", state.tomorrowFirst)
                is NextUp.FreeDay -> DayOffContent("Сегодня пар нет", state.tomorrowFirst)
            }
        }
    }
}

@Composable
private fun DayOffContent(label: String, tomorrowFirst: LessonDto?) {
    val onHero = MaterialTheme.extra.onHero
    HeroLabel(text = label, trailing = null)
    Text(
        text = tomorrowFirst?.let { "Завтра к ${Dates.hhmm(it.startedAt)}" } ?: "Можно выдохнуть",
        style = MaterialTheme.typography.headlineSmall,
        color = onHero,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = tomorrowFirst?.subjectName ?: "Завтра пар тоже нет",
        style = MaterialTheme.typography.bodyMedium,
        color = onHero.copy(alpha = 0.82f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HeroLabel(text: String, trailing: String?) {
    val onHero = MaterialTheme.extra.onHero
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = onHero,
            modifier = Modifier
                .background(onHero.copy(alpha = 0.18f), CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = onHero.copy(alpha = 0.9f),
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun HeroLesson(lesson: LessonDto) {
    val onHero = MaterialTheme.extra.onHero
    Text(
        text = lesson.subjectName,
        style = MaterialTheme.typography.titleLarge,
        color = onHero,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    val details = lessonDetails(lesson.roomName, lesson.teacherName)
    if (details.isNotBlank()) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = details,
            style = MaterialTheme.typography.bodyMedium,
            color = onHero.copy(alpha = 0.82f),
        )
    }
}

private fun durationLabel(minutes: Int): String {
    if (minutes < 60) return "$minutes мин"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "$hours ч" else "$hours ч $rest мин"
}

@Composable
private fun MetricsCard(
    point: MonthPoint?,
    isCurrent: Boolean,
    dashboard: Dashboard,
    scale: Int,
    onBackToCurrent: () -> Unit,
    onClick: () -> Unit,
) {
    val attendance = point?.attendance ?: 0.0
    val average = point?.average ?: 0.0
    val attendanceDelta = if (isCurrent) dashboard.attendance.diffMonth else 0.0
    val averageDelta = if (isCurrent) dashboard.performance.diffMonth else 0.0
    // Место под стрелки оставляем, только если есть что показать,
    // иначе под кольцами пустая полоса.
    val withDeltas = attendanceDelta != 0.0 || averageDelta != 0.0

    JCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = point?.let { Dates.monthTitle(it.date) }.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (isCurrent) {
                Text(
                    text = "за месяц",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "К текущему",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onBackToCurrent)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.Top) {
            Metric(
                modifier = Modifier.weight(1f),
                fraction = attendance / 100.0,
                value = if (attendance > 0.0) "${attendance.roundToInt()}%" else "—",
                label = "Посещаемость",
                delta = attendanceDelta,
                reserveDelta = withDeltas,
                color = MaterialTheme.extra.success,
            )
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .width(0.8.dp)
                    .height(112.dp)
                    .background(MaterialTheme.extra.hairline),
            )
            Metric(
                modifier = Modifier.weight(1f),
                fraction = average / scale,
                value = formatAverage(average),
                label = "Средний балл",
                delta = averageDelta,
                reserveDelta = withDeltas,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (!isCurrent && attendance == 0.0 && average == 0.0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "За этот месяц данных нет",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Metric(
    modifier: Modifier,
    fraction: Double,
    value: String,
    label: String,
    delta: Double,
    reserveDelta: Boolean,
    color: Color,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        RingGauge(fraction = fraction.toFloat(), value = value, color = color, diameter = 96.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (reserveDelta) {
            Spacer(Modifier.height(6.dp))
            if (delta != 0.0) DeltaPill(delta) else Spacer(Modifier.height(20.dp))
        }
    }
}

fun formatAverage(value: Double): String =
    if (value <= 0.0) "—" else String.format(Locale.US, "%.1f", value)

@Composable
private fun Shortcuts(
    dashboard: Dashboard,
    onOpenHomework: () -> Unit,
    onOpenLeaders: () -> Unit,
) {
    val active = dashboard.activeHomework
    val group = dashboard.groupPosition
    val stream = dashboard.streamPosition.studentPosition
    GroupedList {
        ListRow(
            title = "Задания",
            subtitle = if (active > 0) {
                val verb = if (active % 10 == 1 && active % 100 != 11) "ждёт" else "ждут"
                "${pluralRu(active, "задание", "задания", "заданий")} $verb сдачи"
            } else {
                "Всё сдано"
            },
            leading = {
                IconTile(Icons.AutoMirrored.Rounded.Assignment, MaterialTheme.extra.info)
            },
            trailing = if (active > 0) {
                { CountBadge(active) }
            } else {
                null
            },
            showChevron = true,
            onClick = onOpenHomework,
        )
        if (group.totalCount > 0 || stream > 0) {
            RowDivider(inset = 66)
            ListRow(
                title = if (group.studentPosition > 0) {
                    "${group.studentPosition}-е место в группе"
                } else {
                    "Рейтинг"
                },
                subtitle = listOfNotNull(
                    group.totalCount.takeIf { it > 0 }?.let { "из $it" },
                    stream.takeIf { it > 0 }?.let { "$it-е в потоке" },
                ).joinToString(" · "),
                leading = {
                    IconTile(Icons.Rounded.EmojiEvents, MaterialTheme.extra.warning)
                },
                onClick = onOpenLeaders,
            )
        }
    }
}

@Composable
private fun CountBadge(count: Int) {
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelMedium.tabular(),
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun TodayCard(lessons: List<LessonDto>, today: String, now: Long) {
    GroupedList {
        Spacer(Modifier.height(4.dp))
        lessons.forEachIndexed { index, lesson ->
            val start = Dates.momentOf(today, lesson.startedAt)
            val end = Dates.momentOf(today, lesson.finishedAt)
            val dot = when {
                start == null || end == null -> TimelineDot.Next
                now > end -> TimelineDot.Past
                now >= start -> TimelineDot.Now
                else -> TimelineDot.Next
            }
            TimelineRow(
                start = Dates.hhmm(lesson.startedAt),
                end = Dates.hhmm(lesson.finishedAt),
                title = lesson.subjectName,
                details = lessonDetails(lesson.roomName, lesson.teacherName),
                dot = dot,
                isFirst = index == 0,
                isLast = index == lessons.lastIndex,
                extra = if (dot == TimelineDot.Now) {
                    {
                        Spacer(Modifier.height(6.dp))
                        Pill("идёт сейчас", MaterialTheme.colorScheme.primary)
                    }
                } else {
                    null
                },
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TrendCard(
    months: List<MonthPoint>,
    selected: Int,
    onSelect: (Int) -> Unit,
    metric: Int,
    onMetric: (Int) -> Unit,
    scale: Int,
) {
    JCard {
        Segmented(
            options = listOf("Средний балл", "Посещаемость"),
            selected = metric,
            onSelect = onMetric,
        )
        Spacer(Modifier.height(14.dp))
        val values = months.map { month ->
            (if (metric == 0) month.average else month.attendance).takeIf { it > 0.0 }
        }
        val lowest = values.filterNotNull().minOrNull() ?: 0.0
        TrendChart(
            values = values,
            labels = months.map { Dates.monthShortName(it.date) },
            selected = selected,
            onSelect = onSelect,
            color = if (metric == 0) MaterialTheme.colorScheme.primary else MaterialTheme.extra.success,
            format = { if (metric == 0) formatAverage(it) else "${it.roundToInt()}%" },
            floor = if (metric == 0) (lowest - 0.5).coerceAtLeast(0.0) else (lowest - 10.0).coerceAtLeast(0.0),
            ceiling = if (metric == 0) scale.toDouble() else 100.0,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Нажмите на месяц — цифры выше покажут его",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ExamsCard(exams: List<FutureExamDto>) {
    GroupedList {
        val shown = exams.take(5)
        shown.forEachIndexed { index, exam ->
            val relative = Dates.relativeDayLabel(exam.date)
            ListRow(
                title = exam.spec,
                subtitle = "${Dates.weekdayName(exam.date)}, ${Dates.humanDay(exam.date)}",
                leading = {
                    IconTile(Icons.Rounded.EventAvailable, MaterialTheme.colorScheme.primary)
                },
                trailing = relative?.let { label ->
                    {
                        val soon = (Dates.daysUntil(exam.date) ?: 99) <= 3
                        Pill(
                            text = label,
                            color = if (soon) MaterialTheme.extra.danger else MaterialTheme.extra.warning,
                        )
                    }
                },
            )
            if (index != shown.lastIndex) RowDivider(inset = 66)
        }
    }
}

@Composable
private fun DashboardSkeleton() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .padding(start = 4.dp)
                .width(150.dp)
                .height(14.dp)
                .clip(RoundedCornerShape(6.dp))
                .shimmer(),
        )
        Box(
            Modifier
                .padding(start = 4.dp)
                .width(240.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .shimmer(),
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(MaterialTheme.shapes.large)
                .shimmer(),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(MaterialTheme.shapes.large)
                .shimmer(),
        )
        ListSkeleton(rows = 2)
    }
}

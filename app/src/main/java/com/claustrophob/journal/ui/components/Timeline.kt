package com.claustrophob.journal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.claustrophob.journal.ui.theme.extra
import com.claustrophob.journal.ui.theme.tabular

// Какую точку рисовать. Past — пара прошла, делаем бледной (на главной).
// Done — тоже прошла, но в расписании её не бледним, это обычная пара.
enum class TimelineDot { Past, Done, Now, Next, Late, Absent, Unmarked }

// Строка пары: время, точка на линии, название. Линия соединяет пары за день,
// сразу видно, где ты сейчас.
@Composable
fun TimelineRow(
    start: String,
    end: String,
    title: String,
    details: String,
    dot: TimelineDot,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val dimmed = dot == TimelineDot.Past
    val line = MaterialTheme.extra.hairline
    val dotColor = when (dot) {
        TimelineDot.Now -> MaterialTheme.colorScheme.primary
        TimelineDot.Late -> MaterialTheme.extra.warning
        TimelineDot.Absent -> MaterialTheme.extra.danger
        TimelineDot.Past, TimelineDot.Done, TimelineDot.Unmarked -> MaterialTheme.colorScheme.outline
        TimelineDot.Next -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val hollow = dot == TimelineDot.Next || dot == TimelineDot.Unmarked
    val surface = MaterialTheme.colorScheme.surfaceContainer

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(start = 16.dp, end = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .width(46.dp)
                .padding(top = 14.dp, bottom = 14.dp),
        ) {
            Text(
                text = start,
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = if (dimmed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = end,
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Canvas(
            Modifier
                .width(22.dp)
                .fillMaxHeight(),
        ) {
            val x = size.width / 2f
            val y = 22.dp.toPx()
            val width = 1.6.dp.toPx()
            if (!isFirst) drawLine(line, Offset(x, 0f), Offset(x, y), strokeWidth = width)
            if (!isLast) drawLine(line, Offset(x, y), Offset(x, size.height), strokeWidth = width)
            if (dot == TimelineDot.Now) {
                drawCircle(dotColor.copy(alpha = 0.18f), radius = 9.dp.toPx(), center = Offset(x, y))
            }
            drawCircle(surface, radius = 6.dp.toPx(), center = Offset(x, y))
            if (hollow) {
                drawCircle(
                    dotColor,
                    radius = 4.dp.toPx(),
                    center = Offset(x, y),
                    style = Stroke(width = 1.6.dp.toPx()),
                )
            } else {
                drawCircle(dotColor, radius = 4.5.dp.toPx(), center = Offset(x, y))
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 12.dp, bottom = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (dimmed) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (details.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            extra?.invoke(this)
        }

        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.padding(top = 12.dp),
                horizontalAlignment = Alignment.End,
            ) { trailing() }
        }
    }
}

// "Тышкевич Альберт Сергеевич" -> "Тышкевич А. С.", полное ФИО не влезает.
fun shortPersonName(full: String): String {
    val parts = full.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (parts.size < 2) return full.trim()
    return parts.first() + " " + parts.drop(1).take(2).joinToString(" ") { "${it.first()}." }
}

fun lessonDetails(room: String, teacher: String): String = listOfNotNull(
    room.takeIf { it.isNotBlank() }?.let { "ауд. $it" },
    teacher.takeIf { it.isNotBlank() }?.let(::shortPersonName),
).joinToString(" · ")

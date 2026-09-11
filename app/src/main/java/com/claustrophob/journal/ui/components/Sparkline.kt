package com.claustrophob.journal.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.claustrophob.journal.ui.theme.extra
import com.claustrophob.journal.ui.theme.tabular

// График по месяцам. Он же выбор месяца: ткнул в месяц — цифры сверху
// показывают его. Раньше отдельно были кнопки месяцев и график, и было
// непонятно, как они связаны.
// values — по одному на месяц, null — данных нет, точку не рисуем.
@Composable
fun TrendChart(
    values: List<Double?>,
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    format: (Double) -> String = { it.toString() },
    floor: Double? = null,
    ceiling: Double? = null,
    height: Dp = 132.dp,
) {
    if (values.isEmpty()) return
    val present = values.filterNotNull()
    val low = floor ?: (present.minOrNull() ?: 0.0)
    val high = ceiling ?: (present.maxOrNull() ?: 1.0)
    val span = (high - low).takeIf { it > 0.0001 } ?: 1.0

    val reveal by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "chart-reveal",
    )
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelMedium.tabular().copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
    )
    val guide = MaterialTheme.extra.hairline
    val highlight = color.copy(alpha = 0.08f)
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val count = values.size

    Column(
        modifier = modifier.pointerInput(count) {
            detectTapGestures { offset ->
                val slot = size.width.toFloat() / count
                onSelect((offset.x / slot).toInt().coerceIn(0, count - 1))
            }
        },
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val slot = size.width / count
            val top = 26.dp.toPx()
            val bottom = size.height - 8.dp.toPx()
            fun yOf(value: Double) = (bottom - ((value - low) / span) * (bottom - top)).toFloat()

            // Подсветка выбранного месяца — колонка во всю высоту.
            drawRoundRect(
                color = highlight,
                topLeft = Offset(slot * selected + 3.dp.toPx(), 0f),
                size = Size(slot - 6.dp.toPx(), size.height),
                cornerRadius = CornerRadius(10.dp.toPx()),
            )
            listOf(top, (top + bottom) / 2f, bottom).forEach { y ->
                drawLine(guide, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }

            val points = values.mapIndexedNotNull { index, value ->
                value?.let { index to Offset(slot * (index + 0.5f), yOf(it)) }
            }
            if (points.size >= 2) {
                val line = Path().apply {
                    moveTo(points.first().second.x, points.first().second.y)
                    for (i in 1 until points.size) {
                        val previous = points[i - 1].second
                        val current = points[i].second
                        val controlX = (previous.x + current.x) / 2f
                        cubicTo(controlX, previous.y, controlX, current.y, current.x, current.y)
                    }
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(points.last().second.x, bottom)
                    lineTo(points.first().second.x, bottom)
                    close()
                }
                drawPath(
                    path = fill,
                    brush = Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.22f * reveal), color.copy(alpha = 0f)),
                        startY = top,
                        endY = bottom,
                    ),
                )
                drawPath(
                    path = line,
                    color = color.copy(alpha = reveal),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }

            points.forEach { (index, point) ->
                val active = index == selected
                drawCircle(surface, radius = (if (active) 6.5.dp else 4.dp).toPx(), center = point)
                drawCircle(color, radius = (if (active) 5.dp else 2.8.dp).toPx(), center = point)
            }

            // Значение над выбранной точкой.
            val chosen = values.getOrNull(selected)
            if (chosen != null) {
                val text = measurer.measure(format(chosen), labelStyle)
                val center = slot * (selected + 0.5f)
                val x = (center - text.size.width / 2f)
                    .coerceIn(0f, size.width - text.size.width)
                val y = (yOf(chosen) - text.size.height - 8.dp.toPx()).coerceAtLeast(0f)
                drawText(text, topLeft = Offset(x, y))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val active = index == selected
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                    color = if (active) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

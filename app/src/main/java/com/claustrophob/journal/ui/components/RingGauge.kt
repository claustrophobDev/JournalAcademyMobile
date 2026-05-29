package com.claustrophob.journal.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.claustrophob.journal.ui.theme.tabular

// Кольцо вокруг цифры читается быстрее голого текста: видно "много или мало"
// раньше, чем глаз разберёт само число.
@Composable
fun RingGauge(
    fraction: Float,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    diameter: Dp = 92.dp,
    stroke: Dp = 9.dp,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "ring",
    )
    val track = color.copy(alpha = 0.13f)

    Box(
        modifier = modifier.size(diameter),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter)) {
            val width = stroke.toPx()
            val inset = width / 2f
            val arcSize = Size(size.width - width, size.height - width)
            drawArc(
                color = track,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = width, cap = StrokeCap.Round),
            )
            if (animated > 0.001f) {
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = 270f * animated,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = width, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.tabular(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

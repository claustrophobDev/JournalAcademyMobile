package com.claustrophob.journal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// Цвет оценок везде одинаковый. Раньше расписание считало из 5, а экран оценок
// из 12, и пятёрка там была оранжевая.
// Академия переходила с 12-балльной на 5-балльную, а в журнале всё обучение,
// так что бывают обе. Шкалу API не говорит, поэтому если есть оценка больше 5,
// значит шкала из 12.
enum class GradeTone { Excellent, Good, Fair, Poor, None }

fun scaleOf(values: Iterable<Number>): Int = if (values.any { it.toDouble() > 5.0 }) 12 else 5

fun gradeTone(value: Double, scale: Int = if (value > 5.0) 12 else 5): GradeTone = when {
    value <= 0.0 -> GradeTone.None
    scale > 5 -> when {
        value >= 10.0 -> GradeTone.Excellent
        value >= 7.0 -> GradeTone.Good
        value >= 4.0 -> GradeTone.Fair
        else -> GradeTone.Poor
    }

    else -> when {
        value >= 4.5 -> GradeTone.Excellent
        value >= 3.5 -> GradeTone.Good
        value >= 2.5 -> GradeTone.Fair
        else -> GradeTone.Poor
    }
}

val GradeTone.color: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        GradeTone.Excellent -> MaterialTheme.extra.success
        GradeTone.Good -> MaterialTheme.extra.info
        GradeTone.Fair -> MaterialTheme.extra.warning
        GradeTone.Poor -> MaterialTheme.extra.danger
        GradeTone.None -> MaterialTheme.colorScheme.onSurfaceVariant
    }

val GradeTone.container: Color
    @Composable @ReadOnlyComposable get() = when (this) {
        GradeTone.Excellent -> MaterialTheme.extra.successContainer
        GradeTone.Good -> MaterialTheme.extra.infoContainer
        GradeTone.Fair -> MaterialTheme.extra.warningContainer
        GradeTone.Poor -> MaterialTheme.extra.dangerContainer
        GradeTone.None -> MaterialTheme.colorScheme.surfaceContainerHigh
    }

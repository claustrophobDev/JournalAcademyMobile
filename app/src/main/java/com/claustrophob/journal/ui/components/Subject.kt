package com.claustrophob.journal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// У каждого предмета свой цвет, одинаковый на всех экранах. Так нужный
// предмет в списке находишь по цвету, даже не читая.
private val subjectHues = listOf(
    Color(0xFF2F7CF6),
    Color(0xFF12A36B),
    Color(0xFF8B5CF6),
    Color(0xFFE08A00),
    Color(0xFFE5488A),
    Color(0xFF0EA5C6),
    Color(0xFFE2463B),
    Color(0xFF65A30D),
)

fun subjectColor(name: String): Color =
    subjectHues[(name.trim().lowercase().hashCode() and 0x7FFFFFFF) % subjectHues.size]

// "Основы алгоритмизации и программирования" -> "ОА", короткие слова вроде "и" пропускаем.
fun subjectInitials(name: String): String {
    val words = name.trim().split(Regex("[\\s,.-]+")).filter { it.length > 2 }
    return when {
        words.size >= 2 -> "${words[0].first()}${words[1].first()}"
        words.size == 1 -> words[0].take(2)
        else -> name.trim().take(2)
    }.uppercase().ifBlank { "?" }
}

@Composable
fun SubjectTile(name: String, modifier: Modifier = Modifier, size: Dp = 38.dp) {
    val color = subjectColor(name)
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = subjectInitials(name),
            style = if (size >= 36.dp) {
                MaterialTheme.typography.labelLarge
            } else {
                MaterialTheme.typography.labelMedium
            },
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
        )
    }
}

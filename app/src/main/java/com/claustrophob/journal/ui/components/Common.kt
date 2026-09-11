package com.claustrophob.journal.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.claustrophob.journal.ui.theme.extra
import com.claustrophob.journal.ui.theme.gradeTone
import com.claustrophob.journal.ui.theme.color
import com.claustrophob.journal.ui.theme.container
import com.claustrophob.journal.ui.theme.tabular
import com.claustrophob.journal.util.Downloader
import java.util.Locale
import kotlin.math.abs

// При нажатии карточка чуть уменьшается и пружинит обратно. Мелочь, но без
// этого всё какое-то деревянное.
fun Modifier.pressable(
    onClick: (() -> Unit)?,
    enabled: Boolean = true,
): Modifier = if (onClick == null) this else composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.975f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick,
        )
}

// Обычная карточка: белая на сером фоне, без теней и рамок.
@Composable
fun JCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: Shape = MaterialTheme.shapes.large,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .pressable(onClick)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    JCard(modifier = modifier) {
        if (title != null || trailing != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

// Заголовок раздела на фоне, без карточки вокруг. Справа — ссылка "Все".
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        if (action != null && onAction != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

// Иконка в цветном квадратике, по цвету раздел находится быстрее.
@Composable
fun IconTile(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    container: Color = tint.copy(alpha = 0.13f),
) {
    Box(
        modifier = modifier
            .size(size)
            .background(container, RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

// Строка списка: плашка слева, текст, справа что угодно или стрелка.
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    showChevron: Boolean = onClick != null && trailing == null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
        if (showChevron) {
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// Сгруппированный список: одна карточка на весь список вместо карточки на
// каждую строку. Десяток отдельных плашек подряд выглядит как каша.
@Composable
fun GroupedList(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        content = content,
    )
}

// Для длинных списков, где каждая строка — отдельный item в LazyColumn.
// Скругляем верх первой и низ последней, и выглядит как одна карточка.
fun segmentShape(index: Int, count: Int): Shape {
    val radius = 20.dp
    val top = if (index == 0) radius else 0.dp
    val bottom = if (index == count - 1) radius else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

// Разделитель между строками. С отступом слева, чтобы не резал строку насквозь.
@Composable
fun RowDivider(inset: Int = 16) {
    HorizontalDivider(
        modifier = Modifier.padding(start = inset.dp),
        thickness = 0.8.dp,
        color = MaterialTheme.extra.hairline,
    )
}

// Короткая метка: статус, срок, "идёт".
@Composable
fun Pill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    container: Color = color.copy(alpha = 0.13f),
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .background(container, CircleShape)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
        )
    }
}

// Изменение за период: ▲ зелёным, ▼ красным. Ноль — не показываем вовсе.
@Composable
fun DeltaPill(delta: Double, modifier: Modifier = Modifier, suffix: String = "") {
    if (delta == 0.0 || delta.isNaN()) return
    val up = delta > 0
    val magnitude = abs(delta)
    val text = if (magnitude % 1.0 == 0.0) {
        magnitude.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", magnitude)
    }
    Pill(
        text = (if (up) "▲ " else "▼ ") + text + suffix,
        color = if (up) MaterialTheme.extra.success else MaterialTheme.extra.danger,
        modifier = modifier,
    )
}

// Оценка в плашке. Цвет — по шкале, см. Grades.kt.
@Composable
fun GradeBadge(
    value: Double,
    modifier: Modifier = Modifier,
    scale: Int = if (value > 5.0) 12 else 5,
    size: Dp = 34.dp,
    text: String = formatGrade(value),
) {
    val tone = gradeTone(value, scale)
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = size, minHeight = size)
            .background(tone.container, RoundedCornerShape(size * 0.32f))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.tabular(),
            fontWeight = FontWeight.Bold,
            color = tone.color,
        )
    }
}

fun formatGrade(value: Double): String = when {
    value <= 0.0 -> "—"
    value % 1.0 == 0.0 -> value.toInt().toString()
    else -> String.format(Locale.US, "%.1f", value)
}

// Переключатель на две-три вкладки. Бегунок едет, а не перескакивает.
@Composable
fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val offset by animateDpAsState(
            targetValue = segment * selected,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            label = "segment",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segment)
                .fillMaxHeight()
                .graphicsLayer {
                    shadowElevation = 2.dp.toPx()
                    shape = RoundedCornerShape(10.dp)
                    clip = true
                }
                .background(MaterialTheme.extra.segmentThumb),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, label ->
                val active = index == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            if (!active) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(index)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// Ряд фильтров с прокруткой. Для четырёх вариантов и больше, где Segmented не влезает.
// counts — число рядом с подписью, null — не показывать.
@Composable
fun ChipRow(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    counts: List<Int?> = emptyList(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    val haptics = LocalHapticFeedback.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(options) { index, label ->
            val active = index == selected
            val content = if (active) {
                MaterialTheme.colorScheme.inverseOnSurface
            } else {
                MaterialTheme.colorScheme.onSurface
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.inverseSurface
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    )
                    .clickable {
                        if (!active) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onSelect(index)
                        }
                    }
                    .padding(horizontal = 15.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = label, style = MaterialTheme.typography.labelLarge, color = content)
                val count = counts.getOrNull(index)
                if (count != null && count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelLarge.tabular(),
                        color = content.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBanner(
    message: String?,
    modifier: Modifier = Modifier,
    offline: Boolean = false,
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val container = if (offline) {
            MaterialTheme.colorScheme.surfaceContainer
        } else {
            MaterialTheme.extra.dangerContainer
        }
        val tint = if (offline) MaterialTheme.extra.warning else MaterialTheme.extra.danger
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(container)
                .padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (offline) Icons.Rounded.CloudOff else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text("Повторить") }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Скрыть",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Два круга вокруг иконки, чтобы не было совсем пусто.
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Rounded.CloudOff,
        title = "Не удалось загрузить",
        subtitle = message,
        modifier = modifier,
        action = {
            TextButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Повторить")
            }
        },
    )
}

// Бегущий блик на заглушках, пока грузится. Раньше они просто мигали,
// и выглядело так, будто что-то сломалось.
fun Modifier.shimmer(): Modifier = composed {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerLowest
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300)),
        label = "shimmer-progress",
    )
    this.background(
        Brush.linearGradient(
            colors = listOf(base, highlight.copy(alpha = 0.9f), base),
            start = Offset(progress * 600f - 300f, 0f),
            end = Offset(progress * 600f + 300f, 0f),
        ),
    )
}

@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    height: Int = 20,
    corner: Int = 8,
) {
    Box(
        modifier = modifier
            .height(height.dp)
            .clip(RoundedCornerShape(corner.dp))
            .shimmer(),
    )
}

@Composable
fun ListSkeleton(rows: Int = 4, modifier: Modifier = Modifier) {
    GroupedList(modifier) {
        repeat(rows) { index ->
            Row(
                Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .shimmer(),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    ShimmerBlock(Modifier.fillMaxWidth(0.35f), height = 11)
                    Spacer(Modifier.height(8.dp))
                    ShimmerBlock(Modifier.fillMaxWidth(0.8f), height = 15)
                    Spacer(Modifier.height(8.dp))
                    ShimmerBlock(Modifier.fillMaxWidth(0.5f), height = 11)
                }
            }
            if (index != rows - 1) RowDivider(inset = 68)
        }
    }
}

@Composable
fun Avatar(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 44,
) {
    val initials = name.trim().split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifBlank { "?" }

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            style = if (size >= 56) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleSmall
            },
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                // Путь к фото бывает и полный, и относительный.
                model = Downloader.absolute(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size.dp)
                    .clip(CircleShape),
            )
        }
    }
}

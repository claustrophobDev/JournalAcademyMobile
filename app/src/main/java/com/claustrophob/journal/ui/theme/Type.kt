package com.claustrophob.journal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private fun style(
    size: TextUnit,
    line: TextUnit,
    weight: FontWeight,
    tracking: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size,
    lineHeight = line,
    letterSpacing = tracking,
)

// Размеры шрифтов свои. У Material в больших заголовках буквы стоят далеко
// друг от друга, и русский текст от этого расползается.
// headlineMedium — большой заголовок экрана (его берёт LargeTopAppBar).
val JournalTypography = Typography(
    displayLarge = style(48.sp, 52.sp, FontWeight.Bold, (-1.4).sp),
    displayMedium = style(40.sp, 46.sp, FontWeight.Bold, (-1.1).sp),
    displaySmall = style(34.sp, 40.sp, FontWeight.Bold, (-0.9).sp),
    headlineLarge = style(32.sp, 38.sp, FontWeight.Bold, (-0.8).sp),
    headlineMedium = style(30.sp, 36.sp, FontWeight.Bold, (-0.7).sp),
    headlineSmall = style(23.sp, 29.sp, FontWeight.Bold, (-0.4).sp),
    titleLarge = style(20.sp, 26.sp, FontWeight.SemiBold, (-0.3).sp),
    titleMedium = style(17.sp, 22.sp, FontWeight.SemiBold, (-0.2).sp),
    titleSmall = style(15.sp, 20.sp, FontWeight.SemiBold, (-0.1).sp),
    bodyLarge = style(16.sp, 22.sp, FontWeight.Normal, (-0.1).sp),
    bodyMedium = style(14.sp, 20.sp, FontWeight.Normal),
    bodySmall = style(13.sp, 18.sp, FontWeight.Normal, 0.05.sp),
    labelLarge = style(14.sp, 18.sp, FontWeight.SemiBold),
    labelMedium = style(12.sp, 16.sp, FontWeight.Medium, 0.1.sp),
    labelSmall = style(11.sp, 14.sp, FontWeight.Medium, 0.2.sp),
)

// Цифры одинаковой ширины: проценты и баллы не пляшут, когда меняются.
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

// Скругления: карточки 20, мелкое 12. Если делать 30, всё превращается в таблетки.
val JournalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

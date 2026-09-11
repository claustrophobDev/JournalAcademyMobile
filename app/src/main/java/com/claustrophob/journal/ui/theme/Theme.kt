package com.claustrophob.journal.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Цвет снят пипеткой с логотипа академии.
val BrandRed = Color(0xFFDC1146)

// Цвета, которых нет в colorScheme: зелёный, жёлтый, синий, красный и градиент
// для карточки на главной. Брать отсюда, а не писать свой зелёный на каждом экране.
@Immutable
data class ExtraColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
    val hairline: Color,
    // Бегунок переключателя. На тёмном он должен быть светлее дорожки, а не
    // темнее — иначе выглядит как дырка.
    val segmentThumb: Color,
)

private val LightExtra = ExtraColors(
    success = Color(0xFF16864A),
    successContainer = Color(0xFFE1F3E8),
    warning = Color(0xFFC06400),
    warningContainer = Color(0xFFFCEEDB),
    info = Color(0xFF2560E0),
    infoContainer = Color(0xFFE3EBFC),
    danger = Color(0xFFD42A1E),
    dangerContainer = Color(0xFFFCE5E2),
    heroStart = Color(0xFFE0144A),
    heroEnd = Color(0xFF980A31),
    onHero = Color.White,
    hairline = Color(0x17000000),
    segmentThumb = Color.White,
)

private val DarkExtra = ExtraColors(
    success = Color(0xFF4FD186),
    successContainer = Color(0xFF12301F),
    warning = Color(0xFFF4AE45),
    warningContainer = Color(0xFF36280F),
    info = Color(0xFF74A6FF),
    infoContainer = Color(0xFF172846),
    danger = Color(0xFFFF7D70),
    dangerContainer = Color(0xFF3B1714),
    heroStart = Color(0xFFBE123F),
    heroEnd = Color(0xFF660822),
    onHero = Color.White,
    hairline = Color(0x1FFFFFFF),
    segmentThumb = Color(0xFF3A3A42),
)

// Старые названия, остались с прошлой версии.
val PositiveGreen = LightExtra.success
val WarningAmber = LightExtra.warning
val LateOrange = Color(0xFFD9731A)

// Фон серый, карточки белые, малиновый только для акцентов.
// Так края карточек видно без рамок и теней.
private val LightColors = lightColorScheme(
    primary = Color(0xFFD0103F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3E9),
    onPrimaryContainer = Color(0xFF5E0019),
    secondary = Color(0xFF5A5A66),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFECECF0),
    onSecondaryContainer = Color(0xFF26262C),
    tertiary = Color(0xFF8A5A24),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE9D1),
    onTertiaryContainer = Color(0xFF2E1A05),

    background = Color(0xFFF2F2F5),
    onBackground = Color(0xFF131317),
    surface = Color(0xFFF2F2F5),
    onSurface = Color(0xFF131317),
    surfaceVariant = Color(0xFFE8E8ED),
    onSurfaceVariant = Color(0xFF6A6A75),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F8FA),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFECECF0),
    surfaceContainerHighest = Color(0xFFE3E3E9),
    inverseSurface = Color(0xFF26262C),
    inverseOnSurface = Color(0xFFF2F2F5),

    outline = Color(0xFFA2A2AC),
    outlineVariant = Color(0xFFE3E3E9),
    error = Color(0xFFD42A1E),
    onError = Color.White,
    errorContainer = Color(0xFFFCE5E2),
    onErrorContainer = Color(0xFF5A120C),
)

private val DarkColors = darkColorScheme(
    // На тёмном фирменный малиновый не читается, берём светлый тон.
    primary = Color(0xFFFF5E80),
    onPrimary = Color(0xFF45000F),
    primaryContainer = Color(0xFF5A0C22),
    onPrimaryContainer = Color(0xFFFFD9E0),
    secondary = Color(0xFFC4C4CE),
    onSecondary = Color(0xFF2A2A30),
    secondaryContainer = Color(0xFF2D2D33),
    onSecondaryContainer = Color(0xFFE6E6EB),
    tertiary = Color(0xFFEFBB84),
    onTertiary = Color(0xFF432A0A),
    tertiaryContainer = Color(0xFF5E3F18),
    onTertiaryContainer = Color(0xFFFFE7CC),

    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFF2F2F5),
    surface = Color(0xFF0B0B0D),
    onSurface = Color(0xFFF2F2F5),
    surfaceVariant = Color(0xFF25252A),
    onSurfaceVariant = Color(0xFF9C9CA7),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFF060607),
    surfaceContainerLow = Color(0xFF111114),
    surfaceContainer = Color(0xFF17171B),
    surfaceContainerHigh = Color(0xFF212126),
    surfaceContainerHighest = Color(0xFF2B2B31),
    inverseSurface = Color(0xFFE6E6EB),
    inverseOnSurface = Color(0xFF1A1A1E),

    outline = Color(0xFF70707B),
    outlineVariant = Color(0xFF2A2A30),
    error = Color(0xFFFF7D70),
    onError = Color(0xFF4A0D08),
    errorContainer = Color(0xFF3B1714),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val LocalExtraColors = staticCompositionLocalOf { LightExtra }

val MaterialTheme.extra: ExtraColors
    @Composable @ReadOnlyComposable get() = LocalExtraColors.current

enum class ThemeMode { System, Light, Dark }

@Composable
fun JournalTheme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        dark -> DarkColors
        else -> LightColors
    }
    // XML-тема знает только системную настройку, поэтому переключатель темы внутри приложения
    // перекрашивает иконки статус-бара и навигации сам.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }

    CompositionLocalProvider(LocalExtraColors provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(
            colorScheme = colors,
            typography = JournalTypography,
            shapes = JournalShapes,
            content = content,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

package com.serafim.tetris.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.CubicBezierEasing

/** Палитра Material 3 из оригинальной таблицы CSS-переменных. */
object M3 {
    val Surface = Color(0xFF131619)
    val SurfaceContainer = Color(0xFF1E2125)
    val SurfaceContainerHigh = Color(0xFF282B2F)
    val SurfaceContainerHighest = Color(0xFF33363A)
    val SurfaceDim = Color(0xFF1B1F24)
    val Primary = Color(0xFFA8C7FA)
    val OnPrimary = Color(0xFF062E6F)
    val SecondaryContainer = Color(0xFF0B5C9E)
    val OnSecondaryContainer = Color(0xFFD3E3FD)
    val OnSurface = Color(0xFFE3E3E3)
    val OnSurfaceVariant = Color(0xFFC4C7C5)
    val Outline = Color(0xFF8E9199)
    val OutlineVariant = Color(0xFF444746)
    val Xp = Color(0xFFA9E5A5)
    val XpDeep = Color(0xFF7FCB8C)

    /** Необратимое действие — сброс статистики. Белый по нему читается с запасом. */
    val Danger = Color(0xFFD32F2F)
    val OnDanger = Color(0xFFFFFFFF)

    /** Пустая клетка стакана при сборке и разборе. */
    val Empty = Color(0xFF3C4046)
}

/** Те же две кривые, что и в CSS: --expo и --emphasized. */
val Expo = CubicBezierEasing(0.19f, 1f, 0.22f, 1f)
val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Зеркало [Expo]: трогается еле-еле и улетает рывком — для ухода с экрана. */
val ExpoIn = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)

private val Scheme = darkColorScheme(
    primary = M3.Primary,
    onPrimary = M3.OnPrimary,
    secondaryContainer = M3.SecondaryContainer,
    onSecondaryContainer = M3.OnSecondaryContainer,
    background = M3.Surface,
    onBackground = M3.OnSurface,
    surface = M3.Surface,
    onSurface = M3.OnSurface,
    surfaceVariant = M3.SurfaceContainerHigh,
    onSurfaceVariant = M3.OnSurfaceVariant,
    surfaceContainer = M3.SurfaceContainer,
    surfaceContainerHigh = M3.SurfaceContainerHigh,
    surfaceContainerHighest = M3.SurfaceContainerHighest,
    outline = M3.Outline,
    outlineVariant = M3.OutlineVariant,
)

/** Roboto — системный шрифт Android, отдельная поставка не нужна. */
private val Roboto = FontFamily.Default

private val Type = Typography(
    displaySmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 36.sp),
    headlineMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 11.sp),
)

@Composable
fun TetrisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Type, content = content)
}

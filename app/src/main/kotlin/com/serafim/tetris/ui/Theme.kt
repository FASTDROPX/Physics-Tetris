package com.serafim.tetris.ui

import android.content.Context
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.CubicBezierEasing

/**
 * Набор цветов игры. Раньше он был один и лежал прямо в [M3]; теперь тем
 * три, и каждая — это такой набор, который подставляется в [M3] целиком.
 */
data class Palette(
    val surface: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val surfaceDim: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val empty: Color,
)

/** Какой темой красить игру. */
enum class ThemeKind(val title: String, val note: String) {
    /** Та, что была всегда: палитра из оригинальной HTML-игры. */
    DARK("Тёмная", "Как в оригинале игры"),

    /** Цвета берутся у системы — она подбирает их по обоям телефона. */
    MONET("Monet", "Цвета обоев телефона"),

    /** Чёрный по-настоящему: на OLED-экране такие пиксели просто гаснут. */
    AMOLED("AMOLED", "Чистый чёрный фон"),
    ;

    companion object {
        fun of(name: String?): ThemeKind = entries.firstOrNull { it.name == name } ?: DARK

        /** Monet умеет только Android 12 и новее — там, где есть системные цвета. */
        val monetAvailable: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}

/** Палитра из оригинальной таблицы CSS-переменных. */
val DarkPalette = Palette(
    surface = Color(0xFF131619),
    surfaceContainer = Color(0xFF1E2125),
    surfaceContainerHigh = Color(0xFF282B2F),
    surfaceContainerHighest = Color(0xFF33363A),
    surfaceDim = Color(0xFF1B1F24),
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    secondaryContainer = Color(0xFF0B5C9E),
    onSecondaryContainer = Color(0xFFD3E3FD),
    onSurface = Color(0xFFE3E3E3),
    onSurfaceVariant = Color(0xFFC4C7C5),
    outline = Color(0xFF8E9199),
    outlineVariant = Color(0xFF444746),
    empty = Color(0xFF3C4046),
)

/**
 * AMOLED: фон чистый чёрный, а всё, что над ним, поднимается ступеньками —
 * иначе карточки слились бы с пустотой. Цвета кнопок и текста те же:
 * чёрный меняет глубину, а не облик игры.
 */
val AmoledPalette = DarkPalette.copy(
    surface = Color(0xFF000000),
    surfaceDim = Color(0xFF000000),
    surfaceContainer = Color(0xFF0B0D0F),
    surfaceContainerHigh = Color(0xFF15181B),
    surfaceContainerHighest = Color(0xFF1F2327),
    empty = Color(0xFF26292E),
)

/**
 * Monet: цвета, которые система насчитала по обоям. Берётся тёмная ветка —
 * игра всегда тёмная, и светлый вариант сделал бы стакан белым листом.
 */
fun monetPalette(context: Context): Palette? {
    if (!ThemeKind.monetAvailable) return null
    val s = dynamicDarkColorScheme(context)
    return Palette(
        surface = s.surface,
        surfaceContainer = s.surfaceContainer,
        surfaceContainerHigh = s.surfaceContainerHigh,
        surfaceContainerHighest = s.surfaceContainerHighest,
        surfaceDim = s.surfaceDim,
        primary = s.primary,
        onPrimary = s.onPrimary,
        secondaryContainer = s.secondaryContainer,
        onSecondaryContainer = s.onSecondaryContainer,
        onSurface = s.onSurface,
        onSurfaceVariant = s.onSurfaceVariant,
        outline = s.outline,
        outlineVariant = s.outlineVariant,
        empty = s.surfaceContainerHighest,
    )
}

/** Палитра темы; Monet без системных цветов откатывается на тёмную. */
fun paletteOf(kind: ThemeKind, context: Context?): Palette = when (kind) {
    ThemeKind.DARK -> DarkPalette
    ThemeKind.AMOLED -> AmoledPalette
    ThemeKind.MONET -> context?.let { monetPalette(it) } ?: DarkPalette
}

/**
 * Цвета игры. Читаются отовсюду как раньше — M3.Surface, — но теперь это
 * состояние Compose: подстановка другой палитры сама перерисовывает всё,
 * что эти цвета читало, от меню до стакана.
 *
 * Цвета фигур сюда не входят: они у тетриса свои и от темы не зависят.
 */
object M3 {
    var Surface by mutableStateOf(DarkPalette.surface); private set
    var SurfaceContainer by mutableStateOf(DarkPalette.surfaceContainer); private set
    var SurfaceContainerHigh by mutableStateOf(DarkPalette.surfaceContainerHigh); private set
    var SurfaceContainerHighest by mutableStateOf(DarkPalette.surfaceContainerHighest); private set
    var SurfaceDim by mutableStateOf(DarkPalette.surfaceDim); private set
    var Primary by mutableStateOf(DarkPalette.primary); private set
    var OnPrimary by mutableStateOf(DarkPalette.onPrimary); private set
    var SecondaryContainer by mutableStateOf(DarkPalette.secondaryContainer); private set
    var OnSecondaryContainer by mutableStateOf(DarkPalette.onSecondaryContainer); private set
    var OnSurface by mutableStateOf(DarkPalette.onSurface); private set
    var OnSurfaceVariant by mutableStateOf(DarkPalette.onSurfaceVariant); private set
    var Outline by mutableStateOf(DarkPalette.outline); private set
    var OutlineVariant by mutableStateOf(DarkPalette.outlineVariant); private set

    /** Пустая клетка стакана при сборке и разборе. */
    var Empty by mutableStateOf(DarkPalette.empty); private set

    /** Опыт всегда зелёный, а необратимое действие — красное: тема их не трогает. */
    val Xp = Color(0xFFA9E5A5)
    val XpDeep = Color(0xFF7FCB8C)

    /** Необратимое действие — сброс статистики. Белый по нему читается с запасом. */
    val Danger = Color(0xFFD32F2F)
    val OnDanger = Color(0xFFFFFFFF)

    fun apply(p: Palette) {
        Surface = p.surface
        SurfaceContainer = p.surfaceContainer
        SurfaceContainerHigh = p.surfaceContainerHigh
        SurfaceContainerHighest = p.surfaceContainerHighest
        SurfaceDim = p.surfaceDim
        Primary = p.primary
        OnPrimary = p.onPrimary
        SecondaryContainer = p.secondaryContainer
        OnSecondaryContainer = p.onSecondaryContainer
        OnSurface = p.onSurface
        OnSurfaceVariant = p.onSurfaceVariant
        Outline = p.outline
        OutlineVariant = p.outlineVariant
        Empty = p.empty
    }
}

/** Те же две кривые, что и в CSS: --expo и --emphasized. */
val Expo = CubicBezierEasing(0.19f, 1f, 0.22f, 1f)
val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Зеркало [Expo]: трогается еле-еле и улетает рывком — для ухода с экрана. */
val ExpoIn = CubicBezierEasing(0.7f, 0f, 0.84f, 0f)

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

private fun schemeOf(p: Palette) = darkColorScheme(
    primary = p.primary,
    onPrimary = p.onPrimary,
    secondaryContainer = p.secondaryContainer,
    onSecondaryContainer = p.onSecondaryContainer,
    background = p.surface,
    onBackground = p.onSurface,
    surface = p.surface,
    onSurface = p.onSurface,
    surfaceVariant = p.surfaceContainerHigh,
    onSurfaceVariant = p.onSurfaceVariant,
    surfaceContainer = p.surfaceContainer,
    surfaceContainerHigh = p.surfaceContainerHigh,
    surfaceContainerHighest = p.surfaceContainerHighest,
    outline = p.outline,
    outlineVariant = p.outlineVariant,
)

/**
 * Тема игры. [palette] подставляется в [M3] до того, как что-либо
 * нарисуется, поэтому смена темы доходит и до тех мест, которые берут
 * цвета не из MaterialTheme, а прямо из [M3] — а это почти вся отрисовка.
 */
@Composable
fun TetrisTheme(palette: Palette = DarkPalette, content: @Composable () -> Unit) {
    M3.apply(palette)
    MaterialTheme(colorScheme = schemeOf(palette), typography = Type, content = content)
}

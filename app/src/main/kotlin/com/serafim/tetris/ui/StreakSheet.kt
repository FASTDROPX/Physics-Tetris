package com.serafim.tetris.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serafim.tetris.game.FlameTier
import com.serafim.tetris.game.STREAK_DAY_MS
import com.serafim.tetris.game.StreakState
import com.serafim.tetris.game.formatTotal
import com.serafim.tetris.game.pluralRu
import kotlin.math.PI
import kotlin.math.sin

/**
 * Серия глазами интерфейса: только значения, никаких изменяемых объектов —
 * иначе Compose с его пропуском неизменившихся функций не перерисовал бы
 * карточку, когда серия выросла.
 */
data class StreakView(
    /** Дней подряд, как их видит игрок: прерванная серия — ноль. */
    val days: Int = 0,
    val best: Int = 0,
    val state: StreakState = StreakState.NONE,
    /** Сколько всего дней засчитано, не обязательно подряд. */
    val totalDays: Int = 0,
    /** Мс игры за последние дни, от старого к сегодняшнему; последний — сегодня. */
    val history: List<Long> = emptyList(),
    /** День недели сегодня: 1 — понедельник, 7 — воскресенье. */
    val weekday: Int = 1,
    val longestDayMs: Long = 0L,
) {
    /** Огонёк: у прерванной серии он погас, как бы длинна она ни была. */
    val tier: FlameTier get() = if (state == StreakState.NONE) FlameTier.OUT else FlameTier.of(days)

    /** Последние семь дней для полоски в меню. */
    val week: List<Long> get() = history.takeLast(7).let { List(7 - it.size) { 0L } + it }

    val weekMs: Long get() = week.sum()
}

// ---------- огонёк ----------

/** Цвета одного огонька: внешний язык сверху вниз, сердцевина, свечение. */
private class FlameColors(
    val outerTop: Color,
    val outerBottom: Color,
    val coreTop: Color,
    val coreBottom: Color,
    val glow: Color,
)

/**
 * Цвета по ступеням. Чем длиннее серия, тем горячее пламя: красная искра,
 * оранжевый огонёк, жёлтое пламя, потом синее и фиолетовое — как у
 * настоящего огня, который синеет с жаром. Радужное — отдельно, в
 * [flameBrush]: у него не два цвета, а семь.
 */
private fun colorsOf(tier: FlameTier): FlameColors = when (tier) {
    FlameTier.OUT -> FlameColors(
        Color(0xFF5A5F66), Color(0xFF3E4248), Color(0xFF6E737A), Color(0xFF555A61), Color.Transparent,
    )
    FlameTier.SPARK -> FlameColors(
        Color(0xFFFF6A3D), Color(0xFFD9352B), Color(0xFFFFC27A), Color(0xFFFF8248), Color(0xFFFF4A2E),
    )
    FlameTier.EMBER -> FlameColors(
        Color(0xFFFFB23E), Color(0xFFFF7A1A), Color(0xFFFFF0A8), Color(0xFFFFC24D), Color(0xFFFF9A1F),
    )
    FlameTier.FLAME -> FlameColors(
        Color(0xFFFFE45C), Color(0xFFFFB21E), Color(0xFFFFFBE0), Color(0xFFFFE58A), Color(0xFFFFD23F),
    )
    FlameTier.BLUE -> FlameColors(
        Color(0xFF7CC4FF), Color(0xFF2F7BFF), Color(0xFFEAF6FF), Color(0xFF9FD2FF), Color(0xFF4FA3FF),
    )
    FlameTier.STAR -> FlameColors(
        Color(0xFFC9A2FF), Color(0xFF7A45F0), Color(0xFFF5EDFF), Color(0xFFD2B6FF), Color(0xFFA77BFF),
    )
    FlameTier.RAINBOW -> FlameColors(
        Color(0xFFFF5E5E), Color(0xFFE07BFF), Color(0xFFFFFFFF), Color(0xFFF2F2FF), Color(0xFFFFB84D),
    )
}

private val Rainbow = listOf(
    Color(0xFFFF5E5E), Color(0xFFFFB84D), Color(0xFFFFE45C), Color(0xFF6EE7A0),
    Color(0xFF5CC8FF), Color(0xFF8C7BFF), Color(0xFFE07BFF),
)

/** Главный цвет ступени — им красятся клетки календаря и подписи. */
fun flameTint(tier: FlameTier): Color =
    if (tier == FlameTier.OUT) M3.OnSurfaceVariant else colorsOf(tier).glow

/**
 * Контур огонька в сетке 24×24: язык с завитком слева и сердцевина.
 * Нарисован с нуля, как и остальные значки игры.
 */
private const val FLAME_OUTER =
    "M12 1.5C13.2 4.6 18.8 8.6 18.8 14.4C18.8 18.6 15.8 22.5 12 22.5C8.2 22.5 5.2 18.6 5.2 14.4" +
        "C5.2 11.2 7 9 8.4 7.4C8.6 9.4 9.6 10.8 10.8 11.4C10.2 7.8 11 4.4 12 1.5Z"
private const val FLAME_CORE =
    "M12 11.2C13.1 13.2 15.6 15 15.6 17.9C15.6 20.1 14 21.6 12 21.6C10 21.6 8.4 20.1 8.4 17.9" +
        "C8.4 15.9 10.2 14.6 12 11.2Z"

private val OuterPath: Path by lazy { PathParser().parsePathString(FLAME_OUTER).toPath() }
private val CorePath: Path by lazy { PathParser().parsePathString(FLAME_CORE).toPath() }

/**
 * Огонёк. Горящий чуть колышется: язык качается от основания, сердцевина
 * дышит в своём ритме — два синуса с некратными периодами, чтобы движение
 * не повторялось заметно. Погасший стоит неподвижно, «под угрозой»
 * ([dim]) — горит вполсилы.
 */
@Composable
fun Flame(tier: FlameTier, size: Dp, modifier: Modifier = Modifier, dim: Boolean = false, animate: Boolean = true) {
    val c = colorsOf(tier)
    val alive = animate && tier != FlameTier.OUT
    val phase = if (alive) {
        val t = rememberInfiniteTransition(label = "flame")
        t.animateFloat(0f, 1f, infiniteRepeatable(tween(3400, easing = LinearEasing), RepeatMode.Restart), label = "p").value
    } else {
        0f
    }
    val a = if (dim) 0.55f else 1f
    Canvas(modifier.size(size)) {
        val k = this.size.minDimension / 24f
        val w = 2f * PI.toFloat() * phase
        // качание языка и дыхание сердцевины
        val sway = if (alive) sin(w) * 2.2f + sin(w * 3f + 1.3f) * 0.8f else 0f
        val stretch = if (alive) 1f + sin(w * 2f + 0.6f) * 0.035f else 1f
        val breathe = if (alive) 1f + sin(w * 5f) * 0.05f else 1f
        scale(k, k, pivot = Offset.Zero) {
            if (c.glow != Color.Transparent) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(c.glow.copy(alpha = 0.34f * a), Color.Transparent),
                        center = Offset(12f, 15f),
                        radius = 12f,
                    ),
                    radius = 12f,
                    center = Offset(12f, 15f),
                )
            }
            val base = Offset(12f, 22.5f)
            withTransform({
                rotate(sway, pivot = base)
                scale(1f, stretch, pivot = base)
            }) {
                drawPath(OuterPath, flameBrush(tier, c), alpha = a)
                withTransform({ scale(breathe, breathe, pivot = Offset(12f, 21.6f)) }) {
                    drawPath(
                        CorePath,
                        Brush.verticalGradient(listOf(c.coreTop, c.coreBottom), startY = 11f, endY = 21.6f),
                        alpha = a * if (tier == FlameTier.RAINBOW) 0.92f else 1f,
                    )
                }
            }
        }
    }
}

private fun flameBrush(tier: FlameTier, c: FlameColors): Brush =
    if (tier == FlameTier.RAINBOW) {
        Brush.verticalGradient(Rainbow, startY = 1.5f, endY = 22.5f)
    } else {
        Brush.verticalGradient(listOf(c.outerTop, c.outerBottom), startY = 1.5f, endY = 22.5f)
    }

// ---------- слова ----------

private fun daysWord(n: Int) = pluralRu(n.toLong(), "день", "дня", "дней")

private fun titleOf(v: StreakView): String = when {
    v.state != StreakState.NONE -> "${v.days} ${daysWord(v.days)} подряд"
    v.best > 0 -> "Серия прервалась"
    else -> "Серия дней"
}

/** Подпись в карточке меню: короткая, в ней тесно. */
private fun noteOf(v: StreakView): String = when (v.state) {
    StreakState.LIT -> if (v.days >= v.best) "Засчитан · это рекорд" else "Засчитан · рекорд ${v.best}"
    StreakState.AT_RISK -> "Сыграйте сегодня"
    StreakState.NONE -> if (v.best > 0) "Рекорд был ${v.best} ${daysWord(v.best)}" else "Минута игры в день"
}

/** Та же мысль, но полным предложением — в окне серии места хватает. */
private fun leadOf(v: StreakView): String = when (v.state) {
    StreakState.LIT -> if (v.days >= v.best && v.days > 1) {
        "Сегодня уже засчитано, и это ваша самая длинная серия."
    } else {
        "Сегодня уже засчитано. Приходите завтра\u00a0— огонёк станет жарче."
    }
    StreakState.AT_RISK -> "Вчера огонёк горел. Сыграйте сегодня хотя бы минуту\u00a0— и серия продолжится."
    StreakState.NONE -> if (v.best > 0) {
        "Серия прервалась. Минута игры сегодня\u00a0— и огонёк загорится снова."
    } else {
        "Играйте хотя бы минуту в день: каждый день подряд добавляет огоньку жара."
    }
}

// ---------- карточка в меню ----------

/**
 * Карточка серии в меню, под карточкой места: огонёк, сколько дней подряд
 * и полоска последней недели — насколько много игралось в каждый день.
 * Тап открывает окно серии. Как и карточка места, стоит всегда, чтобы меню
 * не прыгало по высоте.
 */
@Composable
fun StreakCard(large: Boolean, view: StreakView, animate: Boolean, onClick: () -> Unit) {
    val tier = view.tier
    Row(
        Modifier
            .wider(20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(M3.SurfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = if (large) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Flame(tier, if (large) 26.dp else 22.dp, dim = view.state == StreakState.AT_RISK, animate = animate)
        Column(Modifier.weight(1f)) {
            Text(
                titleOf(view),
                color = M3.OnSurface,
                fontSize = if (large) 15.sp else 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FitText(
                noteOf(view),
                color = M3.OnSurfaceVariant,
                maxSize = if (large) 13.sp else 12.sp,
                minSize = 9.sp,
                weight = FontWeight.Normal,
                align = TextAlign.Start,
            )
        }
        DayCells(
            days = view.week,
            tier = tier,
            cell = if (large) 9.dp else 8.dp,
            gap = 3.dp,
        )
        Canvas(Modifier.size(16.dp)) {
            val k = size.minDimension / 24f
            drawPath(
                Path().apply {
                    moveTo(9f * k, 5f * k)
                    lineTo(16f * k, 12f * k)
                    lineTo(9f * k, 19f * k)
                },
                M3.Outline,
                style = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * Цвет клетки дня. Ступени берутся из самого огонька: чуть поиграли —
 * нижний, насыщенный цвет пламени вполсилы, засчитанный день — он же
 * почти целиком, долгий день — верхний, самый светлый. Простая
 * прозрачность поверх тёмного фона сюда не годится: жёлтый от неё
 * становится цветом хаки, оранжевый — коричневым.
 *
 * У радужной ступени у каждой клетки свой цвет радуги — [index].
 */
private fun cellColor(tier: FlameTier, ms: Long, index: Int, levels: Int): Color {
    val level = when {
        ms <= 0L -> 0
        ms < STREAK_DAY_MS -> 1
        levels == 2 || ms < 10 * STREAK_DAY_MS -> 2
        else -> 3
    }
    if (level == 0) return M3.SurfaceContainerHighest
    if (tier == FlameTier.OUT) {
        return M3.OnSurfaceVariant.copy(alpha = floatArrayOf(0f, 0.25f, 0.5f, 0.8f)[level])
    }
    val c = colorsOf(tier)
    val deep = if (tier == FlameTier.RAINBOW) Rainbow[index % Rainbow.size] else c.outerBottom
    val light = if (tier == FlameTier.RAINBOW) Rainbow[index % Rainbow.size] else c.outerTop
    return when (level) {
        1 -> deep.copy(alpha = 0.38f)
        2 -> deep.copy(alpha = if (levels == 2) 1f else 0.9f)
        else -> light
    }
}

/**
 * Строка клеток по дням в карточке меню, последняя — сегодня (обведена).
 * Здесь ступеней меньше, чем в календаре: засчитан день или нет — на
 * восьми точках точнее и не разглядеть.
 */
@Composable
private fun DayCells(days: List<Long>, tier: FlameTier, cell: Dp, gap: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
        days.forEachIndexed { i, ms ->
            HeatCell(cellColor(tier, ms, i, levels = 2), cell, today = i == days.lastIndex)
        }
    }
}

@Composable
private fun HeatCell(color: Color, side: Dp, today: Boolean, radius: Dp = side / 3) {
    Box(
        Modifier
            .size(side)
            .clip(RoundedCornerShape(radius))
            .background(color)
            .then(if (today) Modifier.border(1.dp, M3.OnSurface.copy(alpha = 0.8f), RoundedCornerShape(radius)) else Modifier),
    )
}

// ---------- окно серии ----------

/** Сколько недель показывает календарь; истории в памяти хватает на шесть. */
const val CALENDAR_WEEKS = 6

/**
 * Окно серии: большой огонёк, числа, календарь последних недель и лесенка
 * огоньков — какие уже горели и что будет дальше.
 */
@Composable
fun StreakSheet(closing: Boolean, large: Boolean, view: StreakView, animate: Boolean, onBack: () -> Unit) {
    val tier = view.tier
    Overlay(closing, large, onScrim = onBack) { t ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(if (large) 34.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (large) 14.dp else 11.dp),
        ) {
            RiseIn(t, 60f) {
                Flame(tier, if (large) 96.dp else 62.dp, dim = view.state == StreakState.AT_RISK, animate = animate)
            }
            RiseIn(t, 110f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(titleOf(view), color = M3.OnSurface, fontSize = if (large) 26.sp else 22.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        leadOf(view),
                        color = M3.OnSurfaceVariant,
                        fontSize = if (large) 14.sp else 13.sp,
                        lineHeight = if (large) 20.sp else 18.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            RiseIn(t, 160f) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("Рекорд серии", "${view.best} ${daysWord(view.best)}", Modifier.weight(1f))
                        Tile("Дней в игре", "${view.totalDays}", Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tile("За 7 дней", formatTotal(view.weekMs.toDouble()), Modifier.weight(1f))
                        Tile("Лучший день", formatTotal(view.longestDayMs.toDouble()), Modifier.weight(1f))
                    }
                }
            }
            RiseIn(t, 210f) { StreakCalendar(view, large, animate) }
            RiseIn(t, 260f) { Ladder(view, large, animate) }
            RiseIn(t, 310f) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = M3.Primary, contentColor = M3.OnPrimary),
                ) { Text("Назад", fontWeight = FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(M3.SurfaceContainer)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = M3.OnSurfaceVariant, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        FitText(value, color = M3.OnSurface, maxSize = 17.sp, minSize = 11.sp, align = TextAlign.Start)
    }
}

private val WeekdayNames = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

/**
 * Когда появляется клетка календаря. Волна идёт по диагоналям слева сверху
 * направо вниз ([DIAG_STEP] на диагональ), но диагональ не вспыхивает
 * целиком: её клетки встают по очереди снизу вверх, с малым шагом
 * [CELL_STEP] — от левой нижней к правой верхней.
 *
 * Диагональ — это клетки с одной суммой строки и столбца; нижняя из них
 * лежит в строке min(d, последняя), с неё и начинается отсчёт.
 */
fun calendarDelay(week: Int, dow: Int, weeks: Int = CALENDAR_WEEKS): Int {
    val d = week + dow
    val bottom = minOf(d, weeks - 1)
    return CASCADE_LEAD + d * DIAG_STEP + (bottom - week) * CELL_STEP
}

/** Окно успевает въехать, прежде чем пойдёт волна. */
private const val CASCADE_LEAD = 280
private const val DIAG_STEP = 60
private const val CELL_STEP = 24
private const val CELL_IN_MS = 420

/**
 * Календарь последних недель, как у GitHub: строка — неделя с понедельника,
 * клетка — день, яркость — сколько в тот день игралось. Дни после
 * сегодняшнего в текущей неделе не рисуются.
 *
 * Клетки проступают волной ([calendarDelay]): каждая из прозрачности и из
 * половины своего размера по кривой expo. Все клетки идут от одних часов —
 * сорок две отдельные анимации для этого не нужны.
 */
@Composable
internal fun StreakCalendar(view: StreakView, large: Boolean, animate: Boolean) {
    val tier = view.tier
    val cell = if (large) 28.dp else 20.dp
    val gap = if (large) 6.dp else 4.dp
    val end = calendarDelay(CALENDAR_WEEKS - 1, 6) + CELL_IN_MS
    val clock = remember { Animatable(if (animate) 0f else end.toFloat()) }
    LaunchedEffect(Unit) {
        if (animate) clock.animateTo(end.toFloat(), tween(end, easing = LinearEasing))
    }
    // сегодня — последний элемент истории; его место в сетке — по дню недели
    val todayIndex = (CALENDAR_WEEKS - 1) * 7 + (view.weekday - 1)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(gap)) {
        Text(
            "Последние $CALENDAR_WEEKS недель",
            color = M3.OnSurfaceVariant,
            fontSize = if (large) 14.sp else 13.sp,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (name in WeekdayNames) {
                Text(
                    name,
                    color = M3.OnSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(cell),
                )
            }
        }
        for (week in 0 until CALENDAR_WEEKS) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                for (dow in 0 until 7) {
                    val index = week * 7 + dow
                    val back = todayIndex - index
                    val ms = view.history.getOrNull(view.history.lastIndex - back)
                    if (back < 0) {
                        Spacer(Modifier.size(cell))
                    } else {
                        val start = calendarDelay(week, dow)
                        Box(
                            Modifier
                                .testTag("cal-$week-$dow")
                                .graphicsLayer {
                                    val k = Expo.transform(((clock.value - start) / CELL_IN_MS).coerceIn(0f, 1f))
                                    alpha = k
                                    val sc = 0.5f + 0.5f * k
                                    scaleX = sc
                                    scaleY = sc
                                },
                        ) {
                            HeatCell(cellColor(tier, ms ?: 0L, index, levels = 3), cell, today = back == 0, radius = 5.dp)
                        }
                    }
                }
            }
        }
        // подсказка к яркости, как под календарём на GitHub
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            Text("меньше", color = M3.OnSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
            listOf(0L, STREAK_DAY_MS / 2, 5 * STREAK_DAY_MS, 30 * STREAK_DAY_MS).forEachIndexed { i, ms ->
                HeatCell(cellColor(tier, ms, i, levels = 3), 10.dp, today = false, radius = 3.dp)
            }
            Text("больше", color = M3.OnSurfaceVariant.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

/**
 * Лесенка огоньков: все ступени сразу, чтобы было видно, к чему идёшь.
 * Уже достигнутые рекордом горят, текущая выделена, будущие видны
 * вполсилы — цвет не прячется, но и не выдаётся за заработанный.
 */
@Composable
private fun Ladder(view: StreakView, large: Boolean, animate: Boolean) {
    val lit = FlameTier.entries.filter { it != FlameTier.OUT }
    val current = view.tier
    val next = if (current == FlameTier.OUT) FlameTier.SPARK else current.next
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Огоньки", color = M3.OnSurfaceVariant, fontSize = if (large) 14.sp else 13.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (tier in lit) {
                val reached = view.best >= tier.from
                val here = tier == current
                Column(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (here) {
                                Modifier
                                    .background(M3.SurfaceContainer)
                                    .border(1.dp, flameTint(tier).copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 5.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Flame(tier, if (large) 30.dp else 26.dp, dim = !reached, animate = animate && here)
                    Text(
                        "${tier.from}",
                        color = if (reached) M3.OnSurface else M3.OnSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = if (here) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
        val hint = when {
            next == null -> "Выше огонька не бывает. Держите серию."
            else -> {
                val left = next.from - view.days
                "До ступени «${next.title}»\u00a0— ещё $left\u00a0${daysWord(left)}"
            }
        }
        Text(hint, color = M3.OnSurfaceVariant, fontSize = if (large) 13.sp else 12.sp, textAlign = TextAlign.Center)
    }
}

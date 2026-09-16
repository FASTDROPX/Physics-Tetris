package com.serafim.tetris.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor

/**
 * Значок из четырёх квадратиков — тот же, что в заставке и в меню.
 *
 * Обычно рисуется целиком, но заставке нужно собирать его по частям:
 * [square] — насколько проявился сам квадрат, [tiles] — насколько
 * проявилась каждая плитка (порядок как в рисунке: левая верхняя, правая
 * верхняя, левая нижняя, правая нижняя). Плитка не только проступает, но
 * и вырастает из маленькой — размер считается от той же доли.
 */
@Composable
fun Badge(
    size: Int = 56,
    modifier: Modifier = Modifier,
    square: Float = 1f,
    tiles: FloatArray? = null,
) {
    val dot = size * 9f / 56f
    val pitch = size * 11f / 56f
    val shift = size * 5f / 56f
    Box(
        modifier
            .size(size.dp)
            .then(if (square < 1f) Modifier.graphicsLayer { alpha = square } else Modifier)
            .clip(RoundedCornerShape((size * 16f / 56f).dp))
            .background(M3.SecondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val d = dot * density
            val p = pitch * density
            val s = shift * density
            val ox = this.size.width / 2f - d / 2f - s
            val oy = this.size.height / 2f - d / 2f - s
            val c = M3.OnSecondaryContainer
            listOf(
                Triple(0f, 0f, 1f),
                Triple(p, 0f, 1f),
                Triple(0f, p, 1f),
                Triple(p, p, 0.4f),
            ).forEachIndexed { i, (dx, dy, a) ->
                val k = tiles?.getOrNull(i) ?: 1f
                if (k <= 0.001f) return@forEachIndexed
                val grow = TILE_SMALL + (1f - TILE_SMALL) * k
                val side = d * grow
                val cx = ox + dx + d / 2f
                val cy = oy + dy + d / 2f
                drawRoundRect(
                    color = c,
                    topLeft = Offset(cx - side / 2f, cy - side / 2f),
                    size = Size(side, side),
                    cornerRadius = CornerRadius(2f * density * grow),
                    alpha = a * k,
                )
            }
        }
    }
}

/**
 * Заставка загрузки — одно непрерывное движение, собранное из семи
 * предметов на общей ленте времени.
 *
 * Вход: на сером фоне сверху и из прозрачности приезжает пустой квадрат
 * значка; пока он едет, снизу выплывает полоса загрузки, следом название,
 * а дальше по очереди — против часовой стрелки, начиная с левой нижней —
 * вырастают из маленьких четыре плитки. По полосе дважды проходит бегунок.
 *
 * Выход начинается сразу, без паузы, и повторяет вход задом наперёд:
 * предметы уходят в обратном порядке той же дорогой, но кривой expo in.
 * Когда на экране почти ничего не осталось, серый фон растворяется — за
 * ним уже стоит готовое меню.
 *
 * Каждый предмет описывается одним числом — «присутствием» ([live]): от
 * него зависят и прозрачность, и сдвиг, и размер, поэтому уход выходит
 * точным зеркалом входа сам собой.
 */
@Composable
fun BootScreen(visible: Boolean, modifier: Modifier = Modifier) {
    // две ленты времени в миллисекундах: вход идёт сразу, выход — когда
    // игра загрузилась. Обе линейные: все кривые живут в самих предметах
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(IN_TOTAL, tween(IN_TOTAL.toInt(), easing = LinearEasing))
    }
    val exit = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (!visible) exit.animateTo(OUT_TOTAL, tween(OUT_TOTAL.toInt(), easing = LinearEasing))
    }
    if (exit.value >= OUT_TOTAL) return

    val t = enter.value
    val o = exit.value

    val badge = live(t, o, IN_BADGE, IN_BADGE_MS, OUT_BADGE, OUT_ITEM_MS)
    val title = live(t, o, IN_TITLE, IN_RISE_MS, OUT_TITLE, OUT_ITEM_MS)
    val track = live(t, o, IN_TRACK, IN_RISE_MS, OUT_TRACK, OUT_ITEM_MS)
    // плитки: входят снизу слева против часовой, уходят в обратном порядке
    val tiles = FloatArray(4) { i ->
        live(t, o, IN_TILE[i], IN_TILE_MS, OUT_TILE[i], OUT_TILE_MS)
    }
    val ground = 1f - fall(o, OUT_GROUND, OUT_GROUND_MS)

    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { alpha = ground }
            .background(M3.Surface),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Badge(
                modifier = Modifier.graphicsLayer {
                    alpha = badge
                    translationY = -BADGE_DROP * (1f - badge) * density
                },
                square = 1f,
                tiles = tiles,
            )
            Text(
                "Тетрис",
                color = M3.OnSurface,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                modifier = Modifier.graphicsLayer { riseBy(title, density) },
            )
            Box(
                Modifier
                    .graphicsLayer { riseBy(track, density) }
                    .width(172.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(M3.SurfaceContainerHigh),
            ) {
                // бегунок проходит полосу ровно дважды и уезжает за край
                val runs = ((t - IN_SWEEP) / SWEEP_MS).coerceIn(0f, SWEEPS)
                val lap = if (runs >= SWEEPS) 1f else runs - floor(runs)
                val pos = Sweep.transform(lap)
                Box(
                    Modifier
                        .fillMaxWidth(0.42f)
                        .height(4.dp)
                        .graphicsLayer {
                            // ровно от левого края до правого: бегунок шириной в
                            // 0.42 полосы уходит за край ровно тогда, когда кончается
                            // заход, — полоса не стоит пустой ползахода
                            translationX = (-1.05f + 3.48f * pos) * 172f * 0.42f * density
                        }
                        .clip(RoundedCornerShape(100.dp))
                        .background(M3.Primary),
                )
            }
        }
    }
}

/** Выплывание снизу: та же дорога, что и у `riseIn` в меню. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.riseBy(k: Float, density: Float) {
    alpha = k
    translationY = 16f * (1f - k) * density
    val s = 0.97f + 0.03f * k
    scaleX = s
    scaleY = s
}

/** Доля появления: кривая expo out — резко трогается, мягко садится. */
private fun rise(t: Float, at: Float, ms: Float) = Expo.transform(((t - at) / ms).coerceIn(0f, 1f))

/** Доля ухода: та же дорога назад, но кривой expo in. */
private fun fall(o: Float, at: Float, ms: Float) = ExpoIn.transform(((o - at) / ms).coerceIn(0f, 1f))

/**
 * Присутствие предмета: 1 — стоит на своём месте, 0 — его на экране нет.
 * Вход и выход перемножаются, поэтому одно число описывает всю жизнь
 * предмета, а уход получается зеркалом входа.
 */
private fun live(t: Float, o: Float, inAt: Float, inMs: Float, outAt: Float, outMs: Float) =
    rise(t, inAt, inMs) * (1f - fall(o, outAt, outMs))

// ---------- лента времени, мс ----------

/** Сколько заставка стоит на экране до ухода: вход плюс два прохода бегунка. */
const val BOOT_IN_MS = 1400L

/**
 * Через сколько после начала ухода показывать меню. Меню появляется
 * тем же движением, что и при выходе из партии, и начинает его чуть
 * раньше, чем серый фон начнёт таять ([OUT_GROUND]): так оно проступает
 * сквозь уходящую заставку, а не вскакивает на пустом месте.
 */
const val BOOT_REVEAL_MS = 520L

private const val IN_BADGE = 0f
private const val IN_BADGE_MS = 620f
private const val IN_TRACK = 240f
private const val IN_TITLE = 300f
private const val IN_RISE_MS = 560f
private val IN_TILE = floatArrayOf(440f, 520f, 360f, 600f)   // ЛВ, ПВ, ЛН, ПН
private const val IN_TILE_MS = 440f
private const val IN_SWEEP = 320f
private const val SWEEP_MS = 520f
private const val SWEEPS = 2f
private const val IN_TOTAL = 1500f

private val OUT_TILE = floatArrayOf(140f, 70f, 210f, 0f)     // порядок обратный входу
private const val OUT_TILE_MS = 280f
private const val OUT_TITLE = 280f
private const val OUT_TRACK = 340f
private const val OUT_BADGE = 380f
private const val OUT_ITEM_MS = 320f
private const val OUT_GROUND = 560f
private const val OUT_GROUND_MS = 380f
private const val OUT_TOTAL = 960f

/**
 * Ход бегунка: мягко трогается и мягко уходит, но середину полосы
 * проходит ровно. Прежняя кривая (Emphasized) выбрасывала его за край
 * за полтора шага, и половину захода полоса стояла пустой.
 */
private val Sweep = androidx.compose.animation.core.CubicBezierEasing(0.45f, 0.05f, 0.55f, 0.95f)

/** Насколько высоко над местом начинает свой путь значок. */
private const val BADGE_DROP = 64f

/** Из какой доли своего размера вырастает плитка. */
private const val TILE_SMALL = 0.3f

/** CSS-анимация riseIn: 0.6s expo, снизу вверх с лёгким увеличением. */
@Composable
fun RiseIn(timeMs: Float, delay: Float, content: @Composable () -> Unit) {
    val p = Expo.transform(((timeMs - delay) / 600f).coerceIn(0f, 1f))
    Box(
        Modifier.graphicsLayer {
            alpha = p
            translationY = 16f * (1f - p) * density
            val s = 0.97f + 0.03f * p
            scaleX = s
            scaleY = s
        }
    ) { content() }
}

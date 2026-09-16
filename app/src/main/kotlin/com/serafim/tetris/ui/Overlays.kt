package com.serafim.tetris.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serafim.tetris.game.PieceType
import com.serafim.tetris.game.SaveHead
import com.serafim.tetris.game.Stats
import com.serafim.tetris.game.Xp
import com.serafim.tetris.game.formatClock
import com.serafim.tetris.game.formatRu
import com.serafim.tetris.game.formatTotal
import com.serafim.tetris.online.Standing
import kotlin.math.roundToInt
import kotlin.random.Random

/** Затемнение под окном: rgba(19,22,25,.86), как в CSS. */
val SCRIM = Color(0xDB131619)

/** Доли высоты экрана над карточкой и под ней. */
class MenuBand(val top: Float, val bottom: Float)

/**
 * Главное меню на телефоне — от 5 % высоты экрана сверху до 8 % снизу:
 * так его разметил игрок на своём скриншоте. Чуть выше центра — туда,
 * где глаз и считает центр.
 */
val PHONE_MENU_BAND = MenuBand(top = 0.05f, bottom = 0.08f)

/**
 * Как SpaceEvenly, но соседи не ближе [min]: лишнее место делится поровну
 * между всеми промежутками и краями, а когда его нет, остаются просто
 * ровные зазоры. Чистый SpaceEvenly в тесноте склеил бы блоки вплотную.
 */
private class EvenAtLeast(private val min: Dp) : Arrangement.Vertical {
    override val spacing: Dp get() = min
    override fun Density.arrange(totalSize: Int, sizes: IntArray, outPositions: IntArray) {
        val gap = min.roundToPx()
        val used = sizes.sum() + gap * (sizes.size - 1).coerceAtLeast(0)
        val slot = (totalSize - used).coerceAtLeast(0).toFloat() / (sizes.size + 1)
        var y = slot
        for (i in sizes.indices) {
            outPositions[i] = y.roundToInt()
            y += sizes[i] + gap + slot
        }
    }
}

/** Подсказки: одна случайная при каждом заходе в меню. */
val TIPS = listOf(
    "Четыре линии разом — это тетрис: 800 очков вместо 400 за четыре одиночные.",
    "Два тетриса подряд стоят в полтора раза дороже. Серия держится, пока вы не очистите линию попроще.",
    "Полностью пустой стакан после очистки — идеальная очистка, до 2000 очков сверху.",
    "T-фигура, вкрученная поворотом в нишу, засчитывается как T-спин и стоит вдвое дороже.",
    "Комбо считает фигуры подряд с очисткой: каждая следующая добавляет по 50 очков на скорость.",
    "Отложенная фигура возвращается бесплатно, но поменять её можно лишь раз на фигуру.",
    "Призрак внизу показывает место падения — по нему удобно целиться, не глядя на стакан.",
    "Жёсткий сброс приносит по 2 очка за пролетевшую клетку: ронять выгоднее, чем доводить вручную.",
    "Фигуры идут мешками по семь: каждая выпадет ровно раз, прежде чем мешок сменится.",
    "После касания дна есть полсекунды, чтобы успеть двинуть или довернуть фигуру.",
    "Стена не мешает повороту: фигура сама отскочит от края, если ей есть куда.",
    "Уровень игрока растёт от суммы очков за все матчи и не сбрасывается между партиями.",
    "Колодец под палку хорош, но глубже четырёх строк одна ошибка хоронит стакан.",
    "С миллиона очков скорость растёт дальше: +1 за каждые 100 000, до пятидесятой.",
    "Выше двадцатой скорости фигура приносит очки сама: по 10 за каждую ступень сверху.",
)

/**
 * Затемнение с карточкой по центру. Появление — sheetIn 0.5s expo,
 * закрытие — sheetOut 0.34s, ровно как в CSS.
 *
 * Окно модальное: затемнение забирает себе каждое касание, и до того,
 * что под ним, — меню, стакана, кнопок — не доходит ничего. В Compose
 * касание получает верхний из соседей, у которого есть обработчик, а у
 * затемнения его не было: оно только рисовалось, и палец проходил сквозь
 * него на «Играть». Тап по самому затемнению — [onScrim], если он задан;
 * тап по карточке мимо кнопок затемнению не достаётся.
 */
@Composable
fun Overlay(
    closing: Boolean,
    large: Boolean,
    onScrim: (() -> Unit)? = null,
    scrim: Color = SCRIM,
    band: MenuBand? = null,
    ime: Boolean = false,
    content: @Composable (time: Float) -> Unit,
) {
    // обработчик держим по ключу Unit: иначе он перезапускался бы с каждой
    // новой лямбдой, то есть на каждом кадре анимации, и рвал бы начатый тап
    val scrimTap by rememberUpdatedState(onScrim)
    val enter = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(1200, easing = LinearEasing)) }
    LaunchedEffect(closing) { if (closing) exit.animateTo(1f, tween(340, easing = Expo)) }

    val t = enter.value * 1200f
    val inP = Expo.transform((t / 500f).coerceIn(0f, 1f))
    val outP = exit.value

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - outP }
            .background(scrim)
            .pointerInput(Unit) { detectTapGestures(onTap = { scrimTap?.invoke() }) }
            // окно с полем ввода поджимается над клавиатурой
            .then(if (ime) Modifier.imePadding() else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        // полосой — карточка во всю высоту между двумя отметками экрана,
        // иначе — по содержимому и по центру
        val place = if (band != null) {
            Modifier
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = maxHeight * band.top,
                    bottom = maxHeight * band.bottom,
                )
                .fillMaxHeight()
        } else {
            Modifier.padding(16.dp)
        }
        Box(
            place
                .widthIn(max = if (large) 520.dp else 340.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = inP * (1f - outP)
                    translationY = (28f * (1f - inP) - 14f * outP) * density
                    val s = (0.94f + 0.06f * inP) * (1f - 0.1f * outP)
                    scaleX = s
                    scaleY = s
                }
                .clip(RoundedCornerShape(28.dp))
                .background(M3.SurfaceContainerHigh)
                // тап по карточке мимо кнопок — не тап по фону
                .pointerInput(Unit) { detectTapGestures { } },
        ) { content(t) }
    }
}

@Composable
private fun Lead(text: String, large: Boolean, modifier: Modifier = Modifier, small: Boolean = false) {
    Text(
        text,
        color = M3.OnSurfaceVariant,
        fontSize = if (large) 15.sp else if (small) 13.sp else 14.sp,
        lineHeight = if (large) 22.sp else if (small) 18.sp else 20.sp,
        textAlign = TextAlign.Center,
        modifier = modifier.widthIn(max = if (large) 420.dp else 280.dp),
    )
}

@Composable
private fun Toggle(label: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Switch(
            checked = on,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = M3.OnPrimary,
                checkedTrackColor = M3.Primary,
                checkedBorderColor = M3.Primary,
                uncheckedThumbColor = M3.Outline,
                uncheckedTrackColor = M3.SurfaceContainer,
                uncheckedBorderColor = M3.OutlineVariant,
            ),
        )
        Text(label, color = M3.OnSurface, fontSize = 14.sp)
    }
}

/**
 * Звук и вибрация — два мелких тумблера в строку, режим физики отдельной
 * строкой под ними: он меняет не громкость, а сами правила, и стоять
 * вровень с ними не должен.
 */
@Composable
private fun Switches(
    large: Boolean,
    soundOn: Boolean,
    vibrationOn: Boolean,
    physicsOn: Boolean,
    onSound: () -> Unit,
    onVibration: () -> Unit,
    onPhysics: () -> Unit,
    note: String,
    tight: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (tight) 0.dp else 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Toggle("Звук", soundOn, onSound)
            Toggle("Вибрация", vibrationOn, onVibration)
        }
        Toggle("Режим физики", physicsOn, onPhysics)
        Lead(note, large, small = tight)
    }
}

/** Главное меню. */
@Composable
fun MenuSheet(
    closing: Boolean,
    large: Boolean,
    tipIndex: Int,
    titleSeed: Int,
    standing: Standing,
    resume: SaveHead?,
    soundOn: Boolean,
    vibrationOn: Boolean,
    physicsOn: Boolean,
    showKeyboardHelp: Boolean,
    onStats: () -> Unit,
    onBoard: () -> Unit,
    onSound: () -> Unit,
    onVibration: () -> Unit,
    onPhysics: () -> Unit,
    onPlay: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
) {
    // за меню не стакан под затемнением, а тот же фон с летящими фигурами,
    // что и за игрой, — поэтому затемнения нет, а стакан в меню спрятан
    val band = if (large) null else PHONE_MENU_BAND
    val tall = if (band != null) Modifier.fillMaxHeight() else Modifier
    Overlay(closing, large, scrim = Color.Transparent, band = band) { t ->
        Box(Modifier.fillMaxWidth().then(tall)) {
        Column(Modifier.fillMaxWidth().then(tall)) {
            BoxWithConstraints(Modifier.weight(1f, fill = band != null)) {
            val viewport = maxHeight
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    // в высокой карточке блоки расходятся на всю её высоту;
                    // не влезут — прокрутка остаётся запасным выходом
                    .then(if (band != null) Modifier.heightIn(min = viewport) else Modifier)
                    .padding(
                        start = if (large) 34.dp else 24.dp,
                        end = if (large) 34.dp else 24.dp,
                        top = if (large) 34.dp else 18.dp,
                        bottom = if (large) 16.dp else 8.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                // на телефоне меню обязано влезать целиком, без прокрутки:
                // на Redmi 10C (360×825 dp) прежние отступы упирались в край
                verticalArrangement = if (large) Arrangement.spacedBy(20.dp) else EvenAtLeast(10.dp),
            ) {
                RiseIn(t, 80f) { Badge(if (large) 72 else 46) }
                RiseIn(t, 130f) { TitleLetters("Тетрис", titleSeed, large, t) }
                RiseIn(t, 180f) { Tiles(large, t) }
                RiseIn(t, 230f) { Lead(TIPS[tipIndex], large, small = !large) }
                RiseIn(t, 280f) {
                    Lead(
                        "Свайп по полю — двигать, тап — поворот, свайп вниз — сбросить, свайп вверх — отложить.",
                        large,
                        small = !large,
                    )
                }
                // выбора стартового уровня больше нет: все начинают с первого,
                // иначе онлайн-таблица сравнивала бы разные игры — на его
                // месте теперь место игрока и ближайший соперник
                RiseIn(t, 330f) {
                    StandingCard(large, standing, onBoard)
                }
                RiseIn(t, 380f) {
                    Switches(
                        large = large,
                        soundOn = soundOn,
                        vibrationOn = vibrationOn,
                        physicsOn = physicsOn,
                        onSound = onSound,
                        onVibration = onVibration,
                        onPhysics = onPhysics,
                        note = "Блоки падают и заваливаются по-настоящему. Ряд срезается, " +
                            "когда до полного не хватает одной клетки.",
                        tight = !large,
                    )
                }
                if (showKeyboardHelp) {
                    RiseIn(t, 430f) { KeyboardBlock(large) }
                }
            }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(M3.SurfaceContainerHigh)
                    .padding(
                        start = if (large) 34.dp else 24.dp,
                        end = if (large) 34.dp else 24.dp,
                        top = if (large) 12.dp else 8.dp,
                        bottom = if (large) 28.dp else 18.dp,
                    )
            ) {
                val tall = if (large) 56.dp else 48.dp
                if (resume == null) {
                    Button(
                        onClick = onPlay,
                        modifier = Modifier.fillMaxWidth().height(tall),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = M3.Primary,
                            contentColor = M3.OnPrimary,
                        ),
                    ) {
                        Text("Играть", fontSize = if (large) 16.sp else 15.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    // незаконченная партия: продолжить её — главное действие,
                    // а «Заново» стоит рядом обводкой, чтобы не жать случайно
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onResume,
                            modifier = Modifier.weight(1f).height(tall),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = M3.Primary,
                                contentColor = M3.OnPrimary,
                            ),
                        ) {
                            // счёт бывает семизначным, а при крупном шрифте
                            // места мало — подпись мельчает, но не рвётся
                            FitText(
                                "Продолжить · " + formatRu(resume.score),
                                color = M3.OnPrimary,
                                maxSize = if (large) 16.sp else 15.sp,
                                minSize = 11.sp,
                            )
                        }
                        OutlinedButton(
                            onClick = onRestart,
                            modifier = Modifier.height(tall),
                            shape = RoundedCornerShape(100.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            border = BorderStroke(1.dp, M3.OutlineVariant),
                        ) {
                            Text(
                                "Заново",
                                color = M3.Primary,
                                fontSize = if (large) 16.sp else 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) {
            RiseIn(t, 60f) { StatsButton(onStats) }
        }
        Box(Modifier.align(Alignment.TopStart).padding(6.dp)) {
            RiseIn(t, 60f) { BoardButton(onBoard) }
        }
        }
    }
}

/** Значок статистики в углу меню: три столбика, как у остальных иконок. */
@Composable
private fun StatsButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Canvas(Modifier.size(22.dp)) {
            val k = size.minDimension / 24f
            val stroke = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun bar(x: Float, top: Float) = Path().apply {
                moveTo(x * k, 20f * k)
                lineTo(x * k, top * k)
            }
            drawPath(bar(6f, 14f), M3.OnSurfaceVariant, style = stroke)
            drawPath(bar(12f, 5f), M3.OnSurfaceVariant, style = stroke)
            drawPath(bar(18f, 10f), M3.OnSurfaceVariant, style = stroke)
        }
    }
}

/**
 * Итоги за всё время. Считаются не в партии, а поверх неё: очки
 * складываются по мере начисления, фигуры — по мере укладки в стакан.
 */
@Composable
fun StatsSheet(
    closing: Boolean,
    large: Boolean,
    stats: Stats,
    xpTotal: Long,
    resets: Int,
    canReset: Boolean,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    // числа набегают с нуля, а полоса уровня проходит с нуля все ранги —
    // тем дольше, чем их больше, но не дольше пяти секунд
    val k = rememberTally(2000, delayMs = 250)
    val ranks = Xp.position(xpTotal)
    val sweepMs = (1800 + 170 * ranks).toInt().coerceAtMost(4800)
    // после сброса числа не прыгают в ноль, а отматываются к нему по той же
    // кривой — для этого нужны значения, какими они были при открытии
    val opened = remember {
        Frozen(stats.score, stats.pieces, stats.lines, stats.games, stats.timeMs)
    }
    val resetsAtOpen = remember { resets }
    val wipe = remember { Animatable(1f) }
    LaunchedEffect(resets) {
        if (resets != resetsAtOpen) wipe.animateTo(0f, tween(900, easing = Expo))
    }
    val f = k * wipe.value
    Overlay(closing, large, onScrim = onBack) { t ->
        Column(
            Modifier.fillMaxWidth().padding(if (large) 34.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RiseIn(t, 80f) {
                Text("Статистика", color = M3.OnSurface, fontSize = if (large) 26.sp else 22.sp)
            }
            RiseIn(t, 130f) { Lead("За всё время, а не за одну партию.", large) }
            RiseIn(t, 180f) {
                XpSweep(
                    fromXp = 0L,
                    toXp = xpTotal,
                    durationMs = sweepMs,
                    delayMs = 250,
                    compact = !large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
            RiseIn(t, 230f) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatRow("Очки", formatRu(opened.score.tally(f)), large)
                    StatRow("Поставлено фигур", formatRu(opened.pieces.tally(f)), large)
                    StatRow("Стёрто линий", formatRu(opened.lines.tally(f)), large)
                    StatRow("Сыграно партий", formatRu(opened.games.tally(f)), large)
                    StatRow("Время в игре", formatTotal(opened.timeMs * f), large)
                }
            }
            RiseIn(t, 300f) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onReset,
                        enabled = canReset,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = dangerColors(),
                    ) { Text("Сбросить статистику", fontWeight = FontWeight.Medium) }
                    Button(
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = M3.Primary,
                            contentColor = M3.OnPrimary,
                        ),
                    ) { Text("Назад", fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}

/** Значения статистики в момент открытия окна. */
private class Frozen(val score: Long, val pieces: Long, val lines: Long, val games: Long, val timeMs: Double)

@Composable
private fun dangerColors() = ButtonDefaults.buttonColors(
    containerColor = M3.Danger,
    contentColor = M3.OnDanger,
    disabledContainerColor = M3.SurfaceContainerHighest,
    disabledContentColor = M3.OnSurfaceVariant.copy(alpha = 0.45f),
)

/**
 * Подтверждение сброса. Кнопка сброса красная, как и та, что открыла
 * окно; тап мимо окна — то же, что «Отмена»: необратимое действие не
 * должно случаться от промаха. Рекорд и уровень названы числами — чтобы
 * было видно, что именно пропадёт.
 */
@Composable
fun ResetStatsSheet(
    large: Boolean,
    best: Long,
    rank: Int,
    online: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Overlay(closing = false, large = large, onScrim = onCancel) { t ->
        Column(
            Modifier.fillMaxWidth().padding(if (large) 34.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RiseIn(t, 60f) {
                Text(
                    "Сбросить статистику?",
                    color = M3.OnSurface,
                    fontSize = if (large) 24.sp else 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
            RiseIn(t, 110f) {
                Lead(
                    "Обнулится всё: рекорд " + formatRu(best) + ", уровень игрока " + rank +
                        ", очки, фигуры, линии, партии и время в игре" +
                        (if (online) ", а ваша строка уйдёт из онлайн-таблицы" else "") +
                        ". Вернуть будет нельзя.",
                    large,
                )
            }
            RiseIn(t, 160f) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, M3.OutlineVariant),
                    ) { Text("Отмена", color = M3.Primary, fontWeight = FontWeight.Medium) }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = dangerColors(),
                    ) { Text("Сбросить", fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}

/**
 * «Заново» поверх сохранённой партии: её после этого не вернуть, поэтому
 * спрашиваем — ровно как перед сбросом статистики.
 */
@Composable
fun RestartSheet(large: Boolean, save: SaveHead, onCancel: () -> Unit, onConfirm: () -> Unit) {
    Overlay(closing = false, large = large, onScrim = onCancel) { t ->
        Column(
            Modifier.fillMaxWidth().padding(if (large) 34.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RiseIn(t, 60f) {
                Text(
                    "Начать заново?",
                    color = M3.OnSurface,
                    fontSize = if (large) 24.sp else 20.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }
            RiseIn(t, 110f) {
                Lead(
                    "Сохранённая партия — " + formatRu(save.score) + " очков, уровень " +
                        save.level + ", " + formatClock(save.playMs) +
                        " — пропадёт. Продолжить её будет нельзя.",
                    large,
                )
            }
            RiseIn(t, 160f) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, M3.OutlineVariant),
                    ) { Text("Отмена", color = M3.Primary, fontWeight = FontWeight.Medium) }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = dangerColors(),
                    ) { Text("Начать заново", fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, large: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(M3.SurfaceContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = M3.OnSurfaceVariant,
            fontSize = if (large) 15.sp else 14.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = M3.OnSurface,
            fontSize = if (large) 17.sp else 16.sp,
            fontWeight = FontWeight.Medium,
            style = tabular(),
        )
    }
}

/** Заголовок: каждая буква своего цвета и въезжает сверху по очереди. */
@Composable
private fun TitleLetters(word: String, seed: Int, large: Boolean, t: Float) {
    val colors = remember(seed) {
        val pool = PieceType.ALL.toMutableList()
        val rnd = Random(seed)
        for (i in pool.size - 1 downTo 1) {
            val j = rnd.nextInt(i + 1)
            val tmp = pool[i]; pool[i] = pool[j]; pool[j] = tmp
        }
        pool
    }
    Row {
        word.forEachIndexed { i, ch ->
            val p = Expo.transform(((t - (100f + i * 50f)) / 600f).coerceIn(0f, 1f))
            Text(
                ch.toString(),
                color = Color(colors[i % colors.size].color),
                fontSize = if (large) 36.sp else 28.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.graphicsLayer {
                    alpha = p
                    translationY = -18f * (1f - p) * density
                    val s = 0.8f + 0.2f * p
                    scaleX = s
                    scaleY = s
                },
            )
        }
    }
}

/** Семь цветных плиток, падающих сверху по очереди. */
@Composable
private fun Tiles(large: Boolean, t: Float) {
    val side = if (large) 20 else 16
    Row(horizontalArrangement = Arrangement.spacedBy(if (large) 7.dp else 5.dp)) {
        PieceType.ALL.forEachIndexed { i, type ->
            val p = Expo.transform(((t - i * 60f) / 800f).coerceIn(0f, 1f))
            Box(
                Modifier
                    .size(side.dp)
                    .graphicsLayer {
                        alpha = p
                        translationY = -28f * (1f - p) * density
                    }
                    .clip(RoundedCornerShape(if (large) 5.dp else 4.dp))
                    .background(Color(type.color))
            )
        }
    }
}

/** Справка по клавиатуре — показывается, когда к устройству подключены клавиши. */
@Composable
private fun KeyboardBlock(large: Boolean) {
    val rows = listOf(
        "← →" to "двигать",
        "↑ · X" to "поворот вправо",
        "Z · Ctrl" to "поворот влево",
        "↓" to "ускорить падение",
        "Пробел" to "сбросить сразу",
        "C · Shift" to "отложить фигуру",
        "P · Esc" to "пауза",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(M3.SurfaceContainer)
            .padding(horizontal = if (large) 22.dp else 16.dp, vertical = if (large) 18.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(if (large) 7.dp else 4.dp),
    ) {
        Text(
            "Клавиатура",
            color = M3.OnSurfaceVariant,
            fontSize = if (large) 14.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = if (large) 12.dp else 8.dp),
        )
        rows.forEach { (key, what) ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    key,
                    color = M3.Primary,
                    fontSize = if (large) 14.sp else 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.widthIn(min = 76.dp),
                )
                Text(what, color = M3.OnSurfaceVariant, fontSize = if (large) 14.sp else 13.sp)
            }
        }
    }
}

/** Пауза. */
@Composable
fun PauseSheet(
    closing: Boolean,
    large: Boolean,
    soundOn: Boolean,
    vibrationOn: Boolean,
    physicsOn: Boolean,
    onSound: () -> Unit,
    onVibration: () -> Unit,
    onPhysics: () -> Unit,
    onMenu: () -> Unit,
    onResume: () -> Unit,
) {
    Overlay(closing, large) { t ->
        Column(
            Modifier.fillMaxWidth().padding(if (large) 34.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RiseIn(t, 80f) {
                Text(
                    "Пауза",
                    color = M3.OnSurface,
                    fontSize = if (large) 26.sp else 22.sp,
                )
            }
            RiseIn(t, 130f) { Lead("Поле подождёт.", large) }
            RiseIn(t, 180f) {
                Switches(
                    large = large,
                    soundOn = soundOn,
                    vibrationOn = vibrationOn,
                    physicsOn = physicsOn,
                    onSound = onSound,
                    onVibration = onVibration,
                    onPhysics = onPhysics,
                    note = "Режим физики сменится со следующей партии.",
                )
            }
            RiseIn(t, 300f) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = onMenu,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                    ) { Text("В меню", color = M3.Primary, fontWeight = FontWeight.Medium) }
                    Button(
                        onClick = onResume,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = M3.Primary,
                            contentColor = M3.OnPrimary,
                        ),
                    ) { Text("Продолжить", fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}

/** Конец игры. */
@Composable
fun GameOverSheet(
    closing: Boolean,
    large: Boolean,
    score: Long,
    lines: Long,
    level: Int,
    timeMs: Double,
    xpFrom: Long,
    xpTo: Long,
    newRecord: Boolean,
    record: String,
    onMenu: () -> Unit,
    onAgain: () -> Unit,
) {
    val k = rememberTally(1800, delayMs = 250)
    val ranks = Xp.position(xpTo) - Xp.position(xpFrom)
    val sweepMs = (2000 + 170 * ranks).toInt().coerceAtMost(3600)
    Overlay(closing, large) { t ->
        Column(
            Modifier.fillMaxWidth().padding(if (large) 34.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RiseIn(t, 80f) {
                Text(
                    "Игра окончена",
                    color = M3.OnSurface,
                    fontSize = if (large) 26.sp else 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.2.sp,
                )
            }
            // счёт — главное число экрана и самое длинное, поэтому ему вся
            // ширина; остальные три короткие и делят строку под ним
            RiseIn(t, 130f) {
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ScoreBox(formatRu(score.tally(k)), newRecord, large)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FinalBox("Линии", formatRu(lines.tally(k)), large, Modifier.weight(1f))
                        FinalBox("Скорость", level.toLong().tally(k).toString(), large, Modifier.weight(1f))
                        FinalBox("Время", formatClock(timeMs * k), large, Modifier.weight(1f))
                    }
                }
            }
            RiseIn(t, 180f) {
                XpSweep(
                    fromXp = xpFrom,
                    toXp = xpTo,
                    durationMs = sweepMs,
                    delayMs = 250,
                    compact = !large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
            RiseIn(t, 230f) { Lead(record, large) }
            RiseIn(t, 300f) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onMenu,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, M3.OutlineVariant),
                    ) { Text("В меню", color = M3.Primary, fontWeight = FontWeight.Medium) }
                    Button(
                        onClick = onAgain,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = M3.Primary,
                            contentColor = M3.OnPrimary,
                        ),
                    ) { Text("Играть снова", fontWeight = FontWeight.Medium) }
                }
            }
        }
    }
}

@Composable
private fun ScoreBox(score: String, newRecord: Boolean, large: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(M3.SurfaceContainer)
            .padding(horizontal = 14.dp, vertical = if (large) 16.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Очки", color = M3.OnSurfaceVariant, fontSize = 12.sp)
        FitText(
            score,
            color = if (newRecord) M3.Primary else M3.OnSurface,
            maxSize = if (large) 40.sp else 34.sp,
            minSize = 16.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FinalBox(label: String, value: String, large: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(M3.SurfaceContainer)
            .padding(horizontal = if (large) 10.dp else 6.dp, vertical = if (large) 16.dp else 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, color = M3.OnSurfaceVariant, fontSize = 11.sp, maxLines = 1)
        FitText(
            value,
            color = M3.OnSurface,
            maxSize = if (large) 24.sp else 20.sp,
            minSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

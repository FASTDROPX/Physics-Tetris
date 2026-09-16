package com.serafim.tetris.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serafim.tetris.Controller
import com.serafim.tetris.game.C
import com.serafim.tetris.game.GameState
import com.serafim.tetris.game.TetrisGame
import com.serafim.tetris.game.expoOut
import com.serafim.tetris.game.formatClock
import com.serafim.tetris.game.formatRu
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Экран целиком. Раскладка повторяет медиазапросы оригинала: до 820 dp
 * карточки собираются в две колонки над стаканом, шире — уезжают в боковую
 * колонку справа.
 */
@Composable
fun GameScreen(ctrl: Controller, frame: Int, keyboardAttached: Boolean) {
    val game = ctrl.game
    val fx = ctrl.fx
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth <= 820.dp
        val short = maxHeight <= 660.dp
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val pad = if (compact) 8.dp else 16.dp

        // Размер мини-фигур выводится из ШИРИНЫ окна, а не из клетки стакана.
        // Иначе получается петля: полоски выше -> стакану меньше места ->
        // клетка мельче -> полоски ниже. На части экранов она не сходится,
        // разметка пересчитывается каждый кадр, и жесты по полю срываются.
        val stageWidth = if (compact) {
            maxWidth.value - 2 * pad.value
        } else {
            min(560f, maxWidth.value - 216f - 3 * pad.value)
        }
        val stripCell = floor(stageWidth / C.COLS).coerceIn(12f, if (compact) 38f else 46f)
        val stripSmall = max(9f, (stripCell * 0.55f).roundToInt().toFloat())

        // в меню стакана нет — за меню видны только летящие фигуры фона.
        // Стакан растворяется при выходе в меню и проявляется со стартом
        // партии, пока собирается сам; спрятанный он и не рисуется
        val inMenu = game.state == GameState.MENU
        val stageAlpha by animateFloatAsState(
            targetValue = if (inMenu) 0f else 1f,
            animationSpec = tween(if (inMenu) 260 else 420, easing = Expo),
            label = "stage",
        )
        if (!inMenu || stageAlpha > 0.001f) Box(Modifier.fillMaxSize().graphicsLayer { alpha = stageAlpha }) {
        if (compact) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = pad + insets.calculateStartPadding(LayoutDirection.Ltr),
                        end = pad + insets.calculateEndPadding(LayoutDirection.Ltr),
                        top = pad + insets.calculateTopPadding(),
                        bottom = pad + insets.calculateBottomPadding(),
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatsCard(game, fx, frame, compact = true, short = short)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HoldCard(game, fx, frame, stripSmall, compact = true, modifier = Modifier.weight(1f))
                    NextCard(game, frame, stripSmall, compact = true, modifier = Modifier.weight(1f))
                }
                // чип лежит поверх стакана и не занимает места в колонке,
                // поэтому зазоры над стаканом и под ним одинаковые
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Board(ctrl, frame, compact = true)
                    MessageChip(
                        fx.chip?.text,
                        fx.chip?.key ?: 0,
                        Modifier.align(Alignment.TopCenter),
                    )
                }
                Pad(
                    height = if (short) 44.dp else 50.dp,
                    radius = if (short) 12.dp else 14.dp,
                    iconSize = if (short) 20.dp else 24.dp,
                    gap = if (short) 5.dp else 6.dp,
                    onPress = { padPress(ctrl, it) },
                    onRelease = { padRelease(ctrl, it) },
                )
            }
        } else {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(
                        start = pad + insets.calculateStartPadding(LayoutDirection.Ltr),
                        end = pad + insets.calculateEndPadding(LayoutDirection.Ltr),
                        top = pad + insets.calculateTopPadding(),
                        bottom = pad + insets.calculateBottomPadding(),
                    ),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    Modifier.weight(1f).widthIn(max = 560.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Board(ctrl, frame, compact = false)
                    }
                    Pad(
                        height = if (short) 44.dp else 54.dp,
                        radius = if (short) 12.dp else 16.dp,
                        iconSize = if (short) 20.dp else 24.dp,
                        gap = if (short) 5.dp else 8.dp,
                        onPress = { padPress(ctrl, it) },
                        onRelease = { padRelease(ctrl, it) },
                    )
                }
                Column(
                    Modifier.width(216.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatsCard(game, fx, frame, compact = false, short = false)
                    HoldCard(game, fx, frame, stripSmall, compact = false)
                    NextCard(game, frame, stripSmall, compact = false)
                    Box(Modifier.height(36.dp), contentAlignment = Alignment.CenterStart) {
                        MessageChip(fx.chip?.text, fx.chip?.key ?: 0)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolButton("Пауза", Modifier.weight(1f)) { ctrl.click(); ctrl.togglePause() }
                        ToolButton(
                            if (ctrl.soundOn) "Звук вкл" else "Звук выкл",
                            Modifier.weight(1f),
                        ) { ctrl.click(); ctrl.toggleSound() }
                    }
                }
            }
        }
        }

        // диалоги поверх всего
        when (game.state) {
            GameState.MENU -> MenuSheet(
                closing = ctrl.closing,
                large = maxWidth >= 900.dp && maxHeight >= 720.dp,
                tipIndex = ctrl.tipIndex % TIPS.size,
                titleSeed = ctrl.titleSeed,
                standing = ctrl.board.standing,
                resume = ctrl.resume,
                physicsOn = ctrl.physicsOn,
                showKeyboardHelp = keyboardAttached,
                onStats = { ctrl.openStats() },
                onBoard = { ctrl.openBoard() },
                onSettings = { ctrl.openSettings() },
                onPhysics = { ctrl.togglePhysics() },
                onPlay = { ctrl.click(); ctrl.launch() },
                onResume = { ctrl.resumeGame() },
                onRestart = { ctrl.askRestart() },
            )

            GameState.PAUSED -> PauseSheet(
                closing = false,
                large = maxWidth >= 900.dp && maxHeight >= 720.dp,
                soundOn = ctrl.soundOn,
                vibrationOn = ctrl.vibrationOn,
                physicsOn = ctrl.physicsOn,
                onSound = { ctrl.toggleSound() },
                onVibration = { ctrl.toggleVibration() },
                onPhysics = { ctrl.togglePhysics() },
                onMenu = { ctrl.click(); ctrl.backToMenu() },
                onResume = { ctrl.click(); ctrl.togglePause() },
            )

            GameState.OVER -> GameOverSheet(
                closing = ctrl.closing,
                large = maxWidth >= 900.dp && maxHeight >= 720.dp,
                score = game.score,
                lines = game.lines.toLong(),
                level = game.level,
                timeMs = game.playMs,
                xpFrom = game.xpAtStart,
                xpTo = game.xpTotal,
                newRecord = game.score > game.bestAtStart,
                // рекорд давно хранится на диске, так что «сессии» больше нет
                record = if (game.score > game.bestAtStart) {
                    "Новый рекорд!"
                } else {
                    "Рекорд: " + formatRu(game.best)
                },
                onMenu = { ctrl.click(); ctrl.backToMenu() },
                onAgain = { ctrl.click(); ctrl.launch() },
            )

            else -> Unit
        }

        // настройки открываются поверх меню и им же закрываются
        if (ctrl.showSettings && game.state == GameState.MENU) {
            val context = LocalContext.current
            SettingsSheet(
                closing = false,
                large = maxWidth >= 900.dp && maxHeight >= 720.dp,
                soundOn = ctrl.soundOn,
                volume = ctrl.soundVolume,
                vibrationOn = ctrl.vibrationOn,
                power = ctrl.vibrationPower,
                theme = ctrl.theme,
                onSound = { ctrl.toggleSound() },
                onVibration = { ctrl.toggleVibration() },
                onVolume = { v, preview -> ctrl.setSoundVolume(v, preview) },
                onPower = { v, preview -> ctrl.setVibrationPower(v, preview) },
                onTheme = { ctrl.chooseTheme(it) },
                onGithub = { ctrl.click(); openLink(context, SOURCES_URL) },
                onBack = { ctrl.closeSettings() },
            )
        }

        // статистика открывается поверх меню и им же закрывается
        if (ctrl.showStats && game.state == GameState.MENU) {
            StatsSheet(
                closing = false,
                large = maxWidth >= 900.dp && maxHeight >= 720.dp,
                stats = game.stats,
                xpTotal = game.xpTotal,
                resets = ctrl.statsResets,
                canReset = ctrl.canReset,
                onReset = { ctrl.askResetStats() },
                onBack = { ctrl.closeStats() },
            )
            if (ctrl.showResetConfirm) {
                ResetStatsSheet(
                    large = maxWidth >= 900.dp && maxHeight >= 720.dp,
                    best = game.best,
                    rank = game.xpRank,
                    online = ctrl.board.joined,
                    onCancel = { ctrl.cancelResetStats() },
                    onConfirm = { ctrl.resetStats() },
                )
            }
        }
        // онлайн-таблица — тоже поверх меню и тоже закрывается тапом по фону
        if (ctrl.showBoard && game.state == GameState.MENU) {
            BoardSheet(
                large = maxWidth >= 900.dp && maxHeight >= 720.dp,
                view = ctrl.board.view,
                kind = ctrl.board.kind,
                nick = ctrl.board.nick,
                joining = ctrl.board.joining,
                issue = ctrl.board.issue,
                onKind = { ctrl.click(); ctrl.board.select(it) },
                onJoin = { ctrl.joinBoard(it) },
                onRetry = { ctrl.click(); ctrl.board.retry() },
                onBack = { ctrl.closeBoard() },
            )
        }
        // «Заново» поверх сохранённой партии спрашивает подтверждение
        val save = ctrl.resume
        if (ctrl.showRestartConfirm && save != null && game.state == GameState.MENU) {
            RestartSheet(
                large = maxWidth >= 900.dp && maxHeight >= 720.dp,
                save = save,
                onCancel = { ctrl.cancelRestart() },
                onConfirm = { ctrl.confirmRestart() },
            )
        }
        // системная «Назад» закрывает окно поверх меню, а не всё приложение
        BackHandler(
            enabled = (ctrl.showStats || ctrl.showBoard || ctrl.showSettings ||
                ctrl.showRestartConfirm) &&
                game.state == GameState.MENU,
        ) {
            ctrl.dismissTop()
        }
    }
}

private fun padPress(ctrl: Controller, a: PadAction) = when (a) {
    PadAction.LEFT -> ctrl.pressLeft()
    PadAction.RIGHT -> ctrl.pressRight()
    PadAction.CW -> ctrl.rotateCw()
    PadAction.CCW -> ctrl.rotateCcw()
    PadAction.DROP -> ctrl.hardDrop()
    PadAction.HOLD -> ctrl.hold()
    PadAction.PAUSE -> ctrl.togglePause()
}

private fun padRelease(ctrl: Controller, a: PadAction) {
    when (a) {
        PadAction.LEFT -> ctrl.releaseLeft()
        PadAction.RIGHT -> ctrl.releaseRight()
        else -> Unit
    }
}

@Composable
private fun ToolButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(1.dp, M3.OutlineVariant),
    ) {
        Text(text, color = M3.Primary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Стакан: сам холст, встряска, свечение по контуру и жесты по полю. */
@Composable
private fun Board(ctrl: Controller, frame: Int, compact: Boolean) {
    val game = ctrl.game
    val density = LocalDensity.current.density
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val maxCell = if (compact) 38f else 46f
        val cellDp = floor(
            min(maxWidth.value / C.COLS, maxHeight.value / C.ROWS)
        ).coerceIn(12f, maxCell)
        val cellPx = cellDp * density
        val wDp: Dp = (C.COLS * cellDp).dp
        val hDp: Dp = (C.ROWS * cellDp).dp

        LaunchedEffect(cellPx) { game.cellPx = cellPx.roundToInt() }

        // свечение по контуру: новый уровень и идеальная очистка
        val glow = remember { Animatable(1f) }
        LaunchedEffect(ctrl.fx.glowTick) {
            if (ctrl.fx.glowTick > 0) {
                glow.snapTo(0f)
                glow.animateTo(1f, tween(900, easing = LinearEasing))
            }
        }

        val paused = game.state == GameState.PAUSED
        // размер клетки читается на лету: пересоздавать обработчик жестов при
        // каждом изменении размера нельзя — начатый свайп обрывался бы на полпути
        val cell = rememberUpdatedState(cellPx)

        Box(
            Modifier
                .size(wDp, hDp)
                .graphicsLayer {
                    // встряска поля: затухающая синусоида по кривой expo
                    if (game.shakeT < C.SHAKE_TIME) {
                        val k = 1 - expoOut(min(1.0, game.shakeT / C.SHAKE_TIME))
                        translationY = (sin(game.shakeT / C.SHAKE_TIME * PI * 5) *
                            game.shakeMag * k).toFloat() * density
                    }
                }
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(max(2f, cellDp * 0.22f).dp))
                    .then(if (paused) Modifier.blur(4.dp) else Modifier)
                    .pointerInput(game) { boardGestures(ctrl, cell, density) }
            ) {
                frame.let { }
                drawBoard(game, cellPx, density)
            }
            if (glow.value < 1f && game.state != GameState.MENU) {
                Canvas(Modifier.fillMaxSize()) {
                    val e = Expo.transform(glow.value)
                    val spread = 26f * density * e
                    drawRoundRect(
                        color = M3.Primary.copy(alpha = 0.55f * (1f - e)),
                        topLeft = Offset(-spread / 2f, -spread / 2f),
                        size = Size(size.width + spread, size.height + spread),
                        cornerRadius = CornerRadius(max(2f, cellPx * 0.22f) + spread / 2f),
                        style = Stroke(width = max(1f, spread)),
                    )
                }
            }
            if (paused) {
                Box(Modifier.fillMaxSize().background(M3.Surface.copy(alpha = 0.25f)))
            }
        }
    }
}

/**
 * Жесты прямо по полю: свайп двигает и роняет, тап поворачивает,
 * рывок вверх откладывает, рывок вниз сбрасывает.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.boardGestures(
    ctrl: Controller,
    cellState: State<Float>,
    density: Float,
) {
    val game = ctrl.game
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val start = down.position
        val t0 = System.nanoTime()
        var stepX = 0
        var stepY = 0
        var moved = false
        val active = game.state == GameState.PLAYING
        var last = start

        while (true) {
            val event = awaitPointerEvent()
            val ch = event.changes.firstOrNull { it.id == down.id } ?: break
            last = ch.position
            if (!ch.pressed) break
            if (!active || game.state != GameState.PLAYING) continue

            val cellPx = cellState.value.coerceAtLeast(1f)
            val dx = last.x - start.x
            val dy = last.y - start.y
            val nx = (dx / cellPx).roundToInt()
            if (nx != stepX) {
                val d = nx - stepX
                repeat(kotlin.math.abs(d).coerceAtMost(C.COLS)) {
                    game.move(if (d > 0) 1 else -1)
                }
                stepX = nx
                moved = true
            }
            if (dy > 0 && kotlin.math.abs(dy) > kotlin.math.abs(dx)) {
                val ny = floor(dy / cellPx).toInt()
                if (ny > stepY) {
                    // След меряется от того места, где фигура стояла на
                    // прошлом шаге, а не от начала жеста. Иначе полоса
                    // остаётся приколотой к точке, где палец коснулся
                    // экрана, фигура уезжает от неё вниз — своим ходом и
                    // вместе с гравитацией, — и чем дольше свайп, тем
                    // дальше сверху след «начинается».
                    val fromY = game.piece?.y
                    repeat((ny - stepY).coerceAtMost(C.ROWS)) { game.softDrop() }
                    stepY = ny
                    moved = true
                    if (fromY != null) game.dropTrail(fromY)
                }
            }
        }

        if (!active) return@awaitEachGesture
        val cellPx = cellState.value.coerceAtLeast(1f)
        val dtMs = (System.nanoTime() - t0) / 1_000_000.0
        val dx = last.x - start.x
        val dy = last.y - start.y
        val tapLimit = 14f * density
        val tap = !moved && dtMs < 280 && kotlin.math.abs(dx) < tapLimit &&
            kotlin.math.abs(dy) < tapLimit

        if (game.state == GameState.PLAYING) {
            when {
                tap -> game.rotate(1)
                dy < -cellPx * 1.5f && dtMs < 320 &&
                    kotlin.math.abs(dy) > kotlin.math.abs(dx) -> game.holdPiece()
                dy > cellPx * 2.5f && dtMs < 260 &&
                    kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.5f -> game.hardDrop()
            }
        }
    }
}

@Composable
private fun StatsCard(
    game: TetrisGame,
    fx: com.serafim.tetris.AndroidFx,
    frame: Int,
    compact: Boolean,
    short: Boolean,
) {
    @Suppress("UNUSED_EXPRESSION") frame
    val shown = game.shownScore.roundToInt().toLong()
    var levelUp by remember { mutableStateOf(false) }
    LaunchedEffect(fx.levelTick) {
        if (fx.levelTick > 0) {
            levelUp = true
            delay(900)
            levelUp = false
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        radius = if (compact) 16 else 20,
        padH = if (compact) 10 else 16,
        padV = if (compact) 8 else 14,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp)) {
            if (compact) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Stat("Очки", formatRu(shown), true)
                    Stat("Рекорд", formatRu(max(game.best, shown)), true)
                    Stat("Линии", game.lines.toString(), true)
                    Stat("Скорость", game.level.toString(), true, fx.levelTick, levelUp)
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Очки", formatRu(shown), false)
                    Stat("Рекорд", formatRu(max(game.best, shown)), false)
                    Stat("Линии", game.lines.toString(), false)
                    Stat("Скорость", game.level.toString(), false, fx.levelTick, levelUp)
                    Stat("Время", formatClock(game.playMs), false)
                }
            }
            val xp = game.xpProgress()
            XpBar(
                rank = game.xpRank,
                progress = xp.p,
                label = "${game.xpTotal - xp.lo} / ${xp.hi - xp.lo}",
                rankTick = fx.rankTick,
                compact = compact,
                hideLabel = short,
                clock = if (compact) formatClock(game.playMs) else null,
            )
        }
    }
}

@Composable
private fun HoldCard(
    game: TetrisGame,
    fx: com.serafim.tetris.AndroidFx,
    frame: Int,
    smallDp: Float,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(fx.holdTick) {
        if (fx.holdTick > 0) {
            pop.snapTo(0f)
            pop.animateTo(1f, tween(550, easing = Expo))
        }
    }
    val p = pop.value
    MiniCard("Отложено", compact, smallDp, modifier, heightFactor = if (compact) 2.4f else 3.2f) { small ->
        Canvas(
            Modifier.fillMaxSize().clipToBounds().graphicsLayer {
                alpha = 0.35f + 0.65f * p
                val s = 0.62f + 0.38f * p
                scaleX = s
                scaleY = s
            }
        ) {
            frame.let { }
            drawHold(game, small)
        }
    }
}

@Composable
private fun NextCard(
    game: TetrisGame,
    frame: Int,
    smallDp: Float,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    MiniCard("Следующие", compact, smallDp, modifier, heightFactor = if (compact) 2.4f else 12f) { small ->
        Canvas(Modifier.fillMaxSize().clipToBounds()) {
            frame.let { }
            drawNext(game, small, compact)
        }
    }
}

/**
 * Карточка очереди или кармана. На узком экране — полоска в одну строку,
 * на широком — колонка с заголовком сверху.
 */
@Composable
private fun MiniCard(
    title: String,
    compact: Boolean,
    smallDp: Float,
    modifier: Modifier = Modifier,
    heightFactor: Float,
    content: @Composable (small: Float) -> Unit,
) {
    val density = LocalDensity.current.density
    val canvasH = (smallDp * heightFactor).dp
    Card(
        modifier = modifier.fillMaxWidth(),
        radius = 16,
        padH = 10,
        padV = if (compact) 5 else 12,
    ) {
        if (compact) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardTitle(title, size = 10)
                Box(Modifier.weight(1f).height(canvasH)) { content(smallDp * density) }
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                CardTitle(title, size = 12, modifier = Modifier.padding(bottom = 10.dp))
                Box(Modifier.fillMaxWidth().height(canvasH)) { content(smallDp * density) }
            }
        }
    }
}

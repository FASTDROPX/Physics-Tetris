package com.serafim.tetris.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.serafim.tetris.game.C
import com.serafim.tetris.game.GameState
import com.serafim.tetris.game.RevealOrder
import com.serafim.tetris.game.TetrisGame
import com.serafim.tetris.game.expoOut
import com.serafim.tetris.game.expoOutF
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// Цвета стакана берутся из темы при каждой отрисовке, а не один раз при
// загрузке: иначе в AMOLED и Monet сетка, след и обводка очков остались бы
// цветами тёмной темы. Доли прозрачности — те же, что были в CSS.
private val GridLine: Color get() = M3.OnSurface.copy(alpha = 0x0D / 255f)   // rgba(227,227,227,.05)
private val TrailTop: Color get() = M3.Primary.copy(alpha = 0f)
private val PopStroke: Color get() = M3.Surface.copy(alpha = 0xE6 / 255f)    // rgba(19,22,25,.9)
private val PopSub: Color get() = M3.OnSurface.copy(alpha = 0xEB / 255f)     // rgba(227,227,227,.92)
private val SweepColor: Color get() = M3.Primary

private val popPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = Paint.Align.CENTER
    strokeJoin = Paint.Join.ROUND
    strokeCap = Paint.Cap.ROUND
}

/**
 * Стакан целиком: фон, сетка, вступление, следы сброса, зафиксированные
 * блоки с отскоком, искры, световая волна, распад, очистка линий,
 * призрак, доворот и всплывающие очки. Порядок слоёв — как в draw().
 */
fun DrawScope.drawBoard(game: TetrisGame, cell: Float, density: Float) {
    val w = C.COLS * cell
    val h = C.ROWS * cell
    if (game.state == GameState.MENU) return   // до старта стакана нет вовсе
    // после проигрыша стакан уже стёрт распадом и обратно не возвращается
    if (game.state == GameState.OVER) return

    // какая часть стакана вообще видна: вступление собирает его снизу вверх,
    // а проигрыш гасит снизу вверх
    var visibleTop = 0f
    var visibleBottom = h
    var introAlpha = -1f
    var introRow = -1
    var wipeAlpha = -1f
    var wipeRow = -1

    if (game.state == GameState.INTRO) {
        val t = min(1.0, game.introT / C.INTRO_TIME)
        val shown = C.ROWS * expoOut(t)
        val full = floor(shown).toInt()
        val top = C.ROWS - full
        visibleTop = top * cell
        if (top - 1 >= 0) {
            introAlpha = (shown - full).toFloat()
            introRow = top - 1
        }
    }
    val dust = game.dust
    if (game.state == GameState.DYING && dust != null && game.dieT > dust.total) {
        val wt = min(1.0, (game.dieT - dust.total) / C.WIPE_TIME)
        val gone = C.ROWS * expoOut(wt)
        val full = floor(gone).toInt()
        val cur = C.ROWS - 1 - full
        if (cur >= 0) {
            wipeAlpha = (1 - (gone - full)).toFloat()
            wipeRow = cur
        }
        if (full > 0) visibleBottom = (C.ROWS - full) * cell
    }

    if (visibleBottom > visibleTop) {
        drawRect(
            color = M3.SurfaceDim,
            topLeft = Offset(0f, visibleTop),
            size = Size(w, visibleBottom - visibleTop),
        )
        // сетка колодца
        for (x in 1 until C.COLS) {
            val lx = kotlin.math.round(x * cell) + 0.5f
            drawLine(
                color = GridLine,
                start = Offset(lx, visibleTop),
                end = Offset(lx, visibleBottom),
                strokeWidth = 1f * density,
            )
        }
    }

    if (game.state == GameState.INTRO) {
        if (introRow >= 0) {
            for (x in 0 until C.COLS) {
                tile(x * cell, introRow * cell, cell, M3.Empty, alpha = introAlpha)
            }
        }
        return
    }

    // след жёсткого сброса
    if (game.trails.isNotEmpty()) {
        val k = 1 - expoOutF((min(1.0, game.trailTimer / C.TRAIL_TIME)).toFloat())
        for (t in game.trails) {
            val y0 = max(0, t.from) * cell
            val y1 = (t.to + 1) * cell
            if (y1 <= y0) continue
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(TrailTop, SweepColor.copy(alpha = 0.4f * k)),
                    startY = y0,
                    endY = y1,
                ),
                topLeft = Offset(t.x * cell + cell * 0.18f, y0),
                size = Size(cell * 0.64f, y1 - y0),
            )
        }
    }

    // зафиксированные блоки. В режиме физики сетки клеток на экране нет
    // вовсе: рисуются сами тела — с наклонами, обрубленными краями и всем,
    // что с ними сделали законы механики.
    if (game.physics != null) {
        drawRubble(game, cell)
    } else {
    val fx = game.impactFx()
    for (y in 0 until C.ROWS) {
        val ry = game.rowOffset(y).toFloat() * cell
        for (x in 0 until C.COLS) {
            val t = game.at(x, y) ?: continue
            if (game.state == GameState.DYING && dust != null && game.dieT >= game.dustDelay(x, y)) continue
            val color = Color(t.color)
            if (fx != null && game.isImpactCell(x, y)) {
                tile(
                    x = x * cell,
                    y = ry - fx.off.toFloat() * cell,
                    size = cell,
                    color = color,
                    scale = fx.sx.toFloat(),
                    sy = (fx.sy / fx.sx).toFloat(),
                )
            } else {
                tile(x * cell, ry, cell, color)
            }
        }
    }
    }

    // искры от исчезающих линий
    if (game.sparkT < C.SPARK_LIFE + 90) {
        for (p in game.sparks) {
            val t = (game.sparkT - p.delay) / C.SPARK_LIFE
            if (t <= 0 || t >= 1) continue
            val e = expoOut(t).toFloat()
            val tf = t.toFloat()
            val sz = p.s * (1 - tf * 0.5f) * cell
            drawRect(
                color = Color(p.color),
                topLeft = Offset(
                    (p.x + p.vx * e) * cell,
                    (p.y + p.vy * e) * cell + cell * 1.6f * tf * tf,
                ),
                size = Size(sz, sz),
                alpha = max(0f, 1 - tf * tf * 1.2f),
            )
        }
    }

    // новый уровень: световая волна сверху вниз
    if (game.sweepT < C.SWEEP_TIME) {
        val t = (game.sweepT / C.SWEEP_TIME).toFloat()
        val yy = expoOutF(t) * (h + cell * 4) - cell * 4
        drawRect(
            brush = Brush.verticalGradient(
                0f to SweepColor.copy(alpha = 0f),
                0.5f to SweepColor.copy(alpha = 0.30f * (1 - t)),
                1f to SweepColor.copy(alpha = 0f),
                startY = yy,
                endY = yy + cell * 4,
            ),
            topLeft = Offset(0f, yy),
            size = Size(w, cell * 4),
        )
    }

    // проигрыш: стакан рассыпается в пыль
    if (game.state == GameState.DYING) {
        if (dust != null) {
            for (p in dust.parts) {
                val t = (game.dieT - p.delay) / C.DUST_LIFE
                if (t <= 0 || t >= 1) continue
                val e = expoOut(t).toFloat()
                val tf = t.toFloat()
                val sz = p.s * (1 - tf * 0.55f)
                val pad = (p.s - sz) / 2f
                drawRect(
                    color = Color(p.color),
                    topLeft = Offset(
                        (p.x + p.vx * e + pad) * cell,
                        (p.y + p.vy * e + pad) * cell + cell * 1.1f * tf * tf,
                    ),
                    size = Size(sz * cell, sz * cell),
                    alpha = max(0f, 1 - tf * tf * 1.15f),
                )
            }
        }
        if (game.dieT < C.DUST_FLASH) {
            drawRect(
                color = Color.White,
                size = Size(w, h),
                alpha = ((1 - game.dieT / C.DUST_FLASH) * 0.32).toFloat(),
            )
        }
        if (wipeRow >= 0) {
            for (x in 0 until C.COLS) tile(x * cell, wipeRow * cell, cell, M3.Empty, alpha = wipeAlpha)
        }
        return
    }

    // исчезновение заполненных линий
    if (game.state == GameState.CLEARING) {
        // в режиме физики полосу уже срезал нож в drawRubble
        if (game.physics == null) {
            val t = min(1.0, game.clearTimer / C.CLEAR_TIME)
            val e = expoOut(t).toFloat()
            for (row in game.clearRows) {
                val y = row * cell
                drawRect(Color.White, Offset(0f, y), Size(w, cell), alpha = 1 - e)
                val hh = cell * e
                drawRect(M3.SurfaceDim, Offset(0f, y + (cell - hh) / 2f), Size(w, hh))
            }
        }
        drawOverlays(game, cell, density, w, h)
        return
    }

    val piece = game.piece
    if (game.state == GameState.FALLING || piece == null) {
        drawOverlays(game, cell, density, w, h)
        return
    }
    if (game.state != GameState.PLAYING && game.state != GameState.PAUSED &&
        game.state != GameState.SETTLING && game.state != GameState.OVER
    ) return

    val pieceColor = Color(piece.type.color)

    // первая фигура матча: кубики проявляются по одному
    if (game.revealFirst && game.spawnT < C.FIRST_TOTAL) {
        val list = game.cellsOf(piece).sortedWith(RevealOrder.comparator(piece.type))
        for (k in list.indices) {
            val c = list[k]
            if (c.y < 0) continue
            val local = (game.spawnT - k * C.FIRST_STEP) / C.FIRST_CELL
            if (local <= 0) continue
            val e = expoOut(min(1.0, local)).toFloat()
            tile(c.x * cell, c.y * cell, cell, pieceColor, alpha = e, scale = 0.18f + 0.82f * e)
        }
        return
    }

    // призрак
    val gy = game.ghostY()
    if (gy != piece.y) {
        for (c in game.cellsOf(piece, piece.rot, piece.x, gy)) {
            if (c.y >= 0) ghostTile(c.x * cell, c.y * cell, cell, pieceColor)
        }
    }

    val cs = game.cellsOf(piece)
    val sc = if (game.spawnT < C.SPAWN_TIME) {
        0.74f + 0.26f * expoOutF((game.spawnT / C.SPAWN_TIME).toFloat())
    } else 1f

    if (game.rotT < C.ROT_TIME) {
        // доворот: рисуем финальные клетки, отматывая поворот назад по expo out
        val e = expoOutF((game.rotT / C.ROT_TIME).toFloat())
        val n = com.serafim.tetris.game.Shapes.size(piece.type)
        val ox = piece.x + n / 2f
        val oy = piece.y + n / 2f
        val rf = game.rotFrom
        withTransform({
            translate((ox + rf.dx * (1 - e)) * cell, (oy + rf.dy * (1 - e)) * cell)
            rotate((-rf.dir * (1 - e) * 90f), Offset.Zero)
        }) {
            for (c in cs) tile((c.x - ox) * cell, (c.y - oy) * cell, cell, pieceColor, scale = sc)
        }
        return
    }

    for (c in cs) if (c.y >= 0) tile(c.x * cell, c.y * cell, cell, pieceColor, scale = sc)

    drawOverlays(game, cell, density, w, h)
}

/** Поверх поля: кольца идеальной очистки и всплывающие очки. */
private fun DrawScope.drawOverlays(
    game: TetrisGame,
    cell: Float,
    density: Float,
    w: Float,
    h: Float,
) {
    if (game.pcT < C.PC_TIME) {
        val t = (game.pcT / C.PC_TIME).toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val maxR = sqrt(w * w + h * h) * 0.62f
        drawRect(Color.White, size = Size(w, h), alpha = (1 - t) * (1 - t) * 0.5f)
        for (k in 0 until 3) {
            val tk = (t - k * 0.13f) / (1 - k * 0.13f)
            if (tk <= 0f || tk >= 1f) continue
            drawCircle(
                color = if (k == 1) M3.Xp else M3.Primary,
                radius = expoOutF(tk) * maxR,
                center = Offset(cx, cy),
                alpha = (1 - tk) * 0.55f,
                style = Stroke(width = max(2f * density, cell * 0.26f * (1 - tk))),
            )
        }
    }

    if (game.pops.isEmpty()) return
    val canvas = drawContext.canvas.nativeCanvas
    for (p in game.pops) {
        val t = min(1.0, p.t / C.POP_LIFE)
        val tf = t.toFloat()
        val y = (p.row + 0.5f).toFloat() * cell - expoOutF(tf) * cell * 2.6f
        val a = when {
            tf < 0.1f -> tf / 0.1f
            tf > 0.62f -> 1f - (tf - 0.62f) / 0.38f
            else -> 1f
        }
        if (a <= 0f) continue
        val alpha = (max(0f, a) * 255).toInt()
        val big = max(13f * density, cell * 0.66f)

        popPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        popPaint.textSize = big
        val dy = -(popPaint.descent() + popPaint.ascent()) / 2f

        popPaint.style = Paint.Style.STROKE
        popPaint.strokeWidth = max(3f * density, cell * 0.2f)
        popPaint.color = PopStroke.toArgb()
        popPaint.alpha = (alpha * (PopStroke.alpha)).toInt()
        canvas.drawText(p.text, w / 2f, y + dy, popPaint)

        popPaint.style = Paint.Style.FILL
        popPaint.color = Color(p.color).toArgb()
        popPaint.alpha = alpha
        canvas.drawText(p.text, w / 2f, y + dy, popPaint)

        if (p.sub.isNotEmpty()) {
            val small = max(10f * density, cell * 0.38f)
            popPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            popPaint.textSize = small
            val sy = y + big * 0.86f + dy
            popPaint.style = Paint.Style.STROKE
            popPaint.strokeWidth = max(2f * density, cell * 0.14f)
            popPaint.color = PopStroke.toArgb()
            popPaint.alpha = (alpha * PopStroke.alpha).toInt()
            canvas.drawText(p.sub, w / 2f, sy, popPaint)

            popPaint.style = Paint.Style.FILL
            popPaint.color = PopSub.toArgb()
            popPaint.alpha = (alpha * PopSub.alpha).toInt()
            canvas.drawText(p.sub, w / 2f, sy, popPaint)
        }
    }
}

/**
 * Мини-фигура по центру заданной точки — очередь и карман.
 * Копия drawMini(): фигура обрезается по занятым клеткам и центрируется.
 */
fun DrawScope.drawMini(
    piece: com.serafim.tetris.game.PieceType,
    cx: Float,
    cy: Float,
    size: Float,
    alpha: Float = 1f,
) {
    val m = com.serafim.tetris.game.Shapes.matrix(piece, 0)
    var minX = 99; var maxX = -1; var minY = 99; var maxY = -1
    for (y in m.indices) for (x in m.indices) if (m[y][x] != 0) {
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
    }
    if (maxX < 0) return
    val w = (maxX - minX + 1) * size
    val h = (maxY - minY + 1) * size
    val ox = cx - w / 2f
    val oy = cy - h / 2f
    val color = Color(piece.color)
    for (y in minY..maxY) for (x in minX..maxX) if (m[y][x] != 0) {
        tile(ox + (x - minX) * size, oy + (y - minY) * size, size, color, alpha = alpha)
    }
}

/** Очередь следующих фигур: при смене вся колонка съезжает по expo out. */
fun DrawScope.drawNext(game: TetrisGame, small: Float, horizontal: Boolean) {
    val w = size.width
    val h = size.height
    val count = if (horizontal) 3 else 5
    // палка занимает четыре клетки: ужимаем размер, чтобы соседние
    // фигуры в очереди не налезали друг на друга
    val fit = if (horizontal) {
        minOf(small, w / count / 4.4f, h / 2.4f)
    } else {
        minOf(small, w / 4.4f, h / count / 2.4f)
    }
    val p = if (game.nextT < C.NEXT_TIME) 1f - expoOutF((game.nextT / C.NEXT_TIME).toFloat()) else 0f
    val queue = game.queue
    var i = 0
    while (i < count && i < queue.size) {
        val s = if (i == 0) fit else fit * 0.82f
        val a = if (p != 0f && i == count - 1) 1f - p else 1f
        if (horizontal) {
            val slot = w / count
            drawMini(queue[i], slot * i + slot / 2f + slot * p, h / 2f, s, a)
        } else {
            val slot = h / count
            drawMini(queue[i], w / 2f, slot * i + slot / 2f + slot * p, s, a)
        }
        i++
    }
}

/** Карман: фигура тускнеет, пока откладывать нельзя. */
fun DrawScope.drawHold(game: TetrisGame, small: Float) {
    val hold = game.hold ?: return
    drawMini(hold, size.width / 2f, size.height / 2f, small, if (game.canHold) 1f else 0.32f)
}

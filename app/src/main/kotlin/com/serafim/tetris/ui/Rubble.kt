package com.serafim.tetris.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import com.serafim.tetris.game.C
import com.serafim.tetris.game.GameState
import com.serafim.tetris.game.TetrisGame
import com.serafim.tetris.physics.Frag
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private val CutGlow = Color(0xFFFFFFFF)

/**
 * Стакан в режиме физики. Вместо клеток сетки рисуются сами куски
 * вещества — как они лежат в мире, со своими углами наклона и обрезанными
 * краями. Скругление и внутренний блик те же, что у обычных кубиков,
 * поэтому целый блок и обрубок выглядят одним материалом.
 */
fun DrawScope.drawRubble(game: TetrisGame, cell: Float) {
    val ph = game.physics ?: return
    val frags = ph.fragments()
    if (frags.isEmpty()) return

    var maxV = 8
    for (f in frags) if (f.view.size > maxV) maxV = f.view.size
    val bufA = FloatArray(maxV + 8)
    val bufB = FloatArray(maxV + 8)
    val bufT = FloatArray(maxV + 8)
    val path = Path()

    // при проигрыше куски исчезают в том же порядке, в каком рассыпается
    // сетка: очередь задаёт dustDelay, и режим физики её не ломает
    val dying = if (game.state == GameState.DYING && game.dust != null) game.dieT else -1.0

    val cut = game.cutProgress()
    val rows = game.clearRows
    if (cut >= 0f && rows.isNotEmpty()) {
        // нож идёт справа налево: всё, что он уже прошёл, из полосы исчезло
        val bands = bandsOf(rows, cell)
        val knife = cut * C.COLS * cell
        clipBands(bands, knife, cell, 0) { drawFrags(game, frags, cell, dying, bufA, bufB, bufT, path) }
        drawKnife(bands, knife, cell)
    } else {
        drawFrags(game, frags, cell, dying, bufA, bufB, bufT, path)
    }
}

/** Слитые в полосы ряды очистки: пары «верх, низ» в пикселях. */
private fun bandsOf(rows: List<Int>, cell: Float): List<FloatArray> {
    val sorted = rows.sorted()
    val out = ArrayList<FloatArray>(sorted.size)
    var lo = sorted[0]
    var hi = sorted[0]
    for (i in 1 until sorted.size) {
        val v = sorted[i]
        if (v == hi + 1) hi = v else { out.add(floatArrayOf(lo * cell, (hi + 1) * cell)); lo = v; hi = v }
    }
    out.add(floatArrayOf(lo * cell, (hi + 1) * cell))
    return out
}

/** Вырезает из отрисовки каждую полосу правее ножа — по одной вложенной обрезке. */
private fun DrawScope.clipBands(
    bands: List<FloatArray>,
    knife: Float,
    cell: Float,
    i: Int,
    body: DrawScope.() -> Unit,
) {
    if (i >= bands.size) { body(); return }
    val b = bands[i]
    clipRect(
        left = knife,
        top = b[0],
        right = C.COLS * cell + 1f,
        bottom = b[1],
        clipOp = ClipOp.Difference,
    ) {
        clipBands(bands, knife, cell, i + 1, body)
    }
}

/** Светящееся лезвие на границе среза. */
private fun DrawScope.drawKnife(bands: List<FloatArray>, knife: Float, cell: Float) {
    if (knife <= 0f) return
    val w = max(1.5f, cell * 0.08f)
    for (b in bands) {
        val h = b[1] - b[0]
        drawRect(
            brush = Brush.horizontalGradient(
                0f to CutGlow.copy(alpha = 0f),
                1f to CutGlow.copy(alpha = 0.55f),
                startX = knife - cell * 1.6f,
                endX = knife,
            ),
            topLeft = Offset(knife - cell * 1.6f, b[0]),
            size = Size(cell * 1.6f, h),
        )
        drawRect(CutGlow, Offset(knife - w / 2f, b[0]), Size(w, h), alpha = 0.9f)
    }
}

private fun DrawScope.drawFrags(
    game: TetrisGame,
    frags: List<Frag>,
    cell: Float,
    dying: Double,
    bufA: FloatArray,
    bufB: FloatArray,
    bufT: FloatArray,
    path: Path,
) {
    val gap = max(1f, cell * 0.07f)
    val round = cell * 0.20f
    for (f in frags) {
        val src = f.view
        if (src.size < 6) continue
        if (dying >= 0.0 && dying >= dustDelayOf(game, f)) continue
        var n = src.size / 2
        for (i in 0 until src.size) bufA[i] = src[i] * cell

        // Пока кусок не повернулся, он остаётся обычным прямоугольником —
        // а таких в стакане подавляющее большинство. Рисуем их скруглённым
        // прямоугольником, как обычные кубики: кривые пути дороже впятеро,
        // и незачем платить за них там, где кривых нет.
        if (n == 4 && axisRect(bufA)) { rectTile(bufA, gap, cell, Color(f.type.color)); continue }

        var m = inset(bufA, n, gap / 2f, bufB, bufT)
        if (m < 3) continue
        buildPath(path, bufB, m, round)
        drawPath(path, Color(f.type.color))

        // внутренний блик — тот же, что у целого кубика
        n = m
        for (i in 0 until n * 2) bufA[i] = bufB[i]
        m = inset(bufA, n, cell * 0.20f, bufB, bufT)
        if (m < 3) continue
        buildPath(path, bufB, m, round * 0.5f)
        drawPath(path, TileInner, alpha = 0.22f)
    }
}

/**
 * Очередь распада берётся по клетке центра тяжести куска — ровно по той,
 * в которую эту очередь записал `buildDust`. Середина габаритов не годится:
 * у треугольного обрубка она запросто оказывается снаружи него самого.
 */
private fun dustDelayOf(game: TetrisGame, f: Frag): Double = game.dustDelay(f.cx, f.cy)

/** Четырёхугольник со сторонами строго по осям — то есть просто прямоугольник. */
private fun axisRect(v: FloatArray): Boolean {
    for (i in 0 until 4) {
        val j = (i + 1) % 4
        val dx = v[j * 2] - v[i * 2]
        val dy = v[j * 2 + 1] - v[i * 2 + 1]
        if (dx > EPS_AXIS || dx < -EPS_AXIS) { if (dy > EPS_AXIS || dy < -EPS_AXIS) return false }
    }
    return true
}

private const val EPS_AXIS = 0.25f      // четверть пикселя

/** Тот же кубик, что и в сетке, но с произвольными шириной и высотой. */
private fun DrawScope.rectTile(v: FloatArray, gap: Float, cell: Float, color: Color) {
    var x0 = v[0]; var y0 = v[1]; var x1 = v[0]; var y1 = v[1]
    for (i in 1 until 4) {
        val x = v[i * 2]; val y = v[i * 2 + 1]
        if (x < x0) x0 = x; if (x > x1) x1 = x
        if (y < y0) y0 = y; if (y > y1) y1 = y
    }
    val w = x1 - x0 - gap
    val h = y1 - y0 - gap
    if (w <= 0f || h <= 0f) return
    val px = x0 + gap / 2f
    val py = y0 + gap / 2f
    val r = min(max(2f, cell * 0.22f), min(w, h) / 2f)
    drawRoundRect(
        color = color,
        topLeft = Offset(px, py),
        size = Size(w, h),
        cornerRadius = CornerRadius(r),
    )
    drawRoundRect(
        color = TileInner,
        topLeft = Offset(px + w * 0.22f, py + h * 0.22f),
        size = Size(w * 0.56f, h * 0.56f),
        cornerRadius = CornerRadius(min(r * 0.5f, h * 0.28f)),
        alpha = 0.22f,
    )
}

/**
 * Сжимает выпуклый многоугольник внутрь на `d`: каждое ребро сдвигается
 * к центру по своей нормали, остальное отсекается. Это тот же зазор между
 * кубиками, что и у клеток сетки, только для куска произвольной формы.
 * Возвращает число вершин результата в `out`.
 */
private fun inset(src: FloatArray, n: Int, d: Float, out: FloatArray, tmp: FloatArray): Int {
    if (n < 3) return 0
    // знак площади задаёт, куда смотрят внешние нормали
    var area = 0f
    var jx = src[(n - 1) * 2]
    var jy = src[(n - 1) * 2 + 1]
    for (i in 0 until n) {
        val ix = src[i * 2]
        val iy = src[i * 2 + 1]
        area += jx * iy - ix * jy
        jx = ix; jy = iy
    }
    val sign = if (area >= 0f) 1f else -1f

    var cnt = n
    for (i in 0 until n * 2) out[i] = src[i]

    for (e in 0 until n) {
        if (cnt < 3) return 0
        val ax = src[e * 2]
        val ay = src[e * 2 + 1]
        val bx = src[((e + 1) % n) * 2]
        val by = src[((e + 1) % n) * 2 + 1]
        val ex = bx - ax
        val ey = by - ay
        val len = sqrt(ex * ex + ey * ey)
        if (len < 1e-4f) continue
        // внешняя нормаль ребра
        val nx = sign * ey / len
        val ny = -sign * ex / len
        cnt = clipHalfF(out, cnt, nx, ny, nx * ax + ny * ay - d, tmp)
        if (cnt < 3) return 0
    }
    return cnt
}

/** Отсечение полуплоскостью на месте: nx*x + ny*y <= c. */
private fun clipHalfF(
    v: FloatArray,
    n: Int,
    nx: Float,
    ny: Float,
    c: Float,
    tmp: FloatArray,
): Int {
    var m = 0
    var px = v[(n - 1) * 2]
    var py = v[(n - 1) * 2 + 1]
    var pd = nx * px + ny * py - c
    for (i in 0 until n) {
        val cx = v[i * 2]
        val cy = v[i * 2 + 1]
        val cd = nx * cx + ny * cy - c
        if (cd <= 0f) {
            if (pd > 0f) {
                val t = pd / (pd - cd)
                tmp[m++] = px + (cx - px) * t
                tmp[m++] = py + (cy - py) * t
            }
            tmp[m++] = cx
            tmp[m++] = cy
        } else if (pd <= 0f) {
            val t = pd / (pd - cd)
            tmp[m++] = px + (cx - px) * t
            tmp[m++] = py + (cy - py) * t
        }
        px = cx; py = cy; pd = cd
        if (m + 4 > tmp.size) break
    }
    for (i in 0 until m) v[i] = tmp[i]
    return m / 2
}

/** Контур со скруглёнными углами: у каждой вершины дуга радиусом до `r`. */
private fun buildPath(path: Path, v: FloatArray, n: Int, r: Float) {
    path.reset()
    var started = false
    for (i in 0 until n) {
        val px = v[((i - 1 + n) % n) * 2]
        val py = v[((i - 1 + n) % n) * 2 + 1]
        val cx = v[i * 2]
        val cy = v[i * 2 + 1]
        val nx = v[((i + 1) % n) * 2]
        val ny = v[((i + 1) % n) * 2 + 1]

        val inX = cx - px
        val inY = cy - py
        val outX = nx - cx
        val outY = ny - cy
        val inLen = sqrt(inX * inX + inY * inY)
        val outLen = sqrt(outX * outX + outY * outY)
        if (inLen < 1e-4f || outLen < 1e-4f) continue
        val k = min(r, min(inLen, outLen) * 0.5f)

        val sx = cx - inX / inLen * k
        val sy = cy - inY / inLen * k
        val ex = cx + outX / outLen * k
        val ey = cy + outY / outLen * k

        if (!started) { path.moveTo(sx, sy); started = true } else path.lineTo(sx, sy)
        if (k > 0.01f) path.quadraticTo(cx, cy, ex, ey) else path.lineTo(ex, ey)
    }
    if (started) path.close()
}

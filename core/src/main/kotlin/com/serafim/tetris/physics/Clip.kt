package com.serafim.tetris.physics

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Выпуклый многоугольник — плоский массив x0,y0,x1,y1,… с обходом против
 * часовой стрелки. В таком виде его принимает и физический движок, и
 * отрисовка, и все операции ниже, так что промежуточных объектов нет:
 * резать приходится каждый кадр, и мусор здесь дорого обходится.
 */
typealias Poly = DoubleArray

val EMPTY_POLY: Poly = DoubleArray(0)

/** Площадь по формуле шнурования. Для обхода против часовой она положительна. */
fun polyArea(v: Poly): Double {
    val n = v.size / 2
    if (n < 3) return 0.0
    var s = 0.0
    var jx = v[(n - 1) * 2]
    var jy = v[(n - 1) * 2 + 1]
    for (i in 0 until n) {
        val ix = v[i * 2]
        val iy = v[i * 2 + 1]
        s += jx * iy - ix * jy
        jx = ix; jy = iy
    }
    return s * 0.5
}

/**
 * Отсекает всё, что строго за прямой a·p = c, оставляя полуплоскость
 * a·p <= c. Алгоритм Сазерленда — Ходжмана: для выпуклой фигуры результат
 * снова выпуклый и содержит не больше n+1 вершин.
 */
fun clipHalf(v: Poly, ax: Double, ay: Double, c: Double): Poly {
    val n = v.size / 2
    if (n < 3) return EMPTY_POLY
    val out = DoubleArray((n + 2) * 2)
    var m = 0
    var px = v[(n - 1) * 2]
    var py = v[(n - 1) * 2 + 1]
    var pd = ax * px + ay * py - c
    for (i in 0 until n) {
        val cx = v[i * 2]
        val cy = v[i * 2 + 1]
        val cd = ax * cx + ay * cy - c
        if (cd <= 0.0) {
            if (pd > 0.0) {
                val t = pd / (pd - cd)
                out[m++] = px + (cx - px) * t
                out[m++] = py + (cy - py) * t
            }
            out[m++] = cx
            out[m++] = cy
        } else if (pd <= 0.0) {
            val t = pd / (pd - cd)
            out[m++] = px + (cx - px) * t
            out[m++] = py + (cy - py) * t
        }
        px = cx; py = cy; pd = cd
        if (m + 4 > out.size) break
    }
    return if (m < 6) EMPTY_POLY else out.copyOf(m)
}

/** Полоса lo <= y <= hi — два отсечения подряд, самая частая операция. */
fun clipBand(v: Poly, lo: Double, hi: Double): Poly {
    val a = clipHalf(v, 0.0, 1.0, hi)
    if (a.isEmpty()) return EMPTY_POLY
    return clipHalf(a, 0.0, -1.0, -lo)
}

/** Пересечение с прямоугольником — для растеризации по клеткам. */
fun clipBox(v: Poly, x0: Double, y0: Double, x1: Double, y1: Double): Poly {
    var p = clipHalf(v, 1.0, 0.0, x1)
    if (p.isEmpty()) return EMPTY_POLY
    p = clipHalf(p, -1.0, 0.0, -x0)
    if (p.isEmpty()) return EMPTY_POLY
    p = clipHalf(p, 0.0, 1.0, y1)
    if (p.isEmpty()) return EMPTY_POLY
    return clipHalf(p, 0.0, -1.0, -y0)
}

/** Габаритный прямоугольник: minX, minY, maxX, maxY. */
fun polyBounds(v: Poly, out: DoubleArray) {
    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE
    var i = 0
    while (i < v.size) {
        val x = v[i]
        val y = v[i + 1]
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
        i += 2
    }
    out[0] = minX; out[1] = minY; out[2] = maxX; out[3] = maxY
}

/**
 * Приводит обрезок к виду, который примет движок: убирает слипшиеся и
 * почти лежащие на одной прямой вершины, разворачивает обход против
 * часовой стрелки и отбрасывает всё тоньше `minArea`. Без этого движок
 * ругается на вырожденные многоугольники — отсечение регулярно рождает
 * вершины, отличающиеся на 1e-15.
 */
fun sanitize(v: Poly, minArea: Double = 1e-4): Poly? {
    var n = v.size / 2
    if (n < 3) return null

    // слипшиеся вершины
    val tmp = DoubleArray(v.size)
    var m = 0
    for (i in 0 until n) {
        val x = v[i * 2]
        val y = v[i * 2 + 1]
        if (m >= 2) {
            val dx = x - tmp[m - 2]
            val dy = y - tmp[m - 1]
            if (dx * dx + dy * dy < 1e-10) continue
        }
        tmp[m++] = x
        tmp[m++] = y
    }
    if (m >= 6) {
        val dx = tmp[0] - tmp[m - 2]
        val dy = tmp[1] - tmp[m - 1]
        if (dx * dx + dy * dy < 1e-10) m -= 2
    }
    if (m < 6) return null

    // вершины на одной прямой: движок считает такую фигуру невыпуклой
    n = m / 2
    val keep = BooleanArray(n) { true }
    var kept = n
    for (i in 0 until n) {
        if (kept <= 3) break
        var prev = (i - 1 + n) % n
        while (!keep[prev]) prev = (prev - 1 + n) % n
        var next = (i + 1) % n
        while (!keep[next]) next = (next + 1) % n
        if (prev == i || next == i || prev == next) break
        val ax = tmp[i * 2] - tmp[prev * 2]
        val ay = tmp[i * 2 + 1] - tmp[prev * 2 + 1]
        val bx = tmp[next * 2] - tmp[i * 2]
        val by = tmp[next * 2 + 1] - tmp[i * 2 + 1]
        val cross = ax * by - ay * bx
        val scale = max(1e-9, kotlin.math.sqrt((ax * ax + ay * ay) * (bx * bx + by * by)))
        if (abs(cross) / scale < 1e-5) {
            keep[i] = false
            kept--
        }
    }
    if (kept < 3) return null

    val out = DoubleArray(kept * 2)
    var k = 0
    for (i in 0 until n) if (keep[i]) {
        out[k++] = tmp[i * 2]
        out[k++] = tmp[i * 2 + 1]
    }

    val area = polyArea(out)
    if (abs(area) < minArea) return null
    if (area < 0) {
        // обход по часовой — движок ждёт обратного
        var lo = 0
        var hi = kept - 1
        while (lo < hi) {
            val x = out[lo * 2]; val y = out[lo * 2 + 1]
            out[lo * 2] = out[hi * 2]; out[lo * 2 + 1] = out[hi * 2 + 1]
            out[hi * 2] = x; out[hi * 2 + 1] = y
            lo++; hi--
        }
    }
    return out
}

/**
 * Центр тяжести многоугольника. Для выпуклой фигуры он всегда лежит
 * внутри неё — в отличие от середины габаритного прямоугольника, которая
 * у треугольного обрубка запросто оказывается снаружи.
 */
fun polyCentroid(v: Poly, out: DoubleArray) {
    val n = v.size / 2
    if (n < 3) { out[0] = 0.0; out[1] = 0.0; return }
    var a = 0.0
    var cx = 0.0
    var cy = 0.0
    for (i in 0 until n) {
        val j = (i + 1) % n
        val cr = v[i * 2] * v[j * 2 + 1] - v[j * 2] * v[i * 2 + 1]
        a += cr
        cx += (v[i * 2] + v[j * 2]) * cr
        cy += (v[i * 2 + 1] + v[j * 2 + 1]) * cr
    }
    if (abs(a) < 1e-12) {
        val b = DoubleArray(4)
        polyBounds(v, b)
        out[0] = (b[0] + b[2]) * 0.5
        out[1] = (b[1] + b[3]) * 0.5
    } else {
        out[0] = cx / (3.0 * a)
        out[1] = cy / (3.0 * a)
    }
}

/** Лежит ли точка внутри выпуклого многоугольника — обход любой. */
fun containsPoint(v: Poly, x: Double, y: Double): Boolean {
    val n = v.size / 2
    if (n < 3) return false
    var pos = false
    var neg = false
    for (i in 0 until n) {
        val j = (i + 1) % n
        val cross = (v[j * 2] - v[i * 2]) * (y - v[i * 2 + 1]) -
            (v[j * 2 + 1] - v[i * 2 + 1]) * (x - v[i * 2])
        if (cross > 1e-12) pos = true
        if (cross < -1e-12) neg = true
        if (pos && neg) return false
    }
    return true
}

/**
 * Касаются ли два выпуклых многоугольника. Разделяющая ось: если по
 * нормали хоть одного ребра между проекциями есть зазор больше `gap`,
 * фигуры врозь. Точный ответ нужен, чтобы после разреза обломки одного
 * тела не склеились в одно целое через пустоту.
 */
fun touches(a: Poly, b: Poly, gap: Double = 1e-3): Boolean {
    if (a.size < 6 || b.size < 6) return false
    return !separated(a, b, gap) && !separated(b, a, gap)
}

private fun separated(a: Poly, b: Poly, gap: Double): Boolean {
    val n = a.size / 2
    for (i in 0 until n) {
        val j = (i + 1) % n
        val ex = a[j * 2] - a[i * 2]
        val ey = a[j * 2 + 1] - a[i * 2 + 1]
        val len = kotlin.math.sqrt(ex * ex + ey * ey)
        if (len < 1e-9) continue
        // внешняя нормаль ребра при обходе против часовой стрелки
        val nx = ey / len
        val ny = -ex / len
        var aMax = -Double.MAX_VALUE
        for (k in 0 until n) {
            val p = nx * a[k * 2] + ny * a[k * 2 + 1]
            if (p > aMax) aMax = p
        }
        var bMin = Double.MAX_VALUE
        val bn = b.size / 2
        for (k in 0 until bn) {
            val p = nx * b[k * 2] + ny * b[k * 2 + 1]
            if (p < bMin) bMin = p
        }
        if (bMin - aMax > gap) return true
    }
    return false
}

/** Слияние соседних рядов в сплошные полосы: 5,6,9 → [5..6], [9..9]. */
fun mergeRuns(sorted: List<Int>): List<IntRange> {
    if (sorted.isEmpty()) return emptyList()
    val out = ArrayList<IntRange>()
    var lo = sorted[0]
    var hi = sorted[0]
    for (i in 1 until sorted.size) {
        val v = sorted[i]
        if (v == hi + 1) hi = v else { out.add(lo..hi); lo = v; hi = v }
    }
    out.add(lo..hi)
    return out
}

internal fun overlaps(a0: Double, a1: Double, b0: Double, b1: Double): Boolean =
    max(a0, b0) < min(a1, b1)

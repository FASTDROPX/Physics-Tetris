package com.serafim.tetris.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.max
import kotlin.math.min

/** Тень внутри кубика — тот же #0B1220, что и в оригинале. */
internal val TileInner = Color(0xFF0B1220)

/**
 * Один кубик: скруглённый прямоугольник с более тёмным квадратом внутри.
 * Полностью повторяет функцию tile() из оригинала, включая порядок
 * вычисления зазора, скругления и вертикальной привязки при сплющивании.
 */
fun DrawScope.tile(
    x: Float,
    y: Float,
    size: Float,
    color: Color,
    alpha: Float = 1f,
    scale: Float = 1f,
    sy: Float? = null,
) {
    val gap = max(1f, size * 0.07f)
    val w = (size - gap) * scale
    val h = (size - gap) * scale * (sy ?: 1f)
    if (w <= 0f || h <= 0f) return
    val px = x + size / 2f - w / 2f
    val py = if (sy == null) y + size / 2f - h / 2f else y + size - gap / 2f - h
    val r = max(2f, size * 0.22f * scale)

    drawRoundRect(
        color = color,
        topLeft = Offset(px, py),
        size = Size(w, h),
        cornerRadius = CornerRadius(min(r, h / 2f)),
        alpha = alpha,
    )
    drawRoundRect(
        color = TileInner,
        topLeft = Offset(px + w * 0.22f, py + h * 0.22f),
        size = Size(w * 0.56f, h * 0.56f),
        cornerRadius = CornerRadius(min(r * 0.5f, h * 0.28f)),
        alpha = alpha * 0.22f,
    )
}

/** Призрак: тот же кубик, но одной заливкой и почти прозрачный. */
fun DrawScope.ghostTile(x: Float, y: Float, size: Float, color: Color) {
    val gap = max(1f, size * 0.07f)
    val s = size - gap
    val r = max(2f, size * 0.22f)
    drawRoundRect(
        color = color,
        topLeft = Offset(x + gap / 2f, y + gap / 2f),
        size = Size(s, s),
        cornerRadius = CornerRadius(r),
        alpha = 0.22f,
    )
}

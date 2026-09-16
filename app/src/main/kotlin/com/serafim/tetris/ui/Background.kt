package com.serafim.tetris.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameNanos
import com.serafim.tetris.game.PieceType
import com.serafim.tetris.game.Shapes
import kotlin.math.PI
import kotlin.random.Random

/** Холст фона рисуется в 0.55 от экрана и растягивается — как в оригинале. */
private const val BG_SCALE = 0.55f

private class BgItem(
    var type: PieceType,
    var x: Float,
    var y: Float,
    var s: Float,
    var vy: Float,
    var vx: Float,
    var rot: Float,
    var vr: Float,
    var a: Float,
)

private fun make(rnd: Random, w: Float, h: Float, spread: Boolean, still: Boolean): BgItem {
    val size = 34f + rnd.nextFloat() * 70f
    return BgItem(
        type = PieceType.ALL[rnd.nextInt(PieceType.ALL.size)],
        x = rnd.nextFloat() * w,
        y = if (spread) rnd.nextFloat() * h else -size * 2f,
        s = size,
        vy = if (still) 0f else 10f + rnd.nextFloat() * 26f,
        vx = if (still) 0f else (rnd.nextFloat() - 0.5f) * 8f,
        rot = rnd.nextFloat() * (2f * PI.toFloat()),
        vr = if (still) 0f else (rnd.nextFloat() - 0.5f) * 0.45f,
        a = 0.22f + rnd.nextFloat() * 0.38f,
    )
}

/** Размытые фигуры, медленно падающие и вращающиеся за интерфейсом. */
@Composable
fun FallingBackground(reduceMotion: Boolean, modifier: Modifier = Modifier) {
    val cfg = LocalConfiguration.current
    val wDp = cfg.screenWidthDp.toFloat()
    val hDp = cfg.screenHeightDp.toFloat()
    val w = wDp * BG_SCALE
    val h = hDp * BG_SCALE

    val items = remember(wDp, hDp, reduceMotion) {
        val rnd = Random(20260907)
        val count = if (wDp > 900f) 18 else 11
        MutableList(count) { make(rnd, w, h, spread = true, still = reduceMotion) }
    }
    val rnd = remember { Random(4242) }
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(items) {
        var prev = 0L
        while (true) {
            withFrameNanos { now ->
                var dt = if (prev == 0L) 16.0 else (now - prev) / 1_000_000.0
                prev = now
                if (dt > 120.0) dt = 120.0
                val sec = (dt / 1000.0).toFloat()
                for (i in items.indices) {
                    val it = items[i]
                    it.y += it.vy * sec
                    it.x += it.vx * sec
                    it.rot += it.vr * sec
                    if (it.y - it.s * 2f > h) {
                        items[i] = make(rnd, w, h, spread = false, still = reduceMotion)
                    }
                }
                frame++
            }
        }
    }

    Canvas(modifier.fillMaxSize().blur(18.dp)) {
        frame.let { }                       // подписка на кадр
        val k = 1f / BG_SCALE
        withTransform({ scale(k, k, Offset.Zero) }) {
            for (it in items) drawShape(it, density)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShape(
    item: BgItem,
    density: Float,
) {
    val m = Shapes.matrix(item.type, 0)
    val n = m.size
    val s = item.s * 0.42f * density
    val color = Color(item.type.color)
    withTransform({
        translate(item.x * density, item.y * density)
        rotate(item.rot * 180f / PI.toFloat(), Offset.Zero)
    }) {
        for (r in 0 until n) for (c in 0 until n) {
            if (m[r][c] == 0) continue
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    (c - n / 2f) * s + s * 0.07f,
                    (r - n / 2f) * s + s * 0.07f,
                ),
                size = Size(s * 0.86f, s * 0.86f),
                cornerRadius = CornerRadius(s * 0.24f),
                alpha = item.a * 0.55f,        // общая непрозрачность слоя из CSS
            )
        }
    }
}

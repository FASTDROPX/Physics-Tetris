package com.serafim.tetris.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Кнопки сенсорного управления — те же семь, что и в оригинале. */
enum class PadAction { CCW, CW, HOLD, DROP, LEFT, RIGHT, PAUSE }

@Composable
fun Pad(
    height: Dp,
    radius: Dp,
    iconSize: Dp,
    gap: Dp,
    onPress: (PadAction) -> Unit,
    onRelease: (PadAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            PadButton(PadAction.CCW, height, radius, iconSize, false, onPress, onRelease)
            PadButton(PadAction.CW, height, radius, iconSize, false, onPress, onRelease)
            PadButton(PadAction.HOLD, height, radius, iconSize, false, onPress, onRelease)
            PadButton(PadAction.DROP, height, radius, iconSize, true, onPress, onRelease)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            PadButton(PadAction.LEFT, height, radius, iconSize, false, onPress, onRelease)
            PadButton(PadAction.RIGHT, height, radius, iconSize, false, onPress, onRelease)
            PadButton(PadAction.PAUSE, height, radius, iconSize, false, onPress, onRelease)
        }
    }
}

@Composable
private fun RowScope.PadButton(
    action: PadAction,
    height: Dp,
    radius: Dp,
    iconSize: Dp,
    accent: Boolean,
    onPress: (PadAction) -> Unit,
    onRelease: (PadAction) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(180, easing = Expo),
        label = "padScale",
    )
    val base = if (accent) M3.SecondaryContainer else M3.SurfaceContainer
    val bg = if (pressed && !accent) M3.SurfaceContainerHighest else base
    val tint = if (accent) M3.OnSecondaryContainer else M3.OnSurface

    Box(
        Modifier
            .weight(1f)
            .height(height)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(radius))
            .background(bg)
            .pointerInput(action) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    onPress(action)
                    waitForUpOrCancellation()
                    pressed = false
                    onRelease(action)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(iconSize)) { drawPadIcon(action, tint) }
    }
}

/**
 * Иконки нарисованы теми же контурами, что и SVG в разметке:
 * viewBox 24×24, обводка 2, скруглённые концы, без заливки.
 */
private fun DrawScope.drawPadIcon(action: PadAction, color: Color) {
    val k = size.minDimension / 24f
    val stroke = Stroke(width = 2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun p(build: Path.() -> Unit): Path = Path().apply(build)
    fun line(vararg pts: Float): Path = p {
        moveTo(pts[0] * k, pts[1] * k)
        var i = 2
        while (i < pts.size) { lineTo(pts[i] * k, pts[i + 1] * k); i += 2 }
    }

    when (action) {
        PadAction.CCW -> {
            drawPath(line(8f, 6f, 4f, 6f, 4f, 2f), color, style = stroke)
            drawPath(
                p {
                    arcTo(
                        Rect(3.7275f * k, 1.9298f * k, 19.7275f * k, 17.9298f * k),
                        -154.62f, 319.62f, true,
                    )
                },
                color, style = stroke,
            )
        }
        PadAction.CW -> {
            drawPath(line(16f, 6f, 20f, 6f, 20f, 2f), color, style = stroke)
            drawPath(
                p {
                    arcTo(
                        Rect(4.2725f * k, 1.9298f * k, 20.2725f * k, 17.9298f * k),
                        -25.38f, -319.62f, true,
                    )
                },
                color, style = stroke,
            )
        }
        PadAction.HOLD -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(4f * k, 4f * k),
                size = Size(16f * k, 16f * k),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f * k),
                style = stroke,
            )
            drawRect(
                color = color,
                topLeft = Offset(9f * k, 9f * k),
                size = Size(6f * k, 6f * k),
                style = stroke,
            )
        }
        PadAction.DROP -> {
            drawPath(line(6f, 5f, 12f, 11f, 18f, 5f), color, style = stroke)
            drawPath(line(6f, 12f, 12f, 18f, 18f, 12f), color, style = stroke)
        }
        PadAction.LEFT -> drawPath(line(14f, 5f, 7f, 12f, 14f, 19f), color, style = stroke)
        PadAction.RIGHT -> drawPath(line(10f, 5f, 17f, 12f, 10f, 19f), color, style = stroke)
        PadAction.PAUSE -> {
            drawPath(line(9f, 5f, 9f, 19f), color, style = stroke)
            drawPath(line(15f, 5f, 15f, 19f), color, style = stroke)
        }
    }
}

package com.serafim.tetris.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** Карточка интерфейса: surface-container со скруглением. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    radius: Int = 20,
    padH: Int = 16,
    padV: Int = 14,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(radius.dp))
            .background(M3.SurfaceContainer)
            .padding(horizontal = padH.dp, vertical = padV.dp)
    ) { content() }
}

/** Заголовок карточки. */
@Composable
fun CardTitle(text: String, size: Int = 12, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = M3.OnSurfaceVariant,
        fontSize = size.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (size * 0.03f).sp,
        modifier = modifier,
    )
}

/** Подскок из CSS-анимации bump: translateY(-8) scale(1.12) → обычное. */
internal fun Modifier.bump(b: Float) = graphicsLayer {
    translationY = -8f * b * density
    scaleX = 1f + 0.12f * b
    scaleY = 1f + 0.12f * b
}

@Composable
internal fun rememberBump(key: Int): Float {
    val a = remember { Animatable(1f) }
    LaunchedEffect(key) {
        if (key > 0) {
            a.snapTo(0f)
            a.animateTo(1f, tween(600, easing = LinearEasing))
        }
    }
    return 1f - Expo.transform(a.value)
}

/** Один счётчик: подпись сверху, крупное число снизу. */
@Composable
fun Stat(
    label: String,
    value: String,
    compact: Boolean,
    bumpKey: Int = 0,
    highlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val b = rememberBump(bumpKey)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, color = M3.OnSurfaceVariant, fontSize = if (compact) 10.sp else 12.sp)
        Text(
            value,
            color = if (highlight) M3.Primary else M3.OnSurface,
            fontSize = if (compact) 18.sp else 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.bump(b),
        )
    }
}

/**
 * Число, которое обязано уместиться в одну строку. Пока влезает — идёт
 * крупным кеглем, не влезает — мельчает, но никогда не переносится:
 * разорванное посередине «6 718 0 / 82» читается хуже любого мелкого.
 */
@Composable
fun FitText(
    text: String,
    color: Color,
    maxSize: TextUnit,
    minSize: TextUnit,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Medium,
    align: TextAlign = TextAlign.Center,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = LocalTextStyle.current.merge(
            TextStyle(
                color = color,
                fontWeight = weight,
                textAlign = align,
                fontSize = maxSize,
                fontFeatureSettings = "tnum",
            ),
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 0.5.sp),
    )
}

/**
 * То же, но с разметкой внутри строки: куском можно выделить ник —
 * цветом или радужной кистью, — а мельчает строка целиком.
 */
@Composable
fun FitText(
    text: AnnotatedString,
    maxSize: TextUnit,
    minSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    align: TextAlign = TextAlign.Start,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = LocalTextStyle.current.merge(
            TextStyle(
                color = color,
                textAlign = align,
                fontSize = maxSize,
                fontFeatureSettings = "tnum",
            ),
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = maxSize, stepSize = 0.5.sp),
    )
}

/**
 * Полоса опыта. Обычно ширина плавно едет к текущей доле, а при новом
 * ранге проигрывается вся последовательность оригинала: заполнение
 * до конца, вспышка, россыпь частиц и обнуление.
 */
@Composable
fun XpBar(
    rank: Int,
    progress: Float,
    label: String,
    rankTick: Int,
    compact: Boolean,
    hideLabel: Boolean = false,
    clock: String? = null,
    modifier: Modifier = Modifier,
) {
    val fill = remember { Animatable(progress) }
    var busy by remember { mutableStateOf(false) }
    var burstKey by remember { mutableIntStateOf(0) }
    var glow by remember { mutableStateOf(false) }
    val b = rememberBump(rankTick)

    LaunchedEffect(rankTick) {
        if (rankTick > 0) {
            busy = true
            glow = true
            fill.animateTo(1f, tween(460, easing = Expo))
            burstKey++
            glow = false
            fill.snapTo(0f)
            busy = false
        }
    }
    LaunchedEffect(progress, busy) {
        if (!busy) fill.animateTo(progress, tween(550, easing = Expo))
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Уровень", color = M3.OnSurfaceVariant, fontSize = if (compact) 11.sp else 12.sp)
                Text(
                    rank.toString(),
                    color = M3.Xp,
                    fontSize = if (compact) 14.sp else 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(start = 5.dp).bump(b),
                )
            }
            if (clock != null) {
                // часы партии: в верхнем ряду счётчиков для них нет места —
                // семизначный счёт и рекорд занимают его почти целиком
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("Время", color = M3.OnSurfaceVariant, fontSize = if (compact) 11.sp else 12.sp)
                    Text(
                        clock,
                        color = M3.OnSurface,
                        fontSize = if (compact) 14.sp else 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
            if (!hideLabel) {
                Text(
                    label,
                    color = M3.OnSurfaceVariant,
                    fontSize = if (compact) 11.sp else 12.sp,
                )
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(M3.SurfaceContainerHighest)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fill.value.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(100.dp))
                        .background(Brush.horizontalGradient(listOf(M3.XpDeep, M3.Xp)))
                )
                if (glow) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight().background(M3.Xp.copy(alpha = 0.4f)))
                }
            }
            XpBurst(burstKey, Modifier.align(Alignment.TopCenter))
        }
    }
}

/** Двадцать четыре искры, разлетающиеся из полосы при новом ранге. */
@Composable
private fun XpBurst(key: Int, modifier: Modifier = Modifier) {
    if (key == 0) return
    val t = remember(key) { Animatable(0f) }
    val dots = remember(key) {
        val rnd = Random(key * 7919)
        List(24) {
            floatArrayOf(
                rnd.nextFloat(),                                   // положение по ширине
                ((rnd.nextFloat() - 0.5f) * 80f),                  // dx
                (-18f - rnd.nextFloat() * 58f),                    // dy
                rnd.nextFloat() * 110f,                            // задержка
            )
        }
    }
    LaunchedEffect(key) { t.animateTo(1f, tween(1060, easing = LinearEasing)) }
    Canvas(modifier.fillMaxWidth().height(1.dp)) {
        val now = t.value * 1060f
        for (d in dots) {
            val local = ((now - d[3]) / 950f).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) continue
            val e = Expo.transform(local)
            val s = 6f * density * (1f - 0.7f * e)
            drawRoundRect(
                color = M3.Xp,
                topLeft = Offset(
                    d[0] * size.width - s / 2f + d[1] * e * density,
                    d[2] * e * density,
                ),
                size = Size(s, s),
                cornerRadius = CornerRadius(2f * density),
                alpha = 1f - e,
            )
        }
    }
}

/**
 * Всплывающий чип с названием комбинации: 1.6 секунды, три участка
 * по кривой expo — как в CSS-анимации chip.
 */
@Composable
fun MessageChip(text: String?, key: Int, modifier: Modifier = Modifier) {
    if (text == null) return
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { p.animateTo(1f, tween(1600, easing = LinearEasing)) }
    val v = p.value
    val alpha = when {
        v < 0.18f -> Expo.transform(v / 0.18f)
        v < 0.72f -> 1f
        else -> 1f - Expo.transform((v - 0.72f) / 0.28f)
    }
    if (alpha <= 0.004f) return
    val shift = when {
        v < 0.18f -> 14f * (1f - Expo.transform(v / 0.18f))
        v < 0.72f -> 0f
        else -> -6f * Expo.transform((v - 0.72f) / 0.28f)
    }
    val scale = if (v < 0.18f) 0.9f + 0.1f * Expo.transform(v / 0.18f) else 1f
    Box(
        modifier.graphicsLayer {
            this.alpha = alpha
            translationY = shift * density
            scaleX = scale
            scaleY = scale
        }
    ) {
        Text(
            text = text,
            color = M3.OnSecondaryContainer,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(100.dp))
                .background(M3.SecondaryContainer)
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
}


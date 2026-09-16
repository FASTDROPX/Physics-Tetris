package com.serafim.tetris.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serafim.tetris.game.Xp
import com.serafim.tetris.game.formatRu
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Доля пути от нуля до настоящего значения: набегает по той же кривой
 * expo, что и всё остальное в игре, — быстро со старта и мягко к концу,
 * как счёт в партии, когда приходят очки. Задержка входит в саму
 * анимацию, а не в `delay`: так кадры идут от часов Compose, и снимок в
 * тесте ловит ровно тот момент, который задан.
 */
@Composable
fun rememberTally(durationMs: Int, delayMs: Int = 0): Float {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(durationMs, delayMillis = delayMs, easing = Expo)) }
    return a.value
}

/** Часть от целого с округлением — для счётчиков, которые набегают. */
fun Long.tally(k: Float): Long = if (k >= 1f) this else (this.toDouble() * k).roundToLong()

/** Цифры одной ширины: пока число бежит, оно не дрожит по ширине. */
@Composable
fun tabular(): TextStyle = LocalTextStyle.current.merge(TextStyle(fontFeatureSettings = "tnum"))

private const val BURST_MS = 1100L
private const val GLOW_MS = 260f

/**
 * Полоса опыта, которая не показывает, а проигрывает: едет от одного
 * значения опыта к другому по кривой expo и на каждом пройденном ранге
 * делает всё то же, что в партии, — вспышку, россыпь искр и подскок
 * номера. Искры каждого ранга живут своей жизнью, поэтому, когда ранги
 * идут чаще, чем гаснет россыпь, они накладываются, а не обрывают друг
 * друга.
 *
 * Едет по шкале, где все ранги равной ширины ([Xp.position]): в очках
 * каждый следующий вдвое длиннее, и по прямой первые десять пролетели бы
 * за один кадр.
 */
@Composable
fun XpSweep(
    fromXp: Long,
    toXp: Long,
    durationMs: Int,
    delayMs: Int,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val from = remember(fromXp) { Xp.position(fromXp) }
    val to = remember(fromXp, toXp) { Xp.position(maxOf(fromXp, toXp)) }
    var pos by remember { mutableDoubleStateOf(from) }
    var clock by remember { mutableLongStateOf(0L) }
    var rankTick by remember { mutableIntStateOf(0) }
    var glowAt by remember { mutableLongStateOf(Long.MIN_VALUE / 2) }
    // [ранг, время рождения] — по одной россыпи на каждый пройденный ранг
    val bursts = remember { mutableStateListOf<LongArray>() }

    LaunchedEffect(fromXp, toXp) {
        // едем от того, что уже на экране: при первом показе это старт, а
        // после сброса опыта — нынешнее место, и полоса отматывается к нулю
        // без вспышек и искр, быстрее и без задержки
        val begin = pos
        val down = to < begin
        val dur = if (down) 900 else durationMs
        val wait = if (down) 0 else delayMs
        var shown = floor(begin).toInt().coerceAtMost(Xp.TOP_RANK)
        val start = withFrameMillis { it }
        while (true) {
            val now = withFrameMillis { it }
            val lin = ((now - start - wait).toFloat() / dur).coerceIn(0f, 1f)
            val p = if (lin >= 1f) to else begin + (to - begin) * Expo.transform(lin)
            val r = floor(p).toInt().coerceAtMost(Xp.TOP_RANK)
            if (r < shown) shown = r
            while (shown < r) {
                shown++
                bursts.add(longArrayOf(shown.toLong(), now))
                rankTick++
                glowAt = now
            }
            pos = p
            clock = now
            bursts.removeAll { now - it[1] > BURST_MS }
            if (lin >= 1f && bursts.isEmpty() && now - glowAt > GLOW_MS) break
        }
    }

    val rank = floor(pos).toInt().coerceIn(0, Xp.TOP_RANK)
    val frac = (pos - rank).coerceIn(0.0, 1.0)
    val lo = Xp.BOUNDS[rank]
    val need = Xp.BOUNDS[rank + 1] - lo
    val cur = if (pos >= to) (maxOf(fromXp, toXp) - lo).coerceIn(0L, need) else (frac * need).roundToLong()
    val glow = (1f - (clock - glowAt) / GLOW_MS).coerceIn(0f, 1f)
    val b = rememberBump(rankTick)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Уровень", color = M3.OnSurfaceVariant, fontSize = if (compact) 12.sp else 13.sp)
                Text(
                    rank.toString(),
                    color = M3.Xp,
                    fontSize = if (compact) 16.sp else 18.sp,
                    fontWeight = FontWeight.Medium,
                    style = tabular(),
                    modifier = Modifier.padding(start = 6.dp).bump(b),
                )
            }
            Text(
                formatRu(cur) + " / " + formatRu(need),
                color = M3.OnSurfaceVariant,
                fontSize = if (compact) 11.sp else 12.sp,
                style = tabular(),
            )
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
                        .fillMaxWidth(frac.toFloat())
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(100.dp))
                        .background(Brush.horizontalGradient(listOf(M3.XpDeep, M3.Xp)))
                )
                if (glow > 0f) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight().background(M3.Xp.copy(alpha = 0.4f * glow)))
                }
            }
            Canvas(Modifier.matchParentSize()) {
                for (burst in bursts) {
                    val age = (clock - burst[1]).toFloat()
                    val rnd = Random(burst[0].toInt() * 7919 + 17)
                    repeat(24) {
                        val x = rnd.nextFloat()
                        val dx = (rnd.nextFloat() - 0.5f) * 80f
                        val dy = -18f - rnd.nextFloat() * 58f
                        val wait = rnd.nextFloat() * 110f
                        val local = ((age - wait) / 950f).coerceIn(0f, 1f)
                        if (local <= 0f || local >= 1f) return@repeat
                        val e = Expo.transform(local)
                        val s = 6f * density * (1f - 0.7f * e)
                        drawRoundRect(
                            color = M3.Xp,
                            topLeft = Offset(x * size.width - s / 2f + dx * e * density, dy * e * density),
                            size = Size(s, s),
                            cornerRadius = CornerRadius(2f * density),
                            alpha = 1f - e,
                        )
                    }
                }
            }
        }
    }
}

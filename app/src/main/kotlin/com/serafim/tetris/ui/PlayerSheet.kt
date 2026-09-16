package com.serafim.tetris.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.serafim.tetris.game.FlameTier
import com.serafim.tetris.game.StreakState
import com.serafim.tetris.game.formatRu
import com.serafim.tetris.game.formatTotal
import com.serafim.tetris.game.pluralRu
import com.serafim.tetris.online.PlayerView

/**
 * Окно игрока — тап по строке таблицы. Устроено как окно статистики и
 * двигается так же: окно въезжает, полоса уровня проигрывает все ранги с
 * вспышками и искрами, числа набегают от нуля по кривой expo.
 *
 * Отличие одно: чужие числа приходят с сервера не сразу. Поэтому все
 * анимации содержимого идут не от открытия окна, а от прихода данных —
 * иначе к тому моменту, как статистика загрузилась, всё уже «проехало» бы,
 * и числа просто выскочили.
 */
@Composable
fun PlayerSheet(
    large: Boolean,
    view: PlayerView,
    today: Long,
    animate: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Overlay(closing = false, large = large, onScrim = onBack) { t ->
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(if (large) 34.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            RiseIn(t, 80f) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NickText(
                        view.name,
                        M3.OnSurface,
                        fontSize = if (large) 26.sp else 22.sp,
                        fill = false,
                        badge = if (large) 28.dp else 24.dp,
                    )
                }
            }
            RiseIn(t, 130f) { Lead(subtitleOf(view), large) }
            when (view) {
                is PlayerView.Loading -> RiseIn(t, 180f) { Loading(large) }
                is PlayerView.Failed -> RiseIn(t, 180f) { Trouble(view, large, onRetry) }
                // ключ — игрок: пришли данные другого — всё проигрывается заново
                is PlayerView.Ready -> key(view.uid) { Stats(view, today, large, animate) }
            }
            RiseIn(t, 260f) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = M3.Primary, contentColor = M3.OnPrimary),
                ) { Text("Назад", fontWeight = FontWeight.Medium) }
            }
        }
    }
}

private fun subtitleOf(v: PlayerView): String {
    val place = "${v.rank}-е место в таблице"
    return if (v.me) "Это вы · $place" else place
}

@Composable
private fun Lead(text: String, large: Boolean) {
    Text(
        text,
        color = M3.OnSurfaceVariant,
        fontSize = if (large) 15.sp else 14.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Loading(large: Boolean) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(color = M3.Primary, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        Text("Загружаем статистику…", color = M3.OnSurfaceVariant, fontSize = if (large) 14.sp else 13.sp)
    }
}

@Composable
private fun Trouble(view: PlayerView.Failed, large: Boolean, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (view.offline) "Нет соединения с интернетом" else "Статистика не загрузилась",
            color = M3.OnSurface,
            fontSize = if (large) 16.sp else 15.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        if (!view.offline) {
            Text(
                view.message,
                color = M3.OnSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        TextButton(onClick = onRetry) { Text("Обновить", color = M3.Primary, fontWeight = FontWeight.Medium) }
    }
}

/**
 * Числа игрока. Все часы здесь свои и пускаются в тот момент, когда блок
 * впервые показан, то есть когда данные пришли: въезд строк, полоса уровня
 * и набегающие числа.
 */
@Composable
private fun Stats(view: PlayerView.Ready, today: Long, large: Boolean, animate: Boolean) {
    val clock = remember { Animatable(if (animate) 0f else 3000f) }
    LaunchedEffect(Unit) { if (animate) clock.animateTo(3000f, tween(3000, easing = LinearEasing)) }
    val t = clock.value
    val k = if (animate) rememberTally(2000, delayMs = 250) else 1f
    val p = view.profile
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (p != null) {
            val ranks = com.serafim.tetris.game.Xp.position(p.xp)
            RiseIn(t, 0f) {
                XpSweep(
                    fromXp = if (animate) 0L else p.xp,
                    toXp = p.xp,
                    durationMs = (1800 + 170 * ranks).toInt().coerceAtMost(4800),
                    delayMs = 250,
                    compact = !large,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )
            }
        }
        RiseIn(t, 60f) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatRow("Всего очков", formatRu(view.total.tally(k)), large)
                StatRow("Рекорд партии", formatRu(view.best.tally(k)), large)
                if (p != null) {
                    StatRow("Поставлено фигур", formatRu(p.pieces.tally(k)), large)
                    StatRow("Стёрто линий", formatRu(p.lines.tally(k)), large)
                    StatRow("Сыграно партий", formatRu(p.games.tally(k)), large)
                    StatRow("Время в игре", formatTotal(p.timeMs * k.toDouble()), large)
                }
            }
        }
        if (p != null) {
            RiseIn(t, 120f) { StreakRow(p.streakShown(today), p.streakBest, p.streakState(today), k, large, animate) }
        } else {
            RiseIn(t, 120f) {
                Lead("Подробной статистики пока нет: она появится, когда игрок сыграет в новой версии игры.", large)
            }
        }
    }
}

/** Серия игрока: огонёк его ступени, дни подряд и рекорд. */
@Composable
private fun StreakRow(days: Int, best: Int, state: StreakState, k: Float, large: Boolean, animate: Boolean) {
    val tier = if (state == StreakState.NONE) FlameTier.OUT else FlameTier.of(days)
    val shown = days.toLong().tally(k).toInt()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(M3.SurfaceContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Flame(tier, if (large) 30.dp else 26.dp, dim = state == StreakState.AT_RISK, animate = animate)
        Column(Modifier.weight(1f)) {
            Text("Серия дней", color = M3.OnSurfaceVariant, fontSize = if (large) 15.sp else 14.sp)
            if (best > 0) {
                Text(
                    "рекорд $best ${pluralRu(best.toLong(), "день", "дня", "дней")}",
                    color = M3.OnSurfaceVariant.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                )
            }
        }
        Text(
            if (state == StreakState.NONE) "нет" else "$shown ${pluralRu(shown.toLong(), "день", "дня", "дней")}",
            color = M3.OnSurface,
            fontSize = if (large) 17.sp else 16.sp,
            fontWeight = FontWeight.Medium,
            style = tabular(),
        )
    }
}

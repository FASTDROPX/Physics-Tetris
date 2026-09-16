package com.serafim.tetris

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serafim.tetris.game.FlameTier
import com.serafim.tetris.game.STREAK_DAY_MS
import com.serafim.tetris.game.StreakState
import com.serafim.tetris.game.TetrisGame
import com.serafim.tetris.ui.CALENDAR_WEEKS
import com.serafim.tetris.ui.Flame
import com.serafim.tetris.ui.M3
import com.serafim.tetris.ui.StreakCard
import com.serafim.tetris.ui.StreakCalendar
import com.serafim.tetris.ui.StreakSheet
import com.serafim.tetris.ui.StreakView
import com.serafim.tetris.ui.TetrisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Серия дней: огоньки всех ступеней, карточка в меню во всех состояниях,
 * окно серии — и главное, что минута настоящей игры действительно
 * зажигает огонёк и доходит до диска.
 */
@RunWith(AndroidJUnit4::class)
class StreakTest {

    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun save(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.filesDir, "shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun screen(w: Int = 360, h: Int = 825, content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme {
                    Box(Modifier.requiredSize(w.dp, h.dp).background(M3.Surface)) { content() }
                }
            }
        }
    }

    /** Игра по дням: немного каждый день, последние дни — побольше. */
    private fun history(days: Int, pattern: (Int) -> Long) = List(42) { i ->
        val back = 41 - i
        if (back < days) pattern(back) else 0L
    }

    @Test
    fun огоньки_всех_ступеней() {
        // горящие сверху, «под угрозой» снизу; по четыре в ряд — в ширину телефона
        screen(h = 560) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                for (dim in listOf(false, true)) {
                    for (row in FlameTier.entries.chunked(4)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (tier in row) {
                                Column(Modifier.width(78.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Flame(tier, 56.dp, dim = dim, animate = false)
                                    Text(
                                        if (dim) "${tier.from}+ · угроза" else "${tier.from}+ · ${tier.title}",
                                        color = M3.OnSurfaceVariant,
                                        fontSize = 10.sp,
                                        maxLines = 2,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        save("92_flames")
    }

    @Test
    fun карточка_серии_во_всех_состояниях() {
        val views = listOf(
            StreakView(),
            StreakView(days = 0, best = 9, state = StreakState.NONE, totalDays = 20,
                history = history(12) { if (it > 2) 4 * STREAK_DAY_MS else 0L }, weekday = 3),
            StreakView(days = 4, best = 9, state = StreakState.AT_RISK, totalDays = 24,
                history = history(5) { if (it == 0) 0L else 3 * STREAK_DAY_MS }, weekday = 3),
            StreakView(days = 12, best = 47, state = StreakState.LIT, totalDays = 80,
                history = history(12) { (it % 4 + 1) * 3 * STREAK_DAY_MS }, weekday = 5),
            StreakView(days = 30, best = 30, state = StreakState.LIT, totalDays = 30,
                history = history(30) { 20 * STREAK_DAY_MS }, weekday = 7),
            StreakView(days = 120, best = 120, state = StreakState.LIT, totalDays = 150,
                history = history(42) { 12 * STREAK_DAY_MS }, weekday = 1),
        )
        screen(h = 520) {
            Column(Modifier.padding(horizontal = 40.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (v in views) StreakCard(large = false, view = v, animate = false, onClick = {})
            }
        }
        rule.mainClock.advanceTimeBy(500)
        save("93_streak_cards")
        rule.onNodeWithText("Серия дней").assertExists()
        rule.onNodeWithText("Серия прервалась").assertExists()
        rule.onNodeWithText("4 дня подряд").assertExists()
        rule.onNodeWithText("Сыграйте сегодня").assertExists()
        rule.onNodeWithText("12 дней подряд").assertExists()
        rule.onNodeWithText("Засчитан · рекорд 47").assertExists()
        rule.onAllNodesWithText("Засчитан · это рекорд").assertCountEquals(2)
        rule.onNodeWithText("120 дней подряд").assertExists()
    }

    @Test
    fun окно_серии() {
        val v = StreakView(
            days = 8, best = 15, state = StreakState.LIT, totalDays = 37,
            history = history(30) { back ->
                when {
                    back < 8 -> ((back * 7) % 5 + 1) * 4 * STREAK_DAY_MS
                    back == 8 -> 0L
                    back % 3 == 0 -> 0L
                    else -> (back % 4) * 3 * STREAK_DAY_MS
                }
            },
            weekday = 4,
            longestDayMs = 52 * STREAK_DAY_MS,
        )
        screen { StreakSheet(closing = false, large = false, view = v, animate = false, onBack = {}) }
        rule.mainClock.advanceTimeBy(2000)
        save("94_streak_sheet")
        rule.onNodeWithText("8 дней подряд").assertExists()
        rule.onNodeWithText("15 дней").assertExists()
        rule.onNodeWithText("Последние 6 недель").assertExists()
        rule.onNodeWithText("Назад").performScrollTo()
        rule.mainClock.advanceTimeBy(500)
        save("94_streak_sheet_bottom")
    }

    /**
     * Клетки календаря встают волной: диагональ за диагональю слева сверху
     * направо вниз, а внутри диагонали — по очереди снизу вверх. Проверяется
     * по самим кадрам: для каждой клетки ловится момент, когда она
     * проступила, и порядок этих моментов сверяется с задуманным.
     */
    @Test
    fun клетки_календаря_встают_волной() {
        // все дни сыграны долго — все клетки яркие, и их появление видно по пикселю
        val v = StreakView(
            days = 42, best = 42, state = StreakState.LIT, totalDays = 42,
            history = List(42) { 20 * STREAK_DAY_MS }, weekday = 7, longestDayMs = 20 * STREAK_DAY_MS,
        )
        // календарь отдельно от окна: окно въезжает снизу, и пока оно едет,
        // точки замера смещались бы вместе с ним
        screen { Box(Modifier.padding(top = 40.dp).fillMaxWidth(), contentAlignment = Alignment.TopCenter) { StreakCalendar(v, large = false, animate = true) } }
        rule.mainClock.advanceTimeBy(16)
        val centers = HashMap<Pair<Int, Int>, Pair<Int, Int>>()
        for (w in 0 until CALENDAR_WEEKS) for (d in 0 until 7) {
            val b = rule.onNodeWithTag("cal-$w-$d").fetchSemanticsNode().boundsInRoot
            centers[w to d] = b.center.x.toInt() to b.center.y.toInt()
        }
        val base = rule.mainClock.currentTime
        val seen = HashMap<Pair<Int, Int>, Long>()
        val shots = mutableListOf(560L, 760L, 960L)
        while (rule.mainClock.currentTime - base < 2200 && seen.size < centers.size) {
            val ms = rule.mainClock.currentTime - base
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            for ((cell, c) in centers) {
                if (cell in seen) continue
                val px = bmp.getPixel(c.first, c.second)
                // яркая жёлтая клетка: красный канал проступает выше половины
                if ((px shr 16 and 0xFF) > 150) seen[cell] = ms
            }
            if (shots.isNotEmpty() && ms >= shots.first()) {
                val dir = File(context.filesDir, "shots").apply { mkdirs() }
                FileOutputStream(File(dir, "95_cascade_%04d.png".format(shots.removeAt(0)))).use {
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            rule.mainClock.advanceTimeBy(16)
        }
        Log.i("StreakTest", "клеток проступило ${seen.size} из ${centers.size}; не проступили: " +
            centers.keys.filter { it !in seen }.sortedBy { it.first * 10 + it.second } + " центры: " + centers[0 to 0] + " " + centers[5 to 6])
        assertEquals("все клетки проступили", centers.size, seen.size)

        val diagonals = (0..CALENDAR_WEEKS - 1 + 6).map { d ->
            // снизу вверх: от большей строки к меньшей
            (CALENDAR_WEEKS - 1 downTo 0).mapNotNull { w -> val c = d - w; if (c in 0..6) w to c else null }
        }
        for ((d, cells) in diagonals.withIndex()) {
            val times = cells.map { seen.getValue(it) }
            Log.i("StreakTest", "диагональ $d снизу вверх: $times")
            for (i in 1 until times.size) {
                assertTrue("диагональ $d: клетка выше не раньше нижней $times", times[i] >= times[i - 1])
            }
            if (cells.size >= 3) {
                assertTrue("диагональ $d встаёт не разом, а по очереди: $times", times.last() > times.first())
            }
        }
        for (d in 1 until diagonals.size) {
            val prev = seen.getValue(diagonals[d - 1].first())
            val cur = seen.getValue(diagonals[d].first())
            assertTrue("диагональ $d начинается позже диагонали ${d - 1}", cur > prev)
        }
    }

    @Test
    fun минута_игры_зажигает_огонёк() {
        val prefs = Prefs(context)
        val saved = prefs.streak
        prefs.streak = ""
        try {
            val fx = AndroidFx(context).apply { soundOn = false; vibrationOn = false }
            val game = TetrisGame(fx, Random(3))
            var ctrl: Controller? = null
            rule.setContent {
                val scope = rememberCoroutineScope()
                ctrl = remember { Controller(game, fx, prefs, scope) }
            }
            rule.runOnIdle {
                val c = checkNotNull(ctrl)
                assertEquals("до игры серии нет", StreakState.NONE, c.streakView.state)
                game.debugStartImmediate()
                // пятьдесят секунд — мало
                repeat(3125) { game.update(16.0); c.onStateChanged(game.state) }
                assertEquals("меньше минуты день не засчитан", StreakState.NONE, c.streakView.state)
                // ещё пятнадцать — и огонёк горит
                repeat(940) { game.update(16.0); c.onStateChanged(game.state) }
                assertEquals("минута игры зажгла огонёк", StreakState.LIT, c.streakView.state)
                assertEquals(1, c.streakView.days)
                assertTrue("сегодняшний день в истории", c.streakView.week.last() >= STREAK_DAY_MS)
                assertTrue("серия уже на диске", prefs.streak.startsWith("s1;1;1;"))
                assertEquals("об этом сказано в игре", "Серия: 1 день подряд", fx.chip?.text)
            }
        } finally {
            prefs.streak = saved
        }
    }
}

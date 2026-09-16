package com.serafim.tetris

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serafim.tetris.online.BoardKind
import com.serafim.tetris.online.BoardRow
import com.serafim.tetris.online.BoardView
import com.serafim.tetris.online.JoinIssue
import com.serafim.tetris.online.Leaderboard
import com.serafim.tetris.online.isDevNick
import com.serafim.tetris.online.Standing
import com.serafim.tetris.ui.BoardSheet
import com.serafim.tetris.ui.M3
import com.serafim.tetris.ui.TetrisTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class BoardTest {

    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun shot(
        name: String,
        times: List<Long> = listOf(3000L),
        type: String? = null,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme {
                    Box(Modifier.requiredSize(360.dp, 825.dp).background(M3.Surface)) { content() }
                }
            }
        }
        // жалоба на занятый ник видна только рядом с теми буквами,
        // на которые её получили, — значит, их надо набрать
        if (type != null) {
            rule.mainClock.advanceTimeBy(1500)
            rule.onNode(hasSetTextAction()).performTextInput(type)
        }
        var at = 0L
        for (ms in times) {
            rule.mainClock.advanceTimeBy(ms - at)
            at = ms
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            val dir = File(context.filesDir, "shots").apply { mkdirs() }
            val tag = if (times.size == 1) name else "${name}_$ms"
            FileOutputStream(File(dir, "$tag.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private val names = listOf(
        "wor1356", "FastdropX", "Ёжик", "Tetris_Pro", "Макс", "ninja.42", "Аня", "Blocky",
        "Кот Василий", "zzz", "Лёша", "Stack-it",
    )

    @Test
    fun таблица_с_игроком_в_середине() = shot("80_board_ready", listOf(300L, 450L, 600L, 800L, 1100L, 3000L)) {
        val rows = names.mapIndexed { i, n ->
            BoardRow("u$i", n, 48_115_902L - i * 3_517_311L, me = i == 4)
        }
        BoardSheet(
            large = false,
            view = BoardView.Ready(rows, myRank = 5, myValue = rows[4].value, stale = false),
            kind = BoardKind.TOTAL, nick = "Макс",
            joining = false, issue = null,
            onKind = {}, onJoin = {}, onRetry = {}, onBack = {},
        )
    }

    @Test
    fun таблица_до_выбора_ника() = shot("81_board_join") {
        val rows = names.take(5).mapIndexed { i, n -> BoardRow("u$i", n, 6_718_082L - i * 911_000L, me = false) }
        BoardSheet(
            large = false,
            view = BoardView.Ready(rows, myRank = null, myValue = 0, stale = false),
            kind = BoardKind.BEST, nick = "",
            joining = false, issue = null,
            onKind = {}, onJoin = {}, onRetry = {}, onBack = {},
        )
    }

    @Test
    fun таблица_без_сети() = shot("82_board_failed") {
        BoardSheet(
            large = false,
            view = BoardView.Failed("Нет связи с сервером. Проверьте интернет.", offline = true),
            kind = BoardKind.TOTAL, nick = "Макс",
            joining = false, issue = null,
            onKind = {}, onJoin = {}, onRetry = {}, onBack = {},
        )
    }

    /** Карточка места в меню — все её состояния разом, включая набег разрыва. */
    @Test
    fun карточка_места() = shot("83_standing", listOf(400L, 700L, 3000L)) {
        val states = listOf(
            Standing.Placed(2, "wor1356", 82_302, leader = false),
            Standing.Placed(1, "FastdropX", 1_004, leader = true),
            Standing.Placed(1, null, 0, leader = true),
            Standing.Placed(17, "Кот Василий", 21, leader = false),
            Standing.NotJoined,
            Standing.NoScore,
            Standing.Offline,
            Standing.Unknown,
        )
        androidx.compose.foundation.layout.Column(
            Modifier.padding(24.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        ) {
            states.forEach { com.serafim.tetris.ui.StandingCard(large = false, standing = it, onClick = {}) }
        }
    }

    /** Список из памяти телефона: значок сети, время обновления, черта. */
    @Test
    fun таблица_из_памяти() = shot("86_offline_list") {
        val rows = names.take(5).mapIndexed { i, n ->
            BoardRow("u$i", n, 15_812_768L - i * 4_221_009L, me = i == 1)
        }
        BoardSheet(
            large = false,
            view = BoardView.Ready(
                rows, myRank = 2, myValue = rows[1].value, stale = true,
                syncedAt = System.currentTimeMillis() - 42 * 60_000L,
            ),
            kind = BoardKind.TOTAL, nick = "FastdropX",
            joining = false, issue = null,
            onKind = {}, onJoin = {}, onRetry = {}, onBack = {},
        )
    }

    /**
     * Фигурка перед ником друга меняется поворотом: кадры внутри четверти
     * обязаны отличаться друг от друга (значок поворачивается), а кадры
     * через секунду — тем более (фигурка уже другая).
     */
    @Test
    fun фигурка_у_друга() {
        val shots = mutableListOf<android.graphics.Bitmap>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme {
                    Box(Modifier.requiredSize(360.dp, 825.dp).background(M3.Surface)) {
                        BoardSheet(
                            large = false,
                            view = BoardView.Ready(
                                names.take(4).mapIndexed { i, n ->
                                    BoardRow("u$i", n, 900_000L - i * 120_000L, me = false)
                                },
                                myRank = null, myValue = 0, stale = false,
                            ),
                            kind = BoardKind.TOTAL, nick = "Макс",
                            joining = false, issue = null,
                            onKind = {}, onJoin = {}, onRetry = {}, onBack = {},
                        )
                    }
                }
            }
        }
        val dir = File(context.filesDir, "shots").apply { mkdirs() }
        var at = 0L
        // 3000 — начало поворота, 3260 — середина с подменой, 3520 — конец
        for (ms in listOf(3000L, 3130L, 3260L, 3390L, 3520L, 4100L, 5200L)) {
            rule.mainClock.advanceTimeBy(ms - at)
            at = ms
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            shots += bmp
            FileOutputStream(File(dir, "87_piece_$ms.png")).use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        // считаем только полосу, где стоит фигурка первой строки
        fun band(b: android.graphics.Bitmap): IntArray {
            val px = IntArray(65 * 65)
            b.getPixels(px, 0, 65, 150, 500, 65, 65)
            return px
        }
        val bands = shots.map { band(it) }
        fun moved(i: Int, j: Int) = bands[i].indices.count { bands[i][it] != bands[j][it] }
        val turn = moved(0, 2)          // начало поворота против середины
        val done = moved(2, 4)          // середина против конца
        val next = moved(4, 5)          // и следующая секунда
        Log.i("BoardTest", "поворот: $turn, доворот: $done, через секунду: $next")
        assertTrue("значок не поворачивается", turn > 50 && done > 50)
        assertTrue("фигурка не сменилась", next > 50)
    }

    /** Ник занят: форма не закрылась, поле красное, под ним причина. */
    @Test
    fun ник_занят() = shot("84_join_taken", type = "fastdropx") {
        BoardSheet(
            large = false,
            view = BoardView.Ready(
                names.take(4).mapIndexed { i, n -> BoardRow("u$i", n, 900_000L - i * 120_000L, me = false) },
                myRank = null, myValue = 0, stale = false,
            ),
            kind = BoardKind.TOTAL, nick = "",
            joining = false,
            issue = JoinIssue("fastdropx", "Этот ник закреплён за разработчиком игры."),
            onKind = {}, onJoin = {}, onRetry = {}, onBack = {},
        )
    }

    /**
     * Радуга у ника разработчика: два кадра подряд обязаны отличаться —
     * иначе перелив стоит на месте.
     */
    @Test
    fun радуга_ника_разработчика() {
        val shots = mutableListOf<android.graphics.Bitmap>()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme {
                    Box(Modifier.requiredSize(360.dp, 825.dp).background(M3.Surface)) {
                        BoardSheet(
                            large = false,
                            view = BoardView.Ready(
                                names.take(6).mapIndexed { i, n ->
                                    BoardRow("u$i", n, 900_000L - i * 120_000L, me = n == "FastdropX")
                                },
                                myRank = 2, myValue = 780_000L, stale = false,
                            ),
                            kind = BoardKind.TOTAL, nick = "FastdropX",
                            joining = false, issue = null,
                            onKind = {}, onJoin = {}, onRetry = {}, onBack = {},
                        )
                    }
                }
            }
        }
        val dir = File(context.filesDir, "shots").apply { mkdirs() }
        var at = 0L
        for (ms in listOf(3000L, 3650L)) {
            rule.mainClock.advanceTimeBy(ms - at)
            at = ms
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            shots += bmp
            FileOutputStream(File(dir, "85_rainbow_$ms.png")).use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val a = IntArray(shots[0].width * shots[0].height)
        val b = IntArray(a.size)
        shots[0].getPixels(a, 0, shots[0].width, 0, 0, shots[0].width, shots[0].height)
        shots[1].getPixels(b, 0, shots[1].width, 0, 0, shots[1].width, shots[1].height)
        val moved = a.indices.count { a[it] != b[it] }
        Log.i("BoardTest", "радуга сдвинулась на $moved точек")
        assertTrue("перелив стоит на месте", moved > 200)
    }

    /**
     * Место и ближайший соперник — по живой таблице. Тест только читает:
     * ни строки, ни ника после него не остаётся. Проверяется связность
     * ответа, а не конкретные числа: таблица живая и меняется.
     */
    @Test
    fun место_в_живой_таблице() {
        runBlocking {
            val board = Leaderboard(Prefs(context), this)
            withTimeout(40_000) {
                val low = board.place(1)
                val high = board.place(Long.MAX_VALUE / 2)
                Log.i("BoardTest", "место с 1 очком: $low")
                Log.i("BoardTest", "место с горой очков: $high")
                assertTrue("с одним очком место не первое или таблица пуста", low.rank >= 1)
                assertTrue("с горой очков — первое место", high.rank == 1L && high.leader)
                if (low.rank > 1) {
                    assertTrue("сверху кто-то есть", low.rival != null)
                    assertTrue("и он впереди", low.gap > 0)
                }
            }
        }
    }

    /**
     * Ник разработчика чужому не достаётся: проверка идёт по живой базе и
     * только читает — ни брони, ни строки после теста не остаётся.
     */
    @Test
    fun ник_разработчика_занят() {
        val prefs = Prefs(context)
        val oldNick = prefs.nick
        prefs.nick = ""
        try {
            runBlocking {
                val board = Leaderboard(prefs, this)
                withTimeout(40_000) {
                    assertTrue("разные буквы по высоте — тот же ник", isDevNick("fastdropx"))
                    board.join("fastdropx", best = 1, total = 1)
                    // join работает в своей корутине: ждём, пока ответит
                    var waited = 0
                    while (board.issue == null && board.nick.isEmpty() && waited < 300) {
                        kotlinx.coroutines.delay(100)
                        waited++
                    }
                    Log.i("BoardTest", "ответ на чужой ник: " + board.issue)
                    assertTrue("ник не взят", board.nick.isEmpty())
                    assertTrue("сказано, почему", board.issue != null)
                    assertEquals("", prefs.nick)
                }
            }
        } finally {
            prefs.nick = oldNick
        }
    }

    /**
     * Настоящий путь через проект Firebase: записать строку, увидеть её в
     * таблице, стереть и убедиться, что её нет. После теста в базе ничего
     * не остаётся. Падает с понятной ошибкой, если в консоли не включён
     * анонимный вход, не создана база или не опубликованы правила.
     */
    @Test
    fun запись_и_сброс_через_firebase() {
        val prefs = Prefs(context)
        val oldNick = prefs.nick
        prefs.nick = "claude-test"
        try {
            runBlocking {
                val board = Leaderboard(prefs, this)
                withTimeout(40_000) {
                    board.push(best = 1_234, total = 5_678, sync = true)
                    val a = board.fetch(BoardKind.TOTAL)
                    Log.i("BoardTest", "после записи: " + a.rows.map { "${it.name}=${it.value}${if (it.me) "*" else ""}" })
                    assertFalse("ответ не из кеша", a.stale)
                    assertTrue("своя строка в таблице", a.rows.any { it.me && it.value == 5_678L && it.name == "claude-test" })

                    val b = board.fetch(BoardKind.BEST)
                    assertTrue("и в таблице рекордов", b.rows.any { it.me && it.value == 1_234L })

                    board.erase(sync = true)
                    runCatching { board.releaseClaim("claude-test") }
                    val c = board.fetch(BoardKind.TOTAL)
                    Log.i("BoardTest", "после сброса: " + c.rows.map { "${it.name}=${it.value}" })
                    assertFalse("после сброса строки нет", c.rows.any { it.me })
                }
            }
        } finally {
            prefs.nick = oldNick
        }
    }

    /**
     * Потолок очков и запрет на откат назад. Правила живут только на
     * сервере, поэтому и проверяются на живой базе: игра пробует записать
     * 90 миллиардов и уменьшить свою же сумму — оба раза база обязана
     * отказать, а строка остаться прежней. После теста в базе ничего не
     * остаётся.
     */
    @Test
    fun таблица_не_принимает_чушь() {
        val prefs = Prefs(context)
        val oldNick = prefs.nick
        prefs.nick = "claude-rules"
        try {
            runBlocking {
                val board = Leaderboard(prefs, this)
                withTimeout(60_000) {
                    try {
                        board.push(best = 1_000, total = 4_000, sync = true)
                        board.push(best = 2_000, total = 9_000, sync = true)
                        val up = board.fetch(BoardKind.TOTAL)
                        Log.i("BoardTest", "после роста: ${up.myValue}")
                        assertEquals("выросшая сумма записалась", 9_000L, up.myValue)

                        // те самые 90 миллиардов — выше потолка правил
                        val huge = runCatching {
                            board.push(best = 1_000, total = 90_000_000_000L, sync = true)
                        }
                        Log.i("BoardTest", "90 млрд: ${huge.exceptionOrNull()}")
                        assertTrue("90 миллиардов не приняты", huge.isFailure)

                        // и откат назад: сумма очков не уменьшается
                        val back = runCatching {
                            board.push(best = 2_000, total = 5_000, sync = true)
                        }
                        Log.i("BoardTest", "откат: ${back.exceptionOrNull()}")
                        assertTrue("откат назад не принят", back.isFailure)

                        val after = board.fetch(BoardKind.TOTAL)
                        Log.i("BoardTest", "после отказов: ${after.myValue}")
                        assertEquals("строка осталась прежней", 9_000L, after.myValue)
                    } finally {
                        runCatching { board.erase(sync = true) }
                        runCatching { board.releaseClaim("claude-rules") }
                    }
                }
            }
        } finally {
            prefs.nick = oldNick
        }
    }
}

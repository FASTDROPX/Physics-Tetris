package com.serafim.tetris

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serafim.tetris.game.formatRu
import com.serafim.tetris.online.BoardKind
import com.serafim.tetris.online.BoardRow
import com.serafim.tetris.online.BoardView
import com.serafim.tetris.online.PlayerView
import com.serafim.tetris.online.Profile
import com.serafim.tetris.ui.BoardSheet
import com.serafim.tetris.ui.M3
import com.serafim.tetris.ui.PlayerSheet
import com.serafim.tetris.ui.TetrisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Окно игрока из таблицы лидеров: все его состояния, анимации от прихода
 * данных и то, что тап по строке открывает именно того, по кому тапнули.
 */
@RunWith(AndroidJUnit4::class)
class PlayerSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** «Сегодня» для окна: серия с днём 20 000 горит, с 19 990 — давно прервалась. */
    private val today = 20_000L

    private val profile = Profile(
        xp = 3_816_402, pieces = 12_845, lines = 4_120, games = 311, timeMs = 41L * 3_600_000 + 17 * 60_000,
        streak = 23, streakBest = 31, streakDay = today,
    )

    private fun ready(profile: Profile? = this.profile, me: Boolean = false) = PlayerView.Ready(
        uid = "u1", name = "FastdropX", rank = 1, me = me,
        best = 1_284_915, total = 48_115_902, profile = profile,
    )

    private fun save(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.filesDir, "shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun screen(content: @Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme {
                    Box(Modifier.requiredSize(360.dp, 825.dp).background(M3.Surface)) { content() }
                }
            }
        }
    }

    @Test
    fun полная_статистика_игрока() {
        screen { PlayerSheet(large = false, view = ready(), today = today, animate = true, onRetry = {}, onBack = {}) }
        var at = 0L
        for (ms in listOf(250L, 500L, 900L, 1500L, 5000L)) {
            rule.mainClock.advanceTimeBy(ms - at)
            at = ms
            save("96_player_%04d".format(ms))
        }
        rule.onNodeWithText("1-е место в таблице").assertExists()
        rule.onNodeWithText(formatRu(48_115_902)).assertExists()
        rule.onNodeWithText(formatRu(1_284_915)).assertExists()
        rule.onNodeWithText(formatRu(12_845)).assertExists()
        rule.onNodeWithText(formatRu(4_120)).assertExists()
        rule.onNodeWithText("311").assertExists()
        rule.onNodeWithText("23 дня").assertExists()
        rule.onNodeWithText("рекорд 31 день").assertExists()
    }

    @Test
    fun себя_видно_как_себя() {
        screen { PlayerSheet(large = false, view = ready(me = true), today = today, animate = false, onRetry = {}, onBack = {}) }
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithText("Это вы · 1-е место в таблице").assertExists()
    }

    @Test
    fun прерванная_серия_видна_как_прерванная() {
        // записана живой, но с тех пор прошло десять дней
        val old = profile.copy(streakDay = today - 10)
        screen { PlayerSheet(large = false, view = ready(old), today = today, animate = false, onRetry = {}, onBack = {}) }
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithText("нет").assertExists()
        rule.onNodeWithText("рекорд 31 день").assertExists()
    }

    @Test
    fun без_профиля_видно_то_что_есть() {
        screen { PlayerSheet(large = false, view = ready(profile = null), today = today, animate = false, onRetry = {}, onBack = {}) }
        rule.mainClock.advanceTimeBy(1500)
        save("97_player_no_profile")
        rule.onNodeWithText(formatRu(48_115_902)).assertExists()
        rule.onNodeWithText("Поставлено фигур").assertDoesNotExist()
        rule.onNodeWithText("когда игрок сыграет в новой версии", substring = true).assertExists()
    }

    @Test
    fun загрузка_потом_ошибка_потом_данные() {
        var view by mutableStateOf<PlayerView>(PlayerView.Loading("u1", "wor1356", 2, false))
        var retries = 0
        screen { PlayerSheet(large = false, view = view, today = today, animate = true, onRetry = { retries++ }, onBack = {}) }
        rule.mainClock.advanceTimeBy(1200)
        save("98_player_loading")
        rule.onNodeWithText("Загружаем статистику…").assertExists()

        view = PlayerView.Failed("u1", "wor1356", 2, false, "нет сети", offline = true)
        rule.mainClock.advanceTimeBy(800)
        save("98_player_offline")
        rule.onNodeWithText("Нет соединения с интернетом").assertExists()
        rule.onNodeWithText("Обновить").performClick()
        assertEquals("«Обновить» повторяет загрузку", 1, retries)

        // данные пришли спустя долгое время после открытия окна — числа всё
        // равно набегают от нуля, а не выскакивают готовыми
        view = ready().copy(name = "wor1356", rank = 2)
        rule.mainClock.advanceTimeBy(350)
        rule.onNodeWithText(formatRu(48_115_902)).assertDoesNotExist()
        rule.mainClock.advanceTimeBy(4000)
        rule.onNodeWithText(formatRu(48_115_902)).assertExists()
    }

    @Test
    fun тап_по_строке_открывает_этого_игрока() {
        val rows = listOf(
            BoardRow("a", "FastdropX", 48_115_902, me = false),
            BoardRow("b", "wor1356", 40_000_000, me = false),
            BoardRow("c", "Кот Василий", 1_000, me = true),
        )
        var opened: Pair<BoardRow, Int>? = null
        screen {
            BoardSheet(
                large = false,
                view = BoardView.Ready(rows, myRank = 3, myValue = 1_000, stale = false),
                kind = BoardKind.TOTAL,
                nick = "Кот Василий",
                joining = false,
                issue = null,
                onKind = {},
                onJoin = {},
                onRetry = {},
                onBack = {},
                onPlayer = { r, rank -> opened = r to rank },
            )
        }
        rule.mainClock.advanceTimeBy(3000)
        rule.onNodeWithText("wor1356").performClick()
        rule.mainClock.advanceTimeBy(300)
        val got = checkNotNull(opened) { "тап по строке никого не открыл" }
        assertEquals("b", got.first.uid)
        assertEquals("место — по порядку в списке", 2, got.second)
    }
}

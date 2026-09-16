package com.serafim.tetris

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serafim.tetris.game.GameState
import com.serafim.tetris.game.TetrisGame
import com.serafim.tetris.ui.GameScreen
import com.serafim.tetris.ui.M3
import com.serafim.tetris.ui.TetrisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Окна поверх игры модальные: палец не проходит сквозь затемнение.
 * Раньше он проходил — из статистики можно было нажать «Играть» в меню
 * под ней, а в паузе — кнопки под стаканом.
 */
@RunWith(AndroidJUnit4::class)
class ModalTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var game: TetrisGame
    private lateinit var ctrl: Controller
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Номер кадра. Экран игры перерисовывается по нему (состояние партии —
     * обычные поля, не снимки Compose), в приложении его крутит игровой
     * цикл; здесь — каждый тап, иначе окно паузы так и не появилось бы.
     */
    private val frame = mutableIntStateOf(1)

    private fun show(prep: (TetrisGame) -> Unit = {}) {
        val fx = AndroidFx(context)
        game = TetrisGame(fx, Random(5))
        rule.setContent {
            val scope = rememberCoroutineScope()
            ctrl = remember { Controller(game, fx, Prefs(context), scope).also { prep(game) } }
            CompositionLocalProvider(LocalDensity provides Density(2.625f, 1f)) {
                TetrisTheme {
                    Box(Modifier.fillMaxSize().background(M3.Surface)) {
                        GameScreen(ctrl, frame.intValue, keyboardAttached = false)
                    }
                }
            }
        }
        rule.waitForIdle()
    }

    private fun save(name: String) {
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.filesDir, "shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tapAt(p: Offset) {
        rule.onRoot().performTouchInput { click(p) }
        rule.runOnIdle { frame.intValue++ }
        rule.waitForIdle()
    }

    /** Кнопка паузы — правая в нижнем ряду под стаканом. */
    private fun pauseButton(): Offset {
        val root = rule.onRoot().fetchSemanticsNode().size
        return Offset(root.width * 5f / 6f, root.height - 50f * 2.625f)
    }

    @Test
    fun тап_по_фону_статистики_закрывает_её_и_не_запускает_игру() {
        show()
        assertEquals(GameState.MENU, game.state)
        rule.runOnIdle { ctrl.openStats() }
        rule.waitForIdle()
        // ровно туда, где под статистикой лежит «Играть»
        val play = rule.onNodeWithText("Играть").fetchSemanticsNode().boundsInRoot.center
        tapAt(play)
        assertFalse("статистика закрылась", ctrl.showStats)
        assertEquals("а партия не началась", GameState.MENU, game.state)
        assertFalse(ctrl.closing)

        // теперь окна нет, и та же кнопка работает
        rule.onNodeWithText("Играть").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        assertNotEquals(GameState.MENU, game.state)
    }

    @Test
    fun тап_по_самому_окну_статистики_его_не_закрывает() {
        show()
        rule.runOnIdle { ctrl.openStats() }
        rule.waitForIdle()
        rule.onNodeWithText("Статистика").performClick()
        rule.onNodeWithText("Стёрто линий").performClick()
        assertTrue(ctrl.showStats)
    }

    @Test
    fun итоги_партии_не_реагируют_на_фон() {
        show { it.debugStartImmediate(); it.debugState(GameState.OVER) }
        val root = rule.onRoot().fetchSemanticsNode().size
        for (p in listOf(
            Offset(20f, 20f),
            Offset(root.width - 20f, root.height / 2f),
            Offset(root.width / 2f, root.height - 20f),
            pauseButton(),
        )) tapAt(p)
        assertEquals(GameState.OVER, game.state)
        assertFalse(ctrl.closing)
        // кнопки самого окна работают
        rule.onNodeWithText("Играть снова").performClick()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        assertNotEquals(GameState.OVER, game.state)
    }

    @Test
    fun пауза_не_пропускает_касания_к_кнопкам_под_ней() {
        show { it.debugStartImmediate() }
        assertEquals(GameState.PLAYING, game.state)
        save("73_playing")
        // без окна точка — действительно кнопка паузы
        tapAt(pauseButton())
        assertEquals(GameState.PAUSED, game.state)
        // с окном та же точка — затемнение, и игра не продолжается сама
        tapAt(pauseButton())
        assertEquals(GameState.PAUSED, game.state)
        rule.onNodeWithText("Продолжить").performClick()
        assertEquals(GameState.PLAYING, game.state)
        save("74_after_resume")
    }

    @Test
    fun сброс_статистики_только_после_подтверждения() {
        show()
        rule.runOnIdle {
            game.restoreProgress(48_000_000, com.serafim.tetris.game.Xp.rankOf(48_000_000), 6_718_082)
            game.stats.restore(1234, 56, 78, 9, 3_600_000)
            ctrl.openStats()
        }
        rule.waitForIdle()
        save("70_stats_reset_button")

        rule.onNodeWithText("Сбросить статистику").performClick()
        rule.onNodeWithText("Сбросить статистику?").assertExists()
        // окно называет, что именно пропадёт
        rule.onNodeWithText("6\u00A0718\u00A0082", substring = true).assertExists()
        save("71_reset_confirm")

        // «Отмена» — ничего не сброшено, статистика открыта
        rule.onNodeWithText("Отмена").performClick()
        assertFalse(ctrl.showResetConfirm)
        assertTrue(ctrl.showStats)
        assertEquals(1234L, game.stats.score)

        // тап мимо окна подтверждения — тоже отмена, не сброс и не выход
        rule.onNodeWithText("Сбросить статистику").performClick()
        tapAt(Offset(20f, 20f))
        assertFalse(ctrl.showResetConfirm)
        assertTrue(ctrl.showStats)
        assertEquals(1234L, game.stats.score)
        assertEquals(6_718_082L, game.best)

        // «Сбросить» — всё по нулям и на диске тоже
        rule.onNodeWithText("Сбросить статистику").performClick()
        rule.onNodeWithText("Сбросить").performClick()
        assertFalse(ctrl.showResetConfirm)
        assertTrue(ctrl.showStats)
        assertTrue(game.stats.isEmpty)
        val prefs = Prefs(context)
        assertEquals(0L, prefs.statScore)
        assertEquals(0L, prefs.statGames)
        assertEquals(0L, prefs.statTime)
        assertEquals("рекорд сброшен", 0L, game.best)
        assertEquals("уровень сброшен", 0L, game.xpTotal)
        assertEquals(0, game.xpRank)
        assertEquals(0L, prefs.best)
        assertEquals(0L, prefs.xpTotal)
        assertEquals(0, prefs.xpRank)
        rule.mainClock.advanceTimeBy(1500)
        rule.onNodeWithText("Сбросить статистику").assertIsNotEnabled()
        save("72_after_reset")
    }
}

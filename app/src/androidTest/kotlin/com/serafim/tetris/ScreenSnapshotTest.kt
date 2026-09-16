package com.serafim.tetris

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serafim.tetris.game.C
import com.serafim.tetris.game.Piece
import com.serafim.tetris.game.PieceType
import com.serafim.tetris.game.Stats
import com.serafim.tetris.game.TetrisGame
import com.serafim.tetris.game.formatRu
import com.serafim.tetris.ui.GameOverSheet
import com.serafim.tetris.ui.GameScreen
import com.serafim.tetris.ui.M3
import com.serafim.tetris.ui.StatsSheet
import com.serafim.tetris.ui.TetrisTheme
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Снимок всего экрана в момент, который вручную не поймать:
 * сразу после тетриса, когда виден чип с названием комбинации.
 */
@RunWith(AndroidJUnit4::class)
class ScreenSnapshotTest {

    @get:Rule
    val rule = createComposeRule()

    private fun save(name: String, bmp: Bitmap) {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "shots",
        ).apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun экран_после_тетриса() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fx = AndroidFx(context)
        fx.soundOn = false
        val game = TetrisGame(fx, Random(11))
        game.debugStartImmediate()
        game.debugClearGrid()
        for (y in 16 until C.ROWS) for (x in 0 until 9) {
            game.debugSet(x, y, PieceType.entries[(x + y) % PieceType.entries.size])
        }
        game.debugSet(3, 15, PieceType.S)
        game.debugPiece(Piece(PieceType.I, 1, 7, 12))
        game.hardDrop()                    // тетрис: чип и всплывающие очки
        game.update(16.0)

        rule.mainClock.autoAdvance = false
        rule.setContent {
            val scope = rememberCoroutineScope()
            val ctrl = Controller(game, fx, Prefs(context), scope)
            CompositionLocalProvider(LocalDensity provides Density(2.625f, 1f)) {
                TetrisTheme {
                    Box(Modifier.fillMaxSize().background(M3.Surface)) {
                        GameScreen(ctrl, 1, keyboardAttached = false)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(400)   // чип уже проявился
        val img = rule.onRoot().captureToImage().asAndroidBitmap()
        assertNotNull(img)
        save("08_screen_after_tetris", img)
    }

    /**
     * Лист поверх пустого фона, снятый в несколько моментов: числа набегают,
     * полоса уровня проходит ранги, и каждый кадр ложится своим файлом.
     */
    private fun sheet(
        name: String,
        times: List<Long> = listOf(6000L),
        content: @androidx.compose.runtime.Composable () -> Unit,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2.625f, 1f)) {
                TetrisTheme {
                    Box(Modifier.fillMaxSize().background(M3.Surface)) { content() }
                }
            }
        }
        var at = 0L
        for (ms in times) {
            rule.mainClock.advanceTimeBy(ms - at)
            at = ms
            val tag = if (times.size == 1) name else "${name}_${ms}"
            save(tag, rule.onRoot().captureToImage().asAndroidBitmap())
        }
    }

    @Test
    fun конец_игры_с_рекордом_игрока() = sheet("50_over_record", listOf(300L, 600L, 900L, 1400L, 6000L)) {
        GameOverSheet(
            closing = false, large = false,
            score = 6_718_082, lines = 2218, level = 20,
            timeMs = (1 * 3600 + 23 * 60 + 45) * 1000.0,
            // партия подняла игрока с середины 14-го ранга в 16-й
            xpFrom = 3_000_000, xpTo = 3_000_000 + 6_718_082,
            newRecord = true, record = "Новый рекорд!",
            onMenu = {}, onAgain = {},
        )
    }

    @Test
    fun конец_игры_с_огромными_числами() = sheet("51_over_huge") {
        GameOverSheet(
            closing = false, large = false,
            score = 123_456_789_012, lines = 999_999, level = 50,
            timeMs = 127 * 3600 * 1000.0,
            xpFrom = 900_000_000_000, xpTo = 900_000_000_000 + 123_456_789_012,
            newRecord = false, record = "Рекорд: " + formatRu(987_654_321_098),
            onMenu = {}, onAgain = {},
        )
    }

    @Test
    fun статистика_со_временем() = sheet("52_stats", listOf(300L, 450L, 700L, 1100L, 2000L, 3000L, 7000L)) {
        val s = Stats().apply { restore(48_115_902, 91_204, 22_180, 412, (57L * 3600 + 14 * 60) * 1000) }
        StatsSheet(
            closing = false, large = false, stats = s, xpTotal = 48_115_902,
            resets = 0, canReset = true, onReset = {}, onBack = {},
        )
    }

    @Test
    fun часы_в_игре_при_большом_счёте() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fx = AndroidFx(context)
        fx.soundOn = false
        val game = TetrisGame(fx, Random(11))
        game.debugStartImmediate()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val scope = rememberCoroutineScope()
            val ctrl = androidx.compose.runtime.remember {
                Controller(game, fx, Prefs(context), scope).also {
                    game.restoreProgress(0, 0, 12_345_678)
                    game.debugSetScore(11_987_654)
                    game.debugSetLines(4321)
                    game.debugSetLevel(1)
                    repeat(150) { game.update(16.0) }   // счётчик очков доезжает до цели
                    game.debugSetLevel(C.MAX_LEVEL)
                    game.debugSetPlayMs((2 * 3600 + 5 * 60 + 9) * 1000.0)
                }
            }
            CompositionLocalProvider(LocalDensity provides Density(2.625f, 1f)) {
                TetrisTheme {
                    Box(Modifier.fillMaxSize().background(M3.Surface)) {
                        GameScreen(ctrl, 1, keyboardAttached = false)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1500)
        save("53_game_clock", rule.onRoot().captureToImage().asAndroidBitmap())
    }
}

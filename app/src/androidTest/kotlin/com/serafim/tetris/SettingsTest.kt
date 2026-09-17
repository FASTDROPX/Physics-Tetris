package com.serafim.tetris

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serafim.tetris.game.C
import com.serafim.tetris.game.Piece
import com.serafim.tetris.game.PieceType
import com.serafim.tetris.game.TetrisGame
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import com.serafim.tetris.ui.AmoledPalette
import com.serafim.tetris.ui.BoardButton
import com.serafim.tetris.ui.SettingsButton
import com.serafim.tetris.ui.StatsButton
import com.serafim.tetris.ui.THEME_MS
import com.serafim.tetris.ui.DarkPalette
import com.serafim.tetris.ui.GameScreen
import com.serafim.tetris.ui.M3
import com.serafim.tetris.ui.SettingsSheet
import com.serafim.tetris.ui.TetrisTheme
import com.serafim.tetris.ui.ThemeKind
import com.serafim.tetris.ui.paletteOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Настройки и темы. Главное здесь — что выбор темы доходит до самой
 * отрисовки: цвета читаются не из MaterialTheme, а прямо из [M3], и
 * подмена палитры обязана перекрасить то, что уже нарисовано.
 */
@RunWith(AndroidJUnit4::class)
class SettingsTest {

    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun save(name: String, bmp: Bitmap) {
        val dir = File(context.filesDir, "shots").apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** Фон под окном: по нему и видно, какая тема сейчас стоит. */
    private fun Bitmap.corner(): Int = getPixel(6, 6)

    @Test
    fun тема_перекрашивает_уже_нарисованное() {
        var amoled by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme(if (amoled) AmoledPalette else DarkPalette) {
                    Box(Modifier.requiredSize(360.dp, 825.dp).background(M3.Surface))
                }
            }
        }
        rule.mainClock.advanceTimeBy(500)
        val dark = rule.onRoot().captureToImage().asAndroidBitmap().corner()

        amoled = true
        // переход длится THEME_MS — ждём его с запасом
        rule.mainClock.advanceTimeBy(THEME_MS + 500L)
        val black = rule.onRoot().captureToImage().asAndroidBitmap().corner()

        Log.i("SettingsTest", "тёмная=${Integer.toHexString(dark)} amoled=${Integer.toHexString(black)}")
        assertEquals("тёмная тема — цвет оригинала", 0xFF131619.toInt(), dark)
        assertEquals("AMOLED — чистый чёрный", 0xFF000000.toInt(), black)
        assertNotEquals("смена темы дошла до отрисовки", dark, black)
    }

    /**
     * Тема перетекает, а не щёлкает: красный канал фона кадр за кадром идёт
     * от тёмного к белому через промежуточные значения, ни разу не
     * откатываясь. Смена посреди перехода продолжает от того цвета, что на
     * экране, а без анимаций тема встаёт сразу.
     */
    @Test
    fun тема_перетекает_плавно() {
        val white = DarkPalette.copy(surface = androidx.compose.ui.graphics.Color.White)
        var light by mutableStateOf(false)
        var smooth by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme(if (light) white else DarkPalette, smooth = smooth) {
                    Box(Modifier.requiredSize(120.dp, 120.dp).background(M3.Surface))
                }
            }
        }
        fun red() = android.graphics.Color.red(rule.onRoot().captureToImage().asAndroidBitmap().corner())
        rule.mainClock.advanceTimeBy(300)
        assertEquals("при запуске тема стоит сразу", 0x13, red())

        light = true
        val reds = ArrayList<Int>()
        repeat(40) {
            rule.mainClock.advanceTimeBy(16)
            reds += red()
        }
        Log.i("SettingsTest", "переход: $reds")
        assertEquals("переход закончился на белом", 255, reds.last())
        for (i in 1 until reds.size) {
            assertTrue("цвет не откатывается назад: $reds", reds[i] >= reds[i - 1])
        }
        val middle = reds.filter { it in 40..230 }.distinct()
        assertTrue("по пути есть промежуточные цвета, а не щелчок: $reds", middle.size >= 4)
        // половина пути — не в первом кадре и не в последнем
        val half = reds.indexOfFirst { it >= 137 }
        assertTrue("середина перехода не в первом кадре: $reds", half >= 1)

        // обратно, но посреди перехода — снова к тёмному
        light = false
        rule.mainClock.advanceTimeBy(96)
        val before = red()
        light = true
        rule.mainClock.advanceTimeBy(16)
        val after = red()
        Log.i("SettingsTest", "разворот: $before → $after")
        assertTrue("смена посреди перехода не прыгает: $before → $after", before in 40..230 && kotlin.math.abs(after - before) < 60)

        // без анимаций тема встаёт сразу
        rule.mainClock.advanceTimeBy(THEME_MS + 300L)
        smooth = false
        rule.mainClock.advanceTimeBy(16)
        light = false
        rule.mainClock.advanceTimeBy(32)
        assertEquals("без анимаций — сразу", 0x13, red())
    }

    /**
     * Шестерёнка ростом с соседей по углу меню. Меряется настоящий рисунок:
     * в каждой кнопке — рамка из точек, закрашенных линией больше чем
     * наполовину. Прежняя шестерёнка была выше столбиков на треть.
     */
    @Test
    fun шестерёнка_ростом_с_соседей() {
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme {
                    androidx.compose.foundation.layout.Row(Modifier.background(M3.Surface)) {
                        Box(Modifier.testTag("cup")) { BoardButton {} }
                        Box(Modifier.testTag("gear")) { SettingsButton {} }
                        Box(Modifier.testTag("bars")) { StatsButton {} }
                    }
                }
            }
        }
        rule.waitForIdle()
        save("99_icons", rule.onRoot().captureToImage().asAndroidBitmap())
        fun ink(tag: String): android.graphics.Rect {
            val bmp = rule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val bg = android.graphics.Color.red(bmp.getPixel(1, 1))
            val fg = android.graphics.Color.red(M3.OnSurfaceVariant.toArgb())
            val r = android.graphics.Rect(bmp.width, bmp.height, -1, -1)
            for (y in 0 until bmp.height) for (x in 0 until bmp.width) {
                if (android.graphics.Color.red(bmp.getPixel(x, y)) > (bg + fg) / 2) r.union(x, y)
            }
            return r
        }
        val cup = ink("cup")
        val gear = ink("gear")
        val bars = ink("bars")
        Log.i("SettingsTest", "рамки: кубок $cup, шестерёнка $gear, столбики $bars")
        // деление сетки на этой плотности — 22 dp × 2 / 24 ≈ 1,8 точки
        assertTrue("шестерёнка не выше кубка больше чем на деление: $gear vs $cup", gear.height() <= cup.height() + 2)
        assertTrue("шестерёнка не ниже столбиков: $gear vs $bars", gear.height() >= bars.height())
        assertTrue("шестерёнка не шире кубка больше чем на деление: $gear vs $cup", gear.width() <= cup.width() + 2)
    }

    @Test
    fun окно_настроек_собрано_целиком() {
        var volume by mutableFloatStateOf(0.7f)
        var power by mutableFloatStateOf(0.4f)
        var theme by mutableStateOf(ThemeKind.DARK)
        var soundOn by mutableStateOf(true)
        var vibrationOn by mutableStateOf(true)
        var opened = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme {
                    Box(Modifier.requiredSize(360.dp, 825.dp).background(M3.Surface)) {
                        SettingsSheet(
                            closing = false,
                            large = false,
                            soundOn = soundOn,
                            volume = volume,
                            vibrationOn = vibrationOn,
                            power = power,
                            theme = theme,
                            onSound = { soundOn = !soundOn },
                            onVibration = { vibrationOn = !vibrationOn },
                            onVolume = { v, _ -> volume = v },
                            onPower = { v, _ -> power = v },
                            onTheme = { theme = it },
                            onGithub = { opened++ },
                            onBack = {},
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(2000)
        save("87_settings", rule.onRoot().captureToImage().asAndroidBitmap())

        rule.onNodeWithText("Настройки").assertExists()
        rule.onNodeWithText("Звук").assertExists()
        rule.onNodeWithText("70 %").assertExists()
        rule.onNodeWithText("Вибрация").assertExists()
        rule.onNodeWithText("40 %").assertExists()
        // надписей «выключен в меню» больше нет — их место заняли переключатели
        rule.onAllNodesWithText("выключен", substring = true).assertCountEquals(0)

        // переключатели звука и вибрации — в самом окне, и они работают
        val switches = rule.onAllNodes(isToggleable())
        switches.assertCountEquals(2)
        switches[0].assertIsOn()
        switches[0].performClick()
        rule.mainClock.advanceTimeBy(300)
        assertEquals("звук выключился", false, soundOn)
        switches[1].performClick()
        rule.mainClock.advanceTimeBy(300)
        assertEquals("вибрация выключилась", false, vibrationOn)
        save("87_settings_switched_off", rule.onRoot().captureToImage().asAndroidBitmap())
        switches[0].performClick()
        switches[1].performClick()
        rule.mainClock.advanceTimeBy(300)
        for (kind in ThemeKind.entries) rule.onNodeWithText(kind.title).assertExists()
        rule.onNodeWithText("GitHub").assertExists()

        // выбор темы уходит наверх, а ссылка ведёт наружу
        rule.onNodeWithText(ThemeKind.AMOLED.title).performClick()
        rule.mainClock.advanceTimeBy(300)
        assertEquals("тема выбрана", ThemeKind.AMOLED, theme)

        rule.onNodeWithText("GitHub").performClick()
        rule.mainClock.advanceTimeBy(300)
        assertEquals("ссылка нажимается", 1, opened)
        save("88_settings_amoled", rule.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test
    fun выключенный_звук_гасит_ползунок() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme {
                    Box(Modifier.requiredSize(360.dp, 825.dp).background(M3.Surface)) {
                        SettingsSheet(
                            closing = false,
                            large = false,
                            soundOn = false,
                            volume = 1f,
                            vibrationOn = false,
                            power = 1f,
                            theme = ThemeKind.DARK,
                            onSound = {},
                            onVibration = {},
                            onVolume = { _, _ -> },
                            onPower = { _, _ -> },
                            onTheme = {},
                            onGithub = {},
                            onBack = {},
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(2000)
        save("89_settings_off", rule.onRoot().captureToImage().asAndroidBitmap())
        // оба переключателя выключены, оба ползунка погашены, а проценты
        // остаются на месте: включите обратно — громкость будет прежней
        rule.onAllNodes(isToggleable()).apply {
            assertCountEquals(2)
            get(0).assertIsOff()
            get(1).assertIsOff()
        }
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).apply {
            assertCountEquals(2)
            get(0).assertIsNotEnabled()
            get(1).assertIsNotEnabled()
        }
        rule.onAllNodesWithText("100 %").assertCountEquals(2)
    }

    @Test
    fun сила_отдачи_доходит_до_мотора() {
        // размах под ползунком: полная сила — полный размах, ноль решается раньше
        assertEquals(255, AndroidFx.amplitude(1f))
        assertEquals(128, AndroidFx.amplitude(0.5f))
        assertTrue("нулевой размах мотору не отдаём", AndroidFx.amplitude(0f) >= 1)

        // мотор без размаха: толчки короче, паузы те же, ниже порога не падаем
        val weak = AndroidFx.shorten(longArrayOf(100, 50, 80), 0.5f)
        assertEquals(50L, weak[0])
        assertEquals("пауза не трогается", 50L, weak[1])
        assertEquals(40L, weak[2])
        val tiny = AndroidFx.shorten(longArrayOf(40), 0.1f)
        assertEquals("короче порога толчок не делаем", AndroidFx.MIN_BUZZ, tiny[0])
    }

    /**
     * Меню в каждой из трёх тем. Тема переключается на живом экране, без
     * пересоздания, — ровно то, что делает игрок, нажимая строку в настройках.
     */
    @Test
    fun меню_во_всех_темах() {
        val fx = AndroidFx(context).apply { soundOn = false }
        themes("90_menu", TetrisGame(fx, Random(11)), fx)
    }

    /** Стакан посреди партии в каждой из трёх тем. */
    @Test
    fun игра_во_всех_темах() {
        val fx = AndroidFx(context).apply { soundOn = false }
        val game = TetrisGame(fx, Random(11))
        game.debugStartImmediate()
        game.debugClearGrid()
        for (y in 15 until C.ROWS) for (x in 0 until C.COLS) {
            if ((x * 7 + y * 3) % 10 != 0) {
                game.debugSet(x, y, PieceType.entries[(x + y) % PieceType.entries.size])
            }
        }
        game.debugPiece(Piece(PieceType.T, 0, 4, 6))
        game.update(16.0)
        themes("91_game", game, fx)
    }

    /**
     * Один экран, три темы подряд. Фон у тёмной и AMOLED обязан различаться:
     * совпади он — значит, тема до экрана не дошла.
     */
    private fun themes(tag: String, game: TetrisGame, fx: AndroidFx) {
        var kind by mutableStateOf(ThemeKind.DARK)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val scope = rememberCoroutineScope()
            val ctrl = remember { Controller(game, fx, Prefs(context), scope) }
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme(paletteOf(kind, context)) {
                    Box(Modifier.requiredSize(360.dp, 825.dp).background(M3.Surface)) {
                        GameScreen(ctrl, 1, keyboardAttached = false)
                    }
                }
            }
        }
        val backs = HashMap<ThemeKind, Int>()
        for (k in ThemeKind.entries) {
            kind = k
            rule.mainClock.advanceTimeBy(2500)
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            save("${tag}_${k.name.lowercase()}", bmp)
            backs[k] = bmp.getPixel(2, bmp.height - 3)
        }
        Log.i("SettingsTest", "$tag: " + backs.entries.joinToString { "${it.key}=${Integer.toHexString(it.value)}" })
        assertNotEquals("$tag: AMOLED отличается от тёмной", backs[ThemeKind.DARK], backs[ThemeKind.AMOLED])
    }
}

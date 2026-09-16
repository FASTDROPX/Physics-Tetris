package com.serafim.tetris

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serafim.tetris.game.SaveHead
import com.serafim.tetris.online.Standing
import com.serafim.tetris.ui.M3
import com.serafim.tetris.ui.MenuSheet
import com.serafim.tetris.ui.TIPS
import com.serafim.tetris.ui.TetrisTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Главное меню на телефоне обязано влезать целиком, без прокрутки.
 * Проверяется на экране Redmi 10C — 720×1650 при 320 dpi, то есть
 * 360×825 dp, игра во весь экран — с самой длинной подсказкой и без
 * справки по клавиатуре, которой на телефоне нет.
 */
@RunWith(AndroidJUnit4::class)
class MenuFitTest {

    @get:Rule
    val rule = createComposeRule()

    private fun save(name: String, bmp: Bitmap) {
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "shots",
        ).apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Сколько пикселей меню можно прокрутить: ноль — значит влезло. */
    private fun scrollRange(): Float {
        val node = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNode()
        return node.config[SemanticsProperties.VerticalScrollAxisRange].maxValue()
    }

    @Test
    fun меню_влезает_на_redmi_10c() {
        val longest = TIPS.indices.maxBy { TIPS[it].length }
        var fontScale by mutableStateOf(1f)
        var heightDp by mutableStateOf(825)
        var keyboard by mutableStateOf(false)
        var saved by mutableStateOf<SaveHead?>(null)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, fontScale)) {
                TetrisTheme {
                    Box(Modifier.requiredSize(360.dp, heightDp.dp).background(M3.Surface)) {
                        MenuSheet(
                            closing = false, large = false,
                            tipIndex = longest, titleSeed = 1,
                            // самый длинный из возможных вариантов карточки места
                            standing = Standing.Placed(12, "Кот Василий", 82_302, leader = false),
                            resume = saved,
                            soundOn = true, vibrationOn = true, physicsOn = false,
                            showKeyboardHelp = keyboard,
                            onStats = {}, onBoard = {}, onSound = {}, onVibration = {},
                            onPhysics = {}, onPlay = {}, onResume = {}, onRestart = {},
                        )
                    }
                }
            }
        }
        val results = LinkedHashMap<String, Float>()
        for ((fs, h) in listOf(1f to 825, 1.15f to 825, 1.3f to 825, 1f to 780)) {
            fontScale = fs
            heightDp = h
            rule.mainClock.advanceTimeBy(2000)
            val over = scrollRange() / 2f          // в dp
            results["x$fs@$h"] = over
            // карточка стоит полосой: низ «Играть» — на 8 % от низа экрана
            // минус нижний отступ карточки (18 dp)
            if (over == 0f) {
                val bottom = rule.onNodeWithText("Играть").fetchSemanticsNode().boundsInRoot.bottom / 2f
                assertEquals("низ меню при x$fs@$h", h * 0.92f - 18f, bottom, 3f)
            }
            save("60_menu_fs${fs}_h$h", rule.onRoot().captureToImage().asAndroidBitmap())
        }
        // с незаконченной партией внизу не одна кнопка, а две — меню от
        // этого выше не становится, но провериться обязано
        saved = SaveHead(score = 1_284_915, lines = 214, level = 12, playMs = 942_000.0, physics = false)
        fontScale = 1.15f
        heightDp = 825
        rule.mainClock.advanceTimeBy(2000)
        results["resume@x1.15"] = scrollRange() / 2f
        save("62_menu_resume", rule.onRoot().captureToImage().asAndroidBitmap())
        saved = null
        // контроль: со справкой по клавиатуре меню длиннее экрана, и замер
        // обязан это увидеть — иначе нули выше ничего не доказывают
        keyboard = true
        fontScale = 1f
        heightDp = 780
        rule.mainClock.advanceTimeBy(2000)
        results["keyboard@780"] = scrollRange() / 2f
        Log.i("MenuFit", results.toString())
        println("MenuFit $results")
        assertEquals("обычный шрифт", 0f, results["x1.0@825"]!!, 0.5f)
        assertEquals("крупный шрифт MIUI", 0f, results["x1.15@825"]!!, 0.5f)
        assertEquals("с незаконченной партией", 0f, results["resume@x1.15"]!!, 0.5f)
        org.junit.Assert.assertTrue("замер прокрутки живой", results["keyboard@780"]!! > 10f)
    }

    /**
     * Меню как на телефоне целиком: фон с летящими размытыми фигурами,
     * экран игры в меню и само меню полосой — снимок для глаза.
     */
    @Test
    fun меню_на_фоне_летящих_фигур() {
        val fx = AndroidFx(InstrumentationRegistry.getInstrumentation().targetContext)
        val game = com.serafim.tetris.game.TetrisGame(fx, kotlin.random.Random(3))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            val ctrl = androidx.compose.runtime.remember {
                Controller(game, fx, Prefs(InstrumentationRegistry.getInstrumentation().targetContext), scope)
            }
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme {
                    Box(Modifier.requiredSize(360.dp, 825.dp).background(M3.Surface)) {
                        com.serafim.tetris.ui.FallingBackground(reduceMotion = false)
                        com.serafim.tetris.ui.GameScreen(ctrl, 1, keyboardAttached = false)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(2500)
        save("61_menu_over_background", rule.onRoot().captureToImage().asAndroidBitmap())
    }
}

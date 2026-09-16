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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serafim.tetris.ui.BOOT_IN_MS
import com.serafim.tetris.ui.BootScreen
import com.serafim.tetris.ui.TetrisTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Заставка загрузки. Проверяется вся её лента: пустой экран в начале,
 * порядок сборки значка, два прохода бегунка и уход задом наперёд, после
 * которого серый фон растворяется и открывает то, что стоит за ним.
 *
 * Позиции точек считаны из разметки: столбец по центру экрана 360×825 dp,
 * значок 56 dp, плитка 9 dp с шагом 11 dp.
 */
@RunWith(AndroidJUnit4::class)
class BootTest {

    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Плотность 2: все размеры в dp удваиваются. */
    private val badgeX = 304
    private val badgeY = 702
    // центры плиток: 23 dp и 34 dp от угла значка, плотность 2
    private val TL = badgeX + 46 to badgeY + 46
    private val TR = badgeX + 68 to badgeY + 46
    private val BL = badgeX + 46 to badgeY + 68
    private val BR = badgeX + 68 to badgeY + 68

    /** Полоса загрузки — под значком и названием. */
    private val trackY = badgeY + 112 + 36 + 52 + 36 + 4

    private fun Bitmap.lum(p: Pair<Int, Int>): Int {
        val c = getPixel(p.first, p.second)
        return ((c shr 16 and 0xFF) * 30 + (c shr 8 and 0xFF) * 59 + (c and 0xFF) * 11) / 100
    }

    /** Где сейчас бегунок: середина светлой полосы, или -1, если её нет. */
    private fun Bitmap.runner(): Int {
        var from = -1
        var to = -1
        for (x in 0 until width) {
            val c = getPixel(x, trackY.coerceIn(0, height - 1))
            val light = (c shr 16 and 0xFF) > 130 && (c shr 8 and 0xFF) > 170 && (c and 0xFF) > 230
            if (light) {
                if (from < 0) from = x
                to = x
            }
        }
        return if (from < 0) -1 else (from + to) / 2
    }

    @Test
    fun заставка_собирается_и_уходит() {
        var booted by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(2f, 1f)) {
                TetrisTheme {
                    // за заставкой — заметный цвет: по нему видно, что серый
                    // фон в конце действительно ушёл в прозрачность
                    Box(Modifier.requiredSize(360.dp, 825.dp).background(Color.Magenta)) {
                        BootScreen(visible = !booted)
                    }
                }
            }
        }

        val dir = File(context.filesDir, "shots").apply { mkdirs() }
        val runners = ArrayList<Pair<Long, Int>>()
        var at = 0L
        var first: Bitmap? = null
        var atTiles: Bitmap? = null
        var atAll: Bitmap? = null
        var last: Bitmap? = null

        // время берём у самих часов Compose: снимок экрана их тоже двигает,
        // и свой счётчик разошёлся бы с анимацией на сотни миллисекунд
        val base = rule.mainClock.currentTime
        var shot = 0
        while (true) {
            val ms = rule.mainClock.currentTime - base
            if (!booted && ms >= BOOT_IN_MS) booted = true
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            FileOutputStream(File(dir, "90_boot_%04d.png".format(ms))).use {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            if (first == null) first = bmp
            if (atTiles == null && ms >= 500L) atTiles = bmp
            if (atAll == null && ms >= 1200L) atAll = bmp
            if (ms <= BOOT_IN_MS) runners += ms to bmp.runner()
            last = bmp
            at = ms
            shot++
            if (ms > 3400L || shot > 120) break
            rule.mainClock.advanceTimeBy(40)
        }
        Log.i("BootTest", "кадров $shot, последний на $at мс")

        // 1. в самом начале на экране только фон
        val start = checkNotNull(first)
        assertEquals("в начале значка нет", start.getPixel(badgeX + 4, badgeY + 4), start.getPixel(20, 20))

        // 2. плитки собираются по очереди: левая нижняя раньше правой нижней
        val early = checkNotNull(atTiles)
        Log.i("BootTest", "на 500 мс: ЛН=${early.lum(BL)} ЛВ=${early.lum(TL)} ПВ=${early.lum(TR)} ПН=${early.lum(BR)}")
        assertTrue("левая нижняя приходит первой", early.lum(BL) > early.lum(TR) + 20)
        assertTrue("правой нижней ещё нет", early.lum(BL) > early.lum(BR) + 40)

        // 3. к концу входа значок собран целиком; правая нижняя плитка и в
        //    готовом значке вполсилы, поэтому спрос с неё отдельный
        val whole = checkNotNull(atAll)
        Log.i("BootTest", "на 1200 мс: ЛН=${whole.lum(BL)} ЛВ=${whole.lum(TL)} ПВ=${whole.lum(TR)} ПН=${whole.lum(BR)}")
        assertTrue("три плитки на месте", listOf(TL, TR, BL).all { whole.lum(it) > 150 })
        assertTrue("правая нижняя тоже пришла", whole.lum(BR) > 110)

        // 4. бегунок проходит полосу дважды: дважды уезжает вправо и
        //    возвращается к левому краю
        val seen = runners.filter { it.second >= 0 }
        var laps = 1
        for (i in 1 until seen.size) if (seen[i].second < seen[i - 1].second - 100) laps++
        Log.i("BootTest", "бегунок: " + seen.joinToString(" ") { "${it.first}:${it.second}" })
        Log.i("BootTest", "заходов $laps")
        assertEquals("бегунок проходит полосу дважды", 2, laps)
        assertTrue("бегунок доезжал до правого края", seen.any { it.second > 500 })
        assertTrue("первый заход начинается сразу за полосой", seen.first().first < 700L)
        assertTrue("второй заход кончается под самый уход", seen.last().first > BOOT_IN_MS - 500L)

        // 5. в конце заставки нет, за ней видно то, что стояло позади
        val end = checkNotNull(last)
        assertEquals("фон растворился", Color.Magenta.toArgb(), end.getPixel(20, 20))
        assertEquals("значок исчез", end.getPixel(badgeX + 30, badgeY + 30), end.getPixel(20, 20))
    }
}

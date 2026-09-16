package com.serafim.tetris.game

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Накопительный уровень игрока, кривая гравитации и служебные функции. */
class XpAndMathTest {

    private fun game(): TetrisGame {
        val g = TetrisGame()
        g.debugStartImmediate()
        g.debugClearGrid()
        return g
    }

    @Test
    fun `границы уровней 0-100-500 дальше вдвое`() {
        assertEquals(48, Xp.BOUNDS.size)
        assertEquals(0L, Xp.BOUNDS[0])
        assertEquals(100L, Xp.BOUNDS[1])
        assertEquals(500L, Xp.BOUNDS[2])
        assertEquals(1000L, Xp.BOUNDS[3])
        assertEquals(2000L, Xp.BOUNDS[4])
        assertEquals(4000L, Xp.BOUNDS[5])
        for (i in 4 until 48) assertEquals(Xp.BOUNDS[i - 1] * 2, Xp.BOUNDS[i], "граница $i")
    }

    @Test
    fun `опыт растёт вместе со счётом`() {
        val g = game()
        g.debugSetLevel(1)
        g.debugApplyScore(4, null, false)   // 800 очков
        g.update(16.0)
        assertEquals(800L, g.xpTotal)
        assertEquals(2, g.xpRank, "800 лежит между 500 и 1000")
        val p = g.xpProgress()
        assertEquals(500L, p.lo)
        assertEquals(1000L, p.hi)
        assertTrue(abs(p.p - 0.6f) < 1e-4)
    }

    @Test
    fun `за один кадр можно перескочить несколько рангов`() {
        val ups = intArrayOf(0)
        val g = TetrisGame(fx = object : Fx by Fx.None {
            override fun xpRankUp() { ups[0]++ }
        })
        g.debugStartImmediate()
        g.debugClearGrid()
        g.debugSetLevel(20)
        g.debugApplyScore(4, null, false)   // 800 * 20 = 16000
        g.update(16.0)
        assertEquals(16000L, g.xpTotal)
        assertEquals(7, g.xpRank, "16000 = граница ранга 7")
        assertEquals(7, ups[0])
    }

    @Test
    fun `опыт не начисляется дважды за одни и те же очки`() {
        val g = game()
        g.debugSetLevel(1)
        g.debugApplyScore(1, null, false)
        repeat(10) { g.update(16.0) }
        assertEquals(100L, g.xpTotal)
    }

    @Test
    fun `полоса опыта не выходит за края`() {
        val g = game()
        val p = g.xpProgress()
        assertEquals(0f, p.p)
        assertEquals(0L, p.lo)
        assertEquals(100L, p.hi)
    }

    @Test
    fun `кривая гравитации совпадает с оригиналом`() {
        assertEquals(1000.0, gravityMs(1), 1e-9)
        assertEquals(860.0, gravityMs(2), 1e-9)
        assertEquals(1000.0 * 0.86 * 0.86, gravityMs(3), 1e-9)
        assertTrue(abs(gravityMs(20) - 56.92) < 0.05, "двадцатый уровень ≈ 57 мс")
        // до двадцатого пол в 45 мс так ни разу и не срабатывает
        for (lv in 1..C.BASE_MAX_LEVEL) assertTrue(gravityMs(lv) >= 45.0)
    }

    @Test
    fun `выше двадцатого кривая продолжается тем же законом`() {
        for (lv in 2..C.MAX_LEVEL) {
            assertTrue(gravityMs(lv) < gravityMs(lv - 1), "уровень $lv обязан быть быстрее ${lv - 1}")
        }
        assertEquals(gravityMs(20) * 0.86, gravityMs(21), 1e-9)
        // на пятидесятом фигура проходит весь стакан быстрее одного кадра
        assertTrue(gravityMs(C.MAX_LEVEL) * C.ROWS < 1000.0 / 60.0)
        // выше потолка не разгоняется
        assertEquals(gravityMs(C.MAX_LEVEL), gravityMs(99), 1e-12)
    }

    @Test
    fun `потолок скорости растёт с миллиона по сотне тысяч`() {
        assertEquals(20, levelCap(0))
        assertEquals(20, levelCap(999_999))
        assertEquals(20, levelCap(1_000_000))
        assertEquals(20, levelCap(1_099_999))
        assertEquals(21, levelCap(1_100_000))
        assertEquals(30, levelCap(2_000_000))
        assertEquals(49, levelCap(3_999_999))
        assertEquals(50, levelCap(4_000_000))
        assertEquals(50, levelCap(6_718_082))
        assertEquals(50, levelCap(Long.MAX_VALUE))
    }

    @Test
    fun `часы партии и общее время`() {
        assertEquals("0:00", formatClock(0.0))
        assertEquals("0:42", formatClock(42_999.0))
        assertEquals("12:05", formatClock((12 * 60 + 5) * 1000.0))
        assertEquals("1:23:45", formatClock(((1 * 60 + 23) * 60 + 45) * 1000.0))
        assertEquals("27:00:00", formatClock(27 * 3600 * 1000.0))

        assertEquals("42 с", formatTotal(42_000.0))
        assertEquals("17 мин", formatTotal(17 * 60 * 1000.0 + 30_000.0))
        assertEquals("3 ч 5 мин", formatTotal((3 * 3600 + 5 * 60) * 1000.0))
    }

    @Test
    fun `кривая expo out совпадает с оригиналом`() {
        assertEquals(0.0, expoOut(0.0), 1e-12)
        assertEquals(1.0, expoOut(1.0), 1e-12)
        assertEquals(1.0, expoOut(1.5), 1e-12)
        assertEquals(1.0 - 1.0 / 32.0, expoOut(0.5), 1e-12)
        // монотонность
        var prev = -1.0
        var t = 0.0
        while (t <= 1.0) {
            val v = expoOut(t)
            assertTrue(v >= prev, "expoOut падает на $t")
            prev = v
            t += 0.01
        }
    }

    @Test
    fun `разряды числа разделяются неразрывным пробелом`() {
        assertEquals("0", formatRu(0))
        assertEquals("999", formatRu(999))
        assertEquals("1 000", formatRu(1000))
        assertEquals("12 345", formatRu(12345))
        assertEquals("1 234 567", formatRu(1234567))
        assertEquals("-1 500", formatRu(-1500))
    }

    @Test
    fun `порядок проявления первой фигуры задан для всех типов`() {
        val g = TetrisGame()
        for (t in PieceType.ALL) {
            val sp = Shapes.SPAWN.getValue(t)
            val cells = g.cellsOf(Piece(t, 0, sp.x, sp.y))
            val sorted = cells.sortedWith(RevealOrder.comparator(t))
            assertEquals(4, sorted.size, t.name)
            assertEquals(cells.toSet(), sorted.toSet(), t.name)
        }
    }

    @Test
    fun `смещение ряда при обвале идёт по кривой expo out`() {
        val g = game()
        g.debugFillRow(19, exceptX = 0)
        g.debugSet(3, 17, PieceType.I)
        g.debugPiece(Piece(PieceType.I, 1, -2, 16))
        g.hardDrop()
        g.update(100.0); g.update(100.0)
        assertEquals(GameState.FALLING, g.state)
        // в начале обвала ряд ещё наверху, к концу — на месте
        g.update(100.0)
        val early = g.rowOffset(19)
        assertTrue(early < 19.0 && early > 18.0, "ряд едет вниз, сейчас $early")
        repeat(4) { g.update(100.0) }
        assertEquals(19.0, g.rowOffset(19), 1e-9, "обвал закончился")
    }

    @Test
    fun `шкала рангов для анимации`() {
        assertEquals(0.0, Xp.position(0))
        assertEquals(0.5, Xp.position(50), 1e-12)
        assertEquals(1.0, Xp.position(100), 1e-12)
        assertEquals(1.5, Xp.position(300), 1e-12)
        assertEquals(2.0, Xp.position(500), 1e-12)
        for (k in 0..Xp.TOP_RANK) assertEquals(k.toDouble(), Xp.position(Xp.BOUNDS[k]), 1e-12)
        var prev = -1.0
        for (t in listOf(0L, 1, 99, 100, 101, 499, 500, 999, 1000, 6_718_082, 48_115_902)) {
            val p = Xp.position(t)
            assertTrue(p > prev, "шкала обязана расти: $t")
            prev = p
        }
        assertEquals(Xp.TOP_RANK + 1.0, Xp.position(Long.MAX_VALUE), 1e-12)
        assertEquals(0.0, Xp.position(-5))
    }

    @Test
    fun `ранг по опыту совпадает с тем, что насчитала бы партия`() {
        val g = TetrisGame()
        g.debugStartImmediate()
        g.debugSetLevel(20)
        repeat(40) {
            g.debugApplyScore(4, null, false)
            g.update(16.0)
            assertEquals(Xp.rankOf(g.xpTotal), g.xpRank, "опыт ${g.xpTotal}")
        }
        assertTrue(g.xpRank >= 10)
    }
}

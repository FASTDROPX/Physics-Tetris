package com.serafim.tetris.game

import com.serafim.tetris.physics.PhysicsField
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Снимок незаконченной партии. Проверяется главное: партия возвращается
 * ровно той же — поле, счёт, часы, карман, очередь и мешок, — и следующие
 * фигуры идут в том же порядке, что пошли бы без перерыва.
 */
class SaveTest {

    /** Партия с несколькими сброшенными фигурами и одним взятым карманом. */
    private fun played(physics: Boolean = false, drops: Int = 9): TetrisGame {
        val g = TetrisGame(Fx.None, Random(7))
        g.physicsMode = physics
        g.debugStartImmediate()
        repeat(drops) { i ->
            if (i == 2) g.holdPiece()
            repeat(i % 3) { g.move(-1) }
            if (i % 2 == 0) g.rotate(1)
            g.hardDrop()
            repeat(40) { g.update(16.0) }       // фиксация, разбор линий, новая фигура
        }
        return g
    }

    private fun gridText(g: TetrisGame): String = buildString {
        for (y in 0 until C.ROWS) {
            for (x in 0 until C.COLS) append(g.at(x, y)?.name?.get(0) ?: '.')
            append('\n')
        }
    }

    @Test
    fun `партия возвращается той же`() {
        val a = played()
        val text = a.snapshot()

        val b = TetrisGame(Fx.None, Random(99))
        assertTrue(b.restore(text), "снимок не принят")

        assertEquals(GameState.PAUSED, b.state, "партия возвращается на паузе")
        assertEquals(gridText(a), gridText(b), "поле")
        assertEquals(a.score, b.score)
        assertEquals(a.lines, b.lines)
        assertEquals(a.level, b.level)
        assertEquals(a.combo, b.combo)
        assertEquals(a.backToBack, b.backToBack)
        assertEquals(a.playMs.toLong(), b.playMs.toLong(), "часы партии")
        assertEquals(a.hold, b.hold)
        assertEquals(a.canHold, b.canHold)
        assertEquals(a.piece, b.piece)
        assertEquals(a.queue.toList(), b.queue.toList(), "очередь")
        assertEquals(a.xpAtStart, b.xpAtStart)
        assertEquals(a.bestAtStart, b.bestAtStart)
    }

    @Test
    fun `фигуры идут дальше в том же порядке`() {
        val a = played()
        val b = TetrisGame(Fx.None, Random(99))
        b.restore(a.snapshot())
        b.togglePause()
        // мешок сохранён целиком, поэтому ближайшие фигуры совпадают до одной
        assertEquals(comeNext(a, 8), comeNext(b, 8), "порядок фигур после возврата")
    }

    /** Какие фигуры выходят в стакан следующими. */
    private fun comeNext(g: TetrisGame, n: Int): List<PieceType> {
        val out = ArrayList<PieceType>()
        repeat(n) {
            g.piece?.let { out += it.type }
            g.hardDrop()
            repeat(40) { g.update(16.0) }
        }
        return out
    }

    @Test
    fun `счёт и опыт не начисляются заново`() {
        val a = played()
        val b = TetrisGame(Fx.None, Random(99))
        b.restoreProgress(a.xpTotal, a.xpRank, a.best)
        b.restore(a.snapshot())
        val xpBefore = b.xpTotal
        b.togglePause()
        repeat(30) { b.update(16.0) }
        assertEquals(xpBefore, b.xpTotal, "опыт за уже набранные очки второй раз не идёт")
        assertEquals(0L, b.stats.games, "партия второй раз не считается")
    }

    @Test
    fun `заголовок читается без разбора всей партии`() {
        val a = played()
        val head = assertNotNull(peekSave(a.snapshot()))
        assertEquals(a.score, head.score)
        assertEquals(a.lines, head.lines)
        assertEquals(a.level, head.level)
        assertEquals(a.playMs.toLong(), head.playMs.toLong())
        assertTrue(!head.physics)
    }

    @Test
    fun `чужой и рваный снимок не принимается`() {
        val g = TetrisGame(Fx.None, Random(1))
        g.startGame()
        val before = gridText(g)
        assertTrue(!g.restore(""), "пустая строка")
        assertTrue(!g.restore("что-то своё\n1 2 3"), "чужая метка")
        assertTrue(!g.restore(SAVE_TAG + "\n1 2 3\n"), "обрезанный снимок")
        assertNull(peekSave("tetris 0\nx"))
        assertEquals(before, gridText(g), "испорченный снимок партию не трогает")
    }

    @Test
    fun `стакан в режиме физики возвращается лежащим так же`() {
        val a = played(physics = true, drops = 7)
        assertNotNull(a.physics, "партия шла с физикой")
        val before = a.physics!!.snapshot()
        assertTrue(before.isNotEmpty(), "в стакане есть вещество")

        val b = TetrisGame(Fx.None, Random(99))
        assertTrue(b.restore(a.snapshot()))
        val field = assertNotNull(b.physics, "физика вернулась")

        // то же вещество на тех же местах: многоугольники, поднятые из
        // снимка, обязаны лечь в те же клетки, что были у живой партии
        assertEquals(gridText(a), raster(field), "клетки стакана")
        assertEquals(before.size, field.snapshot().size, "число кусков")
    }

    private fun raster(f: PhysicsField): String {
        val grid: Array<Array<PieceType?>> = Array(C.ROWS) { arrayOfNulls<PieceType>(C.COLS) }
        f.rasterize(grid)
        return buildString {
            for (y in 0 until C.ROWS) {
                for (x in 0 until C.COLS) append(grid[y][x]?.name?.get(0) ?: '.')
                append('\n')
            }
        }
    }
}

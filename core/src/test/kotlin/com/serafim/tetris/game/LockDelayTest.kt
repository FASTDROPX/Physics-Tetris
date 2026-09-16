package com.serafim.tetris.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Задержка фиксации 500 мс и пятнадцать сбросов. */
class LockDelayTest {

    /** T лежит на полу в колонках 3..5. */
    private fun grounded(): TetrisGame {
        val g = TetrisGame()
        g.debugStartImmediate()
        g.debugClearGrid()
        g.debugPiece(Piece(PieceType.T, 0, 3, 18))
        g.update(16.0)
        return g
    }

    @Test
    fun `фигура на полу считается приземлившейся`() {
        val g = grounded()
        assertTrue(g.grounded)
        assertEquals(16.0, g.lockTimer)
    }

    @Test
    fun `фигура фиксируется ровно через полсекунды`() {
        val g = grounded()
        repeat(4) { g.update(100.0) }      // 16 + 400 = 416 мс
        assertNull(g.at(4, 18), "рано фиксировать")
        g.update(100.0)                    // 516 мс
        assertEquals(PieceType.T, g.at(4, 18))
        assertEquals(PieceType.T, g.at(3, 19))
        assertEquals(PieceType.T, g.at(5, 19))
    }

    @Test
    fun `сдвиг у пола обнуляет таймер фиксации`() {
        val g = grounded()
        repeat(4) { g.update(100.0) }
        assertEquals(416.0, g.lockTimer)
        g.move(1)
        assertEquals(0.0, g.lockTimer)
        assertEquals(1, g.lockResets)
    }

    @Test
    fun `сбросов не больше пятнадцати`() {
        val g = grounded()
        repeat(30) { g.move(if (it % 2 == 0) 1 else -1) }
        assertEquals(C.MAX_LOCK_RESETS, g.lockResets)
    }

    @Test
    fun `после пятнадцати сбросов фигура ложится несмотря на движение`() {
        val g = grounded()
        repeat(C.MAX_LOCK_RESETS + 2) { g.move(if (it % 2 == 0) 1 else -1) }
        assertEquals(0.0, g.lockTimer)
        // дальше движение таймер уже не трогает
        repeat(5) {
            g.update(100.0)
            g.move(if (it % 2 == 0) 1 else -1)
        }
        assertTrue(g.at(3, 19) != null || g.at(4, 19) != null, "фигура должна была зафиксироваться")
    }

    @Test
    fun `поворот тоже сбрасывает таймер`() {
        val g = grounded()
        repeat(3) { g.update(100.0) }
        g.rotate(1)
        assertEquals(0.0, g.lockTimer)
        assertEquals(1, g.lockResets)
    }

    @Test
    fun `новая фигура получает свежий счётчик сбросов`() {
        val g = grounded()
        repeat(5) { g.move(if (it % 2 == 0) 1 else -1) }
        assertEquals(5, g.lockResets)
        g.hardDrop()
        assertEquals(0, g.lockResets)
        assertFalse(g.grounded)
    }

    @Test
    fun `сдвиг с обрыва снимает признак приземления`() {
        val g = TetrisGame()
        g.debugStartImmediate()
        g.debugClearGrid()
        // подставка только под левой половиной
        for (x in 0 until 5) g.debugSet(x, 19, PieceType.L)
        g.debugPiece(Piece(PieceType.O, 0, 2, 17))
        g.update(16.0)
        assertTrue(g.grounded)
        g.move(1)
        g.move(1)
        g.move(1)   // квадрат уходит вправо, под ним пусто
        assertFalse(g.grounded)
    }
}

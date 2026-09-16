package com.serafim.tetris.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Автоповтор: задержка 150 мс, шаг 35 мс, ускоренное падение. */
class InputTimingTest {

    private fun game(): TetrisGame {
        val g = TetrisGame()
        g.debugStartImmediate()
        g.debugClearGrid()
        g.debugPiece(Piece(PieceType.T, 0, 6, 0))
        return g
    }

    private fun x(g: TetrisGame) = g.piece!!.x

    @Test
    fun `первый шаг происходит сразу при нажатии`() {
        val g = game()
        g.pressLeft()
        assertEquals(5, x(g))
    }

    @Test
    fun `до истечения задержки повторов нет`() {
        val g = game()
        g.pressLeft()
        g.update(140.0)
        assertEquals(5, x(g), "149 мс — ещё рано")
    }

    @Test
    fun `по истечении задержки идёт следующий шаг`() {
        val g = game()
        g.pressLeft()
        g.update(100.0)
        g.update(40.0)   // 140
        assertEquals(5, x(g))
        g.update(20.0)   // 160 — порог пройден
        assertEquals(4, x(g))
    }

    @Test
    fun `за длинный кадр набегает несколько шагов`() {
        val g = game()
        g.pressLeft()
        g.update(100.0)
        g.update(40.0)
        g.update(20.0)   // остаток 10 мс сверх порога, x = 4
        g.update(35.0)   // 10 + 35 = 45 мс → два шага
        assertEquals(2, x(g))
    }

    @Test
    fun `отпущенная клавиша останавливает повтор`() {
        val g = game()
        g.pressLeft()
        g.update(100.0)
        g.update(60.0)
        val before = x(g)
        g.keyLeft = false
        repeat(10) { g.update(100.0) }
        assertEquals(before, x(g))
    }

    @Test
    fun `повторное нажатие без отпускания не даёт лишнего шага`() {
        val g = game()
        g.pressLeft()
        g.pressLeft()
        g.pressLeft()
        assertEquals(5, x(g))
    }

    @Test
    fun `стена гасит автоповтор`() {
        val g = game()
        g.pressLeft()
        repeat(40) { g.update(50.0) }
        assertEquals(0, x(g))
        assertTrue(g.state == GameState.PLAYING)
    }

    @Test
    fun `две стороны считаются независимо`() {
        val g = game()
        g.pressRight()
        assertEquals(7, x(g))
        g.pressLeft()
        assertEquals(6, x(g))
        g.keyRight = false
        g.update(100.0)
        g.update(60.0)
        assertEquals(5, x(g), "должен работать только левый повтор")
    }

    @Test
    fun `ускоренное падение идёт быстрее гравитации и приносит очко за клетку`() {
        val g = game()
        g.debugSetLevel(1)
        g.pressDown()
        assertEquals(1, g.piece!!.y)
        assertEquals(1L, g.score)
        g.update(100.0)          // шаг 45 мс → ещё две клетки
        assertEquals(3, g.piece!!.y)
        assertEquals(3L, g.score)
    }

    @Test
    fun `на высоких уровнях шаг ускоренного падения идёт от гравитации`() {
        // интервал = min(45, гравитация / 2): на двадцатом уровне это 28 мс
        val fast = game()
        fast.debugSetLevel(C.BASE_MAX_LEVEL)
        fast.pressDown()
        fast.update(30.0)
        assertEquals(2, fast.piece!!.y, "28 мс на клетку")

        val slow = game()
        slow.debugSetLevel(1)
        slow.pressDown()
        slow.update(30.0)
        assertEquals(1, slow.piece!!.y, "на первом уровне порог 45 мс ещё не пройден")
    }
}

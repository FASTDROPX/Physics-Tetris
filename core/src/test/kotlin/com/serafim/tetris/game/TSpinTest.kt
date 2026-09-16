package com.serafim.tetris.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Правило трёх углов: полный T-спин, мини и отказ. */
class TSpinTest {

    private fun game(): TetrisGame {
        val g = TetrisGame()
        g.debugStartImmediate()
        g.debugClearGrid()
        return g
    }

    /** T носиком вниз в позиции (0,17): центр — клетка (1,18). */
    private fun TetrisGame.placeT() {
        debugPiece(Piece(PieceType.T, 2, 0, 17))
        debugRotationFlag(true)
    }

    @Test
    fun `оба передних угла заняты — полный спин`() {
        val g = game()
        g.placeT()
        g.debugSet(0, 17, PieceType.L)   // задний угол
        g.debugSet(0, 19, PieceType.L)   // передний
        g.debugSet(2, 19, PieceType.L)   // передний
        assertEquals(Spin.FULL, g.tSpinKind())
    }

    @Test
    fun `занят лишь один передний угол — мини`() {
        val g = game()
        g.placeT()
        g.debugSet(0, 17, PieceType.L)
        g.debugSet(2, 17, PieceType.L)
        g.debugSet(0, 19, PieceType.L)
        assertEquals(Spin.MINI, g.tSpinKind())
    }

    @Test
    fun `двух углов мало`() {
        val g = game()
        g.placeT()
        g.debugSet(0, 17, PieceType.L)
        g.debugSet(2, 17, PieceType.L)
        assertNull(g.tSpinKind())
    }

    @Test
    fun `последнее действие не поворот — спина нет`() {
        val g = game()
        g.placeT()
        g.debugRotationFlag(false)
        g.debugSet(0, 17, PieceType.L)
        g.debugSet(0, 19, PieceType.L)
        g.debugSet(2, 19, PieceType.L)
        assertNull(g.tSpinKind())
    }

    @Test
    fun `последнее смещение таблицы поднимает мини до полного`() {
        val g = game()
        g.placeT()
        g.debugSet(0, 17, PieceType.L)
        g.debugSet(2, 17, PieceType.L)
        g.debugSet(0, 19, PieceType.L)
        g.debugKickIndex(4)
        assertEquals(Spin.FULL, g.tSpinKind())
    }

    @Test
    fun `стены и пол считаются занятыми углами`() {
        val g = game()
        // T носиком вверх у самого пола: нижние углы за краем поля
        g.debugPiece(Piece(PieceType.T, 0, 0, 18))
        g.debugRotationFlag(true)
        g.debugSet(0, 18, PieceType.L)
        // два угла за полом + один настоящий = три
        assertEquals(Spin.MINI, g.tSpinKind())
    }

    @Test
    fun `пустое поле спина не даёт`() {
        val g = game()
        g.placeT()
        assertNull(g.tSpinKind())
    }

    @Test
    fun `не T-фигура никогда не спин`() {
        val g = game()
        g.debugPiece(Piece(PieceType.L, 2, 0, 17))
        g.debugRotationFlag(true)
        g.debugSet(0, 17, PieceType.L)
        g.debugSet(2, 17, PieceType.L)
        g.debugSet(0, 19, PieceType.L)
        g.debugSet(2, 19, PieceType.L)
        assertNull(g.tSpinKind())
    }

    @Test
    fun `настоящий T-спин в нишу засчитывается и очищает линию`() {
        val g = game()
        g.debugSetLevel(1)
        // ниша: ряд 19 полон кроме колонки 1, справа нависает блок
        g.debugFillRow(19, exceptX = 1)
        g.debugSet(2, 17, PieceType.L)
        // T стоит вертикально над нишей и доворачивается носиком вниз
        g.debugPiece(Piece(PieceType.T, 1, 0, 17))
        g.rotate(1)
        assertEquals(2, g.piece!!.rot)
        assertEquals(Spin.FULL, g.tSpinKind())
        g.debugLock()
        // T-спин с одной линией на первом уровне
        assertEquals(800L, g.score)
        assertEquals(1, g.lines)
    }
}

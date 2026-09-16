package com.serafim.tetris.game

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Таблицы отскока от стен и их применение при повороте. */
class KicksTest {

    private fun table(type: PieceType, from: Int, to: Int): List<List<Int>> =
        Kicks.forMove(type, from, to).map { it.toList() }

    @Test
    fun `общая таблица совпадает с оригиналом`() {
        assertEquals(
            listOf(listOf(0, 0), listOf(-1, 0), listOf(-1, -1), listOf(0, 2), listOf(-1, 2)),
            table(PieceType.T, 0, 1),
        )
        assertEquals(
            listOf(listOf(0, 0), listOf(1, 0), listOf(1, 1), listOf(0, -2), listOf(1, -2)),
            table(PieceType.T, 1, 0),
        )
        assertEquals(
            listOf(listOf(0, 0), listOf(1, 0), listOf(1, -1), listOf(0, 2), listOf(1, 2)),
            table(PieceType.J, 2, 3),
        )
        assertEquals(
            listOf(listOf(0, 0), listOf(-1, 0), listOf(-1, 1), listOf(0, -2), listOf(-1, -2)),
            table(PieceType.L, 3, 0),
        )
    }

    @Test
    fun `у палки своя таблица`() {
        assertEquals(
            listOf(listOf(0, 0), listOf(-2, 0), listOf(1, 0), listOf(-2, 1), listOf(1, -2)),
            table(PieceType.I, 0, 1),
        )
        assertEquals(
            listOf(listOf(0, 0), listOf(1, 0), listOf(-2, 0), listOf(1, 2), listOf(-2, -1)),
            table(PieceType.I, 3, 0),
        )
    }

    @Test
    fun `все восемь переходов описаны и содержат по пять смещений`() {
        val moves = listOf(0 to 1, 1 to 0, 1 to 2, 2 to 1, 2 to 3, 3 to 2, 3 to 0, 0 to 3)
        for (type in listOf(PieceType.T, PieceType.I)) {
            for ((from, to) in moves) {
                val k = Kicks.forMove(type, from, to)
                assertEquals(5, k.size, "$type $from>$to")
                assertContentEquals(intArrayOf(0, 0), k[0], "$type $from>$to первое смещение нулевое")
            }
        }
    }

    @Test
    fun `поворот без препятствий берёт нулевое смещение`() {
        val g = TetrisGame()
        g.debugStartImmediate()
        g.debugClearGrid()
        g.debugPiece(Piece(PieceType.T, 0, 3, 5))
        g.rotate(1)
        val p = assertNotNull(g.piece)
        assertEquals(1, p.rot)
        assertEquals(3, p.x)
        assertEquals(5, p.y)
    }

    @Test
    fun `T отскакивает вправо когда мешает блок слева`() {
        val g = TetrisGame()
        g.debugStartImmediate()
        g.debugClearGrid()
        // при 1>2 нулевое смещение ставит клетку в (0,1) — занимаем её
        g.debugSet(0, 1, PieceType.L)
        g.debugPiece(Piece(PieceType.T, 1, 0, 0))
        g.rotate(1)
        val p = assertNotNull(g.piece)
        assertEquals(2, p.rot)
        assertEquals(1, p.x, "должно сработать смещение [1,0]")
        assertEquals(0, p.y)
    }

    @Test
    fun `палка уезжает на две клетки влево при повороте у препятствия`() {
        val g = TetrisGame()
        g.debugStartImmediate()
        g.debugClearGrid()
        // 0>1 без смещения ставит палку в колонку 2 — перекрываем верх колонки
        g.debugSet(2, 0, PieceType.L)
        g.debugPiece(Piece(PieceType.I, 0, 0, 0))
        g.rotate(1)
        val p = assertNotNull(g.piece)
        assertEquals(1, p.rot)
        assertEquals(-2, p.x, "должно сработать смещение [-2,0]")
        assertEquals(
            listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(0, 3)),
            g.cellsOf(p),
        )
    }

    @Test
    fun `когда все пять смещений заняты поворот не происходит`() {
        val g = TetrisGame()
        g.debugStartImmediate()
        g.debugClearGrid()
        // колодец шириной в одну клетку: горизонтальной палке некуда встать
        for (y in 0 until C.ROWS) {
            for (x in 0 until C.COLS) if (x != 4) g.debugSet(x, y, PieceType.L)
        }
        g.debugPiece(Piece(PieceType.I, 1, 2, 0))
        g.rotate(1)
        val p = assertNotNull(g.piece)
        assertEquals(1, p.rot, "поворот должен быть отменён")
        assertEquals(2, p.x)
    }

    @Test
    fun `O не поворачивается`() {
        val g = TetrisGame()
        g.debugStartImmediate()
        g.debugClearGrid()
        g.debugPiece(Piece(PieceType.O, 0, 4, 5))
        g.rotate(1)
        g.rotate(-1)
        val p = assertNotNull(g.piece)
        assertEquals(0, p.rot)
        assertEquals(4, p.x)
        assertEquals(5, p.y)
    }
}

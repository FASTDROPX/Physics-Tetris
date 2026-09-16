package com.serafim.tetris.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Матрицы всех четырёх состояний SRS и их согласованность. */
class ShapesTest {

    private fun rows(type: PieceType, rot: Int): List<String> =
        Shapes.matrix(type, rot).map { row -> row.joinToString("") { if (it != 0) "#" else "." } }

    @Test
    fun `T проходит четыре состояния SRS`() {
        assertEquals(listOf(".#.", "###", "..."), rows(PieceType.T, 0))
        assertEquals(listOf(".#.", ".##", ".#."), rows(PieceType.T, 1))
        assertEquals(listOf("...", "###", ".#."), rows(PieceType.T, 2))
        assertEquals(listOf(".#.", "##.", ".#."), rows(PieceType.T, 3))
    }

    @Test
    fun `I лежит в строке 1 и стоит в колонке 2`() {
        assertEquals(listOf("....", "####", "....", "...."), rows(PieceType.I, 0))
        assertEquals(listOf("..#.", "..#.", "..#.", "..#."), rows(PieceType.I, 1))
        assertEquals(listOf("....", "....", "####", "...."), rows(PieceType.I, 2))
        assertEquals(listOf(".#..", ".#..", ".#..", ".#.."), rows(PieceType.I, 3))
    }

    @Test
    fun `O не меняется при поворотах`() {
        val base = rows(PieceType.O, 0)
        for (rot in 0 until 4) assertEquals(base, rows(PieceType.O, rot))
        assertEquals(listOf("##", "##"), base)
    }

    @Test
    fun `J и S совпадают с эталоном SRS`() {
        assertEquals(listOf("#..", "###", "..."), rows(PieceType.J, 0))
        assertEquals(listOf(".##", ".#.", ".#."), rows(PieceType.J, 1))
        assertEquals(listOf("...", "###", "..#"), rows(PieceType.J, 2))
        assertEquals(listOf(".#.", ".#.", "##."), rows(PieceType.J, 3))

        assertEquals(listOf(".##", "##.", "..."), rows(PieceType.S, 0))
        assertEquals(listOf(".#.", ".##", "..#"), rows(PieceType.S, 1))
    }

    @Test
    fun `у каждой фигуры ровно четыре клетки в любом состоянии`() {
        for (t in PieceType.ALL) for (rot in 0 until 4) {
            val n = Shapes.matrix(t, rot).sumOf { row -> row.count { it != 0 } }
            assertEquals(4, n, "$t rot=$rot")
        }
    }

    @Test
    fun `четвёртый поворот возвращает фигуру в исходное состояние`() {
        for (t in PieceType.ALL) {
            var m = Shapes.matrix(t, 0)
            repeat(4) { m = Shapes.rotateCW(m) }
            assertEquals(
                Shapes.matrix(t, 0).map { it.toList() },
                m.map { it.toList() },
                t.name,
            )
        }
    }

    @Test
    fun `порядок и цвета фигур совпадают с оригиналом`() {
        assertEquals("I,O,T,S,Z,J,L", PieceType.ALL.joinToString(",") { it.name })
        assertEquals(0xFF7FCFE8.toInt(), PieceType.I.color)
        assertEquals(0xFFA8C7FA.toInt(), PieceType.J.color)
    }

    @Test
    fun `все фигуры появляются целиком в видимой части стакана`() {
        val g = TetrisGame()
        for (t in PieceType.ALL) {
            val sp = Shapes.SPAWN.getValue(t)
            val cells = g.cellsOf(Piece(t, 0, sp.x, sp.y))
            assertTrue(cells.all { it.y >= 0 && it.x in 0 until C.COLS }, t.name)
        }
    }
}

package com.serafim.tetris.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Сброс, откладывание, обвал рядов, конец игры и переходы состояний. */
class GameFlowTest {

    private fun game(): TetrisGame {
        val g = TetrisGame(random = Random(42))
        g.debugStartImmediate()
        g.debugClearGrid()
        return g
    }

    @Test
    fun `жёсткий сброс приносит по два очка за клетку`() {
        val g = game()
        g.debugPiece(Piece(PieceType.T, 0, 3, 0))
        g.hardDrop()
        assertEquals(36L, g.score, "18 клеток пролёта")
        assertEquals(PieceType.T, g.at(4, 18))
        assertEquals(PieceType.T, g.at(3, 19))
        assertEquals(PieceType.T, g.at(4, 19))
        assertEquals(PieceType.T, g.at(5, 19))
    }

    @Test
    fun `жёсткий сброс оставляет след по одной полосе на колонку`() {
        val g = game()
        g.debugPiece(Piece(PieceType.T, 0, 3, 0))
        g.hardDrop()
        assertEquals(3, g.trails.size)
        assertEquals(listOf(3, 4, 5), g.trails.map { it.x }.sorted())
    }

    @Test
    fun `сброс с нулевой высоты следа не оставляет`() {
        val g = game()
        g.debugPiece(Piece(PieceType.T, 0, 3, 18))
        g.hardDrop()
        assertTrue(g.trails.isEmpty())
        assertEquals(0L, g.score)
    }

    @Test
    fun `ускоренное падение приносит очко за клетку`() {
        val g = game()
        g.debugPiece(Piece(PieceType.T, 0, 3, 0))
        repeat(5) { g.softDrop() }
        assertEquals(5L, g.score)
        assertEquals(5, g.piece!!.y)
    }

    @Test
    fun `отложить можно один раз на фигуру`() {
        val g = game()
        val first = g.piece!!.type
        g.holdPiece()
        assertEquals(first, g.hold)
        assertFalse(g.canHold)
        val afterHold = g.piece!!.type
        g.holdPiece()
        assertEquals(first, g.hold, "второе откладывание игнорируется")
        assertEquals(afterHold, g.piece!!.type)
    }

    @Test
    fun `отложенная фигура возвращается при следующем откладывании`() {
        val g = game()
        val first = g.piece!!.type
        g.holdPiece()
        val second = g.piece!!.type
        g.debugPiece(Piece(second, 0, 3, 18))
        g.hardDrop()                       // фиксация возвращает право откладывать
        assertTrue(g.canHold)
        val third = g.piece!!.type
        g.holdPiece()
        assertEquals(third, g.hold, "в карман уходит текущая фигура")
        assertEquals(first, g.piece!!.type, "а из кармана выходит отложенная")
    }

    @Test
    fun `очистка проходит через показ и обвал`() {
        val g = game()
        g.debugFillRow(19, exceptX = 0)
        g.debugSet(3, 18, PieceType.I)     // чтобы очистка не была идеальной
        g.debugPiece(Piece(PieceType.I, 1, -2, 16))
        g.hardDrop()
        assertEquals(GameState.CLEARING, g.state)
        assertEquals(listOf(19), g.clearRows)

        g.update(100.0)
        assertEquals(GameState.CLEARING, g.state)
        g.update(100.0)                    // 200 мс — линии исчезли
        assertEquals(GameState.FALLING, g.state)

        repeat(4) { g.update(100.0) }      // 340 мс — обвал закончился
        assertEquals(GameState.PLAYING, g.state)
        assertTrue(g.canHold)
    }

    @Test
    fun `после обвала ряды съезжают вниз`() {
        val g = game()
        g.debugFillRow(19, exceptX = 0)
        g.debugSet(3, 17, PieceType.I)
        g.debugPiece(Piece(PieceType.I, 1, -2, 16))
        g.hardDrop()
        // палка встала в колонку 0 на строках 16..19, строка 19 очистилась
        g.update(100.0); g.update(100.0)
        assertEquals(GameState.FALLING, g.state)
        assertEquals(PieceType.I, g.at(0, 19), "остаток палки сдвинулся на строку вниз")
        assertEquals(PieceType.I, g.at(3, 18))
        assertNull(g.at(3, 17))
    }

    /** Забивает зону появления так, что следующая фигура уже не помещается. */
    private fun TetrisGame.blockSpawnZone() {
        for (y in 0..1) for (x in 3..6) debugSet(x, y, PieceType.L)
    }

    @Test
    fun `переполнение стакана заканчивает игру`() {
        val g = game()
        g.blockSpawnZone()
        g.debugPiece(Piece(PieceType.T, 0, 0, 18))
        g.hardDrop()
        assertEquals(GameState.DYING, g.state)
        assertNotNull(g.dust)
    }

    @Test
    fun `распад стакана доигрывает до экрана результата`() {
        val g = game()
        for (y in 10 until C.ROWS) g.debugFillRow(y, exceptX = 9)
        g.blockSpawnZone()
        g.debugPiece(Piece(PieceType.T, 0, 0, 8))
        g.hardDrop()
        assertEquals(GameState.DYING, g.state)
        val total = assertNotNull(g.dust).total
        var t = 0.0
        while (t < total + C.WIPE_TIME + 400 && g.state == GameState.DYING) {
            g.update(100.0); t += 100.0
        }
        assertEquals(GameState.OVER, g.state)
        assertTrue(g.blasting, "перед уходом стакана звучит взмах")
    }

    @Test
    fun `рекорд сессии обновляется`() {
        val g = game()
        g.debugApplyScore(4, null, false)
        g.blockSpawnZone()
        g.debugPiece(Piece(PieceType.T, 0, 0, 18))
        g.hardDrop()
        assertTrue(g.best >= 800L)
    }

    @Test
    fun `пауза останавливает и возвращает игру`() {
        val g = game()
        g.debugPiece(Piece(PieceType.T, 0, 3, 0))
        g.togglePause()
        assertEquals(GameState.PAUSED, g.state)
        val y = g.piece!!.y
        repeat(30) { g.update(100.0) }
        assertEquals(y, g.piece!!.y, "на паузе фигура не падает")
        g.togglePause()
        assertEquals(GameState.PLAYING, g.state)
    }

    @Test
    fun `вне партии ввод игнорируется`() {
        val g = game()
        g.debugPiece(Piece(PieceType.T, 0, 3, 5))
        g.debugState(GameState.PAUSED)
        assertFalse(g.move(1))
        g.rotate(1)
        g.hardDrop()
        g.softDrop()
        assertEquals(Piece(PieceType.T, 0, 3, 5), g.piece)
    }

    @Test
    fun `гравитация роняет фигуру раз в секунду на первом уровне`() {
        val g = game()
        g.debugSetLevel(1)
        g.debugPiece(Piece(PieceType.T, 0, 3, 0))
        repeat(9) { g.update(100.0) }
        assertEquals(0, g.piece!!.y)
        g.update(100.0)
        assertEquals(1, g.piece!!.y)
    }

    @Test
    fun `вступление длится указанное время и выдаёт первую фигуру`() {
        val g = TetrisGame(random = Random(7))
        g.startGame()
        assertEquals(GameState.INTRO, g.state)
        assertNull(g.piece)
        repeat(5) { g.update(100.0) }      // 500 мс
        assertEquals(GameState.INTRO, g.state)
        g.update(100.0)                    // 600 мс
        assertEquals(GameState.PLAYING, g.state)
        assertNotNull(g.piece)
        assertTrue(g.revealFirst, "первая фигура матча проявляется по кубику")
    }

    @Test
    fun `новая партия обнуляет счёт но не опыт`() {
        val g = game()
        g.debugApplyScore(4, null, false)
        g.update(16.0)
        val xp = g.xpTotal
        assertTrue(xp > 0)
        g.startGame()
        assertEquals(0L, g.score)
        assertEquals(0, g.lines)
        assertEquals(xp, g.xpTotal, "уровень игрока копится между партиями")
    }

    @Test
    fun `призрак показывает место приземления`() {
        val g = game()
        g.debugPiece(Piece(PieceType.T, 0, 3, 0))
        assertEquals(18, g.ghostY())
        g.debugFillRow(19)
        g.debugFillRow(18)
        assertEquals(16, g.ghostY())
    }

    @Test
    fun `падение перетаскиванием оставляет тот же след что и жёсткий сброс`() {
        val g = game()
        g.debugPiece(Piece(PieceType.T, 0, 3, 0))
        repeat(6) { g.softDrop() }
        assertTrue(g.trails.isEmpty(), "сам по себе softDrop следа не рисует")
        g.dropTrail(0)
        assertEquals(3, g.trails.size)
        assertEquals(listOf(3, 4, 5), g.trails.map { it.x }.sorted())
        // полоса тянется на всю пройденную высоту
        val t = g.trails.first { it.x == 4 }
        assertEquals(6, t.to - t.from)
    }

    @Test
    fun `след не появляется если фигура не двигалась`() {
        val g = game()
        g.debugPiece(Piece(PieceType.T, 0, 3, 5))
        g.dropTrail(5)
        assertTrue(g.trails.isEmpty())
    }

    @Test
    fun `опыт и рекорд восстанавливаются между запусками`() {
        val g = TetrisGame(random = Random(3))
        g.restoreProgress(totalXp = 2600, rank = 4, bestScore = 5400)
        assertEquals(2600L, g.xpTotal)
        assertEquals(4, g.xpRank)
        assertEquals(5400L, g.best)
        val p = g.xpProgress()
        assertEquals(2000L, p.lo)
        assertEquals(4000L, p.hi)
        // и продолжают расти с восстановленной точки
        g.debugStartImmediate()
        g.debugClearGrid()
        g.debugSetLevel(1)
        g.debugApplyScore(4, null, false)
        g.update(16.0)
        assertEquals(3400L, g.xpTotal)
    }

    @Test
    fun `восстановление не принимает мусор`() {
        val g = TetrisGame(random = Random(3))
        g.restoreProgress(totalXp = -50, rank = 999, bestScore = -7)
        assertEquals(0L, g.xpTotal)
        assertEquals(Xp.BOUNDS.size - 2, g.xpRank)
        assertEquals(0L, g.best)
    }

    @Test
    fun `рекорд можно зафиксировать не доигрывая партию`() {
        val g = game()
        g.debugApplyScore(4, null, false)
        assertEquals(0L, g.best, "пока партия идёт рекорд не тронут")
        g.commitBest()
        assertEquals(800L, g.best)
        g.commitBest()
        assertEquals(800L, g.best, "повторный вызов ничего не портит")
    }
}

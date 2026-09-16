package com.serafim.tetris.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Счётчики за всё время. Отдельно от счёта партии: тот обнуляется с новой
 * игрой, эти складываются и уходят на диск.
 */
class StatsTest {

    private fun game() = TetrisGame(random = Random(4)).also { it.debugStartImmediate() }

    @Test
    fun `новая партия увеличивает счётчик партий`() {
        val g = TetrisGame(random = Random(4))
        assertEquals(0L, g.stats.games)
        g.debugStartImmediate()
        assertEquals(1L, g.stats.games)
        g.debugStartImmediate()
        assertEquals(2L, g.stats.games)
    }

    @Test
    fun `каждая улёгшаяся фигура идёт в счёт`() {
        val g = game()
        repeat(5) {
            g.hardDrop()
            // после фиксации сразу появляется следующая
            var n = 0
            while (g.piece == null && n < 200) { g.update(16.0); n++ }
        }
        assertEquals(5L, g.stats.pieces)
    }

    @Test
    fun `фигура, не влезшая в стакан, не засчитывается`() {
        val g = game()
        g.debugPiece(Piece(PieceType.O, 0, 4, -2))
        g.debugLock()
        assertEquals(GameState.DYING, g.state)
        assertEquals(0L, g.stats.pieces, "она не легла в стакан, а закончила партию")
    }

    @Test
    fun `стёртые линии складываются между партиями`() {
        val g = game()
        g.debugSetLevel(1)
        g.debugApplyScore(2, null, false)
        g.debugApplyScore(4, null, false)
        assertEquals(6L, g.stats.lines)
        val was = g.stats.lines
        g.debugStartImmediate()                 // новая партия
        assertEquals(0, g.lines, "счёт партии обнулился")
        assertEquals(was, g.stats.lines, "а общий счётчик — нет")
    }

    @Test
    fun `очки за всё время копятся по мере начисления`() {
        val g = game()
        g.debugSetLevel(1)
        g.debugApplyScore(1, null, false)       // 100 очков
        g.update(16.0)
        assertEquals(100L, g.stats.score)
        g.debugApplyScore(4, null, false)       // ещё 800 плюс 50 за комбо
        g.update(16.0)
        assertEquals(950L, g.stats.score)

        g.debugStartImmediate()
        assertEquals(0L, g.score, "счёт партии обнулился")
        assertEquals(950L, g.stats.score, "общая сумма осталась")
    }

    @Test
    fun `очки не начисляются в общий счёт дважды`() {
        val g = game()
        g.debugSetLevel(1)
        g.debugApplyScore(1, null, false)
        repeat(20) { g.update(16.0) }
        assertEquals(100L, g.stats.score)
    }

    @Test
    fun `значения с диска подставляются, отрицательные отбрасываются`() {
        val g = TetrisGame(random = Random(4))
        g.stats.restore(1234, 56, 78, 9)
        assertEquals(1234L, g.stats.score)
        assertEquals(56L, g.stats.pieces)
        assertEquals(78L, g.stats.lines)
        assertEquals(9L, g.stats.games)

        g.stats.restore(-1, -1, -1, -1)
        assertTrue(
            g.stats.score == 0L && g.stats.pieces == 0L &&
                g.stats.lines == 0L && g.stats.games == 0L,
        )
    }

    @Test
    fun `часы идут только пока идёт партия`() {
        val g = game()
        repeat(10) { g.update(16.0) }
        assertEquals(160.0, g.playMs, 1e-9)
        assertEquals(160.0, g.stats.timeMs, 1e-9)

        g.togglePause()
        repeat(50) { g.update(16.0) }
        assertEquals(160.0, g.playMs, 1e-9, "пауза не считается")
        g.togglePause()
        g.update(16.0)
        assertEquals(176.0, g.playMs, 1e-9)

        g.backToMenu()
        repeat(50) { g.update(16.0) }
        assertEquals(176.0, g.stats.timeMs, 1e-9, "меню не считается")
    }

    @Test
    fun `новая партия обнуляет часы, а общее время копится`() {
        val g = game()
        repeat(10) { g.update(16.0) }
        g.debugStartImmediate()
        assertEquals(0.0, g.playMs)
        repeat(5) { g.update(16.0) }
        assertEquals(80.0, g.playMs, 1e-9)
        assertEquals(240.0, g.stats.timeMs, 1e-9)
    }

    @Test
    fun `после проигрыша часы партии стоят`() {
        val g = game()
        repeat(10) { g.update(16.0) }
        g.debugPiece(Piece(PieceType.O, 0, 4, -2))
        g.debugLock()
        assertEquals(GameState.DYING, g.state)
        val at = g.playMs
        repeat(300) { g.update(16.0) }
        assertEquals(at, g.playMs, 1e-9, "время рассыпания в партию не входит")
    }

    @Test
    fun `общее время восстанавливается с диска`() {
        val g = TetrisGame(random = Random(4))
        g.stats.restore(1, 2, 3, 4, 3_600_000L)
        assertEquals(3_600_000.0, g.stats.timeMs)
        g.stats.restore(1, 2, 3, 4, -5L)
        assertEquals(0.0, g.stats.timeMs)
    }

    @Test
    fun `в режиме физики фигуры считаются так же`() {
        val g = TetrisGame(random = Random(4))
        g.physicsMode = true
        g.debugStartImmediate()
        repeat(4) {
            g.hardDrop()
            var n = 0
            while (g.piece == null && n < 400) { g.update(16.0); n++ }
        }
        assertEquals(4L, g.stats.pieces)
    }

    @Test
    fun `опыт на старте партии запоминается`() {
        val g = TetrisGame(random = Random(4))
        g.restoreProgress(12_345, Xp.rankOf(12_345), 0)
        g.debugStartImmediate()
        assertEquals(12_345L, g.xpAtStart)
        g.debugSetLevel(1)
        g.debugApplyScore(4, null, false)
        g.update(16.0)
        assertEquals(12_345L + 800, g.xpTotal)
        assertEquals(12_345L, g.xpAtStart, "в партии точка отсчёта не двигается")
        g.debugStartImmediate()
        assertEquals(12_345L + 800, g.xpAtStart, "новая партия считает от набранного")
    }

    @Test
    fun `сброс обнуляет счётчики, но не рекорд и не опыт`() {
        val g = TetrisGame(random = Random(4))
        g.restoreProgress(50_000, Xp.rankOf(50_000), 12_000)
        g.stats.restore(48_000, 900, 200, 12, 3_600_000)
        assertTrue(!g.stats.isEmpty)
        g.stats.reset()
        assertEquals(0L, g.stats.score)
        assertEquals(0L, g.stats.pieces)
        assertEquals(0L, g.stats.lines)
        assertEquals(0L, g.stats.games)
        assertEquals(0.0, g.stats.timeMs)
        assertTrue(g.stats.isEmpty)
        assertEquals(12_000L, g.best, "рекорд остался")
        assertEquals(50_000L, g.xpTotal, "уровень игрока остался")
        g.debugStartImmediate()
        assertEquals(1L, g.stats.games, "после сброса счёт идёт заново")
    }

    @Test
    fun `полный сброс обнуляет и рекорд, и уровень`() {
        val g = TetrisGame(random = Random(4))
        g.restoreProgress(48_000_000, Xp.rankOf(48_000_000), 6_718_082)
        g.stats.restore(48_000_000, 91_204, 22_180, 412, 3_600_000)
        g.debugStartImmediate()
        g.debugSetLevel(1)
        g.debugApplyScore(4, null, false)
        g.update(16.0)
        g.backToMenu()
        assertTrue(g.score > 0, "счёт последней партии ещё лежит в игре")

        g.resetProgress()
        assertEquals(0L, g.best)
        assertEquals(0L, g.xpTotal)
        assertEquals(0, g.xpRank)
        assertTrue(g.stats.isEmpty)
        assertEquals(0L, g.score)

        // сворачивание в меню не возвращает в рекорд счёт прошлой партии
        g.commitBest()
        assertEquals(0L, g.best)
        repeat(10) { g.update(16.0) }
        assertEquals(0L, g.xpTotal, "и опыт не набегает заново")

        // новая партия идёт с чистого листа
        g.debugStartImmediate()
        assertEquals(0L, g.bestAtStart)
        assertEquals(0L, g.xpAtStart)
        assertEquals(1L, g.stats.games)
    }

    @Test
    fun `рекорд на старте партии не переписывается её счётом`() {
        val g = TetrisGame(random = Random(4))
        g.restoreProgress(0, 0, 800)
        g.debugStartImmediate()
        assertEquals(800L, g.bestAtStart)
        g.debugSetLevel(1)
        g.debugApplyScore(4, null, false)          // ровно 800 — повтор, не рекорд
        g.commitBest()
        assertEquals(800L, g.best)
        assertEquals(800L, g.bestAtStart)
        assertTrue(g.score <= g.bestAtStart, "повторить рекорд — не побить его")
    }
}

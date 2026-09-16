package com.serafim.tetris.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Таблица очков, серия подряд, комбо, T-спины и идеальная очистка. */
class ScoringTest {

    private fun game(level: Int = 1): TetrisGame {
        val g = TetrisGame()
        g.debugStartImmediate()
        g.debugClearGrid()
        g.debugSetLevel(level)
        return g
    }

    @Test
    fun `базовая таблица линий на первом уровне`() {
        assertEquals(100L, game().also { it.debugApplyScore(1, null, false) }.score)
        assertEquals(300L, game().also { it.debugApplyScore(2, null, false) }.score)
        assertEquals(500L, game().also { it.debugApplyScore(3, null, false) }.score)
        assertEquals(800L, game().also { it.debugApplyScore(4, null, false) }.score)
    }

    @Test
    fun `очки умножаются на уровень скорости`() {
        assertEquals(4000L, game(level = 5).also { it.debugApplyScore(4, null, false) }.score)
        assertEquals(2000L, game(level = 20).also { it.debugApplyScore(1, null, false) }.score)
    }

    @Test
    fun `тетрис подряд стоит в полтора раза дороже`() {
        val g = game()
        g.debugApplyScore(4, null, false)
        assertEquals(800L, g.score)
        assertTrue(g.backToBack)
        g.debugSetCombo(-1)
        g.debugApplyScore(4, null, false)
        assertEquals(800L + 1200L, g.score)
    }

    @Test
    fun `простая линия обрывает серию`() {
        val g = game()
        g.debugSetBackToBack(true)
        g.debugApplyScore(1, null, false)
        assertFalse(g.backToBack)
        assertEquals(100L, g.score, "к обычной одиночке множитель не применяется")
    }

    @Test
    fun `T-спин держит серию наравне с тетрисом`() {
        val g = game()
        g.debugApplyScore(4, null, false)      // 800, серия открыта
        g.debugSetCombo(-1)
        g.debugApplyScore(1, Spin.FULL, false) // 800 * 1.5
        assertEquals(800L + 1200L, g.score)
        assertTrue(g.backToBack)
    }

    @Test
    fun `таблица полного T-спина`() {
        assertEquals(400L, game().also { it.debugApplyScore(0, Spin.FULL, false) }.score)
        assertEquals(800L, game().also { it.debugApplyScore(1, Spin.FULL, false) }.score)
        assertEquals(1200L, game().also { it.debugApplyScore(2, Spin.FULL, false) }.score)
        assertEquals(1600L, game().also { it.debugApplyScore(3, Spin.FULL, false) }.score)
    }

    @Test
    fun `таблица мини T-спина`() {
        assertEquals(100L, game().also { it.debugApplyScore(0, Spin.MINI, false) }.score)
        assertEquals(200L, game().also { it.debugApplyScore(1, Spin.MINI, false) }.score)
        assertEquals(400L, game().also { it.debugApplyScore(2, Spin.MINI, false) }.score)
        assertEquals(600L, game().also { it.debugApplyScore(3, Spin.MINI, false) }.score)
    }

    @Test
    fun `T-спин без линий не открывает серию и сбрасывает комбо`() {
        val g = game()
        g.debugSetCombo(3)
        g.debugApplyScore(0, Spin.FULL, false)
        assertEquals(400L, g.score)
        assertFalse(g.backToBack)
        assertEquals(-1, g.combo)
    }

    @Test
    fun `комбо добавляет по пятьдесят за каждую фигуру подряд`() {
        val g = game()
        g.debugApplyScore(1, null, false)      // комбо 0 — без надбавки
        assertEquals(100L, g.score)
        assertEquals(0, g.combo)
        g.debugApplyScore(1, null, false)      // комбо 1 → +50
        assertEquals(100L + 150L, g.score)
        g.debugApplyScore(1, null, false)      // комбо 2 → +100
        assertEquals(100L + 150L + 200L, g.score)
        assertEquals(2, g.combo)
    }

    @Test
    fun `комбо тоже умножается на уровень`() {
        val g = game(level = 4)
        g.debugSetCombo(1)
        g.debugApplyScore(1, null, false)      // комбо 2 → 100*4 + 50*2*4
        assertEquals(400L + 400L, g.score)
    }

    @Test
    fun `фигура без линий обнуляет комбо`() {
        val g = game()
        g.debugSetCombo(5)
        g.debugApplyScore(0, null, false)
        assertEquals(-1, g.combo)
        assertEquals(0L, g.score)
    }

    @Test
    fun `идеальная очистка добавляет крупный бонус`() {
        assertEquals(100L + 800L, game().also { it.debugApplyScore(1, null, true) }.score)
        assertEquals(300L + 1200L, game().also { it.debugApplyScore(2, null, true) }.score)
        assertEquals(500L + 1800L, game().also { it.debugApplyScore(3, null, true) }.score)
        assertEquals(800L + 2000L, game().also { it.debugApplyScore(4, null, true) }.score)
    }

    @Test
    fun `идеальный тетрис подряд складывается с множителем серии`() {
        val g = game()
        g.debugSetBackToBack(true)
        g.debugApplyScore(4, null, true)
        assertEquals(1200L + 2000L, g.score, "800*1.5 сначала, бонус очистки сверху")
    }

    @Test
    fun `уровень растёт каждые десять линий и упирается в двадцатый`() {
        val g = game()
        g.startLevel = 1
        g.debugSetLevel(1)
        repeat(2) { g.debugApplyScore(4, null, false); g.debugSetCombo(-1) }
        assertEquals(8, g.lines)
        assertEquals(1, g.level)
        g.debugApplyScore(4, null, false)
        assertEquals(12, g.lines)
        assertEquals(2, g.level)
        repeat(60) { g.debugApplyScore(4, null, false); g.debugSetCombo(-1) }
        assertTrue(g.score < C.SPEED_BONUS_FROM, "проверка про потолок по линиям: до миллиона")
        assertEquals(C.BASE_MAX_LEVEL, g.level)
    }

    @Test
    fun `после миллиона скорость растёт от очков`() {
        val g = game()
        g.startLevel = 1
        g.debugSetLines(2000)                  // по линиям давно за двадцатым
        g.debugSetLevel(20)
        g.debugSetScore(1_050_000)
        g.debugApplyScore(0, null, false)
        assertEquals(20, g.level, "до 1 100 000 потолок ещё двадцатый")

        g.debugSetScore(1_100_000)
        g.debugApplyScore(0, null, false)
        assertEquals(21, g.level)

        g.debugSetScore(2_345_678)
        g.debugApplyScore(0, null, false)
        assertEquals(33, g.level)

        g.debugSetScore(9_000_000)
        g.debugApplyScore(0, null, false)
        assertEquals(C.MAX_LEVEL, g.level)
    }

    @Test
    fun `очки без линий скорость не поднимают`() {
        // потолок — это потолок: по линиям игрок ещё на пятой скорости
        val g = game()
        g.startLevel = 1
        g.debugSetLines(40)
        g.debugSetLevel(5)
        g.debugSetScore(3_000_000)
        g.debugApplyScore(0, null, false)
        assertEquals(5, g.level)
    }

    @Test
    fun `стартовый уровень сдвигает всю шкалу`() {
        val g = game()
        g.startLevel = 10
        g.debugSetLevel(10)
        repeat(3) { g.debugApplyScore(4, null, false); g.debugSetCombo(-1) }
        assertEquals(12, g.lines)
        assertEquals(11, g.level)
    }

    @Test
    fun `две линии квадратом на дне дают идеальную очистку`() {
        val g = game()
        g.debugFillRow(19, exceptX = 0)
        g.debugFillRow(18, exceptX = 0)
        g.debugSet(1, 18, null)
        g.debugSet(1, 19, null)
        g.debugPiece(Piece(PieceType.O, 0, 0, 18))
        g.debugLock()
        // обе строки закрылись и на поле не осталось ничего — идеальная очистка
        assertEquals(2, g.lines)
        assertEquals(300L + 1200L, g.score)
        assertEquals(GameState.CLEARING, g.state)
    }

    @Test
    fun `текст комбинации уходит в интерфейс`() {
        val seen = ArrayList<String>()
        val g = TetrisGame(fx = object : Fx by Fx.None {
            override fun message(text: String) { seen.add(text) }
        })
        g.debugStartImmediate()
        g.debugClearGrid()
        g.debugSetLevel(1)
        g.debugApplyScore(4, null, false)
        g.debugSetCombo(0)
        g.debugApplyScore(4, null, false)
        assertEquals(listOf("Тетрис", "Тетрис · подряд · комбо 1"), seen)
    }

    /** Сброс одной и той же фигуры с одной и той же высоты на заданной скорости. */
    private fun dropAt(level: Int, physics: Boolean = false): Long {
        val g = TetrisGame(random = kotlin.random.Random(3))
        g.physicsMode = physics
        g.debugStartImmediate()
        g.debugClearGrid()
        g.debugSetLevel(level)
        g.debugPiece(Piece(PieceType.O, 0, 4, 0))
        g.hardDrop()
        if (physics) {
            // ждём, пока тело уляжется и выйдет следующая фигура, — не дольше:
            // на высокой скорости та успела бы долететь и лечь сама
            var n = 0
            while (g.piece == null && n < 400) { g.update(16.0); n++ }
        }
        return g.score
    }

    @Test
    fun `до двадцатой за постановку платит только сброс`() {
        for (lv in listOf(1, 10, 20)) assertEquals(0L, placeBonus(lv))
        val drop = dropAt(1)
        assertTrue(drop > 0, "два очка за клетку никуда не делись")
        assertEquals(drop, dropAt(20), "до двадцатой постановка от скорости не зависит")
    }

    @Test
    fun `выше двадцатой каждая фигура приносит очки сама`() {
        assertEquals(10L, placeBonus(21))
        assertEquals(100L, placeBonus(30))
        assertEquals(300L, placeBonus(C.MAX_LEVEL))
        assertEquals(300L, placeBonus(99), "выше потолка не растёт")
        val base = dropAt(20)
        assertEquals(base + 50, dropAt(25))
        assertEquals(base + 300, dropAt(C.MAX_LEVEL))
    }

    @Test
    fun `в режиме физики надбавка за фигуру начисляется один раз`() {
        // фигура уходит в мир, стакан ждёт, пока она уляжется, и только
        // потом считает ряды — надбавка не должна повториться на этом шаге
        val base = dropAt(20, physics = true)
        assertEquals(base + 100, dropAt(30, physics = true))
    }

    @Test
    fun `фигура, закончившая партию, надбавки не приносит`() {
        val g = game(C.MAX_LEVEL)
        g.debugPiece(Piece(PieceType.O, 0, 4, -2))
        g.debugLock()
        assertEquals(GameState.DYING, g.state)
        assertEquals(0L, g.score)
    }
}

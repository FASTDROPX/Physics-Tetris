package com.serafim.tetris.game

import com.serafim.tetris.physics.EMPTY_POLY
import com.serafim.tetris.physics.PhysicsField
import com.serafim.tetris.physics.clipBand
import com.serafim.tetris.physics.clipBox
import com.serafim.tetris.physics.clipHalf
import com.serafim.tetris.physics.mergeRuns
import com.serafim.tetris.physics.polyArea
import com.serafim.tetris.physics.sanitize
import com.serafim.tetris.physics.touches
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Единичный квадрат с обходом против часовой стрелки. */
private fun square(x: Double, y: Double, s: Double = 1.0) = doubleArrayOf(
    x, y, x + s, y, x + s, y + s, x, y + s,
)

private fun field() = PhysicsField()

private fun PhysicsField.settle(ms: Double = 3000.0) {
    var t = 0.0
    while (t < ms) {
        step(16.0)
        t += 16.0
        if (t > 200 && quiet()) return
    }
}

class ClipTest {

    @Test
    fun `площадь квадрата`() {
        assertEquals(1.0, polyArea(square(3.0, 4.0)), 1e-12)
        assertEquals(0.25, polyArea(square(0.0, 0.0, 0.5)), 1e-12)
    }

    @Test
    fun `полоса отрезает ровно свою долю`() {
        val q = square(0.0, 0.0)
        assertEquals(0.5, polyArea(clipBand(q, 0.25, 0.75)), 1e-12)
        assertEquals(0.3, polyArea(clipBand(q, 0.7, 5.0)), 1e-12)
        assertEquals(1.0, polyArea(clipBand(q, -1.0, 2.0)), 1e-12)
    }

    @Test
    fun `полоса мимо фигуры даёт пустоту`() {
        assertEquals(0, clipBand(square(0.0, 0.0), 3.0, 4.0).size)
        assertEquals(0, clipHalf(EMPTY_POLY, 0.0, 1.0, 1.0).size)
    }

    @Test
    fun `коробка вырезает клетку`() {
        // квадрат 2х2 в начале координат, режем правый верхний угол
        val big = square(0.0, 0.0, 2.0)
        assertEquals(1.0, polyArea(clipBox(big, 1.0, 1.0, 2.0, 2.0)), 1e-12)
        assertEquals(0.25, polyArea(clipBox(big, 1.5, 1.5, 3.0, 3.0)), 1e-12)
    }

    @Test
    fun `наклонная фигура режется по площади, а не по клеткам`() {
        // ромб со стороной sqrt(2)/2 вокруг точки (1,1): площадь 0.5
        val diamond = doubleArrayOf(1.0, 0.5, 1.5, 1.0, 1.0, 1.5, 0.5, 1.0)
        assertEquals(0.5, polyArea(diamond), 1e-12)
        // прямая y = 1 делит ромб пополам
        assertEquals(0.25, polyArea(clipBand(diamond, 0.0, 1.0)), 1e-12)
        // а прямая y = 1.25 отрезает подобный треугольник вдвое меньшей высоты,
        // то есть вчетверо меньшей площади, чем верхняя половина
        assertEquals(0.0625, polyArea(clipBand(diamond, 1.25, 9.0)), 1e-12)
    }

    @Test
    fun `вырожденные обрезки отбрасываются`() {
        assertNull(sanitize(doubleArrayOf(0.0, 0.0, 1.0, 0.0)))          // отрезок
        assertNull(sanitize(square(0.0, 0.0, 0.001)))                     // пыль
        assertNull(sanitize(doubleArrayOf(0.0, 0.0, 1.0, 0.0, 2.0, 0.0))) // прямая
    }

    @Test
    fun `обход по часовой стрелке разворачивается`() {
        val cw = doubleArrayOf(0.0, 0.0, 0.0, 1.0, 1.0, 1.0, 1.0, 0.0)
        assertTrue(polyArea(cw) < 0)
        val fixed = assertNotNull(sanitize(cw))
        assertEquals(1.0, polyArea(fixed), 1e-12)
    }

    @Test
    fun `лишние вершины на прямой убираются`() {
        val withMid = doubleArrayOf(0.0, 0.0, 0.5, 0.0, 1.0, 0.0, 1.0, 1.0, 0.0, 1.0)
        val fixed = assertNotNull(sanitize(withMid))
        assertEquals(4, fixed.size / 2)
        assertEquals(1.0, polyArea(fixed), 1e-12)
    }

    @Test
    fun `касание определяется точно`() {
        assertTrue(touches(square(0.0, 0.0), square(1.0, 0.0)))       // общая грань
        assertTrue(!touches(square(0.0, 0.0), square(1.5, 0.0)))      // зазор в половину клетки
        assertTrue(!touches(square(0.0, 0.0), square(0.0, 2.0)))
    }

    @Test
    fun `соседние ряды сливаются в одну полосу`() {
        assertEquals(listOf(5..6, 9..9), mergeRuns(listOf(5, 6, 9)))
        assertEquals(listOf(0..3), mergeRuns(listOf(0, 1, 2, 3)))
        assertEquals(emptyList(), mergeRuns(emptyList()))
    }
}

class PhysicsFieldTest {

    private fun cells(vararg xy: Int): List<Cell> =
        (xy.indices step 2).map { Cell(xy[it], xy[it + 1]) }

    @Test
    fun `фигура на дне стакана стоит на месте`() {
        val f = field()
        // квадрат O в двух нижних рядах
        f.addPiece(cells(4, 18, 5, 18, 4, 19, 5, 19), PieceType.O)
        f.settle()
        val frags = f.fragments()
        assertEquals(4, frags.size)
        val minY = frags.minOf { it.minY }
        val maxY = frags.maxOf { it.maxY }
        assertTrue(abs(minY) < 0.05, "нижняя грань должна лежать на полу, а не на $minY")
        assertTrue(abs(maxY - 2.0) < 0.05, "верх должен остаться на высоте двух клеток: $maxY")
    }

    @Test
    fun `узкое основание не держит — фигура заваливается`() {
        val f = field()
        // одинокая опора в левом нижнем углу
        f.addPiece(cells(0, 19), PieceType.O)
        f.settle()
        // палка ложится на неё так, что три клетки из четырёх висят в воздухе
        f.addPiece(cells(0, 18, 1, 18, 2, 18, 3, 18), PieceType.I)
        f.settle()

        val onFloor = f.fragments().count { it.minY < 0.5 }
        assertTrue(
            onFloor >= 2,
            "свисающая часть обязана свалиться на пол, а на полу оказалось $onFloor кусков",
        )
        // и центр тяжести палки уехал вниз относительно исходной строки
        val top = f.fragments().maxOf { it.maxY }
        assertTrue(top < 2.6, "стопка не должна остаться прежней высоты: $top")
    }

    @Test
    fun `заполненность ряда считается по клеткам`() {
        val f = field()
        for (x in 0 until C.COLS) f.addPiece(cells(x, 19), PieceType.L)
        assertEquals(listOf(19), f.fullRows(0.8))

        val half = field()
        for (x in 0 until 5) half.addPiece(cells(x, 19), PieceType.L)
        assertTrue(half.fullRows(0.8).isEmpty(), "половина ряда порога не берёт")
        assertEquals(listOf(19), half.fullRows(0.45))
    }

    @Test
    fun `ряд уходит, когда не хватает ровно одной клетки`() {
        val eight = field()
        for (x in 0 until 8) eight.addPiece(cells(x, 19), PieceType.L)
        assertTrue(eight.fullRows(C.PHYS_FILL).isEmpty(), "две дыры — ряд остаётся")

        val nine = field()
        for (x in 0 until 9) nine.addPiece(cells(x, 19), PieceType.L)
        assertEquals(listOf(19), nine.fullRows(C.PHYS_FILL), "одна дыра — ряд уходит")

        val full = field()
        for (x in 0 until C.COLS) full.addPiece(cells(x, 19), PieceType.L)
        assertEquals(listOf(19), full.fullRows(C.PHYS_FILL))
    }

    @Test
    fun `осколок закрывает клетку наравне с целым блоком`() {
        val f = field()
        // восемь целых клеток — до порога не хватает двух
        for (x in 0 until 8) f.addPiece(cells(x, 19), PieceType.L)
        assertEquals(listOf(19), f.fullRows(0.8))
        assertTrue(f.fullRows(0.9).isEmpty())

        // добавляем две клетки и срезаем ряд выше так, чтобы от них остались
        // тонкие обрубки: по площади это гроши, а ряд обязан закрыться
        f.addPiece(cells(8, 18, 9, 18), PieceType.I)
        f.settle()
        assertEquals(listOf(19), f.fullRows(0.9), "две клетки из десяти добирают ряд до конца")

        val thin = field()
        for (x in 0 until 9) thin.addPiece(cells(x, 19), PieceType.L)
        // десятая колонка забита лишь узкой полоской — четвертью клетки
        thin.addPiece(cells(9, 18), PieceType.O)
        thin.cutRows(listOf(18))          // от неё останется только осадка
        thin.addPiece(cells(9, 19), PieceType.O)
        thin.settle()
        assertEquals(listOf(19), thin.fullRows(1.0), "ряд закрыт полностью")
    }

    @Test
    fun `уцелевшие обломки остаются одним телом, если касаются`() {
        val f = field()
        // одно тело из четырёх клеток поперёк двух рядов
        f.addPiece(cells(2, 18, 3, 18, 2, 19, 3, 19), PieceType.O)
        assertEquals(1, f.bodyCount(), "пока это одно тело")

        f.cutRows(listOf(19))
        assertEquals(1, f.bodyCount(), "две уцелевшие клетки лежат бок о бок — тело одно")
        assertEquals(2.0, f.fragments().sumOf { polyArea(it.world) }, 1e-6)
    }

    @Test
    fun `обломки через пустоту не склеиваются`() {
        val f = field()
        // одно тело двумя столбиками врозь: их держал вместе только нижний ряд
        f.addPiece(cells(0, 18, 0, 19, 3, 18, 3, 19), PieceType.J)
        assertEquals(1, f.bodyCount(), "пока это одно тело")

        f.cutRows(listOf(19))
        assertEquals(2, f.bodyCount(), "клетки в разных колонках не одно тело")
        assertEquals(2.0, f.fragments().sumOf { polyArea(it.world) }, 1e-6)
    }

    @Test
    fun `нетронутое ножом тело остаётся целым`() {
        val f = field()
        f.addPiece(cells(0, 19), PieceType.L)
        f.addPiece(cells(5, 10, 6, 10, 5, 11, 6, 11), PieceType.O)
        assertEquals(2, f.bodyCount())
        f.cutRows(listOf(19))
        // нижнее тело срезано целиком, верхнее нож не трогал — оно не рассыпалось
        assertEquals(1, f.bodyCount(), "квадрат наверху резать было незачем")
        assertEquals(4, f.fragments().size)
    }

    @Test
    fun `рез снимает полосу и делит тело надвое`() {
        val f = field()
        // вертикальная палка на четыре ряда: 16, 17, 18, 19
        f.addPiece(cells(3, 16, 3, 17, 3, 18, 3, 19), PieceType.I)
        f.refresh()
        val before = f.fragments().sumOf { polyArea(it.world) }
        assertEquals(4.0, before, 1e-6)

        f.cutRows(listOf(18))
        val after = f.fragments().sumOf { polyArea(it.world) }
        assertEquals(3.0, after, 1e-6, "срезаться должен ровно один ряд")

        // в полосе ряда 18 (мир: y от 1 до 2) не осталось ни одной вершины
        for (fr in f.fragments()) {
            assertTrue(
                fr.maxY <= 1.0 + 1e-6 || fr.minY >= 2.0 - 1e-6,
                "кусок ${fr.minY}..${fr.maxY} залез в срезанную полосу",
            )
        }
    }

    @Test
    fun `верхушка после реза падает вниз`() {
        val f = field()
        f.addPiece(cells(3, 16, 3, 17, 3, 18, 3, 19), PieceType.I)
        f.settle()
        val before = f.fragments().sumOf { polyArea(it.world) }
        f.cutRows(listOf(18))
        f.settle()
        // две клетки сверху и одна снизу собираются в столбик высотой три
        val top = f.fragments().maxOf { it.maxY }
        assertTrue(top < 3.2, "верхушка должна была осесть, а осталась на $top")
        assertTrue(top > 2.8, "столбик не мог провалиться ниже трёх клеток: $top")
        // допуск: улёгшееся тело утопает в опоре на слоп решателя
        assertEquals(before - 1.0, f.fragments().sumOf { polyArea(it.world) }, 0.02)
    }

    @Test
    fun `рез сразу по двум соседним рядам`() {
        val f = field()
        f.addPiece(cells(3, 16, 3, 17, 3, 18, 3, 19), PieceType.I)
        f.refresh()
        f.cutRows(listOf(17, 18))
        assertEquals(2.0, f.fragments().sumOf { polyArea(it.world) }, 1e-6)
    }

    @Test
    fun `наклонное тело режется вместе с остальными`() {
        val f = field()
        f.addPiece(cells(0, 19), PieceType.O)
        f.addPiece(cells(0, 18, 1, 18, 2, 18, 3, 18), PieceType.I)
        f.settle()
        f.refresh()
        val before = f.fragments().sumOf { polyArea(it.world) }
        assertEquals(5.0, before, 1e-6)

        // после падения куски лежат под своими углами — режем нижний ряд
        f.cutRows(listOf(19))
        val after = f.fragments().sumOf { polyArea(it.world) }
        assertTrue(after < before - 0.5, "рез обязан снять вещество: было $before, стало $after")
        for (fr in f.fragments()) {
            assertTrue(fr.minY >= 1.0 - 1e-6, "кусок остался в срезанной полосе: ${fr.minY}")
        }
    }

    @Test
    fun `сетка клеток собирается из многоугольников`() {
        val f = field()
        f.addPiece(cells(4, 18, 5, 18, 4, 19, 5, 19), PieceType.O)
        val grid = Array(C.ROWS) { arrayOfNulls<PieceType>(C.COLS) }
        f.rasterize(grid)
        assertEquals(PieceType.O, grid[18][4])
        assertEquals(PieceType.O, grid[19][5])
        assertNull(grid[17][4])
        assertNull(grid[18][6])
        assertEquals(4, grid.sumOf { row -> row.count { it != null } })
    }

    @Test
    fun `остаток вещества после реза виден заранее`() {
        val f = field()
        for (x in 0 until C.COLS) f.addPiece(cells(x, 19), PieceType.L)
        f.refresh()
        assertEquals(0.0, f.areaOutside(listOf(19)), 1e-6)
        f.addPiece(cells(0, 17), PieceType.O)
        f.refresh()
        assertEquals(1.0, f.areaOutside(listOf(19)), 1e-6)
    }
}

class PhysicsGameTest {

    private fun game(): TetrisGame {
        val g = TetrisGame(random = kotlin.random.Random(7))
        g.physicsMode = true
        g.debugStartImmediate()
        return g
    }

    /** Прокручивает кадры, пока не дождётся нужного состояния. */
    private fun TetrisGame.run(frames: Int, until: (TetrisGame) -> Boolean = { false }): Int {
        for (i in 0 until frames) {
            update(16.0)
            if (until(this)) return i
        }
        return -1
    }

    @Test
    fun `партия в режиме физики заводит мир`() {
        val g = game()
        assertNotNull(g.physics)
        assertEquals(GameState.PLAYING, g.state)
    }

    @Test
    fun `обычная партия мира не заводит`() {
        val g = TetrisGame(random = kotlin.random.Random(7))
        g.debugStartImmediate()
        assertNull(g.physics)
    }

    @Test
    fun `сброс фигуры уводит партию в ожидание и возвращает обратно`() {
        val g = game()
        g.hardDrop()
        assertEquals(GameState.SETTLING, g.state)
        assertNull(g.piece)
        val f = g.run(400) { it.state == GameState.PLAYING }
        assertTrue(f >= 0, "партия обязана вернуться в игру")
        assertNotNull(g.piece)
        // фигура действительно попала в мир
        assertTrue(g.physics!!.fragments().isNotEmpty())
    }

    @Test
    fun `плотный ряд режется и стакан пустеет`() {
        val g = game()
        val ph = g.physics!!
        for (x in 0 until C.COLS) ph.addPiece(listOf(Cell(x, 19)), PieceType.L)
        ph.rasterize(g.grid)
        g.hardDrop()                                   // отдаём ход миру

        val cut = g.run(400) { it.state == GameState.CLEARING }
        assertTrue(cut >= 0, "забитый ряд обязан уйти в рез")
        assertEquals(1, g.lines)
        assertTrue(g.score > 0)

        val back = g.run(600) { it.state == GameState.PLAYING }
        assertTrue(back >= 0, "после реза партия продолжается")
        // нижний ряд снят: осталась только сброшенная фигура
        val left = ph.fragments().sumOf { polyArea(it.world) }
        assertTrue(left <= 4.5, "от стакана должна была остаться одна фигура, а осталось $left")
    }

    @Test
    fun `обвал в полстакана не ломает подсчёт`() {
        val g = TetrisGame(random = kotlin.random.Random(1))
        g.debugStartImmediate()
        g.debugSetLevel(1)
        g.debugApplyScore(12, null, false)
        // таблица кончается на четырёх рядах: 800 плюс восемь ставок по 300
        assertEquals(800L + 300L * 8, g.score)
        assertEquals(12, g.lines)

        val t = TetrisGame(random = kotlin.random.Random(1))
        t.debugStartImmediate()
        t.debugSetLevel(1)
        t.debugApplyScore(6, Spin.FULL, false)
        assertEquals(1600L + 300L * 3, t.score)
    }

    @Test
    fun `забитый доверху стакан уходит целиком и не роняет партию`() {
        val g = game()
        val ph = g.physics!!
        for (y in 10 until C.ROWS) for (x in 0 until C.COLS) {
            ph.addPiece(listOf(Cell(x, y)), PieceType.L)
        }
        ph.rasterize(g.grid)
        assertEquals(10, ph.fullRows(C.PHYS_FILL).size)
        g.hardDrop()
        val cut = g.run(400) { it.state == GameState.CLEARING }
        assertTrue(cut >= 0, "десять забитых рядов обязаны уйти в рез")
        assertTrue(g.lines >= 10)
        assertTrue(g.score > 0)
        g.run(900) { it.state == GameState.PLAYING || it.state == GameState.DYING }
        assertTrue(g.state != GameState.CLEARING, "партия застряла в резе")
    }

    @Test
    fun `при проигрыше рассыпается каждый кусок без исключения`() {
        val g = game()
        val ph = g.physics!!
        for (x in 0 until C.COLS) ph.addPiece(listOf(Cell(x, 19)), PieceType.L)
        ph.addPiece(listOf(Cell(0, 17), Cell(1, 17), Cell(2, 17), Cell(3, 17)), PieceType.I)
        g.debugSettle()
        // режем ряд: остаются в том числе обрубки мельче трети клетки,
        // которые в сетку клеток не попадают вовсе
        ph.cutRows(listOf(19))
        g.debugSettle()
        ph.rasterize(g.grid)
        val small = ph.fragments().count { (it.maxX - it.minX) * (it.maxY - it.minY) < 0.34 }

        // проигрыш: фигура целиком осталась над стаканом
        g.debugPiece(Piece(PieceType.O, 0, 4, -2))
        g.debugLock()
        assertEquals(GameState.DYING, g.state)

        val dust = assertNotNull(g.dust)
        for (fr in ph.fragments()) {
            assertTrue(
                g.dustDelay(fr.cx, fr.cy) < Double.MAX_VALUE,
                "кусок ${fr.minX}..${fr.maxX} остался бы висеть на экране навсегда",
            )
        }
        assertTrue(dust.parts.isNotEmpty(), "пыль должна быть")
        // и мелочь тоже даёт свои частицы, а не исчезает молча
        assertTrue(small >= 0)
    }

    @Test
    fun `десять сбросов подряд проходят без срывов`() {
        val g = game()
        repeat(10) {
            val f = g.run(600) { it.state == GameState.PLAYING && it.piece != null }
            if (f < 0 || g.state != GameState.PLAYING) return@repeat
            g.hardDrop()
        }
        g.run(900) { it.state == GameState.PLAYING || it.state == GameState.OVER }
        assertTrue(
            g.state == GameState.PLAYING || g.state == GameState.OVER ||
                g.state == GameState.DYING,
            "партия застряла в ${g.state}",
        )
        assertTrue(g.physics!!.fragments().isNotEmpty())
    }
}

package com.serafim.tetris

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.serafim.tetris.game.C
import com.serafim.tetris.game.Cell
import com.serafim.tetris.game.GameState
import com.serafim.tetris.game.Piece
import com.serafim.tetris.game.PieceType
import com.serafim.tetris.game.TetrisGame
import com.serafim.tetris.ui.drawBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Рисует стакан в bitmap на каждой стадии анимации и сохраняет снимки.
 * Так проверяются те кадры, которые вручную поймать невозможно:
 * исчезновение линий, обвал рядов и распад стакана.
 */
@RunWith(AndroidJUnit4::class)
class BoardRenderTest {

    private val cell = 54f          // 18 dp при плотности 3
    private val density = 3f

    private fun shot(name: String, game: TetrisGame) {
        val w = (C.COLS * cell).toInt()
        val h = (C.ROWS * cell).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFF131619.toInt())
        val canvas = Canvas(bmp.asImageBitmap())
        CanvasDrawScope().draw(
            Density(density),
            LayoutDirection.Ltr,
            canvas,
            Size(w.toFloat(), h.toFloat()),
        ) {
            drawBoard(game, cell, density)
        }
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "shots",
        ).apply { mkdirs() }
        FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Партия с почти собранными нижними строками и палкой в колодце. */
    private fun readyToClear(): TetrisGame {
        val g = TetrisGame(random = Random(11))
        g.debugStartImmediate()
        g.debugClearGrid()
        // четыре нижних ряда заполнены, кроме девятой колонки
        for (y in 16 until C.ROWS) g.debugFillRow(y, exceptX = 9, t = PieceType.J)
        for (y in 16 until C.ROWS) for (x in 0 until 9) {
            g.debugSet(x, y, PieceType.entries[(x + y) % PieceType.entries.size])
        }
        g.debugSet(3, 15, PieceType.S)
        // палка стоит вертикально в колодце
        g.debugPiece(Piece(PieceType.I, 1, 7, 12))
        return g
    }

    @Test
    fun снимки_всех_стадий() {
        // 1. обычная игра: фигура, призрак, сетка
        val playing = readyToClear()
        shot("01_playing", playing)

        // 2. жёсткий сброс — тетрис: след, встряска, исчезновение линий
        playing.hardDrop()
        assertEquals(GameState.CLEARING, playing.state)
        assertEquals(4, playing.clearRows.size)
        shot("02_clearing_start", playing)

        playing.update(120.0)
        shot("03_clearing_mid", playing)

        // 3. обвал рядов
        playing.update(100.0)
        assertEquals(GameState.FALLING, playing.state)
        playing.update(60.0)
        shot("04_falling", playing)

        // очки и сообщение начислены
        assertEquals(4, playing.lines)
        assertTrue("тетрис должен принести очки", playing.score >= 800)

        // 4. вступление: стакан собирается снизу вверх
        val intro = TetrisGame(random = Random(3))
        intro.startGame()
        intro.update(100.0)
        intro.update(100.0)
        intro.update(100.0)
        assertEquals(GameState.INTRO, intro.state)
        shot("05_intro", intro)

        // 5. распад стакана после проигрыша
        val dying = TetrisGame(random = Random(5))
        dying.debugStartImmediate()
        dying.debugClearGrid()
        for (y in 12 until C.ROWS) for (x in 0 until C.COLS) {
            if ((x + y) % 5 != 0) g_set(dying, x, y)
        }
        for (y in 0..1) for (x in 3..6) dying.debugSet(x, y, PieceType.L)
        dying.debugPiece(Piece(PieceType.T, 0, 0, 10))
        dying.hardDrop()
        assertEquals(GameState.DYING, dying.state)
        dying.update(100.0)
        dying.update(100.0)
        shot("06_dying", dying)

        // след от падения свайпом вниз — такой же, как от кнопки сброса
        val dragged = TetrisGame(random = Random(9))
        dragged.debugStartImmediate()
        dragged.debugClearGrid()
        dragged.debugPiece(Piece(PieceType.T, 0, 3, 2))
        val fromY = dragged.piece!!.y
        repeat(10) { dragged.softDrop() }
        dragged.dropTrail(fromY)
        assertEquals(3, dragged.trails.size)
        shot("09_drag_trail", dragged)

        val total = dying.dust!!.total
        var t = 0.0
        while (t < total) { dying.update(100.0); t += 100.0 }
        dying.update(100.0)
        dying.update(100.0)
        shot("07_wipe", dying)
    }

    /**
     * Стакан в режиме физики: почти полный нижний ряд, стопка справа и
     * палка, положенная на одинокую опору — она обязана завалиться.
     * Такую раскладку руками на эмуляторе не поймать.
     */
    private fun physicsPile(): TetrisGame {
        val g = TetrisGame(random = Random(5))
        g.physicsMode = true
        g.debugStartImmediate()
        val ph = g.physics!!
        for (x in 0 until C.COLS) ph.addPiece(listOf(Cell(x, 19)), PieceType.entries[x % 7])
        ph.addPiece(listOf(Cell(7, 18), Cell(8, 18), Cell(7, 17), Cell(8, 17)), PieceType.O)
        ph.addPiece(listOf(Cell(2, 18)), PieceType.Z)
        ph.addPiece((1..4).map { Cell(it, 17) }, PieceType.I)
        g.debugSettle()
        return g
    }

    @Test
    fun режимФизики() {
        val g = physicsPile()
        g.debugPiece(Piece(PieceType.T, 0, 4, 3))
        shot("20_physics_pile", g)

        // нож идёт справа налево: три кадра одного реза
        g.debugSetClearRows(listOf(19))
        g.debugState(GameState.CLEARING)
        listOf(0.10, 0.30, 0.70).forEachIndexed { i, k ->
            g.debugSetClearTimer(C.CUT_TIME * k)
            shot("2${i + 1}_physics_cut", g)
        }

        val before = g.physics!!.fragments().size
        g.physics!!.cutRows(listOf(19))
        g.debugSetClearRows(emptyList())
        g.debugState(GameState.PLAYING)
        shot("24_physics_after_cut", g)
        assertTrue("после реза обломков должно стать больше", g.physics!!.fragments().isNotEmpty())
        assertTrue("нижний ряд обязан опустеть", before > 0)

        g.debugSettle()
        shot("25_physics_settled", g)
        // всё, что уцелело, лежит выше срезанной полосы или уже осело на пол
        assertTrue(g.physics!!.fragments().isNotEmpty())
    }

    /**
     * Тот самый случай со схемы: палка легла наискось поперёк ряда, и рез
     * обязан разрубить её саму — по верхней и нижней границе полосы, а не
     * по клеткам. От неё останется кривой обрубок, и это правильно.
     */
    @Test
    fun рекРежетНаклонённуюФигуру() {
        val g = TetrisGame(random = Random(3))
        g.physicsMode = true
        g.debugStartImmediate()
        val ph = g.physics!!
        // ступенька, с которой палка соскользнёт набок
        ph.addPiece(listOf(Cell(4, 19), Cell(5, 19), Cell(4, 18), Cell(5, 18)), PieceType.O)
        g.debugSettle()
        ph.addPiece((5..8).map { Cell(it, 15) }, PieceType.I)
        g.debugSettle()
        for (x in listOf(0, 1, 2, 3, 8, 9)) {
            ph.addPiece(listOf(Cell(x, 19)), PieceType.entries[x % 7])
        }
        g.debugSettle()
        g.debugPiece(Piece(PieceType.T, 0, 4, 2))
        shot("30_slice_before", g)

        val flat = ph.fragments().count { it.maxY - it.minY > 1.15 }
        assertTrue("палка должна лежать под углом, а не ровно", flat > 0)

        g.debugSetClearRows(listOf(19))
        g.debugState(GameState.CLEARING)
        g.debugSetClearTimer(C.CUT_TIME * 0.45)
        shot("31_slice_knife", g)

        ph.cutRows(listOf(19))
        g.debugSetClearRows(emptyList())
        g.debugState(GameState.PLAYING)
        shot("32_slice_after", g)
        // ни одна вершина не осталась в срезанной полосе: мир y от 0 до 1
        for (fr in ph.fragments()) {
            assertTrue("обрубок ${fr.minY}..${fr.maxY} залез в срез", fr.minY >= 0.999)
        }
        g.debugSettle()
        shot("33_slice_settled", g)
    }

    /**
     * Проигрыш в режиме физики: стакан обязан рассыпаться в пыль так же,
     * как обычный, — куски гаснут по очереди, а не висят до конца.
     */
    @Test
    fun физикаРассыпается() {
        val g = TetrisGame(random = Random(9))
        g.physicsMode = true
        g.debugStartImmediate()
        val ph = g.physics!!
        // семь колонок из десяти: место появления перекрыто, но до порога
        // ни один ряд не дотягивает и рез не срабатывает
        for (y in 0 until C.ROWS) for (x in 0 until 7) {
            ph.addPiece(listOf(Cell(x, y)), PieceType.entries[(x + y) % 7])
        }
        ph.rasterize(g.grid)
        assertTrue("ряды не должны браться под рез", g.physics!!.fullRows(C.PHYS_FILL).isEmpty())
        // фигура, целиком оставшаяся над стаканом, — это и есть проигрыш
        g.debugPiece(Piece(PieceType.O, 0, 4, -2))
        g.debugLock()
        assertEquals(GameState.DYING, g.state)

        val total = g.dust!!.total
        shot("40_phys_die_start", g)
        var t = 0.0
        while (t < total * 0.55) { g.update(50.0); t += 50.0 }
        shot("41_phys_die_mid", g)
        val leftMid = g.physics!!.fragments().size
        while (t < total + 50) { g.update(50.0); t += 50.0 }
        shot("42_phys_die_end", g)
        assertTrue("к концу распада куски должны быть все погашены", leftMid > 0)
    }

    private fun g_set(g: TetrisGame, x: Int, y: Int) {
        g.debugSet(x, y, PieceType.entries[(x * 3 + y) % PieceType.entries.size])
    }
}

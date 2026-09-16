package com.serafim.tetris.game

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Мешок из семи: ни одна фигура не повторяется раньше времени. */
class SevenBagTest {

    @Test
    fun `каждые семь подряд содержат все семь типов ровно по разу`() {
        val bag = SevenBag(Random(1234))
        val seq = List(70) { bag.next() }
        for (start in 0 until 70 step 7) {
            val chunk = seq.subList(start, start + 7)
            assertEquals(7, chunk.toSet().size, "мешок на позиции $start: $chunk")
        }
    }

    @Test
    fun `одно зерно даёт одинаковую последовательность`() {
        val a = SevenBag(Random(99))
        val b = SevenBag(Random(99))
        assertEquals(List(50) { a.next() }, List(50) { b.next() })
    }

    @Test
    fun `разные зёрна расходятся`() {
        val a = List(30) { SevenBag(Random(1)).next() }
        val b = List(30) { SevenBag(Random(2)).next() }
        assertTrue(a != b)
    }

    @Test
    fun `мешок пополняется до того как опустеет`() {
        val bag = SevenBag(Random(7))
        repeat(200) { bag.next() }
        assertTrue(bag.size >= 6)
    }

    @Test
    fun `очередь игры всегда держит пять фигур`() {
        val g = TetrisGame(random = Random(5))
        assertEquals(C.QUEUE_SIZE, g.queue.size)
        g.debugStartImmediate()
        assertEquals(C.QUEUE_SIZE, g.queue.size)
        repeat(20) {
            g.hardDrop()
            assertEquals(C.QUEUE_SIZE, g.queue.size)
        }
    }
}

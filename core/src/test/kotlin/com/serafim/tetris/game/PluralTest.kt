package com.serafim.tetris.game

import kotlin.test.Test
import kotlin.test.assertEquals

class PluralTest {

    private fun p(n: Long) = pluralRu(n, "очко", "очка", "очков")

    @Test
    fun `единица и всё, что на неё кончается`() {
        assertEquals("очко", p(1))
        assertEquals("очко", p(21))
        assertEquals("очко", p(101))
        assertEquals("очко", p(6_718_081))
    }

    @Test
    fun `два три четыре`() {
        assertEquals("очка", p(2))
        assertEquals("очка", p(23))
        assertEquals("очка", p(82_302))
        assertEquals("очка", p(1_004))
    }

    @Test
    fun `остальные и подвох с одиннадцатью`() {
        assertEquals("очков", p(0))
        assertEquals("очков", p(5))
        for (n in 11L..14L) assertEquals("очков", p(n))
        for (n in 111L..114L) assertEquals("очков", p(n))
        assertEquals("очков", p(20))
        assertEquals("очков", p(-11))
        assertEquals("очко", p(-21))
    }
}

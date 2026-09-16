package com.serafim.tetris.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NickTest {

    @Test
    fun `пробелы срезаются и сливаются`() {
        assertEquals("Серафим", Nick.clean("  Серафим  "))
        assertEquals("fast drop", Nick.clean("fast    drop"))
        assertEquals("a b", Nick.clean("a\t\nb"))
    }

    @Test
    fun `остаются буквы, цифры и три знака`() {
        assertEquals("x_1.2-y", Nick.clean("x_1.2-y"))
        assertEquals("Tetris", Nick.clean("<Tet'ris>"))
        assertEquals("ЁжиК99", Nick.clean("Ёжи!К99?"))
    }

    @Test
    fun `длина не больше шестнадцати`() {
        assertEquals(Nick.MAX, Nick.clean("a".repeat(40)).length)
        // пробел на границе не остаётся висеть в конце
        assertEquals("abcdefghijklmno", Nick.clean("abcdefghijklmno            p"))
        assertTrue(Nick.isValid(Nick.clean("abcdefghijklmno            p")))
        assertTrue(Nick.clean("abcdefghijklmnop qrs").length <= Nick.MAX)
    }

    @Test
    fun `высота букв на занятость не влияет`() {
        assertEquals("fastdropx", Nick.key("FastdropX"))
        assertEquals("fastdropx", Nick.key("  FASTDROPX "))
        assertTrue(Nick.same("FastdropX", "fastdropx"))
        assertTrue(Nick.same("Кот Василий", "кот   ВАСИЛИЙ"))
        // разные ники остаются разными, пустой не совпадает ни с чем
        assertFalse(Nick.same("fastdrop", "fastdropx"))
        assertFalse(Nick.same("", ""))
        assertFalse(Nick.same("!!!", "???"))
    }

    @Test
    fun `пустой и мусорный ник не проходит`() {
        assertFalse(Nick.isValid(""))
        assertEquals("", Nick.clean("   !!!  "))
        assertTrue(Nick.isValid("Игрок 1"))
        assertFalse(Nick.isValid(" Игрок"))
    }
}

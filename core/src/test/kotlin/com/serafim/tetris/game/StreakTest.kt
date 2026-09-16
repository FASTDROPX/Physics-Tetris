package com.serafim.tetris.game

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Серия дней подряд. Дни — просто числа, так что «вчера», «сегодня» и
 * «через неделю» здесь подставляются руками.
 */
class StreakTest {

    private val min = 60_000L
    private val day0 = 20_000L

    @Test
    fun `минута игры зажигает огонёк, но только один раз за день`() {
        val s = Streak()
        assertEquals(StreakState.NONE, s.state(day0))
        assertFalse(s.addPlay(day0, 40_000), "сорока секунд мало")
        assertTrue(s.addPlay(day0, 25_000), "вместе больше минуты — день засчитан")
        assertFalse(s.addPlay(day0, 5 * min), "второй раз за день не засчитывается")
        assertEquals(1, s.current)
        assertEquals(StreakState.LIT, s.state(day0))
        assertEquals(1, s.totalDays)
    }

    @Test
    fun `дни подряд складываются в серию`() {
        val s = Streak()
        for (d in 0 until 5) s.addPlay(day0 + d, min)
        assertEquals(5, s.current)
        assertEquals(5, s.best)
        assertEquals(5, s.shown(day0 + 4))
    }

    @Test
    fun `вчерашняя серия жива до конца сегодняшнего дня`() {
        val s = Streak()
        s.addPlay(day0, min)
        s.addPlay(day0 + 1, min)
        assertEquals(StreakState.AT_RISK, s.state(day0 + 2))
        assertEquals(2, s.shown(day0 + 2), "пока день не кончился, серия показывается")
        s.addPlay(day0 + 2, min)
        assertEquals(3, s.current)
    }

    @Test
    fun `пропущенный день прерывает серию, а рекорд остаётся`() {
        val s = Streak()
        for (d in 0 until 4) s.addPlay(day0 + d, min)
        assertEquals(StreakState.NONE, s.state(day0 + 5), "пропущен целый день")
        assertEquals(0, s.shown(day0 + 5))
        s.addPlay(day0 + 5, min)
        assertEquals(1, s.current, "начинается заново")
        assertEquals(4, s.best, "рекорд не теряется")
        assertEquals(5, s.totalDays, "дней в игре — все, а не подряд")
    }

    @Test
    fun `часы, переведённые назад, серию не ломают`() {
        val s = Streak()
        s.addPlay(day0 + 3, min)
        assertFalse(s.addPlay(day0 + 1, min), "день в прошлом не засчитывается")
        assertEquals(1, s.current)
        assertEquals(StreakState.LIT, s.state(day0 + 1))
    }

    @Test
    fun `история по дням и самый долгий день`() {
        val s = Streak()
        s.addPlay(day0, 3 * min)
        s.addPlay(day0 + 2, 15 * min)
        assertContentEquals(longArrayOf(3 * min, 0, 15 * min), s.recent(day0 + 2, 3))
        assertEquals(15 * min, s.longestDay())
    }

    @Test
    fun `старая история забывается`() {
        val s = Streak()
        s.addPlay(day0, min)
        s.addPlay(day0 + STREAK_HISTORY_DAYS + 5, min)
        assertEquals(0L, s.playedOn(day0))
    }

    @Test
    fun `серия переживает запись и чтение`() {
        val a = Streak()
        for (d in 0 until 3) a.addPlay(day0 + d, (d + 1) * min)
        val b = Streak()
        assertTrue(b.decode(a.encode()))
        assertEquals(a.current, b.current)
        assertEquals(a.best, b.best)
        assertEquals(a.lastDay, b.lastDay)
        assertEquals(a.totalDays, b.totalDays)
        assertContentEquals(a.recent(day0 + 2, 7), b.recent(day0 + 2, 7))
        // пустая история тоже читается
        assertTrue(Streak().decode(Streak().encode()))
    }

    @Test
    fun `битая строка ничего не портит`() {
        val s = Streak()
        s.addPlay(day0, min)
        val before = s.encode()
        assertFalse(s.decode(null))
        assertFalse(s.decode(""))
        assertFalse(s.decode("s1;1;1"))
        assertFalse(s.decode("s0;1;1;1;1;"))
        assertFalse(s.decode("s1;x;1;1;1;"))
        assertFalse(s.decode("s1;1;1;1;1;5:abc"))
        assertEquals(before, s.encode())
    }

    @Test
    fun `сброс гасит всё`() {
        val s = Streak()
        for (d in 0 until 3) s.addPlay(day0 + d, min)
        s.reset()
        assertEquals(0, s.current)
        assertEquals(0, s.best)
        assertEquals(0, s.totalDays)
        assertEquals(StreakState.NONE, s.state(day0 + 2))
        assertEquals(0L, s.longestDay())
    }

    @Test
    fun `огоньки по порогам`() {
        assertEquals(FlameTier.OUT, FlameTier.of(0))
        assertEquals(FlameTier.SPARK, FlameTier.of(1))
        assertEquals(FlameTier.SPARK, FlameTier.of(2))
        assertEquals(FlameTier.EMBER, FlameTier.of(3))
        assertEquals(FlameTier.FLAME, FlameTier.of(7))
        assertEquals(FlameTier.BLUE, FlameTier.of(14))
        assertEquals(FlameTier.STAR, FlameTier.of(30))
        assertEquals(FlameTier.STAR, FlameTier.of(99))
        assertEquals(FlameTier.RAINBOW, FlameTier.of(100))
        assertEquals(FlameTier.RAINBOW, FlameTier.of(5000))
        assertEquals(FlameTier.EMBER, FlameTier.SPARK.next)
        assertEquals(null, FlameTier.RAINBOW.next)
    }
}

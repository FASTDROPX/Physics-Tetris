package com.serafim.tetris

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Отдача на простом телефоне. Логика шлёт толчки по 12–20 мс — в браузере
 * их отрабатывает линейный мотор, а в Redmi 10C грузик на оси за это время
 * не успевает тронуться, и вибрации нет вовсе. Здесь проверяется правка:
 * короткий толчок подтягивается до [AndroidFx.MIN_BUZZ], паузы остаются
 * как были, а сам вызов доходит до мотора и не падает.
 */
@RunWith(AndroidJUnit4::class)
class VibrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun короткий_толчок_подтягивается() {
        // одиночные толчки игры: постановка фигуры, стирание линии
        assertArrayEquals(longArrayOf(0, 30), AndroidFx.timings(longArrayOf(12)))
        assertArrayEquals(longArrayOf(0, 30), AndroidFx.timings(longArrayOf(18)))
        // рисунок уровня: работа подтянута, паузы нетронуты
        assertArrayEquals(longArrayOf(0, 30, 50, 30), AndroidFx.timings(longArrayOf(12, 50, 12)))
        assertArrayEquals(longArrayOf(0, 30, 40, 30), AndroidFx.timings(longArrayOf(18, 40, 18)))
        // длинные толчки остаются собой
        assertArrayEquals(longArrayOf(0, 40, 60, 80), AndroidFx.timings(longArrayOf(40, 60, 80)))
        assertArrayEquals(
            longArrayOf(0, 30, 50, 30, 50, 30),
            AndroidFx.timings(longArrayOf(20, 50, 20, 50, 30)),
        )
        // ноль — это пропуск, его не растягиваем
        assertArrayEquals(longArrayOf(0, 0, 50, 30), AndroidFx.timings(longArrayOf(0, 50, 12)))
    }

    /** Вызов доходит до мотора: ни исключения, ни отказа. */
    @Test
    fun вибрация_не_падает() {
        val fx = AndroidFx(context)
        try {
            fx.vibrationOn = true
            fx.vibrate(12)
            fx.vibrate(18, 40, 18)
            fx.vibrate(40, 60, 80)
            fx.vibrationOn = false
            fx.vibrate(12)
        } finally {
            fx.release()
        }
    }
}

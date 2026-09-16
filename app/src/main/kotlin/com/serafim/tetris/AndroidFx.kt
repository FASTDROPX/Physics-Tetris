package com.serafim.tetris

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.serafim.tetris.game.Fx
import com.serafim.tetris.game.Sfx
import com.serafim.tetris.sound.ToneEngine

/** Чип с названием комбинации: текст плюс счётчик, чтобы перезапускать анимацию. */
data class ChipMessage(val text: String, val key: Int)

/**
 * То, что логика поручает Android: звук, вибрация и четыре эффекта,
 * которые в оригинале жили на DOM-узлах. Каждый из них здесь —
 * счётчик, на который смотрит Compose и заново запускает свою анимацию.
 */
class AndroidFx(context: Context) : Fx {

    val tones = ToneEngine(context)

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val m = context.getSystemService(VibratorManager::class.java)
            m?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    var chip by mutableStateOf<ChipMessage?>(null)
        private set
    var glowTick by mutableIntStateOf(0)
        private set
    var levelTick by mutableIntStateOf(0)
        private set
    var holdTick by mutableIntStateOf(0)
        private set
    var rankTick by mutableIntStateOf(0)
        private set

    private var chipCounter = 0

    var soundOn: Boolean
        get() = tones.on
        set(value) { tones.on = value }

    /** Отдельный переключатель: звук и отдача независимы. */
    @Volatile
    var vibrationOn: Boolean = true

    override fun sound(s: Sfx) = tones.play(s)

    override fun soundClear(lines: Int) = tones.playClear(lines)

    /**
     * Отдача. Логика задаёт рисунок как в браузере — [работа, пауза,
     * работа…] в миллисекундах, — а телефон получает его с двумя правками.
     *
     * Первая: короткий толчок поднимается до [MIN_BUZZ]. В простых
     * телефонах (Redmi 10C и вся его родня) стоит не линейный мотор, а
     * грузик на оси: за 12 мс он не успевает и тронуться, и вся отдача в
     * игре пропадала впустую. Слышимая длительность у такого мотора
     * начинается примерно с тридцати миллисекунд.
     *
     * Вторая: рисунок уходит прямо в мотор, без сборки через менеджер. На
     * MIUI 14 сборка через менеджер молча глохнет, а обычный путь работает.
     * Если и он откажет, остаётся старый вызов массивом.
     */
    override fun vibrate(vararg pattern: Long) {
        if (!vibrationOn) return
        val v = vibrator ?: return
        if (!v.hasVibrator() || pattern.isEmpty()) return
        val timings = timings(pattern)
        val fired = runCatching {
            val effect = if (pattern.size == 1) {
                VibrationEffect.createOneShot(timings[1], VibrationEffect.DEFAULT_AMPLITUDE)
            } else if (v.hasAmplitudeControl()) {
                // на полную силу: слабая отдача на таком моторе не чувствуется
                val amps = IntArray(timings.size) { if (it % 2 == 1) 255 else 0 }
                VibrationEffect.createWaveform(timings, amps, -1)
            } else {
                VibrationEffect.createWaveform(timings, -1)
            }
            v.vibrate(effect)
        }.isSuccess
        @Suppress("DEPRECATION")
        if (!fired) runCatching { v.vibrate(timings, -1) }
    }

    override fun message(text: String) {
        chipCounter++
        chip = ChipMessage(text, chipCounter)
    }

    override fun boardGlow() { glowTick++ }

    override fun levelBump() { levelTick++ }

    override fun holdPop() { holdTick++ }

    override fun xpRankUp() { rankTick++ }

    fun release() = tones.release()

    companion object {
        /** Ниже этого мотор в простом телефоне не успевает раскрутиться. */
        const val MIN_BUZZ = 30L

        /**
         * Рисунок браузера [работа, пауза, работа…] — в рисунок Android
         * [пауза, работа…], с подтягиванием коротких толчков до [min].
         * Паузы остаются как были: иначе рассыпался бы сам рисунок.
         */
        fun timings(pattern: LongArray, min: Long = MIN_BUZZ): LongArray {
            val out = LongArray(pattern.size + 1)
            for (i in pattern.indices) {
                val ms = pattern[i]
                out[i + 1] = if (i % 2 == 0 && ms in 1..<min) min else ms
            }
            return out
        }
    }
}

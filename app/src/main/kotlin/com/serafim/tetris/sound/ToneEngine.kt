package com.serafim.tetris.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.serafim.tetris.game.Sfx
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

private const val SR = 44100

private enum class Wave { SINE, TRIANGLE, SAWTOOTH }

/**
 * Один осциллятор оригинала: частота, длительность, форма, громкость и
 * задержка запуска. Огибающая — те же два экспоненциальных участка,
 * что задаёт WebAudio: подъём за 12 мс и спад до конца.
 */
private class Voice(
    val freq: Double,
    val freqEnd: Double = freq,
    val sweepTime: Double = 0.0,
    val dur: Double,
    val wave: Wave,
    val vol: Double,
    val delay: Double = 0.0,
    val attack: Double = 0.012,
    val tail: Double = 0.02,
)

private fun tone(
    freq: Double,
    dur: Double,
    wave: Wave,
    vol: Double,
    delay: Double = 0.0,
) = Voice(freq = freq, dur = dur, wave = wave, vol = vol, delay = delay)

/** Полный список эффектов из объекта Sound. */
private object Bank {

    private val clearBase = doubleArrayOf(0.0, 440.0, 523.0, 587.0, 659.0)

    fun clear(n: Int): List<Voice> {
        val base = clearBase.getOrNull(n)?.takeIf { it > 0.0 } ?: 440.0
        val out = mutableListOf(
            tone(base, 0.11, Wave.TRIANGLE, 0.045, 0.0),
            tone(base * 1.5, 0.13, Wave.TRIANGLE, 0.04, 0.06),
        )
        if (n == 4) out.add(tone(base * 2, 0.22, Wave.TRIANGLE, 0.04, 0.13))
        return out
    }

    fun of(s: Sfx): List<Voice> = when (s) {
        Sfx.MOVE -> listOf(tone(300.0, 0.03, Wave.SINE, 0.022))
        Sfx.ROTATE -> listOf(tone(420.0, 0.045, Wave.TRIANGLE, 0.03))
        Sfx.LOCK -> listOf(tone(150.0, 0.08, Wave.SINE, 0.045))
        Sfx.HOLD -> listOf(tone(520.0, 0.07, Wave.SINE, 0.035))
        Sfx.DROP -> listOf(tone(90.0, 0.12, Wave.TRIANGLE, 0.05))
        Sfx.LEVEL_UP -> listOf(
            tone(587.0, 0.09, Wave.SINE, 0.045, 0.0),
            tone(880.0, 0.16, Wave.SINE, 0.045, 0.09),
        )
        Sfx.CLICK -> listOf(tone(1100.0, 0.025, Wave.SINE, 0.028))
        Sfx.RANK -> doubleArrayOf(659.0, 880.0, 1175.0, 1568.0)
            .mapIndexed { i, f -> tone(f, 0.16, Wave.SINE, 0.042, i * 0.075) }
        Sfx.REVEAL -> doubleArrayOf(523.0, 659.0, 784.0, 1047.0)
            .mapIndexed { i, f -> tone(f, 0.24, Wave.SINE, 0.03, i * 0.115) }
        Sfx.PERFECT -> buildList {
            doubleArrayOf(523.0, 659.0, 784.0, 1047.0, 1319.0, 1568.0).forEachIndexed { i, f ->
                add(tone(f, 0.30, Wave.TRIANGLE, 0.042, i * 0.065))
                add(tone(f * 2, 0.22, Wave.SINE, 0.02, i * 0.065 + 0.03))
            }
        }
        Sfx.START -> doubleArrayOf(523.0, 659.0, 784.0, 1047.0)
            .mapIndexed { i, f -> tone(f, 0.12, Wave.SINE, 0.04, i * 0.07) }
        Sfx.PAUSE -> listOf(
            tone(392.0, 0.08, Wave.SINE, 0.04, 0.0),
            tone(294.0, 0.12, Wave.SINE, 0.04, 0.07),
        )
        Sfx.RESUME -> listOf(
            tone(294.0, 0.08, Wave.SINE, 0.04, 0.0),
            tone(392.0, 0.12, Wave.SINE, 0.04, 0.07),
        )
        // взмах: пила со скольжением 180 → 1400 Гц и своей огибающей
        Sfx.WHOOSH -> listOf(
            Voice(
                freq = 180.0, freqEnd = 1400.0, sweepTime = 0.45,
                dur = 0.5, wave = Wave.SAWTOOTH, vol = 0.035,
                attack = 0.08, tail = 0.05,
            )
        )
        Sfx.OVER -> doubleArrayOf(523.0, 392.0, 294.0, 196.0)
            .mapIndexed { i, f -> tone(f, 0.24, Wave.TRIANGLE, 0.045, i * 0.15) }
    }
}

private const val FLOOR = 0.0001

/** Треугольник считается напрямую, без пары asin(sin(...)) на каждый отсчёт. */
private fun shape(wave: Wave, phase: Double): Double {
    val p = phase - kotlin.math.floor(phase)          // 0..1
    return when (wave) {
        Wave.SINE -> sin(2 * PI * p)
        Wave.TRIANGLE -> {
            val t = 4.0 * p
            if (t < 1.0) t else if (t < 3.0) 2.0 - t else t - 4.0
        }
        Wave.SAWTOOTH -> 2.0 * p - 1.0
    }
}

/**
 * Огибающая и скольжение частоты у WebAudio экспоненциальные, то есть
 * геометрические прогрессии: 0.0001 → vol за время атаки и обратно к
 * 0.0001 к концу. Считаем их умножением на постоянный шаг, а не Math.pow
 * на каждый отсчёт — синтез всех девятнадцати эффектов перестаёт
 * отъедать кадры у заставки.
 */
private fun render(voices: List<Voice>): FloatArray {
    var total = 0.0
    for (v in voices) total = max(total, v.delay + v.dur + v.tail)
    val n = (total * SR).toInt() + 1
    val buf = FloatArray(n)
    val dt = 1.0 / SR
    for (v in voices) {
        val start = (v.delay * SR).toInt()
        val end = ((v.delay + v.dur + v.tail) * SR).toInt().coerceAtMost(n - 1)
        val attackSamples = (v.attack * SR).toInt().coerceAtLeast(1)
        val decaySamples = ((v.dur - v.attack) * SR).toInt().coerceAtLeast(1)
        val bodySamples = (v.dur * SR).toInt()
        val rAttack = Math.pow(v.vol / FLOOR, 1.0 / attackSamples)
        val rDecay = Math.pow(FLOOR / v.vol, 1.0 / decaySamples)
        val sweepSamples = (v.sweepTime * SR).toInt()
        val rFreq = if (sweepSamples > 0 && v.freqEnd != v.freq) {
            Math.pow(v.freqEnd / v.freq, 1.0 / sweepSamples)
        } else 1.0

        var gain = FLOOR
        var freq = v.freq
        var phase = 0.0
        var i = start
        var k = 0
        while (i <= end) {
            buf[i] += (shape(v.wave, phase) * gain).toFloat()
            phase += freq * dt
            k++
            gain = when {
                k <= attackSamples -> gain * rAttack
                k <= bodySamples -> gain * rDecay
                else -> FLOOR
            }
            if (k < sweepSamples) freq *= rFreq
            i++
        }
    }
    return buf
}

private fun toWav(samples: FloatArray, gain: Float): ByteArray {
    val n = samples.size
    val data = ByteBuffer.allocate(44 + n * 2).order(ByteOrder.LITTLE_ENDIAN)
    data.put("RIFF".toByteArray())
    data.putInt(36 + n * 2)
    data.put("WAVE".toByteArray())
    data.put("fmt ".toByteArray())
    data.putInt(16)
    data.putShort(1)            // PCM
    data.putShort(1)            // моно
    data.putInt(SR)
    data.putInt(SR * 2)
    data.putShort(2)
    data.putShort(16)
    data.put("data".toByteArray())
    data.putInt(n * 2)
    for (s in samples) {
        val v = (s * gain).coerceIn(-1f, 1f)
        data.putShort((v * 32767f).toInt().toShort())
    }
    return data.array()
}

/**
 * Пул коротких сэмплов. Все эффекты синтезируются один раз при запуске,
 * складываются в кэш как WAV и дальше играются через SoundPool —
 * он сам смешивает наложения и не даёт задержки на каждое движение.
 */
class ToneEngine(private val context: Context) {

    @Volatile
    var on: Boolean = true

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(12)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val ids = HashMap<String, Int>()
    private val ready = HashSet<Int>()

    init {
        pool.setOnLoadCompleteListener { _, id, status ->
            if (status == 0) synchronized(ready) { ready.add(id) }
        }
    }

    /** Синтез и загрузка. Вызывается один раз в фоновом потоке. */
    fun prepare() {
        val banks = LinkedHashMap<String, List<Voice>>()
        for (s in Sfx.entries) banks[s.name] = Bank.of(s)
        for (n in 1..4) banks["CLEAR$n"] = Bank.clear(n)

        val rendered = LinkedHashMap<String, FloatArray>()
        var peak = 0f
        for ((k, v) in banks) {
            val buf = render(v)
            for (s in buf) if (abs(s) > peak) peak = abs(s)
            rendered[k] = buf
        }
        // общий множитель: сохраняет соотношение громкостей оригинала
        val gain = if (peak > 0f) 0.9f / peak else 1f

        val dir = File(context.cacheDir, "tones").apply { mkdirs() }
        for ((k, buf) in rendered) {
            val f = File(dir, "$k.wav")
            FileOutputStream(f).use { it.write(toWav(buf, gain)) }
            val id = pool.load(f.absolutePath, 1)
            synchronized(ids) { ids[k] = id }
        }
    }

    private fun playKey(key: String) {
        if (!on) return
        val id = synchronized(ids) { ids[key] } ?: return
        val loaded = synchronized(ready) { ready.contains(id) }
        if (!loaded) return
        pool.play(id, 1f, 1f, 1, 0, 1f)
    }

    fun play(s: Sfx) = playKey(s.name)

    fun playClear(lines: Int) = playKey("CLEAR${lines.coerceIn(1, 4)}")

    fun release() = pool.release()
}

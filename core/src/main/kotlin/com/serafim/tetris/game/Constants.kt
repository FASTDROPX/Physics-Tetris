package com.serafim.tetris.game

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Все числа игры и анимаций — один в один с константами оригинала. */
object C {
    const val COLS = 10
    const val ROWS = 20

    const val LOCK_DELAY = 500.0
    const val MAX_LOCK_RESETS = 15

    const val CLEAR_TIME = 200.0    // исчезновение линий
    const val FALL_TIME = 340.0     // обвал рядов, expo out
    const val TRAIL_TIME = 320.0    // след жёсткого сброса
    const val IMPACT_TIME = 260.0   // отскок при фиксации
    const val SHAKE_TIME = 300.0    // встряска поля
    const val NEXT_TIME = 300.0     // сдвиг очереди
    const val DUST_LIFE = 620.0     // жизнь одной частицы
    const val DUST_FLASH = 170.0    // вспышка в момент поражения
    const val WIPE_TIME = 560.0     // стакан гаснет снизу вверх
    const val INTRO_TIME = 580.0    // стакан собирается при входе в игру
    const val FIRST_STEP = 115.0    // пауза между кубиками первой фигуры
    const val FIRST_CELL = 430.0    // проявление одного кубика
    const val FIRST_TOTAL = FIRST_STEP * 3 + FIRST_CELL

    const val SPARK_LIFE = 520.0    // искры от очищенных линий
    const val SPAWN_TIME = 190.0    // проявление новой фигуры
    const val ROT_TIME = 200.0      // доворот фигуры
    const val DROP_TRAVEL = 130.0   // пролёт блоков при жёстком сбросе
    const val POP_LIFE = 950.0      // всплывающие очки
    const val PC_TIME = 1200.0      // вспышка идеальной очистки
    const val SWEEP_TIME = 620.0    // световая волна на новом уровне

    const val DAS = 150.0
    const val ARR = 35.0

    /** Признак «таймер не идёт» — то же самое 1e9, что и в оригинале. */
    const val OFF = 1e9

    /** Абсолютный потолок скорости. */
    const val MAX_LEVEL = 50

    /** До миллиона очков скорость упирается в двадцатую — как в оригинале. */
    const val BASE_MAX_LEVEL = 20

    /** С этого счёта потолок начинает расти… */
    const val SPEED_BONUS_FROM = 1_000_000L

    /** …на единицу за каждые столько очков сверху. */
    const val SPEED_BONUS_STEP = 100_000L

    /** Очки за постановку фигуры на каждую ступень скорости сверх двадцатой. */
    const val PLACE_BONUS = 10L
    const val QUEUE_SIZE = 5

    // ---------- режим физики ----------
    /**
     * Доля занятых клеток ряда, после которой он срезается. При десяти
     * колонках 0.9 — это ровно «не хватает одной клетки»: девять из десяти
     * закрыты. Порог в 0.8 отпускал ряд с двумя дырами, и стакан почти не
     * успевал набираться.
     */
    const val PHYS_FILL = 0.9
    /** Остаток вещества, который уже считается пустым стаканом. */
    const val PHYS_EMPTY = 0.05
    /** Рез идёт справа налево по той же кривой, что и всё остальное. */
    const val CUT_TIME = 460.0
    /** Сколько ждём, прежде чем поверить, что стакан замер. */
    const val SETTLE_MIN = 70.0
    /**
     * Страховка от обломка, который дрожит бесконечно: дальше партия идёт
     * своим ходом, а ряды продолжают проверяться на каждом изменении мира.
     */
    const val SETTLE_MAX = 900.0
}

/** Экспоненциальное замедление — основа всех анимаций падения. */
fun expoOut(t: Double): Double = if (t >= 1.0) 1.0 else 1.0 - 2.0.pow(-10.0 * t)

fun expoOutF(t: Float): Float = expoOut(t.toDouble()).toFloat()

/**
 * Интервал гравитации в миллисекундах для уровня скорости. До двадцатого —
 * кривая оригинала, пол в 45 мс там ни разу не срабатывает. Выше кривая
 * продолжается тем же законом, ×0.86 на уровень, но уже без пола: иначе
 * новые скорости ничем не отличались бы от двадцатой — цифра росла бы, а
 * фигура падала бы так же. К пятидесятому интервал уходит в доли
 * миллисекунды, и фигура достигает дна за один кадр. Это и есть потолок:
 * быстрее падать уже некуда, дальше решает только задержка фиксации.
 */
fun gravityMs(level: Int): Double {
    val lv = level.coerceIn(1, C.MAX_LEVEL)
    val g = 1000.0 * 0.86.pow((lv - 1).toDouble())
    return if (lv <= C.BASE_MAX_LEVEL) max(g, 45.0) else g
}

/**
 * Потолок скорости при данном счёте. До миллиона — двадцатая, как в
 * оригинале; дальше каждые сто тысяч очков поднимают потолок на единицу:
 * 1 100 000 — двадцать первая, 2 000 000 — тридцатая, 4 000 000 и выше —
 * пятидесятая.
 */
fun levelCap(score: Long): Int {
    if (score < C.SPEED_BONUS_FROM) return C.BASE_MAX_LEVEL
    val extra = (score - C.SPEED_BONUS_FROM) / C.SPEED_BONUS_STEP
    return min(C.MAX_LEVEL.toLong(), C.BASE_MAX_LEVEL + extra).toInt()
}

/**
 * Очки за саму постановку фигуры. До двадцатой скорости их нет: там за
 * постановку платит только сброс, 1 или 2 очка за клетку, как в оригинале.
 * Выше фигура падает сама — с тридцатой она долетает до дна раньше, чем
 * до неё дотянется палец, — и очки за сброс пропадают как раз тогда, когда
 * ставить фигуры труднее всего. Поэтому каждая ступень сверх двадцатой
 * добавляет по 10 очков за фигуру: 21-я — 10, 30-я — 100, 50-я — 300.
 * Это около 7 % дохода на пятидесятой (тетрис там стоит 40 000, а
 * ставится за десять фигур): заметно, но основным источником очков
 * остаются линии.
 */
fun placeBonus(level: Int): Long =
    C.PLACE_BONUS * (level.coerceIn(1, C.MAX_LEVEL) - C.BASE_MAX_LEVEL).coerceAtLeast(0)

/**
 * Уровни игрока: 0-100, 100-500, 500-1000, дальше вдвое.
 * Накопительный опыт не сбрасывается между партиями.
 */
object Xp {
    val BOUNDS: LongArray = LongArray(48).also { b ->
        b[0] = 0; b[1] = 100; b[2] = 500
        for (i in 3 until 48) b[i] = b[i - 1] * 2
    }

    /** Старший ранг: выше полоса просто остаётся полной. */
    val TOP_RANK = BOUNDS.size - 2

    /** Ранг при данном опыте — тот же, до которого дошла бы партия. */
    fun rankOf(total: Long): Int {
        var r = 0
        while (r < TOP_RANK && total >= BOUNDS[r + 1]) r++
        return r
    }

    /**
     * Ранг и доля пути внутри него одним числом: 3.25 — четверть пути от
     * третьего к четвёртому. По такой шкале удобно анимировать: ранги в
     * ней равной ширины, хотя в очках каждый следующий вдвое длиннее.
     */
    fun position(total: Long): Double {
        val t = total.coerceAtLeast(0L)
        val r = rankOf(t)
        val lo = BOUNDS[r]
        val hi = BOUNDS[r + 1]
        return r + ((t - lo).toDouble() / (hi - lo).toDouble()).coerceIn(0.0, 1.0)
    }
}

/** Состояние полосы опыта на текущем ранге. */
data class XpProgress(val lo: Long, val hi: Long, val p: Float)

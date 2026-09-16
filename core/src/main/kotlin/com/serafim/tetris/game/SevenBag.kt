package com.serafim.tetris.game

import kotlin.math.floor
import kotlin.random.Random

/**
 * Мешок из семи: каждая фигура выпадает ровно раз, прежде чем мешок сменится.
 * Перемешивание — тот же Фишер—Йетс, что и в оригинале, чтобы при одинаковом
 * зерне последовательность совпадала до фигуры.
 */
class SevenBag(private val random: Random = Random.Default) {

    private val items = ArrayDeque<PieceType>()

    /** Сколько фигур сейчас лежит в мешке. */
    val size: Int get() = items.size

    init {
        refill()
    }

    private fun refill() {
        val b = PieceType.ALL.toMutableList()
        for (i in b.size - 1 downTo 1) {
            val j = floor(random.nextDouble() * (i + 1)).toInt()
            val t = b[i]; b[i] = b[j]; b[j] = t
        }
        items.addAll(b)
    }

    fun next(): PieceType {
        if (items.size < 7) refill()
        return items.removeFirst()
    }

    fun clear() {
        items.clear()
        refill()
    }

    /** Что осталось в мешке — для снимка партии. */
    fun state(): List<PieceType> = items.toList()

    /**
     * Вернуть мешок в сохранённое состояние. Пустой список — начать
     * заново: так снимок старой версии не ломает выдачу фигур.
     */
    fun restore(rest: List<PieceType>) {
        items.clear()
        if (rest.isEmpty()) refill() else items.addAll(rest)
    }
}

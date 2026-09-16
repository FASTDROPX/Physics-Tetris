package com.serafim.tetris.game

/** Неразрывный пробел — именно им ru-RU разделяет разряды. */
private const val NBSP = ' '

/**
 * Разряды числа через неразрывный пробел — то же, что даёт
 * toLocaleString('ru-RU') в браузере.
 */
fun formatRu(value: Long): String {
    val neg = value < 0
    val digits = if (neg) (-value).toString() else value.toString()
    val sb = StringBuilder(digits.length + digits.length / 3 + 1)
    val first = digits.length % 3
    if (first > 0) sb.append(digits, 0, first)
    var i = first
    while (i < digits.length) {
        if (sb.isNotEmpty()) sb.append(NBSP)
        sb.append(digits, i, i + 3)
        i += 3
    }
    return if (neg) "-$sb" else sb.toString()
}

fun formatRu(value: Int): String = formatRu(value.toLong())

/**
 * Часы партии: 0:42, 12:05, 1:23:45. Часы не ограничены — партия на
 * шесть миллионов очков идёт не один час.
 */
fun formatClock(ms: Double): String {
    val total = (ms / 1000.0).toLong().coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Время за всё время: 42 с, 17 мин, 3 ч 5 мин — секунды там уже не нужны. */
fun formatTotal(ms: Double): String {
    val total = (ms / 1000.0).toLong().coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    return when {
        total < 60 -> "$total${NBSP}с"
        h == 0L -> "$m${NBSP}мин"
        else -> "${formatRu(h)}${NBSP}ч $m${NBSP}мин"
    }
}

/**
 * Русское склонение по числу: 1 очко, 2 очка, 5 очков, 11 очков, 21 очко.
 * Нужно карточке места в меню — «до соперника 82 302 очка».
 */
fun pluralRu(n: Long, one: String, few: String, many: String): String {
    val a = (if (n < 0) -n else n) % 100
    if (a in 11..14) return many
    return when (a % 10) {
        1L -> one
        2L, 3L, 4L -> few
        else -> many
    }
}

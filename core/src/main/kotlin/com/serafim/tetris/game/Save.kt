package com.serafim.tetris.game

/**
 * Снимок незаконченной партии — чтобы свёрнутая игра не пропадала.
 *
 * Формат нарочно текстовый и построчный: его видно глазами, он переживает
 * любую версию телефона и легко проверяется тестом. Первая строка — метка,
 * вторая — счёт и часы, дальше поле, активная фигура, карман, очередь и
 * мешок; в режиме физики следом идут многоугольники стакана.
 *
 * Анимации в снимок не попадают: партия возвращается на паузе, а всё, что
 * мигало и сыпалось, начинается заново.
 */
const val SAVE_TAG = "tetris 1"

/** Что видно по снимку, пока его не разобрали целиком. */
data class SaveHead(
    val score: Long,
    val lines: Int,
    val level: Int,
    val playMs: Double,
    val physics: Boolean,
)

/** Буква поля — фигура; точка — пустая клетка. */
fun typeOfLetter(c: Char): PieceType? = when (c) {
    'I' -> PieceType.I
    'O' -> PieceType.O
    'T' -> PieceType.T
    'S' -> PieceType.S
    'Z' -> PieceType.Z
    'J' -> PieceType.J
    'L' -> PieceType.L
    else -> null
}

/**
 * Заголовок снимка: столько-то очков, такой-то уровень, столько-то
 * времени. Нужен меню, чтобы подписать кнопку «Продолжить», не поднимая
 * всю партию. null — это не снимок или он испорчен.
 */
fun peekSave(text: String): SaveHead? {
    val ls = text.lineSequence().take(2).toList()
    if (ls.size < 2 || ls[0].trim() != SAVE_TAG) return null
    val p = ls[1].trim().split(' ')
    if (p.size < 10) return null
    return SaveHead(
        score = p[0].toLongOrNull() ?: return null,
        lines = p[1].toIntOrNull() ?: return null,
        level = p[2].toIntOrNull() ?: return null,
        playMs = (p[5].toLongOrNull() ?: return null).toDouble(),
        physics = p[9] == "1",
    )
}

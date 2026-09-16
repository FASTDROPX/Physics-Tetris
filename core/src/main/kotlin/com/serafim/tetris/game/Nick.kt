package com.serafim.tetris.game

/**
 * Ник в онлайн-таблице. Его видят все, поэтому он чистится до того, как
 * уйдёт в базу: буквы любого алфавита, цифры, пробел, дефис, точка и
 * подчёркивание; пробелы по краям срезаются, подряд идущие сливаются в
 * один. То же ограничение длины стоит в правилах базы — клиент, который
 * его обойдёт, получит отказ.
 */
object Nick {
    const val MAX = 16

    fun clean(raw: String): String {
        val sb = StringBuilder()
        var space = false
        for (ch in raw) {
            if (ch.isLetterOrDigit() || ch == '-' || ch == '_' || ch == '.') {
                // пробел ставится только перед следующим знаком и только если
                // влезает вместе с ним — висящего пробела в конце не бывает
                val gap = space && sb.isNotEmpty()
                if (sb.length + (if (gap) 2 else 1) > MAX) break
                if (gap) sb.append(' ')
                sb.append(ch)
                space = false
            } else if (ch.isWhitespace()) {
                space = true
            }
        }
        return sb.toString()
    }

    fun isValid(nick: String): Boolean = nick.isNotEmpty() && nick == clean(nick)

    /**
     * Ключ занятости: два ника, различающиеся только высотой букв, — один
     * и тот же ник. FastdropX, fastdropx и FASTDROPX сводятся к «fastdropx».
     * Регистр снимается без оглядки на язык телефона: турецкая раскладка
     * иначе превратила бы `I` в `ı` и развела бы одинаковые ники.
     */
    fun key(raw: String): String = clean(raw).lowercase()

    /** Одинаковые ли это ники с точки зрения занятости. */
    fun same(a: String, b: String): Boolean = key(a) == key(b) && key(a).isNotEmpty()
}

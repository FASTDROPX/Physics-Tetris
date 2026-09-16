package com.serafim.tetris.game

/**
 * Счётчики за всё время. В отличие от счёта партии они не обнуляются с
 * новой игрой: складываются из партии в партию и переживают перезапуск.
 * Менять их может только логика — снаружи они доступны на чтение.
 */
class Stats {

    /** Сумма всех начисленных очков за всё время. */
    var score = 0L
        internal set

    /** Сколько фигур легло в стакан. */
    var pieces = 0L
        internal set

    /** Сколько линий стёрто. */
    var lines = 0L
        internal set

    /** Сколько партий начато. */
    var games = 0L
        internal set

    /** Сколько миллисекунд шла игра — паузы и меню не в счёт. */
    var timeMs = 0.0
        internal set

    /** Счётчики по нулям. Рекорд и опыт живут в игре — их сбрасывает `resetProgress`. */
    fun reset() {
        score = 0L
        pieces = 0L
        lines = 0L
        games = 0L
        timeMs = 0.0
    }

    /** Нечего сбрасывать. */
    val isEmpty: Boolean
        get() = score == 0L && pieces == 0L && lines == 0L && games == 0L && timeMs < 1000.0

    /** Подставляет значения с диска. Отрицательные не принимаются. */
    fun restore(score: Long, pieces: Long, lines: Long, games: Long, timeMs: Long = 0L) {
        this.score = score.coerceAtLeast(0L)
        this.pieces = pieces.coerceAtLeast(0L)
        this.lines = lines.coerceAtLeast(0L)
        this.games = games.coerceAtLeast(0L)
        this.timeMs = timeMs.coerceAtLeast(0L).toDouble()
    }
}

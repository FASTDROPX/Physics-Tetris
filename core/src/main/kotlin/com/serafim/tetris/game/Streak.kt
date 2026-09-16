package com.serafim.tetris.game

/** Сколько надо сыграть за день, чтобы он засчитался в серию. */
const val STREAK_DAY_MS = 60_000L

/** Сколько дней истории помнится: календарю в окне серии хватает с запасом. */
const val STREAK_HISTORY_DAYS = 42

/** Как сейчас обстоят дела с серией. */
enum class StreakState {
    /** Серии нет: ни разу не играли или прервали. */
    NONE,

    /** Сегодня уже засчитано — огонёк горит. */
    LIT,

    /** Вчера засчитано, сегодня ещё нет: серия жива до конца дня. */
    AT_RISK,
}

/**
 * Огоньки серии. Чем длиннее серия, тем горячее пламя — от красной искры
 * через жёлтое к синему и фиолетовому, как у настоящего огня, а на сотый
 * день радужное. Цвета живут в интерфейсе, здесь только пороги и имена.
 */
enum class FlameTier(val from: Int, val title: String) {
    OUT(0, "Погас"),
    SPARK(1, "Искра"),
    EMBER(3, "Огонёк"),
    FLAME(7, "Пламя"),
    BLUE(14, "Синее пламя"),
    STAR(30, "Звёздное пламя"),
    RAINBOW(100, "Радужное пламя"),
    ;

    /** Следующая ступень, если она есть. */
    val next: FlameTier? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(days: Int): FlameTier = entries.last { days >= it.from }
    }
}

/**
 * Серия дней подряд и сколько игралось в каждый из них.
 *
 * День засчитывается, когда за него набирается [STREAK_DAY_MS] настоящей
 * игры — паузы и меню не в счёт, несколько коротких партий складываются.
 * Дни считаются числами «эпохального дня» (дни с 1 января 1970-го) по
 * местному времени; какой сегодня день, решает не этот класс, а тот, кто
 * его зовёт, — так логику можно проверить на любых датах.
 *
 * Серия, у которой вчерашний день засчитан, а сегодняшний ещё нет, жива до
 * конца суток ([StreakState.AT_RISK]). Пропущен целый день — серия
 * прервалась, и показывать от неё больше нечего, но рекорд остаётся.
 */
class Streak {

    /** Дней подряд на момент последнего засчитанного дня. */
    var current = 0
        private set

    /** Самая длинная серия за всё время. */
    var best = 0
        private set

    /** Последний засчитанный день; [NEVER] — не было ни одного. */
    var lastDay = NEVER
        private set

    /** Сколько всего дней засчитано — не обязательно подряд. */
    var totalDays = 0
        private set

    private val days = HashMap<Long, Long>()

    /**
     * Добавить сыгранное время к сегодняшнему дню. Вернёт true ровно в тот
     * момент, когда сегодняшний день засчитался, — один раз за день.
     */
    fun addPlay(today: Long, ms: Long): Boolean {
        if (ms <= 0L) return false
        val before = days[today] ?: 0L
        val after = before + ms
        days[today] = after
        prune(today)
        if (before >= STREAK_DAY_MS || after < STREAK_DAY_MS) return false
        // часы на телефоне перевели назад — день уже засчитан позже, серию не трогаем
        if (lastDay != NEVER && today <= lastDay) return false
        current = if (lastDay != NEVER && today - lastDay == 1L) current + 1 else 1
        lastDay = today
        totalDays++
        if (current > best) best = current
        return true
    }

    fun state(today: Long): StreakState = when {
        lastDay == NEVER -> StreakState.NONE
        today <= lastDay -> StreakState.LIT
        today - lastDay == 1L -> StreakState.AT_RISK
        else -> StreakState.NONE
    }

    /** Серия, какой её видит игрок: прерванная — ноль. */
    fun shown(today: Long): Int = if (state(today) == StreakState.NONE) 0 else current

    /** Сколько миллисекунд игры было в этот день. */
    fun playedOn(day: Long): Long = days[day] ?: 0L

    /** Игра за последние [count] дней, от старого к сегодняшнему. */
    fun recent(today: Long, count: Int): LongArray =
        LongArray(count) { playedOn(today - (count - 1 - it)) }

    /** Самый долгий день в памяти. */
    fun longestDay(): Long = days.values.maxOrNull() ?: 0L

    /** Сброс статистики гасит и серию: рекорд, история — всё в ноль. */
    fun reset() {
        current = 0
        best = 0
        lastDay = NEVER
        totalDays = 0
        days.clear()
    }

    private fun prune(today: Long) {
        days.keys.removeAll { it <= today - STREAK_HISTORY_DAYS }
    }

    /** Одна строка для настроек: `s1;текущая;рекорд;последний;всего;день:мс,…`. */
    fun encode(): String = buildString {
        append(TAG).append(';').append(current).append(';').append(best)
        append(';').append(lastDay).append(';').append(totalDays).append(';')
        days.entries.sortedBy { it.key }.joinTo(this, ",") { "${it.key}:${it.value}" }
    }

    /** Разбор строки из [encode]. Чужая или битая строка ничего не меняет. */
    fun decode(text: String?): Boolean {
        val parts = text?.split(';') ?: return false
        if (parts.size != 6 || parts[0] != TAG) return false
        val cur = parts[1].toIntOrNull() ?: return false
        val bst = parts[2].toIntOrNull() ?: return false
        val last = parts[3].toLongOrNull() ?: return false
        val total = parts[4].toIntOrNull() ?: return false
        if (cur < 0 || bst < 0 || total < 0) return false
        val map = HashMap<Long, Long>()
        if (parts[5].isNotEmpty()) {
            for (item in parts[5].split(',')) {
                val kv = item.split(':')
                if (kv.size != 2) return false
                val d = kv[0].toLongOrNull() ?: return false
                val ms = kv[1].toLongOrNull() ?: return false
                if (ms > 0L) map[d] = ms
            }
        }
        current = cur
        best = maxOf(bst, cur)
        lastDay = last
        totalDays = total
        days.clear()
        days.putAll(map)
        return true
    }

    companion object {
        const val NEVER = Long.MIN_VALUE
        private const val TAG = "s1"
    }
}

package com.serafim.tetris.online

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.serafim.tetris.game.StreakState

/**
 * Подробная статистика игрока для окна, которое открывается тапом по
 * строке таблицы. Строка таблицы знает только ник, рекорд и сумму очков —
 * этого хватает на список, но не на «статистику, как у себя». Остальное
 * лежит отдельной записью `profiles/{uid}`.
 *
 * Отдельной, а не лишними полями строки таблицы, нарочно: правила базы
 * пускают в строку ровно четыре поля, и пока новые правила не
 * опубликованы, лишнее поле уронило бы запись в таблицу у всех. Запись
 * профиля в такой момент просто не пройдёт, а таблица будет жить как жила.
 */
data class Profile(
    /** Опыт за всё время — из него уровень игрока. */
    val xp: Long,
    val pieces: Long,
    val lines: Long,
    val games: Long,
    val timeMs: Long,
    /** Серия на день [streakDay] — последний засчитанный. */
    val streak: Int,
    val streakBest: Int,
    /** Последний засчитанный день серии в днях от 1970-го; −1 — серии не было. */
    val streakDay: Long,
) {
    /**
     * Серия, как её увидит смотрящий сегодня: у него свой «сегодня», и серия,
     * записанная неделю назад, давно прервалась, хотя в записи она живая.
     */
    fun streakState(today: Long): StreakState = when {
        streakDay < 0L -> StreakState.NONE
        today <= streakDay -> StreakState.LIT
        today - streakDay == 1L -> StreakState.AT_RISK
        else -> StreakState.NONE
    }

    fun streakShown(today: Long): Int = if (streakState(today) == StreakState.NONE) 0 else streak

    fun toMap(): Map<String, Any> = mapOf(
        "xp" to xp,
        "pieces" to pieces,
        "lines" to lines,
        "games" to games,
        "timeMs" to timeMs,
        "streak" to streak.toLong(),
        "streakBest" to streakBest.toLong(),
        "streakDay" to streakDay,
        "updated" to FieldValue.serverTimestamp(),
    )

    companion object {
        /** Чтение записи; неполная или чужая запись — как будто её нет. */
        fun from(doc: DocumentSnapshot): Profile? {
            if (!doc.exists()) return null
            return Profile(
                xp = doc.getLong("xp") ?: return null,
                pieces = doc.getLong("pieces") ?: return null,
                lines = doc.getLong("lines") ?: return null,
                games = doc.getLong("games") ?: return null,
                timeMs = doc.getLong("timeMs") ?: return null,
                streak = (doc.getLong("streak") ?: return null).toInt(),
                streakBest = (doc.getLong("streakBest") ?: return null).toInt(),
                streakDay = doc.getLong("streakDay") ?: return null,
            )
        }
    }
}

/** Что показывает окно игрока. */
sealed interface PlayerView {
    val uid: String
    val name: String
    val rank: Int
    val me: Boolean

    data class Loading(
        override val uid: String,
        override val name: String,
        override val rank: Int,
        override val me: Boolean,
    ) : PlayerView

    /** [profile] — null, если игрок ещё не играл в версии, которая его пишет. */
    data class Ready(
        override val uid: String,
        override val name: String,
        override val rank: Int,
        override val me: Boolean,
        val best: Long,
        val total: Long,
        val profile: Profile?,
    ) : PlayerView

    data class Failed(
        override val uid: String,
        override val name: String,
        override val rank: Int,
        override val me: Boolean,
        val message: String,
        val offline: Boolean,
    ) : PlayerView
}

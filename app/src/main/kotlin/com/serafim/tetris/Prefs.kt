package com.serafim.tetris

import android.content.Context
import com.serafim.tetris.game.Stats
import java.io.File

/**
 * То, что переживает перезапуск: накопленный опыт игрока, рекорд,
 * статистика, ник в онлайн-таблице и переключатели звука, вибрации и
 * физики. Выбранный когда-то стартовый уровень в старых установках так и
 * лежит под ключом `startLevel`, но больше не читается.
 * В браузере всё это жило до перезагрузки страницы — на телефоне
 * процесс убивают куда чаще, поэтому храним на диске.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("tetris", Context.MODE_PRIVATE)
    private val dir = context.filesDir

    var xpTotal: Long
        get() = sp.getLong(KEY_XP_TOTAL, 0L)
        private set(v) { sp.edit().putLong(KEY_XP_TOTAL, v).apply() }

    var xpRank: Int
        get() = sp.getInt(KEY_XP_RANK, 0)
        private set(v) { sp.edit().putInt(KEY_XP_RANK, v).apply() }

    var best: Long
        get() = sp.getLong(KEY_BEST, 0L)
        private set(v) { sp.edit().putLong(KEY_BEST, v).apply() }

    var soundOn: Boolean
        get() = sp.getBoolean(KEY_SOUND, true)
        set(v) { sp.edit().putBoolean(KEY_SOUND, v).apply() }

    var vibrationOn: Boolean
        get() = sp.getBoolean(KEY_VIBRATION, true)
        set(v) { sp.edit().putBoolean(KEY_VIBRATION, v).apply() }

    /** Режим физики выключен по умолчанию: это другая игра, а не настройка. */
    var physicsOn: Boolean
        get() = sp.getBoolean(KEY_PHYSICS, false)
        set(v) { sp.edit().putBoolean(KEY_PHYSICS, v).apply() }

    val statScore: Long get() = sp.getLong(KEY_STAT_SCORE, 0L)
    val statPieces: Long get() = sp.getLong(KEY_STAT_PIECES, 0L)
    val statLines: Long get() = sp.getLong(KEY_STAT_LINES, 0L)
    val statGames: Long get() = sp.getLong(KEY_STAT_GAMES, 0L)
    /** Появилось позже остальных — у старых установок ключа нет, отсюда ноль. */
    val statTime: Long get() = sp.getLong(KEY_STAT_TIME, 0L)

    /** Ник в онлайн-таблице. Пустой — игрок в таблице не участвует. */
    /**
     * Незаконченная партия лежит файлом, а не в настройках: со стаканом в
     * режиме физики снимок тянет на десятки килобайт, и переписывать из-за
     * него весь XML настроек на каждой фигуре — плохая мысль.
     *
     * Пишется через временный файл с переименованием: если телефон умрёт
     * посреди записи, старый снимок останется целым.
     */
    fun readResume(): String? = runCatching {
        val f = File(dir, RESUME)
        if (f.exists()) f.readText() else null
    }.getOrNull()

    fun writeResume(text: String) {
        runCatching {
            val tmp = File(dir, "$RESUME.tmp")
            tmp.writeText(text)
            val f = File(dir, RESUME)
            f.delete()
            if (!tmp.renameTo(f)) f.writeText(text)
        }
    }

    fun clearResume() {
        runCatching { File(dir, RESUME).delete() }
    }

    /** Когда таблица в последний раз приходила с сервера, мс эпохи. */
    var boardSyncedAt: Long
        get() = sp.getLong(KEY_SYNCED, 0L)
        set(v) { sp.edit().putLong(KEY_SYNCED, v).apply() }

    var nick: String
        get() = sp.getString(KEY_NICK, "") ?: ""
        set(v) { sp.edit().putString(KEY_NICK, v).apply() }

    /** Одной записью, чтобы не дёргать диск по разу на поле. */
    fun saveProgress(xpTotal: Long, xpRank: Int, best: Long, stats: Stats) {
        sp.edit()
            .putLong(KEY_XP_TOTAL, xpTotal)
            .putInt(KEY_XP_RANK, xpRank)
            .putLong(KEY_BEST, best)
            .putLong(KEY_STAT_SCORE, stats.score)
            .putLong(KEY_STAT_PIECES, stats.pieces)
            .putLong(KEY_STAT_LINES, stats.lines)
            .putLong(KEY_STAT_GAMES, stats.games)
            .putLong(KEY_STAT_TIME, stats.timeMs.toLong())
            .apply()
    }

    private companion object {
        const val KEY_XP_TOTAL = "xpTotal"
        const val KEY_XP_RANK = "xpRank"
        const val KEY_BEST = "best"
        const val KEY_SOUND = "sound"
        const val KEY_VIBRATION = "vibration"
        const val KEY_PHYSICS = "physics"
        const val KEY_STAT_SCORE = "statScore"
        const val KEY_STAT_PIECES = "statPieces"
        const val KEY_STAT_LINES = "statLines"
        const val KEY_STAT_GAMES = "statGames"
        const val KEY_STAT_TIME = "statTimeMs"
        const val KEY_NICK = "nick"
        const val KEY_SYNCED = "boardSyncedAt"
        const val RESUME = "resume.txt"
    }
}

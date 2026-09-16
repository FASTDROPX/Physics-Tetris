package com.serafim.tetris

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.serafim.tetris.game.GameState
import com.serafim.tetris.game.STREAK_HISTORY_DAYS
import com.serafim.tetris.game.SaveHead
import com.serafim.tetris.game.Sfx
import com.serafim.tetris.game.Streak
import com.serafim.tetris.game.pluralRu
import com.serafim.tetris.game.peekSave
import com.serafim.tetris.game.TetrisGame
import com.serafim.tetris.online.BoardRow
import com.serafim.tetris.online.Leaderboard
import com.serafim.tetris.online.PlayerView
import com.serafim.tetris.online.Profile
import com.serafim.tetris.ui.StreakView
import com.serafim.tetris.ui.ThemeKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.random.Random

/**
 * Всё, что в оригинале лежало в обработчиках событий: запуск партии
 * с задержкой на закрытие диалога, переключение звука, обновление
 * подсказки при заходе в меню и разбор действий ввода.
 */
class Controller(
    val game: TetrisGame,
    val fx: AndroidFx,
    private val prefs: Prefs,
    private val scope: CoroutineScope,
) {

    var closing by mutableStateOf(false)
        private set
    var tipIndex by mutableIntStateOf(Random.nextInt(1000))
        private set
    var titleSeed by mutableIntStateOf(Random.nextInt(100000))
        private set
    var soundOn by mutableStateOf(prefs.soundOn)
        private set
    var vibrationOn by mutableStateOf(prefs.vibrationOn)
        private set
    var physicsOn by mutableStateOf(prefs.physicsOn)
        private set

    /** Громкость звуков и сила отдачи, 0..1 — ползунки в настройках. */
    var soundVolume by mutableFloatStateOf(prefs.soundVolume)
        private set
    var vibrationPower by mutableFloatStateOf(prefs.vibrationPower)
        private set

    /** Выбранная тема. Что она делает с цветами — в ui/Theme.kt. */
    var theme by mutableStateOf(ThemeKind.of(prefs.themeName))
        private set

    /** Окно настроек поверх меню. */
    var showSettings by mutableStateOf(false)
        private set

    /**
     * Серия дней подряд. Сам счёт живёт в [streak], а интерфейс получает
     * снимок [streakView] — неизменяемый, чтобы Compose его заметил.
     */
    private val streak = Streak().apply { decode(prefs.streak) }
    var streakView by mutableStateOf(StreakView())
        private set
    var showStreak by mutableStateOf(false)
        private set

    /** Сколько времени игры уже разнесено по дням. */
    private var creditedPlayMs = 0.0

    /** Окно статистики поверх меню. */
    var showStats by mutableStateOf(false)
        private set

    /** Подтверждение сброса поверх статистики. */
    var showResetConfirm by mutableStateOf(false)
        private set

    /**
     * Незаконченная партия, если она есть: меню предлагает её продолжить.
     * Ноль знаний о самой партии — только счёт, уровень и часы для подписи.
     */
    var resume by mutableStateOf<SaveHead?>(null)
        private set

    /** Подтверждение «Заново», когда сохранённой партии есть что терять. */
    var showRestartConfirm by mutableStateOf(false)
        private set

    /** Сколько раз сбрасывали: окно статистики по нему отматывает числа к нулю. */
    var statsResets by mutableIntStateOf(0)
        private set

    /** Онлайн-таблица лидеров и её окно поверх меню. */
    val board = Leaderboard(prefs, scope)
    var showBoard by mutableStateOf(false)
        private set

    /**
     * Открыто окно с полем ввода: клавиши идут в текст, а не в игру —
     * иначе с подключённой клавиатурой пробел в нике запускал бы партию.
     */
    val typing: Boolean get() = showBoard

    private var lastState: GameState = GameState.MENU
    private var lastPieces = 0L
    private var lastSavedRank = -1
    private var lastSavedXp = -1L
    private var lastSaveAt = 0L

    init {
        fx.soundOn = soundOn
        fx.soundVolume = soundVolume
        fx.vibrationOn = vibrationOn
        fx.vibrationPower = vibrationPower
        game.physicsMode = prefs.physicsOn
        game.restoreProgress(prefs.xpTotal, prefs.xpRank, prefs.best)
        game.stats.restore(
            prefs.statScore, prefs.statPieces, prefs.statLines, prefs.statGames, prefs.statTime,
        )
        lastSavedRank = game.xpRank
        lastSavedXp = game.xpTotal
        creditedPlayMs = game.stats.timeMs
        refreshStreak()
        board.refreshStanding(game.stats.score)
        resume = prefs.readResume()?.let { peekSave(it) }
        lastPieces = game.stats.pieces
    }

    /** Опыт, рекорд и серию пишем на диск на переходах состояния и при новом ранге. */
    fun saveProgress() {
        prefs.saveProgress(game.xpTotal, game.xpRank, game.best, game.stats)
        prefs.streak = streak.encode()
    }

    /** Сегодняшний день по часам телефона — в днях от 1 января 1970-го. */
    private fun today(): Long = LocalDate.now().toEpochDay()

    /** Снимок серии для интерфейса; зовётся, когда серия или день могли смениться. */
    fun refreshStreak() {
        val now = LocalDate.now()
        val day = now.toEpochDay()
        streakView = StreakView(
            days = streak.shown(day),
            best = streak.best,
            state = streak.state(day),
            totalDays = streak.totalDays,
            history = streak.recent(day, STREAK_HISTORY_DAYS).toList(),
            weekday = now.dayOfWeek.value,
            longestDayMs = streak.longestDay(),
        )
    }

    /**
     * Разнести новое время игры по дням. Время берётся из той же статистики,
     * что и «Время в игре», поэтому паузы и меню сюда не попадают. Копится
     * секундами: дёргать серию с каждым кадром незачем.
     */
    private fun creditPlay() {
        val total = game.stats.timeMs
        // статистику сбросили — считаем заново от нового нуля
        if (total < creditedPlayMs) creditedPlayMs = total
        val delta = (total - creditedPlayMs).toLong()
        if (delta < 1000L) return
        creditedPlayMs += delta
        if (streak.addPlay(today(), delta)) {
            refreshStreak()
            prefs.streak = streak.encode()
            val n = streakView.days
            fx.message("Серия: $n ${pluralRu(n.toLong(), "день", "дня", "дней")} подряд")
        }
    }

    fun openStreak() { click(); refreshStreak(); showStreak = true }

    fun closeStreak() { click(); showStreak = false }

    /**
     * Снимок незаконченной партии на диск. Делается на каждой лёгшей
     * фигуре, на паузе и при сворачивании — то есть партия переживает и
     * закрытое приложение, и убитый процесс, теряя в худшем случае одну
     * летящую фигуру.
     *
     * [now] — писать прямо здесь: при сворачивании ждать фоновой корутины
     * уже нельзя, процесс могут снять в любую секунду.
     */
    fun keepGame(now: Boolean = false) {
        val live = game.state == GameState.PLAYING || game.state == GameState.PAUSED
        if (!live) return
        val text = game.snapshot()
        resume = peekSave(text)
        if (now) prefs.writeResume(text) else scope.launch(Dispatchers.IO) { prefs.writeResume(text) }
    }

    /** Партия кончилась или её бросили — продолжать больше нечего. */
    private fun dropGame() {
        resume = null
        prefs.clearResume()
    }

    /**
     * Кнопка «Продолжить»: поднимаем партию из снимка. Она возвращается на
     * паузе, поэтому фигура не свалится, пока игрок не будет готов.
     */
    fun resumeGame() {
        if (closing) return
        val text = prefs.readResume() ?: run { resume = null; return }
        click()
        closing = true
        scope.launch {
            delay(320)
            closing = false
            if (!game.restore(text)) {
                // снимок не читается — не мучаем игрока, начинаем заново
                dropGame()
                game.startGame()
            } else {
                lastPieces = game.stats.pieces
            }
        }
    }

    fun askRestart() { click(); showRestartConfirm = true }

    fun cancelRestart() { click(); showRestartConfirm = false }

    fun confirmRestart() { showRestartConfirm = false; launch() }

    fun openStats() { click(); showStats = true }

    fun openSettings() { click(); showSettings = true }

    fun closeSettings() { click(); showSettings = false }

    fun openBoard() { click(); showBoard = true; board.open() }

    fun closeBoard() { click(); showBoard = false }

    fun joinBoard(nick: String) { click(); board.join(nick, game.best, game.stats.score, profile()) }

    /**
     * Профиль игрока для таблицы: всё, что показывает окно статистики, и
     * серия. Уходит вместе с очками — и только у тех, кто выбрал ник.
     */
    fun profile(): Profile = Profile(
        xp = game.xpTotal,
        pieces = game.stats.pieces,
        lines = game.stats.lines,
        games = game.stats.games,
        timeMs = game.stats.timeMs.toLong(),
        streak = streak.current,
        streakBest = streak.best,
        streakDay = if (streak.lastDay == Streak.NEVER) -1L else streak.lastDay,
    )

    /** Тап по строке таблицы. Себя — сразу из телефона, других — с сервера. */
    fun openPlayer(row: BoardRow, rank: Int) {
        click()
        val mine = PlayerView.Ready(row.uid, row.name, rank, true, game.best, game.stats.score, profile())
        board.openPlayer(row, rank, mine)
    }

    fun closePlayer() { click(); board.closePlayer() }

    fun closeStats() { click(); showStats = false; showResetConfirm = false }

    fun askResetStats() { click(); showResetConfirm = true }

    fun cancelResetStats() { click(); showResetConfirm = false }

    /**
     * Сброс всего: статистика, рекорд и уровень игрока — в ноль и сразу на
     * диск. Настройки (звук, вибрация, физика) остаются.
     */
    fun resetStats() {
        click()
        game.resetProgress()
        lastSavedRank = 0
        lastSavedXp = 0L
        streak.reset()
        creditedPlayMs = game.stats.timeMs
        refreshStreak()
        saveProgress()
        dropGame()
        board.remove()
        statsResets++
        showResetConfirm = false
    }

    /** Есть ли что сбрасывать: хоть одно число не ноль. */
    val canReset: Boolean
        get() = !game.stats.isEmpty || game.best > 0 || game.xpTotal > 0

    /**
     * Esc и системная «Назад» закрывают верхнее окно: сначала
     * подтверждение, потом статистику. Больше ничего они не закрывают.
     */
    fun dismissTop(): Boolean {
        if (showRestartConfirm) { cancelRestart(); return true }
        if (showResetConfirm) { cancelResetStats(); return true }
        if (showSettings) { closeSettings(); return true }
        if (showStreak) { closeStreak(); return true }
        if (showStats) { closeStats(); return true }
        if (board.player != null) { closePlayer(); return true }
        if (showBoard) { closeBoard(); return true }
        return false
    }

    /** Меню каждый раз показывает новую подсказку и новые цвета заголовка. */
    fun onStateChanged(state: GameState) {
        creditPlay()
        if (state == GameState.MENU && lastState != GameState.MENU) {
            refreshMenu()
            // пока играли, могла наступить полночь
            refreshStreak()
            // в меню заходят после каждой партии — там и обновляем место
            board.refreshStanding(game.stats.score)
        }
        if (state == GameState.OVER && lastState != GameState.OVER) {
            saveProgress()
            board.submit(game.best, game.stats.score, profile())
            dropGame()
        }
        // партия пишется на диск с каждой лёгшей фигурой
        if (game.stats.pieces != lastPieces) {
            lastPieces = game.stats.pieces
            keepGame()
        }
        if (game.xpRank != lastSavedRank) {
            lastSavedRank = game.xpRank
            saveProgress()
        }
        // подстраховка на случай, если процесс убьют без onPause
        val now = System.currentTimeMillis()
        if (game.xpTotal != lastSavedXp && now - lastSaveAt > 3000L) {
            lastSavedXp = game.xpTotal
            lastSaveAt = now
            saveProgress()
        }
        lastState = state
    }

    fun refreshMenu() {
        tipIndex = Random.nextInt(1000)
        titleSeed = Random.nextInt(100000)
    }

    /** Кнопка «Играть»: сначала уезжает диалог, потом стартует партия. */
    fun launch() {
        if (closing) return
        dropGame()
        val hasOverlay = game.state == GameState.MENU || game.state == GameState.OVER
        if (!hasOverlay) {
            game.startGame()
            return
        }
        closing = true
        scope.launch {
            delay(320)
            closing = false
            game.startGame()
        }
    }

    fun setSound(on: Boolean) {
        soundOn = on
        fx.soundOn = on
        prefs.soundOn = on
        if (on) fx.sound(Sfx.ROTATE)
    }

    fun toggleSound() = setSound(!soundOn)

    fun setVibration(on: Boolean) {
        vibrationOn = on
        fx.vibrationOn = on
        prefs.vibrationOn = on
        if (on) fx.vibrate(14, 45, 14)
    }

    fun toggleVibration() = setVibration(!vibrationOn)

    /**
     * Громкость. Звук нового уровня даётся сразу — ползунок без отклика
     * пришлось бы крутить вслепую; но только когда палец отпущен, иначе
     * при протягивании получилась бы очередь щелчков.
     */
    fun setSoundVolume(v: Float, preview: Boolean = false) {
        soundVolume = v.coerceIn(0f, 1f)
        fx.soundVolume = soundVolume
        prefs.soundVolume = soundVolume
        if (preview && soundOn && soundVolume > 0f) fx.sound(Sfx.ROTATE)
    }

    /** Сила отдачи — с таким же пробным толчком по отпусканию ползунка. */
    fun setVibrationPower(v: Float, preview: Boolean = false) {
        vibrationPower = v.coerceIn(0f, 1f)
        fx.vibrationPower = vibrationPower
        prefs.vibrationPower = vibrationPower
        if (preview && vibrationOn && vibrationPower > 0f) fx.vibrate(14, 45, 14)
    }

    /** Тема. Цвета подставляются сами: их держит состояние Compose. */
    fun chooseTheme(kind: ThemeKind) {
        click()
        theme = kind
        prefs.themeName = kind.name
    }

    /**
     * Режим физики меняет правила целиком, поэтому включается только на
     * следующей партии — переобувать стакан посреди игры нечестно.
     */
    fun setPhysics(on: Boolean) {
        physicsOn = on
        game.physicsMode = on
        prefs.physicsOn = on
    }

    fun togglePhysics() { click(); setPhysics(!physicsOn) }

    fun click() = fx.sound(Sfx.CLICK)

    fun togglePause() {
        if (game.state == GameState.PLAYING || game.state == GameState.PAUSED) {
            game.togglePause()
            if (game.state == GameState.PAUSED) keepGame()
        }
    }

    fun backToMenu() {
        // из меню партию можно будет продолжить — сохраняем как есть
        keepGame()
        game.backToMenu()
        refreshMenu()
        saveProgress()
        board.submit(game.best, game.stats.score, profile())
    }


    // ---------- ввод ----------

    fun pressLeft() = game.pressLeft()
    fun pressRight() = game.pressRight()
    fun pressDown() = game.pressDown()
    fun releaseLeft() { game.keyLeft = false }
    fun releaseRight() { game.keyRight = false }
    fun releaseDown() { game.keyDown = false }

    fun rotateCw() = game.rotate(1)
    fun rotateCcw() = game.rotate(-1)
    fun hardDrop() = game.hardDrop()
    fun hold() = game.holdPiece()

    /** Пробел в меню начинает партию — как в оригинале, но не из-под статистики. */
    fun dropOrStart() {
        if (game.state == GameState.PLAYING) game.hardDrop()
        else if (game.state == GameState.MENU && !showStats && !showBoard) launch()
    }

    fun restart() {
        if (game.state != GameState.MENU) game.startGame()
    }

    /** Сворачивание окна: клавиши отпускаются, партия встаёт на паузу. */
    fun onBackground() {
        game.releaseAllKeys()
        if (game.state == GameState.PLAYING) game.togglePause()
        // процесс могут снять сразу после этого — пишем не откладывая
        keepGame(now = true)
        game.commitBest()
        saveProgress()
    }
}

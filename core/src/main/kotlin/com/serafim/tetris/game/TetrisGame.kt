package com.serafim.tetris.game

import com.serafim.tetris.physics.PhysicsField
import com.serafim.tetris.physics.containsPoint
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

enum class GameState {
    MENU, INTRO, PLAYING, CLEARING, FALLING,
    /** Режим физики: ждём, пока стакан перестанет шевелиться. */
    SETTLING,
    PAUSED, DYING, OVER,
}

/**
 * Вся игровая логика оригинала: SRS с таблицами отскока, мешок из семи,
 * задержка фиксации, DAS/ARR, подсчёт очков и накопительный опыт игрока.
 * Ни одной зависимости от Android — класс целиком проверяется юнит-тестами.
 */
class TetrisGame(
    private val fx: Fx = Fx.None,
    private val random: Random = Random.Default,
) {

    // ---------- поле и фигуры ----------
    var grid: Array<Array<PieceType?>> = emptyGrid(); private set
    private val bag = SevenBag(random)
    val queue = ArrayDeque<PieceType>()
    var piece: Piece? = null; private set
    var hold: PieceType? = null; private set
    var canHold = true; private set

    var state = GameState.MENU; private set

    // ---------- счёт ----------
    var score = 0L; private set
    var lines = 0; private set
    var level = 1; private set
    var startLevel = 1
    var best = 0L; private set
    var combo = -1; private set
    var backToBack = false; private set

    // ---------- тайминги падения и фиксации ----------
    private var dropTimer = 0.0
    var lockTimer = 0.0; private set
    var lockResets = 0; private set
    var grounded = false; private set

    // ---------- анимации ----------
    var clearTimer = 0.0; private set
    var clearRows: List<Int> = emptyList(); private set
    var fallTimer = 0.0; private set
    var fallOff: IntArray = IntArray(0); private set
    var trails: List<Trail> = emptyList(); private set
    var trailTimer = 0.0; private set
    private var impactKeys: BooleanArray? = null
    var impactT = C.OFF; private set
    var shakeT = C.OFF; private set
    var shakeMag = 0.0; private set
    var nextT = C.OFF; private set
    var dieT = 0.0; private set
    var shownScore = 0.0; private set
    var introT = 0.0; private set
    private var firstSpawn = false
    var revealFirst = false; private set
    var rotT = C.OFF; private set
    var rotFrom = RotFrom(); private set
    private var dropDist = 0
    private var pendingDrop = 0
    val pops = ArrayList<Pop>()
    var pcT = C.OFF; private set
    var dust: Dust? = null; private set
    var blasting = false; private set
    var sparks: List<Particle> = emptyList(); private set
    var sparkT = C.OFF; private set
    var spawnT = C.OFF; private set
    var sweepT = C.OFF; private set

    /** Отключает встряску, вибрацию и мгновенно проматывает распад стакана. */
    var reduceMotion = false

    // ---------- режим физики ----------

    /** Переключатель из меню. Применяется со следующей партии. */
    var physicsMode = false

    /** Мир твёрдых тел. Пусто — значит партия идёт по обычным правилам. */
    var physics: PhysicsField? = null; private set

    var settleT = 0.0; private set
    private var pendingSpin: Spin? = null
    private var physSpawn = false
    private var scoredThisLock = false
    private var physMark = 0L

    /** Размер клетки в пикселях — от него зависит дробность частиц. */
    var cellPx = 28

    /** Счётчики за всё время: очки, фигуры, линии, партии, время. */
    val stats = Stats()

    /** Сколько миллисекунд идёт текущая партия. Пауза часы останавливает. */
    var playMs = 0.0; private set

    /** Опыт на старте партии — от него окно итогов заполняет полосу. */
    var xpAtStart = 0L; private set

    /**
     * Рекорд на старте партии. К концу партии `best` уже переписан её же
     * счётом, и сравнивать с ним нельзя: повторить рекорд — не побить его.
     */
    var bestAtStart = 0L; private set

    // ---------- опыт игрока ----------
    var xpTotal = 0L; private set
    var xpRank = 0; private set
    private var xpLastScore = 0L

    // ---------- зажатые клавиши ----------
    var keyLeft = false
    var keyRight = false
    var keyDown = false
    private var repLeft = 0.0
    private var repRight = 0.0
    private var repDown = 0.0

    private var lastMoveWasRotation = false
    private var lastKickIndex = 0

    init {
        repeat(C.QUEUE_SIZE) { queue.addLast(nextType()) }
    }

    // ================= поле =================

    private fun emptyGrid(): Array<Array<PieceType?>> =
        Array(C.ROWS) { arrayOfNulls<PieceType>(C.COLS) }

    fun at(x: Int, y: Int): PieceType? = grid[y][x]

    // ================= мешок =================

    private fun nextType(): PieceType = bag.next()

    // ================= геометрия =================

    fun cellsOf(
        p: Piece,
        rot: Int = p.rot,
        x: Int = p.x,
        y: Int = p.y,
    ): List<Cell> {
        val m = Shapes.matrix(p.type, rot)
        val out = ArrayList<Cell>(4)
        for (r in m.indices) for (c in m.indices) if (m[r][c] != 0) out.add(Cell(x + c, y + r))
        return out
    }

    fun collides(p: Piece, rot: Int = p.rot, x: Int = p.x, y: Int = p.y): Boolean {
        val m = Shapes.matrix(p.type, rot)
        for (r in m.indices) for (c in m.indices) {
            if (m[r][c] == 0) continue
            val cx = x + c
            val cy = y + r
            if (cx < 0 || cx >= C.COLS || cy >= C.ROWS) return true
            if (cy >= 0 && grid[cy][cx] != null) return true
        }
        return false
    }

    // ================= появление фигур =================

    private fun spawn(type: PieceType? = null): Boolean {
        val t = type ?: nextType()
        val sp = Shapes.SPAWN.getValue(t)
        piece = Piece(t, 0, sp.x, sp.y)
        while (queue.size < C.QUEUE_SIZE) queue.addLast(nextType())
        lockTimer = 0.0; lockResets = 0; grounded = false; dropTimer = 0.0
        spawnT = 0.0
        rotT = C.OFF
        revealFirst = firstSpawn
        if (firstSpawn) { firstSpawn = false; fx.sound(Sfx.REVEAL) }
        lastMoveWasRotation = false; lastKickIndex = 0
        if (collides(piece!!)) { gameOver(); return false }
        return true
    }

    private fun pullNext(): Boolean {
        nextT = 0.0
        val t = queue.removeFirst()
        while (queue.size < C.QUEUE_SIZE) queue.addLast(nextType())
        return spawn(t)
    }

    // ================= действия игрока =================

    fun move(dx: Int): Boolean {
        val p = piece ?: return false
        if (state != GameState.PLAYING) return false
        if (collides(p, p.rot, p.x + dx, p.y)) return false
        piece = p.copy(x = p.x + dx)
        lastMoveWasRotation = false
        resetLock()
        fx.sound(Sfx.MOVE)
        return true
    }

    fun softDrop() {
        val p = piece ?: return
        if (state != GameState.PLAYING) return
        if (!collides(p, p.rot, p.x, p.y + 1)) {
            piece = p.copy(y = p.y + 1)
            score += 1
            lastMoveWasRotation = false
            dropTimer = 0.0
        } else grounded = true
    }

    fun rotate(dir: Int) {
        val p = piece ?: return
        if (state != GameState.PLAYING || p.type == PieceType.O) return
        val from = p.rot
        val to = (from + dir + 4) % 4
        val kicks = Kicks.forMove(p.type, from, to)
        for (i in kicks.indices) {
            val nx = p.x + kicks[i][0]
            val ny = p.y + kicks[i][1]
            if (!collides(p, to, nx, ny)) {
                piece = Piece(p.type, to, nx, ny)
                rotFrom = RotFrom(p.x - nx, p.y - ny, dir)
                rotT = 0.0
                lastMoveWasRotation = true
                lastKickIndex = i
                resetLock()
                fx.sound(Sfx.ROTATE)
                return
            }
        }
    }

    fun hardDrop() {
        var p = piece ?: return
        if (state != GameState.PLAYING) return
        val fromY = p.y
        var d = 0
        while (!collides(p, p.rot, p.x, p.y + 1)) { p = p.copy(y = p.y + 1); d++ }
        piece = p
        if (d > 0) makeTrails(p, fromY, d)
        score += d * 2L
        pendingDrop = d
        fx.sound(Sfx.DROP)
        shakeBoard(min(9.0, 2 + d * 0.35))
        buzz(12)
        lastMoveWasRotation = false
        lockPiece()
    }

    /** След падения: по одной полосе на колонку фигуры. */
    private fun makeTrails(p: Piece, fromY: Int, d: Int) {
        val top = LinkedHashMap<Int, Int>()
        for (c in cellsOf(p, p.rot, p.x, fromY)) {
            val cur = top[c.x]
            if (cur == null || c.y < cur) top[c.x] = c.y
        }
        trails = top.entries.map { Trail(it.key, it.value, it.value + d) }
        trailTimer = 0.0
    }

    /**
     * Такой же след для падения свайпом вниз: у обоих способов уронить
     * фигуру одинаковая отдача.
     */
    fun dropTrail(fromY: Int) {
        val p = piece ?: return
        if (state != GameState.PLAYING) return
        val d = p.y - fromY
        if (d > 0) makeTrails(p, fromY, d)
    }

    fun holdPiece() {
        val p = piece ?: return
        if (state != GameState.PLAYING || !canHold) return
        val cur = p.type
        canHold = false
        fx.sound(Sfx.HOLD)
        val h = hold
        if (h != null) { hold = cur; spawn(h) } else { hold = cur; pullNext() }
        fx.holdPop()
    }

    private fun resetLock() {
        val p = piece ?: return
        if (grounded && lockResets < C.MAX_LOCK_RESETS) { lockTimer = 0.0; lockResets++ }
        if (!collides(p, p.rot, p.x, p.y + 1)) grounded = false
    }

    // ================= фиксация =================

    /** Правило трёх углов: полный T-спин, мини или ничего. */
    fun tSpinKind(): Spin? {
        val p = piece ?: return null
        if (p.type != PieceType.T || !lastMoveWasRotation) return null
        val cx = p.x + 1
        val cy = p.y + 1
        val corners = arrayOf(
            intArrayOf(-1, -1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(1, 1),
        )
        val filled = BooleanArray(4)
        for (i in 0 until 4) {
            val x = cx + corners[i][0]
            val y = cy + corners[i][1]
            filled[i] = when {
                x < 0 || x >= C.COLS || y >= C.ROWS -> true
                y < 0 -> false
                else -> grid[y][x] != null
            }
        }
        if (filled.count { it } < 3) return null
        val front = when (p.rot) {
            0 -> intArrayOf(0, 1)
            1 -> intArrayOf(1, 3)
            2 -> intArrayOf(2, 3)
            else -> intArrayOf(0, 2)
        }
        return if ((filled[front[0]] && filled[front[1]]) || lastKickIndex == 4) Spin.FULL else Spin.MINI
    }

    private fun lockPiece() {
        val p = piece ?: return
        val cs = cellsOf(p)
        var allAbove = true
        for (c in cs) if (c.y >= 0) { grid[c.y][c.x] = p.type; allAbove = false }
        if (allAbove) { gameOver(); return }
        stats.pieces++
        score += placeBonus(level)

        val ph = physics
        if (ph != null) { lockIntoPhysics(p, cs, ph); return }

        val keys = BooleanArray(C.ROWS * C.COLS)
        for (c in cs) if (c.y >= 0) keys[c.y * C.COLS + c.x] = true
        impactKeys = keys
        impactT = 0.0
        dropDist = pendingDrop; pendingDrop = 0

        val spin = tSpinKind()

        val rows = ArrayList<Int>()
        for (y in 0 until C.ROWS) {
            var full = true
            for (x in 0 until C.COLS) if (grid[y][x] == null) { full = false; break }
            if (full) rows.add(y)
        }
        clearRows = rows

        var perfect = false
        if (rows.isNotEmpty()) {
            perfect = true
            val inClear = BooleanArray(C.ROWS)
            for (y in rows) inClear[y] = true
            outer@ for (y in 0 until C.ROWS) {
                if (inClear[y]) continue
                for (x in 0 until C.COLS) if (grid[y][x] != null) { perfect = false; break@outer }
            }
        }

        applyScore(rows.size, spin, perfect)

        if (rows.isNotEmpty()) {
            fx.soundClear(rows.size)
            spawnSparks(rows)
            shakeBoard(if (rows.size == 4) 10.0 else 4.0)
            if (rows.size == 4) buzz(18, 40, 18) else buzz(18)
            state = GameState.CLEARING
            clearTimer = 0.0
        } else {
            fx.sound(Sfx.LOCK)
            canHold = true
            pullNext()
        }
    }

    /**
     * Режим физики: фигура перестаёт быть клетками сетки и уходит в мир
     * твёрдым телом. Считать ряды сразу нельзя — тело ещё не улеглось и,
     * если оно опирается на слишком узкое основание, сейчас завалится
     * набок. Поэтому партия переходит в ожидание.
     */
    private fun lockIntoPhysics(p: Piece, cs: List<Cell>, ph: PhysicsField) {
        pendingSpin = tSpinKind()      // правило трёх углов смотрит на сетку до пересборки
        ph.addPiece(cs, p.type)
        ph.rasterize(grid)
        piece = null
        physSpawn = true
        scoredThisLock = false
        pendingDrop = 0
        impactKeys = null
        impactT = C.OFF
        fx.sound(Sfx.LOCK)
        settleT = 0.0
        state = GameState.SETTLING
    }

    /**
     * Стакан замер — можно смотреть, что вышло. Ряд уходит, если вещество
     * закрыло его плотнее порога: целыми клетками, обрезками или боком
     * привалившегося тела — считается площадь, а не клетки. После реза
     * ждём снова: обвалившееся сверху может закрыть следующий ряд, и
     * цепочка продолжится сама собой.
     */
    private fun resolveSettled(ph: PhysicsField) {
        val rows = ph.fullRows(C.PHYS_FILL)
        if (rows.isNotEmpty()) {
            val perfect = ph.areaOutside(rows) < C.PHYS_EMPTY
            clearRows = rows
            applyScore(rows.size, pendingSpin, perfect)
            pendingSpin = null
            scoredThisLock = true
            fx.soundClear(rows.size)
            spawnSparks(rows)
            shakeBoard(if (rows.size >= 4) 10.0 else 4.0)
            if (rows.size >= 4) buzz(18, 40, 18) else buzz(18)
            clearTimer = 0.0
            state = GameState.CLEARING
            return
        }
        if (!scoredThisLock) applyScore(0, pendingSpin, false)
        pendingSpin = null
        scoredThisLock = false
        physMark = ph.mark()
        state = GameState.PLAYING
        if (physSpawn) {
            physSpawn = false
            canHold = true
            pullNext()
        }
    }

    /** Ход реза: 1 — нож у правой стенки, 0 — дошёл до левой. */
    fun cutProgress(): Float {
        if (state != GameState.CLEARING || physics == null) return -1f
        return (1.0 - expoOut(min(1.0, clearTimer / C.CUT_TIME))).toFloat()
    }

    // ================= очки =================

    private fun applyScore(n: Int, spin: Spin?, perfect: Boolean) {
        var pts = 0L
        var text = ""
        val lvl = level.toLong()
        // В обычной партии за раз уходит не больше четырёх рядов, и таблицы
        // оригинала ровно на столько и рассчитаны. В режиме физики рухнувшая
        // постройка может закрыть хоть весь стакан, поэтому за пределами
        // таблицы каждый лишний ряд идёт по отдельной ставке. Внутри
        // четырёх счёт остаётся ровно прежним.
        if (spin != null) {
            val k = min(n, 3)
            if (spin == Spin.FULL) {
                pts = longArrayOf(400, 800, 1200, 1600)[k] * lvl
                text = if (n > 0) "T-спин ×$n" else "T-спин"
            } else {
                pts = longArrayOf(100, 200, 400, 600)[k] * lvl
                text = if (n > 0) "Мини T-спин ×$n" else "Мини T-спин"
            }
            pts += EXTRA_LINE * (n - k) * lvl
        } else if (n > 0) {
            val k = min(n, 4)
            pts = longArrayOf(0, 100, 300, 500, 800)[k] * lvl
            text = if (n <= 4) arrayOf("", "Одна", "Две", "Три", "Тетрис")[k] else "Обвал ×$n"
            pts += EXTRA_LINE * (n - k) * lvl
        }
        val difficult = (n >= 4) || (spin != null && n > 0)
        if (difficult && backToBack && pts > 0) {
            pts = floor(pts * 1.5).toLong()
            text += " · подряд"
        }
        if (n > 0) backToBack = difficult

        // стакан очищен полностью — крупный бонус
        if (perfect && n > 0) {
            pts += longArrayOf(0, 800, 1200, 1800, 2000)[min(n, 4)] * lvl
            text = "Идеальная очистка"
            perfectFx()
        }

        if (n > 0) {
            combo++
            if (combo > 0) {
                pts += 50L * combo * lvl
                text += " · комбо $combo"
            }
            lines += n
            stats.lines += n
        } else combo = -1

        score += pts
        updateLevel()
        if (text.isNotEmpty()) fx.message(text)
        if (pts > 0) {
            val row = if (clearRows.isNotEmpty()) {
                clearRows.sum().toDouble() / clearRows.size
            } else {
                piece?.let { it.y + 1.0 } ?: (C.ROWS / 2.0)
            }
            val color = if (perfect) 0xFFA9E5A5.toInt() else 0xFFA8C7FA.toInt()
            spawnPop("+" + formatRu(pts), text, row, color)
        }
    }

    /**
     * Скорость растёт по линиям, как в оригинале, но упирается в потолок,
     * который зависит от счёта: до миллиона это двадцатая, дальше каждые
     * сто тысяч добавляют по одной. Проверяется на каждой фиксации — за
     * одну фигуру счёт может перевалить через границу и без очистки линий,
     * одними очками за сброс.
     */
    private fun updateLevel() {
        val target = min(startLevel + lines / 10, levelCap(score))
        if (target > level) { level = target; fx.sound(Sfx.LEVEL_UP); flashLevel() }
    }

    /** Ставка за каждый ряд сверх таблицы — только режим физики. */
    private val EXTRA_LINE = 300L

    /** Убираем линии и готовим смещения для анимации обвала. */
    private fun collapseRows() {
        val cleared = BooleanArray(C.ROWS)
        for (y in clearRows) cleared[y] = true
        val keptRows = ArrayList<Array<PieceType?>>()
        val keptShift = ArrayList<Int>()
        var below = 0
        for (y in C.ROWS - 1 downTo 0) {
            if (cleared[y]) { below++; continue }
            keptRows.add(grid[y]); keptShift.add(below)
        }
        val g = Array(C.ROWS) { arrayOfNulls<PieceType>(C.COLS) }
        val off = IntArray(C.ROWS)
        var idx = C.ROWS - 1
        for (i in keptRows.indices) { g[idx] = keptRows[i]; off[idx] = keptShift[i]; idx-- }
        for (y in idx downTo 0) { g[y] = arrayOfNulls(C.COLS); off[y] = 0 }
        grid = g
        fallOff = off
        clearRows = emptyList()
        fallTimer = 0.0
        state = GameState.FALLING
    }

    // ================= частицы =================

    /**
     * Распад стакана в режиме физики: пыль сыпется из самих кусков, а не из
     * клеток сетки. Обрубок меньше трети клетки в сетку не попадает вовсе,
     * и раньше он так и оставался висеть на экране, когда всё остальное
     * уже рассыпалось.
     */
    private fun buildDustPhysics(ph: PhysicsField) {
        val parts = ArrayList<Particle>()
        val delay = DoubleArray(C.ROWS * C.COLS) { Double.MAX_VALUE }
        val per = if (cellPx >= 24) 4 else 3
        val ps = 1.0 / per
        var total = 0.0
        for (f in ph.fragments()) {
            val idx = f.cy * C.COLS + f.cx
            var d = delay[idx]
            if (d == Double.MAX_VALUE) {
                d = f.cx * 20.0 + (C.ROWS - 1 - f.cy) * 6.0 + random.nextDouble() * 40.0
                delay[idx] = d
            }
            if (d + C.DUST_LIFE > total) total = d + C.DUST_LIFE

            var n = 0
            var wy = floor(f.minY / ps) * ps
            while (wy < f.maxY) {
                var wx = floor(f.minX / ps) * ps
                while (wx < f.maxX) {
                    if (containsPoint(f.world, wx + ps / 2, wy + ps / 2)) {
                        parts.add(dustAt(wx, wy, ps, f.type.color, d))
                        n++
                    }
                    wx += ps
                }
                wy += ps
            }
            // совсем мелкому обрубку не досталось ни одной точки сетки —
            // сыпем одну частицу по его середине, чтобы он не исчез молча
            if (n == 0) {
                parts.add(
                    dustAt(
                        (f.minX + f.maxX) / 2 - ps / 2,
                        (f.minY + f.maxY) / 2 - ps / 2,
                        ps, f.type.color, d,
                    )
                )
            }
        }
        dust = Dust(parts, delay, total + 160.0)
    }

    /** Одна пылинка: мир (y вверх) переводится в координаты стакана. */
    private fun dustAt(wx: Double, wy: Double, ps: Double, color: Int, d: Double) = Particle(
        x = wx.toFloat(),
        y = (C.ROWS - wy - ps).toFloat(),
        s = ps.toFloat(),
        vx = (random.nextDouble() * 1.5 - 0.4).toFloat(),
        vy = (-(0.35 + random.nextDouble() * 1.5)).toFloat(),
        color = color,
        delay = d + random.nextDouble() * 70.0,
    )

    /** Распад стакана: каждая клетка рассыпается в облако частиц. */
    private fun buildDust() {
        val ph = physics
        if (ph != null) { buildDustPhysics(ph); return }
        val parts = ArrayList<Particle>()
        val delay = DoubleArray(C.ROWS * C.COLS) { Double.MAX_VALUE }
        val per = if (cellPx >= 24) 4 else 3
        val ps = 1f / per
        var total = 0.0
        for (y in 0 until C.ROWS) for (x in 0 until C.COLS) {
            val t = grid[y][x] ?: continue
            val d = x * 20.0 + (C.ROWS - 1 - y) * 6.0 + random.nextDouble() * 40.0
            delay[y * C.COLS + x] = d
            if (d + C.DUST_LIFE > total) total = d + C.DUST_LIFE
            for (i in 0 until per) for (j in 0 until per) {
                parts.add(
                    Particle(
                        x = x + i * ps,
                        y = y + j * ps,
                        s = ps,
                        vx = (random.nextDouble() * 1.5 - 0.4).toFloat(),
                        vy = (-(0.35 + random.nextDouble() * 1.5)).toFloat(),
                        color = t.color,
                        delay = d + random.nextDouble() * 70.0,
                    )
                )
            }
        }
        dust = Dust(parts, delay, total + 160.0)
    }

    /** Искры от исчезающих линий — тот же материал, что и распад стакана. */
    private fun spawnSparks(rows: List<Int>) {
        val out = ArrayList<Particle>()
        // при обвале в полстакана дробить каждую клетку на девять частиц
        // незачем — кадр этого не стоит
        val per = if (rows.size > 6) 1 else if (cellPx >= 24) 3 else 2
        val ps = 1f / per
        for (y in rows) for (x in 0 until C.COLS) {
            val t = grid[y][x] ?: continue
            for (i in 0 until per) for (j in 0 until per) {
                out.add(
                    Particle(
                        x = x + i * ps,
                        y = y + j * ps,
                        s = ps,
                        vx = ((random.nextDouble() - 0.5) * 3.4).toFloat(),
                        vy = (-(0.1 + random.nextDouble() * 1.1)).toFloat(),
                        color = t.color,
                        delay = random.nextDouble() * 70.0,
                    )
                )
            }
        }
        sparks = out
        sparkT = 0.0
    }

    private fun spawnPop(text: String, sub: String, row: Double, color: Int) {
        pops.add(Pop(text, sub, row, color))
        if (pops.size > 6) pops.removeAt(0)
    }

    /** Идеальная очистка: вспышка, расходящиеся кольца, встряска. */
    private fun perfectFx() {
        pcT = 0.0
        fx.sound(Sfx.PERFECT)
        buzz(20, 50, 20, 50, 30)
        shakeBoard(12.0)
        fx.boardGlow()
    }

    private fun flashLevel() {
        fx.levelBump()
        fx.boardGlow()
        sweepT = 0.0
        buzz(12, 50, 12)
    }

    private fun shakeBoard(mag: Double) {
        if (reduceMotion) return
        shakeMag = mag
        shakeT = 0.0
    }

    private fun buzz(vararg pattern: Long) {
        if (reduceMotion) return
        fx.vibrate(*pattern)
    }

    // ================= опыт =================

    fun xpProgress(): XpProgress {
        val lo = Xp.BOUNDS[xpRank]
        val hi = Xp.BOUNDS[xpRank + 1]
        val p = ((xpTotal - lo).toDouble() / (hi - lo).toDouble()).coerceIn(0.0, 1.0)
        return XpProgress(lo, hi, p.toFloat())
    }

    /**
     * Записывает текущий счёт в рекорд, если он больше. В браузере рекорд
     * обновлялся только по концу партии, но теперь он ещё и хранится между
     * запусками — иначе выход посреди партии терял бы уже показанный рекорд.
     */
    fun commitBest() {
        if (score > best) best = score
    }

    /**
     * Полный сброс из меню: рекорд, уровень игрока и вся статистика — в
     * ноль. Счёт последней партии обнуляется тоже: он ещё лежит в `score`,
     * и первое же сворачивание (`commitBest`) вернуло бы его в рекорд.
     */
    fun resetProgress() {
        xpTotal = 0L
        xpRank = 0
        best = 0L
        bestAtStart = 0L
        xpAtStart = 0L
        score = 0L
        shownScore = 0.0
        xpLastScore = 0L
        stats.reset()
    }

    /** Накопленный опыт и рекорд переживают перезапуск приложения. */
    fun restoreProgress(totalXp: Long, rank: Int, bestScore: Long) {
        xpTotal = totalXp.coerceAtLeast(0L)
        xpRank = rank.coerceIn(0, Xp.BOUNDS.size - 2)
        best = bestScore.coerceAtLeast(0L)
    }

    private fun addXp(n: Long) {
        if (n <= 0) return
        xpTotal += n
        while (xpRank < Xp.BOUNDS.size - 2 && xpTotal >= Xp.BOUNDS[xpRank + 1]) {
            xpRank++
            fx.sound(Sfx.RANK)
            buzz(14, 45, 14)
            fx.xpRankUp()
        }
    }

    // ================= поток игры =================

    fun startGame() {
        grid = emptyGrid()
        bag.clear(); queue.clear()
        repeat(C.QUEUE_SIZE) { queue.addLast(nextType()) }
        hold = null; canHold = true
        score = 0; lines = 0; level = startLevel; combo = -1; backToBack = false
        clearRows = emptyList(); clearTimer = 0.0; trails = emptyList(); fallOff = IntArray(0)
        impactKeys = null; impactT = C.OFF; shakeT = C.OFF; nextT = C.OFF
        shownScore = 0.0; dieT = 0.0; dust = null
        sparks = emptyList(); sparkT = C.OFF; spawnT = C.OFF; sweepT = C.OFF; blasting = false
        pops.clear(); pcT = C.OFF
        xpLastScore = 0
        firstSpawn = true; revealFirst = false
        piece = null
        physics = if (physicsMode) PhysicsField() else null
        settleT = 0.0; pendingSpin = null; physSpawn = false; scoredThisLock = false
        physMark = physics?.mark() ?: 0L
        releaseAllKeys()
        repLeft = 0.0; repRight = 0.0; repDown = 0.0
        stats.games++
        playMs = 0.0
        xpAtStart = xpTotal
        bestAtStart = best
        fx.sound(Sfx.START)
        state = GameState.INTRO
        introT = 0.0
    }

    private fun gameOver() {
        state = GameState.DYING
        dieT = 0.0
        buildDust()
        if (reduceMotion) dieT = dust!!.total
        fx.sound(Sfx.OVER)
        buzz(40, 60, 80)
        shakeBoard(7.0)
        if (score > best) best = score
    }

    fun togglePause() {
        if (state == GameState.PLAYING) {
            state = GameState.PAUSED
            fx.sound(Sfx.PAUSE)
        } else if (state == GameState.PAUSED) {
            state = GameState.PLAYING
            fx.sound(Sfx.RESUME)
        }
    }

    fun backToMenu() {
        state = GameState.MENU
        blasting = false
    }

    /** Клавиши отпускаются при сворачивании окна, игра встаёт на паузу. */
    fun releaseAllKeys() {
        keyLeft = false; keyRight = false; keyDown = false
    }

    // ================= снимок партии =================

    /**
     * Партия в строку: поле, активная фигура, карман, очередь, мешок, счёт
     * и часы. В режиме физики следом идут многоугольники стакана — иначе
     * завалившиеся набок фигуры выпрямились бы при возврате.
     *
     * Статистика за всё время сюда не попадает: она живёт отдельно и уже
     * сохранена. Иначе партия при возврате посчиталась бы дважды.
     */
    fun snapshot(): String {
        val sb = StringBuilder(1024)
        sb.append(SAVE_TAG).append('\n')
        sb.append(score).append(' ').append(lines).append(' ').append(level).append(' ')
            .append(combo).append(' ').append(if (backToBack) 1 else 0).append(' ')
            .append(playMs.toLong()).append(' ').append(xpAtStart).append(' ')
            .append(bestAtStart).append(' ').append(if (canHold) 1 else 0).append(' ')
            .append(if (physics != null) 1 else 0).append('\n')
        for (y in 0 until C.ROWS) {
            for (x in 0 until C.COLS) sb.append(grid[y][x]?.name?.get(0) ?: '.')
            sb.append('\n')
        }
        val p = piece
        if (p == null) sb.append('-') else {
            sb.append(p.type.name).append(' ').append(p.rot).append(' ')
                .append(p.x).append(' ').append(p.y)
        }
        sb.append('\n')
        sb.append(hold?.name ?: "-").append('\n')
        for (t in queue) sb.append(t.name)
        sb.append('\n')
        for (t in bag.state()) sb.append(t.name)
        sb.append('\n')
        physics?.let { for (line in it.snapshot()) sb.append(line).append('\n') }
        return sb.toString()
    }

    /**
     * Поднять партию из снимка. Возвращается она на паузе: игрок сам
     * решает, когда фигура снова поедет вниз. false — снимок чужой или
     * испорчен, и трогать партию не стали.
     */
    fun restore(text: String): Boolean {
        val ls = text.lines()
        if (ls.size < C.ROWS + 6 || ls[0].trim() != SAVE_TAG) return false
        val h = ls[1].trim().split(' ')
        if (h.size < 10) return false
        val newScore = h[0].toLongOrNull() ?: return false
        val newLines = h[1].toIntOrNull() ?: return false
        val newLevel = h[2].toIntOrNull() ?: return false
        val newCombo = h[3].toIntOrNull() ?: return false
        val newPlay = h[5].toLongOrNull() ?: return false

        val newGrid = emptyGrid()
        for (y in 0 until C.ROWS) {
            val row = ls[2 + y]
            if (row.length < C.COLS) return false
            for (x in 0 until C.COLS) {
                val c = row[x]
                if (c != '.') newGrid[y][x] = typeOfLetter(c) ?: return false
            }
        }
        var i = 2 + C.ROWS
        val pieceLine = ls[i++].trim()
        val newPiece = if (pieceLine == "-") null else {
            val q = pieceLine.split(' ')
            if (q.size < 4) return false
            Piece(
                typeOfLetter(q[0].firstOrNull() ?: ' ') ?: return false,
                q[1].toIntOrNull() ?: return false,
                q[2].toIntOrNull() ?: return false,
                q[3].toIntOrNull() ?: return false,
            )
        }
        val holdLine = ls[i++].trim()
        val newHold = if (holdLine == "-") null else typeOfLetter(holdLine[0]) ?: return false
        val newQueue = ls[i++].trim().map { typeOfLetter(it) ?: return false }
        if (newQueue.isEmpty()) return false
        val newBag = ls[i++].trim().mapNotNull { typeOfLetter(it) }
        val field = if (h[9] == "1") PhysicsField().also { it.restore(ls.drop(i)) } else null

        grid = newGrid
        queue.clear(); queue.addAll(newQueue)
        bag.restore(newBag)
        piece = newPiece
        hold = newHold
        canHold = h[8] == "1"
        score = newScore
        lines = newLines
        level = newLevel
        combo = newCombo
        backToBack = h[4] == "1"
        playMs = newPlay.toDouble()
        xpAtStart = h[6].toLongOrNull() ?: xpTotal
        bestAtStart = h[7].toLongOrNull() ?: best
        // счётчик очков не должен набегать с нуля, а опыт — начисляться заново
        shownScore = score.toDouble()
        xpLastScore = score
        physics = field

        clearRows = emptyList(); clearTimer = 0.0
        trails = emptyList(); trailTimer = 0.0
        fallOff = IntArray(0); fallTimer = 0.0
        impactKeys = null; impactT = C.OFF; shakeT = C.OFF; shakeMag = 0.0; nextT = C.OFF
        dieT = 0.0; dust = null; sparks = emptyList(); sparkT = C.OFF
        spawnT = C.OFF; sweepT = C.OFF; blasting = false; pops.clear(); pcT = C.OFF
        rotT = C.OFF; introT = 0.0
        dropTimer = 0.0; lockTimer = 0.0; lockResets = 0; grounded = false
        firstSpawn = false; revealFirst = true
        settleT = 0.0; pendingSpin = null; physSpawn = false; scoredThisLock = false
        physMark = physics?.mark() ?: 0L
        releaseAllKeys(); repLeft = 0.0; repRight = 0.0; repDown = 0.0
        lastMoveWasRotation = false; lastKickIndex = 0
        state = GameState.PAUSED
        return true
    }

    // ================= кадр =================

    fun update(dtRaw: Double) {
        val dt = if (dtRaw > 100.0) 100.0 else dtRaw

        if (trails.isNotEmpty()) {
            trailTimer += dt
            if (trailTimer > C.TRAIL_TIME) trails = emptyList()
        }
        if (impactT < C.IMPACT_TIME + C.DROP_TRAVEL) impactT += dt
        if (shakeT < C.SHAKE_TIME) shakeT += dt
        if (nextT < C.NEXT_TIME) nextT += dt
        if (score > xpLastScore) {
            val gained = score - xpLastScore
            addXp(gained)
            stats.score += gained
            xpLastScore = score
        }
        if (sparkT < C.SPARK_LIFE + 90) sparkT += dt
        if (spawnT < C.FIRST_TOTAL + 40) spawnT += dt
        if (rotT < C.ROT_TIME) rotT += dt
        if (sweepT < C.SWEEP_TIME) sweepT += dt
        if (pcT < C.PC_TIME) pcT += dt
        for (i in pops.indices.reversed()) {
            pops[i].t += dt
            if (pops[i].t >= C.POP_LIFE) pops.removeAt(i)
        }
        animScore(dt)

        if (state == GameState.PLAYING || state == GameState.CLEARING ||
            state == GameState.FALLING || state == GameState.SETTLING
        ) {
            playMs += dt
            stats.timeMs += dt
        }

        // мир живёт только пока идёт партия: во время реза он заморожен,
        // а после проигрыша стакан уже рассыпается в пыль
        val ph = physics
        if (ph != null && (state == GameState.PLAYING || state == GameState.SETTLING)) {
            ph.step(dt)
            ph.rasterize(grid)
        }

        when (state) {
            GameState.INTRO -> {
                introT += dt
                if (introT >= C.INTRO_TIME) {
                    state = GameState.PLAYING
                    pullNext()
                }
            }

            GameState.PLAYING -> {
                // обломок мог доехать до нужной плотности уже после того,
                // как мы выпустили новую фигуру. Смотрим ровно на тех кадрах,
                // где мир сдвинулся, — включая последний, на котором он замер:
                // пока стакан стоит, проверять нечего и это ничего не стоит.
                if (ph != null && ph.mark() != physMark) {
                    physMark = ph.mark()
                    if (ph.fullRows(C.PHYS_FILL).isNotEmpty()) {
                        settleT = 0.0
                        state = GameState.SETTLING
                        return
                    }
                }
                handleRepeat(dt)
                val revealing = revealFirst && spawnT < C.FIRST_TOTAL
                if (!revealing) dropTimer += dt
                val g = gravityMs(level)
                while (!revealing && dropTimer >= g) {
                    dropTimer -= g
                    val p = piece ?: break
                    if (!collides(p, p.rot, p.x, p.y + 1)) {
                        piece = p.copy(y = p.y + 1)
                        lastMoveWasRotation = false
                    } else {
                        grounded = true
                        break
                    }
                }
                val p = piece
                if (p != null) {
                    if (collides(p, p.rot, p.x, p.y + 1)) {
                        grounded = true
                        lockTimer += dt
                        if (lockTimer >= C.LOCK_DELAY) lockPiece()
                    } else {
                        grounded = false
                        lockTimer = 0.0
                    }
                }
            }

            GameState.CLEARING -> {
                clearTimer += dt
                if (ph != null) {
                    if (clearTimer >= C.CUT_TIME) {
                        ph.cutRows(clearRows)
                        ph.rasterize(grid)
                        clearRows = emptyList()
                        settleT = 0.0
                        state = GameState.SETTLING
                    }
                } else if (clearTimer >= C.CLEAR_TIME) collapseRows()
            }

            GameState.SETTLING -> {
                settleT += dt
                if (ph == null) state = GameState.PLAYING
                else if (settleT >= C.SETTLE_MIN && (ph.quiet() || settleT >= C.SETTLE_MAX)) {
                    resolveSettled(ph)
                }
            }

            GameState.FALLING -> {
                fallTimer += dt
                if (fallTimer >= C.FALL_TIME) {
                    state = GameState.PLAYING
                    canHold = true
                    fallOff = IntArray(0)
                    pullNext()
                }
            }

            GameState.DYING -> {
                dieT += dt
                val d = dust
                if (d != null && !blasting && dieT >= d.total) {
                    blasting = true
                    fx.sound(Sfx.WHOOSH)
                    buzz(30)
                }
                if (d == null || dieT >= d.total + C.WIPE_TIME + 160) state = GameState.OVER
            }

            else -> Unit
        }
    }

    private fun animScore(dt: Double) {
        if (shownScore == score.toDouble()) return
        val diff = score - shownScore
        shownScore += diff * min(1.0, dt / 110.0)
        if (abs(score - shownScore) < 0.6) shownScore = score.toDouble()
    }

    // ================= автоповтор =================

    private fun handleRepeat(dt: Double) {
        repLeft = repeatAxis(keyLeft, repLeft, dt, -1)
        repRight = repeatAxis(keyRight, repRight, dt, 1)
        if (keyDown) {
            repDown += dt
            val interval = min(45.0, gravityMs(level) / 2.0)
            while (repDown >= interval) { repDown -= interval; softDrop() }
        } else repDown = 0.0
    }

    private fun repeatAxis(held: Boolean, acc: Double, dt: Double, dx: Int): Double {
        if (!held) return 0.0
        var r = acc + dt
        if (r >= C.DAS) {
            val over = r - C.DAS
            val steps = floor(over / C.ARR).toInt() + 1
            repeat(steps) { move(dx) }
            r = C.DAS + (over % C.ARR)
        }
        return r
    }

    /** Нажатие: первый шаг сразу, дальше автоповтор через DAS. */
    fun pressLeft() { if (!keyLeft) { keyLeft = true; repLeft = 0.0; move(-1) } }
    fun pressRight() { if (!keyRight) { keyRight = true; repRight = 0.0; move(1) } }
    fun pressDown() { if (!keyDown) { keyDown = true; repDown = 0.0; softDrop() } }

    // ================= помощники отрисовки =================

    /** Смещение и сплющивание для клеток, только что упавших на место. */
    fun impactFx(): ImpactFx? {
        if (impactKeys == null || impactT >= C.DROP_TRAVEL + C.IMPACT_TIME) return null
        val travel = if (dropDist != 0) min(1.0, impactT / C.DROP_TRAVEL) else 1.0
        val off = dropDist * (1 - expoOut(travel))
        var b = 0.0
        if (travel >= 1.0) {
            val t = (impactT - (if (dropDist != 0) C.DROP_TRAVEL else 0.0)) / C.IMPACT_TIME
            if (t < 1) b = 1 - expoOut(max(0.0, t))
        }
        return ImpactFx(off, 1 + 0.17 * b, 1 - 0.24 * b)
    }

    fun isImpactCell(x: Int, y: Int): Boolean = impactKeys?.get(y * C.COLS + x) == true

    /** Ряд во время обвала едет вниз по той же кривой expo out. */
    fun rowOffset(y: Int): Double {
        if (state == GameState.FALLING && fallOff.isNotEmpty()) {
            val e = expoOut(min(1.0, fallTimer / C.FALL_TIME))
            return y - fallOff[y] * (1 - e)
        }
        return y.toDouble()
    }

    /** Где встанет фигура при сбросе. */
    fun ghostY(): Int {
        val p = piece ?: return 0
        var gy = p.y
        while (!collides(p, p.rot, p.x, gy + 1)) gy++
        return gy
    }

    /** Задержка исчезновения клетки при распаде стакана. */
    fun dustDelay(x: Int, y: Int): Double = dust?.delay?.get(y * C.COLS + x) ?: Double.MAX_VALUE

    // ================= крючки для тестов =================

    /** Ставит игру в состояние партии без вступительной анимации. */
    fun debugStartImmediate() {
        startGame()
        state = GameState.PLAYING
        introT = C.INTRO_TIME
        pullNext()
        spawnT = C.FIRST_TOTAL + 100
        revealFirst = false
    }

    fun debugSet(x: Int, y: Int, t: PieceType?) { grid[y][x] = t }

    fun debugPiece(p: Piece) { piece = p }

    fun debugState(s: GameState) { state = s }

    fun debugClearGrid() { grid = emptyGrid() }

    fun debugFillRow(y: Int, exceptX: Int = -1, t: PieceType = PieceType.L) {
        for (x in 0 until C.COLS) grid[y][x] = if (x == exceptX) null else t
    }

    fun debugRotationFlag(v: Boolean) { lastMoveWasRotation = v }

    fun debugKickIndex(v: Int) { lastKickIndex = v }

    fun debugLock() { lockPiece() }

    fun debugSetBackToBack(v: Boolean) { backToBack = v }

    fun debugSetCombo(v: Int) { combo = v }

    fun debugSetLevel(v: Int) { level = v }

    fun debugSetScore(v: Long) { score = v; xpLastScore = v }

    fun debugSetLines(v: Int) { lines = v }

    fun debugSetPlayMs(v: Double) { playMs = v }

    fun debugSetGrounded(v: Boolean) { grounded = v }

    fun debugApplyScore(n: Int, spin: Spin?, perfect: Boolean) = applyScore(n, spin, perfect)

    fun debugSetClearRows(rows: List<Int>) { clearRows = rows }

    fun debugCollapse() = collapseRows()

    /** Включает мир твёрдых тел прямо посреди партии — только для тестов. */
    fun debugEnablePhysics() {
        physicsMode = true
        physics = PhysicsField()
        physics!!.rasterize(grid)
    }

    fun debugSetClearTimer(v: Double) { clearTimer = v }

    fun debugSettle(maxMs: Double = 4000.0) {
        val ph = physics ?: return
        var t = 0.0
        while (t < maxMs) { ph.step(16.0); t += 16.0; if (t > 100 && ph.quiet()) break }
        ph.rasterize(grid)
    }
}

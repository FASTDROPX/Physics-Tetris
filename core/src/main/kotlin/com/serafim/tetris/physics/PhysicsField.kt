package com.serafim.tetris.physics

import com.serafim.tetris.game.C
import com.serafim.tetris.game.Cell
import com.serafim.tetris.game.PieceType
import com.serafim.tetris.game.typeOfLetter
import org.dyn4j.dynamics.Body
import org.dyn4j.dynamics.BodyFixture
import org.dyn4j.geometry.Geometry
import org.dyn4j.geometry.MassType
import org.dyn4j.geometry.Polygon
import org.dyn4j.geometry.Transform
import org.dyn4j.geometry.Vector2
import org.dyn4j.world.World
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Один выпуклый кусок вещества: где он сейчас и какого был цвета. */
class Frag(
    @JvmField val type: PieceType,
    /** Мир: x вправо, y вверх, пол на нуле. */
    @JvmField val world: Poly,
    /** Стакан: x вправо, y вниз, в клетках — для отрисовки. */
    @JvmField val view: FloatArray,
    @JvmField val minX: Double,
    @JvmField val minY: Double,
    @JvmField val maxX: Double,
    @JvmField val maxY: Double,
    /** Клетка стакана, в которой лежит центр тяжести куска. */
    @JvmField val cx: Int,
    @JvmField val cy: Int,
)

/**
 * Режим физики: после фиксации фигура перестаёт быть набором клеток и
 * становится твёрдым телом. Дальше всё решают обычные законы механики —
 * тело с узким основанием само заваливается в ту сторону, куда его тянет
 * собственный вес.
 *
 * Единица мира — одна клетка стакана, тяготение земное, так что фигура
 * пролетает стакан примерно за две секунды. Начало координат в левом
 * нижнем углу, ось y смотрит вверх: у стакана она смотрит вниз, поэтому
 * все переходы идут через `rows - y`.
 *
 * Сетка клеток при этом никуда не девается — она пересобирается из
 * многоугольников каждый кадр и служит ровно тем же, чем служила раньше:
 * по ней ходит, поворачивается и упирается активная фигура. Вся логика
 * тетриса поэтому осталась нетронутой.
 */
class PhysicsField(
    private val cols: Int = C.COLS,
    private val rows: Int = C.ROWS,
) {

    private val world = World<Body>()
    private val frags = ArrayList<Frag>(128)
    private var dirty = true

    /**
     * Счётчик изменений геометрии. Пока он стоит на месте, сетку клеток
     * пересобирать не из чего — а это самая дорогая операция кадра.
     */
    private var stamp = 1L
    private var rasterStamp = 0L
    private var cellStamp = 0L

    /** Накопители растеризации живут вне кадра — их нельзя выделять по 60 раз в секунду. */
    private val cover = DoubleArray(rows * cols)
    private val bestArea = DoubleArray(rows * cols)
    private val bestType = arrayOfNulls<PieceType>(rows * cols)


    init {
        val s = world.settings
        s.isAtRestDetectionEnabled = true
        s.maximumAtRestLinearVelocity = 0.10
        s.maximumAtRestAngularVelocity = 0.30
        s.velocityConstraintSolverIterations = 12
        s.positionConstraintSolverIterations = 8
        addWall(cols / 2.0, -0.5, cols + 8.0, 1.0)          // пол
        addWall(-0.5, rows.toDouble(), 1.0, rows * 4.0)     // левая стенка
        addWall(cols + 0.5, rows.toDouble(), 1.0, rows * 4.0)
    }

    private fun addWall(cx: Double, cy: Double, w: Double, h: Double) {
        val b = Body()
        val f = b.addFixture(Geometry.createRectangle(w, h))
        f.friction = WALL_FRICTION
        f.restitution = 0.0
        b.translate(cx, cy)
        b.setMass(MassType.INFINITE)
        world.addBody(b)
    }

    // ================= жизнь мира =================

    /** Шаг мира. `dtMs` — тот же кадровый шаг, что и у остальной игры. */
    fun step(dtMs: Double) {
        val stepped = world.update(dtMs / 1000.0)
        if (stepped && anyMoving()) { dirty = true; stamp++ }
    }

    private fun anyMoving(): Boolean {
        val bs = world.bodies
        for (i in bs.indices) {
            val b = bs[i]
            if (b.isDynamic && !b.isAtRest) return true
        }
        return false
    }

    /**
     * Всё замерло. Проверяем скорости напрямую, а не флаг покоя движка:
     * тот загорается лишь через полсекунды тишины, и партия начала бы
     * заикаться на каждой фигуре.
     */
    fun quiet(): Boolean {
        val bs = world.bodies
        for (i in bs.indices) {
            val b = bs[i]
            if (!b.isDynamic) continue
            val v = b.linearVelocity
            if (v.x * v.x + v.y * v.y > 0.05) return false
            if (abs(b.angularVelocity) > 0.2) return false
        }
        return true
    }

    /** Есть ли вообще вещество в стакане — нужно для идеальной очистки. */
    fun isEmpty(): Boolean {
        refresh()
        return frags.isEmpty()
    }

    // ================= добавление фигуры =================

    /** Зафиксированная фигура превращается в одно твёрдое тело из своих клеток. */
    fun addPiece(cells: List<Cell>, type: PieceType) {
        val b = Body()
        var n = 0
        for (c in cells) {
            if (c.y < 0) continue
            val r = Geometry.createRectangle(1.0, 1.0)
            r.translate(c.x + 0.5, rows - c.y - 0.5)
            val f = b.addFixture(r, DENSITY, FRICTION, RESTITUTION)
            f.userData = type
            n++
        }
        if (n == 0) return
        b.setMass(MassType.NORMAL)
        b.linearDamping = LIN_DAMP
        b.angularDamping = ANG_DAMP
        world.addBody(b)
        dirty = true
        stamp++
    }

    // ================= состояние для отрисовки и сетки =================

    /** Пересобирает мировые многоугольники, если что-то сдвинулось. */
    fun refresh() {
        if (!dirty) return
        dirty = false
        frags.clear()
        val bs = world.bodies
        val bounds = DoubleArray(4)
        val centre = DoubleArray(2)
        for (i in bs.indices) {
            val b = bs[i]
            if (!b.isDynamic) continue
            val tx = b.transform
            val fs = b.fixtures
            for (j in fs.indices) {
                val f = fs[j]
                val p = worldPoly(f, tx) ?: continue
                polyBounds(p, bounds)
                polyCentroid(p, centre)
                frags.add(
                    Frag(
                        type = f.userData as? PieceType ?: PieceType.L,
                        world = p,
                        view = toView(p),
                        minX = bounds[0], minY = bounds[1],
                        maxX = bounds[2], maxY = bounds[3],
                        cx = floor(centre[0]).toInt().coerceIn(0, cols - 1),
                        cy = (rows - 1 - floor(centre[1]).toInt()).coerceIn(0, rows - 1),
                    )
                )
            }
        }
    }

    /**
     * Отметка состояния мира. Пока она не менялась, геометрия та же самая,
     * и пересчитывать по ней нечего.
     */
    fun mark(): Long = stamp

    /** Сколько в стакане самостоятельных тел. */
    fun bodyCount(): Int {
        var n = 0
        val bs = world.bodies
        for (i in bs.indices) if (bs[i].isDynamic) n++
        return n
    }

    /** Куски в координатах стакана — то, что рисует холст. */
    fun fragments(): List<Frag> {
        refresh()
        return frags
    }

    private fun worldPoly(f: BodyFixture, tx: Transform): Poly? {
        val shape = f.shape as? Polygon ?: return null
        val vs = shape.vertices
        val out = DoubleArray(vs.size * 2)
        for (i in vs.indices) {
            out[i * 2] = tx.getTransformedX(vs[i])
            out[i * 2 + 1] = tx.getTransformedY(vs[i])
        }
        return out
    }

    /** Мир (y вверх) → стакан (y вниз), обход при этом переворачивается. */
    private fun toView(p: Poly): FloatArray {
        val n = p.size / 2
        val out = FloatArray(p.size)
        for (i in 0 until n) {
            out[i * 2] = p[i * 2].toFloat()
            out[i * 2 + 1] = (rows - p[i * 2 + 1]).toFloat()
        }
        return out
    }

    // ================= сетка =================

    /**
     * Пересобирает сетку клеток из многоугольников: клетка занята, если
     * вещество покрывает её больше чем на `CELL_FILL`. По этой сетке
     * работает всё остальное — столкновения, призрак, правило трёх углов
     * и распад стакана.
     */
    /**
     * Площадь вещества в каждой клетке стакана. Один проход обслуживает и
     * сетку столкновений, и подсчёт заполненности рядов — считать его
     * дважды за кадр незачем.
     */
    private fun cells() {
        if (cellStamp == stamp) return
        cellStamp = stamp
        refresh()
        java.util.Arrays.fill(cover, 0.0)
        java.util.Arrays.fill(bestArea, 0.0)
        java.util.Arrays.fill(bestType, null)

        for (fi in frags.indices) {
            val f = frags[fi]
            // за края стакана не выходим: тело, торчащее выше верхнего ряда,
            // не должно приписывать свою площадь нулевому
            val x0 = max(0, floor(f.minX).toInt())
            val x1 = min(cols - 1, floor(f.maxX - 1e-9).toInt())
            if (x1 < x0) continue
            val y0 = max(0, floor(f.minY).toInt())
            val y1 = min(rows - 1, floor(f.maxY - 1e-9).toInt())
            if (y1 < y0) continue
            for (wy in y0..y1) for (wx in x0..x1) {
                val a = polyArea(
                    clipBox(f.world, wx.toDouble(), wy.toDouble(), wx + 1.0, wy + 1.0)
                )
                if (a <= 1e-9) continue
                val idx = (rows - 1 - wy) * cols + wx
                cover[idx] += a
                if (a > bestArea[idx]) { bestArea[idx] = a; bestType[idx] = f.type }
            }
        }
    }

    fun rasterize(grid: Array<Array<PieceType?>>): Boolean {
        if (rasterStamp == stamp) return false
        rasterStamp = stamp
        cells()

        for (y in 0 until rows) {
            val row = grid[y]
            val base = y * cols
            for (x in 0 until cols) {
                row[x] = if (cover[base + x] >= CELL_FILL) bestType[base + x] else null
            }
        }
        return true
    }

    // ================= очистка рядов =================

    /**
     * Ряды, заполненные плотнее `fill`. Считаются занятые клетки, а не
     * площадь: осколок закрывает своё место в ряду ровно так же, как целый
     * блок, а наклонившееся тело закрывает всё, через что проходит.
     *
     * Клетка считается занятой по тому же порогу `CELL_FILL`, по которому
     * игра и так решает, что сквозь неё не пройти. Это не произвольное
     * число: колонка идёт в счёт ровно тогда, когда фигуру туда уже не
     * уронить. Прежний порог был в сто раз мягче, и скользящий угол
     * наклонённой фигуры засчитывался за целую клетку — ряд уходил,
     * держа в себе полторы клетки вещества из десяти.
     */
    fun fullRows(fill: Double): List<Int> {
        cells()
        val out = ArrayList<Int>(4)
        for (y in 0 until rows) {
            var n = 0
            val base = y * cols
            for (x in 0 until cols) if (cover[base + x] >= CELL_FILL) n++
            if (n.toDouble() / cols >= fill) out.add(y)
        }
        return out
    }

    /** Сколько вещества останется, если срезать эти ряды. Ноль — идеальная очистка. */
    fun areaOutside(rowsToCut: List<Int>): Double {
        cells()
        var total = 0.0
        for (i in cover.indices) total += cover[i]
        for (y in rowsToCut) {
            val base = y * cols
            for (x in 0 until cols) total -= cover[base + x]
        }
        return total
    }

    /**
     * Срезает полосы рядов. Каждый кусок вещества рубится по двум
     * горизонталям — верхней и нижней границе полосы, — середина
     * выбрасывается, а от куска может остаться сколь угодно кривой
     * многоугольник.
     *
     * Уцелевшие обломки одного тела снова связываются в тело, но только
     * те, что действительно касаются друг друга: то, что держалось на
     * срезанном, дальше падает само по себе. Тела, которых нож не
     * коснулся, не трогаем вовсе: пересборка стоила бы им скоростей и
     * наработанных контактов.
     */
    fun cutRows(rowsToCut: List<Int>) {
        if (rowsToCut.isEmpty()) return
        val bands = mergeRuns(rowsToCut.sorted()).map { r ->
            // ряд r стакана — это полоса [rows-r-1, rows-r] мира
            doubleArrayOf((rows - r.last - 1).toDouble(), (rows - r.first).toDouble())
        }.sortedBy { it[0] }

        // то, что остаётся: промежутки между полосами плюс хвост сверху.
        // Всё, что ниже пола, отбрасывается: улёгшееся тело утопает в опоре
        // на допуск решателя, и без этого от нижнего ряда оставались бы
        // волоски в сотую клетки — невидимые, но с массой и столкновениями.
        val keep = ArrayList<DoubleArray>(bands.size + 1)
        var low = 0.0
        for (b in bands) {
            if (b[0] > low) keep.add(doubleArrayOf(low, b[0]))
            low = max(low, b[1])
        }
        keep.add(doubleArrayOf(low, rows * 4.0))

        val lo = bands.first()[0]
        val hi = bands.last()[1]
        val old = ArrayList(world.bodies)
        val aabb = DoubleArray(4)
        for (b in old) {
            if (!b.isDynamic) continue
            bodyBounds(b, aabb)
            if (!overlaps(aabb[1], aabb[3], lo, hi)) continue   // нож сюда не дошёл
            val tx = b.transform
            val vx = b.linearVelocity.x
            val vy = b.linearVelocity.y
            val w = b.angularVelocity
            val pieces = ArrayList<Frag>(8)
            val bounds = DoubleArray(4)
            val nb = DoubleArray(4)
            for (f in b.fixtures) {
                val p = worldPoly(f, tx) ?: continue
                val type = f.userData as? PieceType ?: PieceType.L
                polyBounds(p, bounds)
                for (k in keep) {
                    if (!overlaps(bounds[1], bounds[3], k[0], k[1])) continue
                    val cut = sanitize(clipBand(p, k[0], k[1]), MIN_FRAGMENT) ?: continue
                    polyBounds(cut, nb)
                    pieces.add(Frag(type, cut, EMPTY_VIEW, nb[0], nb[1], nb[2], nb[3], 0, 0))
                }
            }
            world.removeBody(b)
            for (group in group(pieces)) {
                val body = makeBody(group, vx, vy, w) ?: continue
                world.addBody(body)
            }
        }
        dirty = true
        stamp++
        refresh()
    }

    // ================= снимок стакана =================

    /**
     * Вещество стакана строками: цвет куска и его мировые вершины. Тела
     * при этом не сохраняются — только их геометрия на текущий миг, и
     * этого хватает: снимок делается, когда стакан уже успокоился.
     */
    fun snapshot(): List<String> {
        refresh()
        return frags.map { f ->
            val sb = StringBuilder(f.type.name)
            for (v in f.world) sb.append(' ').append((v * 1000).roundToInt() / 1000.0)
            sb.toString()
        }
    }

    /**
     * Обратно: куски, которые касаются друг друга, снова собираются в одно
     * тело — тем же правилом, каким стакан пересобирается после разреза
     * линии. Скорости нулевые, стакан стоит на месте.
     */
    fun restore(rows: List<String>) {
        for (b in ArrayList(world.bodies)) if (b.isDynamic) world.removeBody(b)
        val pieces = ArrayList<Frag>(rows.size)
        val bounds = DoubleArray(4)
        for (line in rows) {
            val parts = line.trim().split(' ')
            if (parts.size < 7) continue                 // меньше трёх вершин — не многоугольник
            val type = typeOfLetter(parts[0].firstOrNull() ?: ' ') ?: continue
            val poly = DoubleArray(parts.size - 1)
            var ok = true
            for (i in poly.indices) {
                val v = parts[i + 1].toDoubleOrNull()
                if (v == null) { ok = false; break }
                poly[i] = v
            }
            if (!ok || poly.size % 2 != 0) continue
            polyBounds(poly, bounds)
            pieces.add(Frag(type, poly, EMPTY_VIEW, bounds[0], bounds[1], bounds[2], bounds[3], 0, 0))
        }
        for (group in group(pieces)) {
            val body = makeBody(group, 0.0, 0.0, 0.0) ?: continue
            world.addBody(body)
        }
        dirty = true
        stamp++
        refresh()
    }

    private fun bodyBounds(b: Body, out: DoubleArray) {
        val a = b.createAABB()
        out[0] = a.minX; out[1] = a.minY; out[2] = a.maxX; out[3] = a.maxY
    }

    /** Объединение обломков, которые касаются друг друга, — система непересекающихся множеств. */
    private fun group(pieces: List<Frag>): List<List<Frag>> {
        val n = pieces.size
        val owner = IntArray(n) { it }
        fun find(a: Int): Int {
            var x = a
            while (owner[x] != x) { owner[x] = owner[owner[x]]; x = owner[x] }
            return x
        }
        for (i in 0 until n) for (j in i + 1 until n) {
            if (find(i) == find(j)) continue
            val a = pieces[i]
            val b = pieces[j]
            if (!overlaps(a.minX - TOUCH, a.maxX + TOUCH, b.minX, b.maxX)) continue
            if (!overlaps(a.minY - TOUCH, a.maxY + TOUCH, b.minY, b.maxY)) continue
            if (touches(a.world, b.world, TOUCH)) owner[find(i)] = find(j)
        }
        val buckets = LinkedHashMap<Int, ArrayList<Frag>>()
        for (i in 0 until n) buckets.getOrPut(find(i)) { ArrayList() }.add(pieces[i])
        return buckets.values.toList()
    }

    private fun makeBody(group: List<Frag>, vx: Double, vy: Double, w: Double): Body? {
        val b = Body()
        var n = 0
        for (fr in group) {
            val count = fr.world.size / 2
            val vs = Array(count) { Vector2(fr.world[it * 2], fr.world[it * 2 + 1]) }
            val shape = try {
                Geometry.createPolygon(*vs)
            } catch (e: RuntimeException) {
                null                       // вырожденный обрезок движок не примет
            } ?: continue
            val f = b.addFixture(shape, DENSITY, FRICTION, RESTITUTION)
            f.userData = fr.type
            n++
        }
        if (n == 0) return null
        b.setMass(MassType.NORMAL)
        b.linearDamping = LIN_DAMP
        b.angularDamping = ANG_DAMP
        b.setLinearVelocity(vx, vy)
        b.angularVelocity = w
        return b
    }

    private companion object {
        const val DENSITY = 1.0
        const val FRICTION = 0.45
        const val WALL_FRICTION = 0.35
        const val RESTITUTION = 0.02
        const val LIN_DAMP = 0.08
        const val ANG_DAMP = 0.20

        /** Клетка считается занятой, если вещество закрыло её на треть. */
        const val CELL_FILL = 0.30

        /** Мельче этого обрезок не выживает: движку не нужна совсем уж пыль. */
        const val MIN_FRAGMENT = 8e-3

        /** Зазор, в пределах которого обломки считаются соприкасающимися. */
        const val TOUCH = 2e-3

        val EMPTY_VIEW = FloatArray(0)


    }
}

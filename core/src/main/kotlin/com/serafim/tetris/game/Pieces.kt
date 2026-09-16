package com.serafim.tetris.game

/**
 * Семь фигур в том же порядке, что и TYPES в оригинале: I O T S Z J L.
 * Цвета — ARGB, один в один из палитры COLORS.
 */
enum class PieceType(val color: Int) {
    I(0xFF7FCFE8.toInt()),
    O(0xFFF2C55C.toInt()),
    T(0xFFC7A8F5.toInt()),
    S(0xFF86D28C.toInt()),
    Z(0xFFF2A6A0.toInt()),
    J(0xFFA8C7FA.toInt()),
    L(0xFFF5BE8A.toInt());

    companion object {
        val ALL: List<PieceType> = listOf(I, O, T, S, Z, J, L)
    }
}

/** Клетка поля в координатах стакана: x вправо, y вниз. */
data class Cell(val x: Int, val y: Int)

/** Активная фигура: тип, состояние поворота 0..3 и позиция матрицы. */
data class Piece(val type: PieceType, val rot: Int, val x: Int, val y: Int)

object Shapes {

    /** Исходные матрицы (rot = 0). */
    private val BASE: Map<PieceType, Array<IntArray>> = mapOf(
        PieceType.I to arrayOf(
            intArrayOf(0, 0, 0, 0),
            intArrayOf(1, 1, 1, 1),
            intArrayOf(0, 0, 0, 0),
            intArrayOf(0, 0, 0, 0),
        ),
        PieceType.O to arrayOf(
            intArrayOf(1, 1),
            intArrayOf(1, 1),
        ),
        PieceType.T to arrayOf(
            intArrayOf(0, 1, 0),
            intArrayOf(1, 1, 1),
            intArrayOf(0, 0, 0),
        ),
        PieceType.S to arrayOf(
            intArrayOf(0, 1, 1),
            intArrayOf(1, 1, 0),
            intArrayOf(0, 0, 0),
        ),
        PieceType.Z to arrayOf(
            intArrayOf(1, 1, 0),
            intArrayOf(0, 1, 1),
            intArrayOf(0, 0, 0),
        ),
        PieceType.J to arrayOf(
            intArrayOf(1, 0, 0),
            intArrayOf(1, 1, 1),
            intArrayOf(0, 0, 0),
        ),
        PieceType.L to arrayOf(
            intArrayOf(0, 0, 1),
            intArrayOf(1, 1, 1),
            intArrayOf(0, 0, 0),
        ),
    )

    /** Поворот матрицы по часовой: r[y][x] = m[n-1-x][y]. */
    fun rotateCW(m: Array<IntArray>): Array<IntArray> {
        val n = m.size
        val r = Array(n) { IntArray(n) }
        for (y in 0 until n) for (x in 0 until n) r[y][x] = m[n - 1 - x][y]
        return r
    }

    /** Четыре состояния каждой фигуры, посчитанные один раз при загрузке. */
    val STATES: Map<PieceType, List<Array<IntArray>>> = PieceType.ALL.associateWith { t ->
        val list = ArrayList<Array<IntArray>>(4)
        list.add(BASE.getValue(t))
        for (i in 1 until 4) list.add(rotateCW(list[i - 1]))
        list
    }

    /** Точка появления: все фигуры целиком в видимой части стакана. */
    val SPAWN: Map<PieceType, Cell> = mapOf(
        PieceType.I to Cell(3, 0),
        PieceType.O to Cell(4, 0),
        PieceType.T to Cell(3, 0),
        PieceType.S to Cell(3, 0),
        PieceType.Z to Cell(3, 0),
        PieceType.J to Cell(3, 0),
        PieceType.L to Cell(3, 0),
    )

    fun matrix(type: PieceType, rot: Int): Array<IntArray> =
        STATES.getValue(type)[((rot % 4) + 4) % 4]

    fun size(type: PieceType): Int = BASE.getValue(type).size
}

/**
 * Таблицы отскока от стен. Ключ — переход состояний, значение — список смещений
 * (dx, dy) в экранных координатах, где y растёт вниз.
 */
object Kicks {

    private val COMMON: Map<String, Array<IntArray>> = mapOf(
        "0>1" to arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, 2), intArrayOf(-1, 2)),
        "1>0" to arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, -2), intArrayOf(1, -2)),
        "1>2" to arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(0, -2), intArrayOf(1, -2)),
        "2>1" to arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(0, 2), intArrayOf(-1, 2)),
        "2>3" to arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, -1), intArrayOf(0, 2), intArrayOf(1, 2)),
        "3>2" to arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, -2), intArrayOf(-1, -2)),
        "3>0" to arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, -2), intArrayOf(-1, -2)),
        "0>3" to arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(1, -1), intArrayOf(0, 2), intArrayOf(1, 2)),
    )

    private val I_PIECE: Map<String, Array<IntArray>> = mapOf(
        "0>1" to arrayOf(intArrayOf(0, 0), intArrayOf(-2, 0), intArrayOf(1, 0), intArrayOf(-2, 1), intArrayOf(1, -2)),
        "1>0" to arrayOf(intArrayOf(0, 0), intArrayOf(2, 0), intArrayOf(-1, 0), intArrayOf(2, -1), intArrayOf(-1, 2)),
        "1>2" to arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(2, 0), intArrayOf(-1, -2), intArrayOf(2, 1)),
        "2>1" to arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(-2, 0), intArrayOf(1, 2), intArrayOf(-2, -1)),
        "2>3" to arrayOf(intArrayOf(0, 0), intArrayOf(2, 0), intArrayOf(-1, 0), intArrayOf(2, -1), intArrayOf(-1, 2)),
        "3>2" to arrayOf(intArrayOf(0, 0), intArrayOf(-2, 0), intArrayOf(1, 0), intArrayOf(-2, 1), intArrayOf(1, -2)),
        "3>0" to arrayOf(intArrayOf(0, 0), intArrayOf(1, 0), intArrayOf(-2, 0), intArrayOf(1, 2), intArrayOf(-2, -1)),
        "0>3" to arrayOf(intArrayOf(0, 0), intArrayOf(-1, 0), intArrayOf(2, 0), intArrayOf(-1, -2), intArrayOf(2, 1)),
    )

    private val NONE = arrayOf(intArrayOf(0, 0))

    fun forMove(type: PieceType, from: Int, to: Int): Array<IntArray> {
        val table = if (type == PieceType.I) I_PIECE else COMMON
        return table["$from>$to"] ?: NONE
    }
}

/**
 * У каждой фигуры свой порядок проявления кубиков первой фигуры матча.
 * Сравнение один в один с REVEAL_ORDER, где a[0] = x, a[1] = y.
 */
object RevealOrder {
    fun comparator(type: PieceType): Comparator<Cell> = when (type) {
        PieceType.I -> Comparator { a, b -> a.x - b.x }
        PieceType.O -> Comparator { a, b -> (a.x + a.y) - (b.x + b.y) }
        PieceType.T -> Comparator { a, b -> if (a.y != b.y) a.y - b.y else a.x - b.x }
        PieceType.S -> Comparator { a, b -> if (a.x != b.x) a.x - b.x else b.y - a.y }
        PieceType.Z -> Comparator { a, b -> if (b.x != a.x) b.x - a.x else a.y - b.y }
        PieceType.J -> Comparator { a, b -> if (b.y != a.y) b.y - a.y else a.x - b.x }
        PieceType.L -> Comparator { a, b -> if (b.x != a.x) b.x - a.x else a.y - b.y }
    }
}

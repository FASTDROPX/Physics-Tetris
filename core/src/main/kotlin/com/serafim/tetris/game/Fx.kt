package com.serafim.tetris.game

/** Звуковые события. Один к одному с методами объекта Sound из оригинала. */
enum class Sfx {
    MOVE, ROTATE, LOCK, HOLD, DROP,
    LEVEL_UP, CLICK, RANK, REVEAL, PERFECT,
    START, PAUSE, RESUME, WHOOSH, OVER,
}

/**
 * Всё, что логика поручает внешнему миру: звук, вибрация и те эффекты,
 * которые в оригинале жили на DOM-узлах (чип с сообщением, свечение стакана,
 * всплеск карточки «Отложено», анимация полосы опыта).
 */
interface Fx {
    fun sound(s: Sfx)
    fun soundClear(lines: Int)
    fun vibrate(vararg pattern: Long)

    /** Всплывающий чип с названием комбинации. */
    fun message(text: String)

    /** Свечение по контуру стакана: новый уровень и идеальная очистка. */
    fun boardGlow()

    /** Подпрыгивание счётчика скорости при новом уровне. */
    fun levelBump()

    /** Карточка «Отложено» проявляется заново. */
    fun holdPop()

    /** Полоса опыта заполняется до конца, вспыхивает и обнуляется. */
    fun xpRankUp()

    object None : Fx {
        override fun sound(s: Sfx) {}
        override fun soundClear(lines: Int) {}
        override fun vibrate(vararg pattern: Long) {}
        override fun message(text: String) {}
        override fun boardGlow() {}
        override fun levelBump() {}
        override fun holdPop() {}
        override fun xpRankUp() {}
    }
}

/** Полоса следа за жёстко сброшенной фигурой: одна на колонку. */
class Trail(val x: Int, val from: Int, val to: Int)

/**
 * Частица распада или искры. Координаты и скорости — в клетках поля,
 * а не в пикселях, чтобы отрисовка работала при любом размере клетки.
 */
class Particle(
    val x: Float,
    val y: Float,
    val s: Float,
    val vx: Float,
    val vy: Float,
    val color: Int,
    val delay: Double,
)

/** Облако распада стакана после проигрыша. */
class Dust(
    val parts: List<Particle>,
    /** Задержка исчезновения клетки, индекс y * COLS + x. */
    val delay: DoubleArray,
    val total: Double,
)

/** Всплывающие очки над местом события. */
class Pop(
    val text: String,
    val sub: String,
    val row: Double,
    val color: Int,
    var t: Double = 0.0,
)

/** Откуда доворачивается фигура: смещение в клетках и направление. */
data class RotFrom(val dx: Int = 0, val dy: Int = 0, val dir: Int = 1)

/** Смещение и сплющивание клеток, только что вставших на место. */
data class ImpactFx(val off: Double, val sx: Double, val sy: Double)

/** Тип T-спина. */
enum class Spin { FULL, MINI }

package de.graetz.electronote.diagram

import kotlin.math.roundToInt

/** Same column/row spacing as iOS's PAPGrid — column 1 is the "Hauptprogramm" spine,
 * column 0 branches left, columns 2/3 branch right. */
object PapGrid {
    const val COL_WIDTH = 210f
    const val ROW_HEIGHT = 115f
    const val ORIGIN_X = 340f
    const val ORIGIN_Y = 85f

    fun centerX(col: Int): Float = ORIGIN_X + (col - 1) * COL_WIDTH
    fun centerY(row: Int): Float = ORIGIN_Y + row * ROW_HEIGHT

    fun nearestGrid(x: Float, y: Float): Pair<Int, Int> {
        val col = (((x - ORIGIN_X) / COL_WIDTH).roundToInt() + 1).coerceIn(0, 5)
        val row = ((y - ORIGIN_Y) / ROW_HEIGHT).roundToInt().coerceIn(0, 25)
        return col to row
    }

    fun columnLabel(col: Int): String = when (col) {
        0 -> "← Zweig Links"
        1 -> "Hauptprogramm"
        2 -> "Zweig Rechts 1 →"
        3 -> "Zweig Rechts 2 →"
        else -> "Spalte $col"
    }
}

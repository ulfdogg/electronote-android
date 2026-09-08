package de.graetz.electronote.canvas

/** A single sampled point of a pen/finger stroke, in canvas-local pixel coordinates. */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f
)

/** One continuous stroke (pen-down to pen-up). */
data class Stroke(
    val points: List<StrokePoint>,
    val colorArgb: Int,
    val widthPx: Float
)

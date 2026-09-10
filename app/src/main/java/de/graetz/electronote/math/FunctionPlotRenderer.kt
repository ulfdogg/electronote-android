package de.graetz.electronote.math

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/** Renders a function plot to a bitmap for insertion into the notebook canvas. */
object FunctionPlotRenderer {

    fun sample(expression: String, xMin: Double, xMax: Double, steps: Int): List<Double?> =
        (0 until steps).map { i ->
            val x = xMin + (xMax - xMin) * i / (steps - 1)
            try {
                val y = MathEvaluator.evaluate(expression, x)
                if (y.isFinite()) y else null
            } catch (e: Exception) {
                null
            }
        }

    fun render(expression: String, xMin: Double, xMax: Double, widthPx: Int = 800, heightPx: Int = 500): Bitmap {
        val samples = sample(expression, xMin, xMax, widthPx)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val validYs = samples.filterNotNull()
        if (validYs.isEmpty()) return bitmap
        var yMin = validYs.min()
        var yMax = validYs.max()
        if (yMin == yMax) { yMin -= 1; yMax += 1 }
        val margin = 40f

        val axisPaint = Paint().apply { color = Color.parseColor("#B0B4BA"); strokeWidth = 2f }
        val curvePaint = Paint().apply {
            color = Color.parseColor("#007AFF")
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        fun toPx(x: Double, y: Double): Pair<Float, Float> {
            val px = margin + ((x - xMin) / (xMax - xMin)).toFloat() * (widthPx - 2 * margin)
            val py = heightPx - margin - ((y - yMin) / (yMax - yMin)).toFloat() * (heightPx - 2 * margin)
            return px to py
        }

        if (0.0 in xMin..xMax) {
            val (ax, _) = toPx(0.0, yMin)
            canvas.drawLine(ax, margin, ax, heightPx - margin, axisPaint)
        }
        if (0.0 in yMin..yMax) {
            val (_, ay) = toPx(xMin, 0.0)
            canvas.drawLine(margin, ay, widthPx - margin, ay, axisPaint)
        }

        var lastPoint: Pair<Float, Float>? = null
        for (i in samples.indices) {
            val y = samples[i]
            if (y == null) { lastPoint = null; continue }
            val x = xMin + (xMax - xMin) * i / (samples.size - 1)
            val point = toPx(x, y)
            lastPoint?.let { canvas.drawLine(it.first, it.second, point.first, point.second, curvePaint) }
            lastPoint = point
        }
        return bitmap
    }
}

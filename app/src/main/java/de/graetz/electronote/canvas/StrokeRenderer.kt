package de.graetz.electronote.canvas

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

private val strokePaint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.STROKE
    strokeCap = Paint.Cap.ROUND
    strokeJoin = Paint.Join.ROUND
}

/** Shared by the live canvas view and the PDF exporter, so on-screen and exported ink always match. */
fun Canvas.drawStroke(stroke: Stroke) {
    val points = stroke.points
    if (points.isEmpty()) return

    strokePaint.color = stroke.colorArgb
    strokePaint.strokeWidth = stroke.widthPx

    if (points.size == 1) {
        drawPoint(points[0].x, points[0].y, strokePaint)
        return
    }

    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    for (p in points.drop(1)) {
        path.lineTo(p.x, p.y)
    }
    drawPath(path, strokePaint)
}

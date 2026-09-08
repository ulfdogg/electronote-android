package de.graetz.electronote.canvas

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class SnappedShape { LINE, CIRCLE, RECTANGLE }

/**
 * Turns a roughly-drawn stroke into a clean geometric shape when it clearly looks like
 * one — the Android analogue of the iPad app's shape-snap feature. Heuristic, not exact:
 * a line is a stroke whose points barely deviate from the straight line between its ends;
 * a circle/ellipse is a closed loop with fairly constant radius from its centroid;
 * a rectangle is a closed loop whose bounding box the points hug closely.
 */
object ShapeSnapper {

    fun snap(stroke: Stroke): Pair<Stroke, SnappedShape>? {
        val points = stroke.points
        if (points.size < 6) return null

        asLine(stroke)?.let { return it to SnappedShape.LINE }
        asRectangle(stroke)?.let { return it to SnappedShape.RECTANGLE }
        asCircle(stroke)?.let { return it to SnappedShape.CIRCLE }
        return null
    }

    private fun asLine(stroke: Stroke): Stroke? {
        val points = stroke.points
        val start = points.first()
        val end = points.last()
        val length = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble())
        if (length < 30) return null

        val dx = end.x - start.x
        val dy = end.y - start.y
        val lenSq = (dx * dx + dy * dy).toDouble()

        var maxDeviation = 0.0
        for (p in points) {
            // Perpendicular distance from p to the line start-end.
            val t = (((p.x - start.x) * dx + (p.y - start.y) * dy) / lenSq).coerceIn(0.0, 1.0)
            val projX = start.x + t * dx
            val projY = start.y + t * dy
            val dist = hypot((p.x - projX).toDouble(), (p.y - projY).toDouble())
            maxDeviation = max(maxDeviation, dist)
        }

        val tolerance = max(10.0, length * 0.06)
        if (maxDeviation > tolerance) return null

        return Stroke(points = listOf(start, end), colorArgb = stroke.colorArgb, widthPx = stroke.widthPx)
    }

    private fun centroidAndClosure(stroke: Stroke): Triple<Float, Float, Double>? {
        val points = stroke.points
        val start = points.first()
        val end = points.last()
        val closeGap = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble())

        var minX = start.x
        var maxX = start.x
        var minY = start.y
        var maxY = start.y
        var sumX = 0f
        var sumY = 0f
        for (p in points) {
            minX = min(minX, p.x); maxX = max(maxX, p.x)
            minY = min(minY, p.y); maxY = max(maxY, p.y)
            sumX += p.x; sumY += p.y
        }
        val width = maxX - minX
        val height = maxY - minY
        val span = max(width, height)
        if (span < 40) return null
        // A closed loop: the stroke ends close to where it started, relative to its size.
        if (closeGap > span * 0.35) return null

        return Triple(sumX / points.size, sumY / points.size, span.toDouble())
    }

    private fun asCircle(stroke: Stroke): Stroke? {
        val (cx, cy, span) = centroidAndClosure(stroke) ?: return null
        val points = stroke.points

        val radii = points.map { hypot((it.x - cx).toDouble(), (it.y - cy).toDouble()) }
        val meanRadius = radii.average()
        if (meanRadius < 15) return null
        val maxDeviation = radii.maxOf { abs(it - meanRadius) }
        // Circle-ness: radius shouldn't vary by more than ~22% of the mean anywhere.
        if (maxDeviation > meanRadius * 0.22) return null

        val segments = 48
        val circlePoints = (0..segments).map { i ->
            val angle = (2 * Math.PI * i / segments)
            StrokePoint(
                x = (cx + meanRadius * Math.cos(angle)).toFloat(),
                y = (cy + meanRadius * Math.sin(angle)).toFloat()
            )
        }
        return Stroke(points = circlePoints, colorArgb = stroke.colorArgb, widthPx = stroke.widthPx)
    }

    private fun asRectangle(stroke: Stroke): Stroke? {
        centroidAndClosure(stroke) ?: return null
        val points = stroke.points

        var minX = points[0].x
        var maxX = points[0].x
        var minY = points[0].y
        var maxY = points[0].y
        for (p in points) {
            minX = min(minX, p.x); maxX = max(maxX, p.x)
            minY = min(minY, p.y); maxY = max(maxY, p.y)
        }
        val width = maxX - minX
        val height = maxY - minY
        if (width < 40 || height < 40) return null

        // Rectangle-ness: every point should sit close to one of the four edges of the
        // bounding box (a circle's points, by contrast, mostly sit far from the corners).
        val tolerance = max(14.0, min(width, height) * 0.12)
        for (p in points) {
            val distToVerticalEdge = min(abs(p.x - minX), abs(p.x - maxX))
            val distToHorizontalEdge = min(abs(p.y - minY), abs(p.y - maxY))
            val distToEdge = min(distToVerticalEdge, distToHorizontalEdge)
            if (distToEdge > tolerance) return null
        }

        val rectPoints = listOf(
            StrokePoint(minX, minY), StrokePoint(maxX, minY),
            StrokePoint(maxX, maxY), StrokePoint(minX, maxY),
            StrokePoint(minX, minY)
        )
        return Stroke(points = rectPoints, colorArgb = stroke.colorArgb, widthPx = stroke.widthPx)
    }
}

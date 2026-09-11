package de.graetz.electronote.diagram

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import kotlin.math.abs

/**
 * Simplified Manhattan/orthogonal routing for PAP connections — echoes the *shape* of
 * iOS's OrthogonalRoutingEngine (axis-aligned elbow paths, arrows) without replicating
 * its full bypass-corridor logic for routing around intermediate nodes.
 */
fun portPoint(node: DiagramNode, port: DiagramPort): Offset {
    val cx = node.x + node.widthPx / 2f
    val cy = node.y + node.heightPx / 2f
    return when (port) {
        DiagramPort.BOTTOM -> Offset(cx, node.y + node.heightPx)
        DiagramPort.TOP -> Offset(cx, node.y)
        DiagramPort.LEFT -> Offset(node.x, cy)
        DiagramPort.RIGHT -> Offset(node.x + node.widthPx, cy)
    }
}

private fun routeOrthogonal(from: Offset, to: Offset, port: DiagramPort): List<Offset> {
    if (abs(from.x - to.x) < 1f && (port == DiagramPort.BOTTOM || port == DiagramPort.TOP)) {
        return listOf(from, to)
    }
    if (abs(from.y - to.y) < 1f && (port == DiagramPort.LEFT || port == DiagramPort.RIGHT)) {
        return listOf(from, to)
    }
    return when (port) {
        DiagramPort.BOTTOM -> {
            val midY = (from.y + to.y) / 2f
            listOf(from, Offset(from.x, midY), Offset(to.x, midY), to)
        }
        DiagramPort.TOP -> {
            val midY = minOf(from.y, to.y) - 40f
            listOf(from, Offset(from.x, midY), Offset(to.x, midY), to)
        }
        DiagramPort.RIGHT -> {
            val midX = maxOf(from.x, to.x) + 40f
            listOf(from, Offset(midX, from.y), Offset(midX, to.y), to)
        }
        DiagramPort.LEFT -> {
            val midX = minOf(from.x, to.x) - 40f
            listOf(from, Offset(midX, from.y), Offset(midX, to.y), to)
        }
    }
}

fun routePapConnection(from: DiagramNode, to: DiagramNode, port: DiagramPort): List<Offset> {
    val start = portPoint(from, port)
    val toCenter = Offset(to.x + to.widthPx / 2f, to.y + to.heightPx / 2f)
    val end = when (port) {
        DiagramPort.BOTTOM, DiagramPort.TOP ->
            if (toCenter.y >= start.y) Offset(toCenter.x, to.y) else Offset(toCenter.x, to.y + to.heightPx)
        DiagramPort.LEFT, DiagramPort.RIGHT ->
            if (toCenter.x >= start.x) Offset(to.x, toCenter.y) else Offset(to.x + to.widthPx, toCenter.y)
    }
    return routeOrthogonal(start, end, port)
}

/** A small triangular arrowhead pointing from the second-to-last point towards [tip]. */
fun arrowHeadPath(points: List<Offset>): Path? {
    if (points.size < 2) return null
    val tip = points.last()
    val prev = points[points.size - 2]
    val dx = tip.x - prev.x
    val dy = tip.y - prev.y
    val len = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.01f)
    val ux = dx / len
    val uy = dy / len
    val size = 12f
    val leftX = tip.x - ux * size - uy * size * 0.6f
    val leftY = tip.y - uy * size + ux * size * 0.6f
    val rightX = tip.x - ux * size + uy * size * 0.6f
    val rightY = tip.y - uy * size - ux * size * 0.6f
    return Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(leftX, leftY)
        lineTo(rightX, rightY)
        close()
    }
}

/** Cubic-Bézier "branch" curve for MindMap connections, exiting the left/right side of
 * each node depending on relative horizontal position (matches iOS's always-horizontal
 * S-curve regardless of vertical offset). */
fun mindMapEndpoints(from: DiagramNode, to: DiagramNode): Pair<Offset, Offset> {
    val fromCenter = Offset(from.x + from.widthPx / 2f, from.y + from.heightPx / 2f)
    val toCenter = Offset(to.x + to.widthPx / 2f, to.y + to.heightPx / 2f)
    val fromPoint = if (toCenter.x >= fromCenter.x) Offset(from.x + from.widthPx, fromCenter.y) else Offset(from.x, fromCenter.y)
    val toPoint = if (toCenter.x >= fromCenter.x) Offset(to.x, toCenter.y) else Offset(to.x + to.widthPx, toCenter.y)
    return fromPoint to toPoint
}

fun mindMapCurvePath(start: Offset, end: Offset): Path {
    val path = Path()
    path.moveTo(start.x, start.y)
    val dx = end.x - start.x
    path.cubicTo(start.x + dx * 0.5f, start.y, start.x + dx * 0.5f, end.y, end.x, end.y)
    return path
}

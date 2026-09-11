package de.graetz.electronote.diagram

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

private val parallelogramShape = GenericShape { size, _ ->
    val offset = size.width * 0.16f
    moveTo(offset, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - offset, size.height)
    lineTo(0f, size.height)
    close()
}

private val diamondShape = GenericShape { size, _ ->
    moveTo(size.width / 2f, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width / 2f, size.height)
    lineTo(0f, size.height / 2f)
    close()
}

fun composeShapeFor(kind: DiagramShapeKind): Shape = when (kind) {
    DiagramShapeKind.START -> RoundedCornerShape(50)
    DiagramShapeKind.END -> RoundedCornerShape(50)
    DiagramShapeKind.PROCESS -> RoundedCornerShape(5.dp)
    DiagramShapeKind.IO -> parallelogramShape
    DiagramShapeKind.DECISION -> diamondShape
    DiagramShapeKind.SUBROUTINE -> RoundedCornerShape(2.dp)
    DiagramShapeKind.CONNECTOR -> CircleShape
    DiagramShapeKind.CIRCLE -> CircleShape
    DiagramShapeKind.OVAL -> RoundedCornerShape(50)
    DiagramShapeKind.RECTANGLE -> RoundedCornerShape(14.dp)
    DiagramShapeKind.DIAMOND -> diamondShape
}

/** True for shapes that get the DIN "double vertical stripe" subroutine decoration. */
fun hasSubroutineStripes(kind: DiagramShapeKind): Boolean = kind == DiagramShapeKind.SUBROUTINE

package de.graetz.electronote.diagram

enum class DiagramShapeKind(val label: String) {
    // PAP (DIN 66001)
    START_END("Start/Ende"),
    PROCESS("Prozess"),
    IO("Ein-/Ausgabe"),
    DECISION("Verzweigung"),
    SUBROUTINE("Unterprogramm"),

    // MindMap
    CIRCLE("Kreis"),
    OVAL("Oval"),
    RECTANGLE("Rechteck"),
    DIAMOND("Raute")
}

val PAP_SHAPES = listOf(
    DiagramShapeKind.START_END,
    DiagramShapeKind.PROCESS,
    DiagramShapeKind.IO,
    DiagramShapeKind.DECISION,
    DiagramShapeKind.SUBROUTINE
)

val MINDMAP_SHAPES = listOf(
    DiagramShapeKind.CIRCLE,
    DiagramShapeKind.OVAL,
    DiagramShapeKind.RECTANGLE,
    DiagramShapeKind.DIAMOND
)

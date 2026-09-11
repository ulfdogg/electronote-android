package de.graetz.electronote.diagram

// Matches iOS's PAPShapeType/BubbleShape vocabulary and per-type sizing exactly
// (PAPDesignerView.swift / MindMapDesignerView.swift), even though iOS never actually
// persists these — only Android keeps the diagram editable after closing it.
enum class DiagramShapeKind(val label: String, val widthPx: Float, val heightPx: Float) {
    // PAP (DIN 66001)
    START("Start", 150f, 52f),
    END("Ende", 150f, 52f),
    PROCESS("Prozess", 160f, 54f),
    IO("Ein-/Ausgabe", 160f, 54f),
    DECISION("Verzweigung", 150f, 70f),
    SUBROUTINE("Unterprogramm", 160f, 54f),
    CONNECTOR("Verbindung", 32f, 32f),

    // MindMap
    CIRCLE("Kreis", 130f, 130f),
    OVAL("Oval", 160f, 95f),
    RECTANGLE("Rechteck", 150f, 90f),
    DIAMOND("Raute", 140f, 110f)
}

val PAP_SHAPES = listOf(
    DiagramShapeKind.START,
    DiagramShapeKind.END,
    DiagramShapeKind.PROCESS,
    DiagramShapeKind.IO,
    DiagramShapeKind.DECISION,
    DiagramShapeKind.SUBROUTINE,
    DiagramShapeKind.CONNECTOR
)

val MINDMAP_SHAPES = listOf(
    DiagramShapeKind.CIRCLE,
    DiagramShapeKind.OVAL,
    DiagramShapeKind.RECTANGLE,
    DiagramShapeKind.DIAMOND
)

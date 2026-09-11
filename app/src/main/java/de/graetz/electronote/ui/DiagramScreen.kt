package de.graetz.electronote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.graetz.electronote.diagram.DiagramConnection
import de.graetz.electronote.diagram.DiagramDocument
import de.graetz.electronote.diagram.DiagramNode
import de.graetz.electronote.diagram.DiagramPort
import de.graetz.electronote.diagram.DiagramShapeKind
import de.graetz.electronote.diagram.DiagramStore
import de.graetz.electronote.diagram.MINDMAP_SHAPES
import de.graetz.electronote.diagram.PAP_SHAPES
import de.graetz.electronote.diagram.PapGrid
import de.graetz.electronote.diagram.arrowHeadPath
import de.graetz.electronote.diagram.composeShapeFor
import de.graetz.electronote.diagram.hasSubroutineStripes
import de.graetz.electronote.diagram.mindMapCurvePath
import de.graetz.electronote.diagram.mindMapEndpoints
import de.graetz.electronote.diagram.routePapConnection
import de.graetz.electronote.ui.theme.IosColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CANVAS_SIZE_DP = 2400

// Exact iOS MindMap palette (bubbleColors), in order.
private val MINDMAP_COLORS = listOf(
    IosColors.Blue, IosColors.Purple, IosColors.Teal, IosColors.Green,
    IosColors.Orange, IosColors.Pink, IosColors.Red
)

/**
 * Shared node-and-connection diagram editor for "Ablaufplan" (PAP, DIN 66001) and
 * "MindMap". Visual/interaction style follows the real iOS PAPDesignerView /
 * MindMapDesignerView (grid-snapped orthogonal PAP routing vs. free-form Bézier MindMap
 * branches) — but unlike iOS (which never actually persists these, only flattens them to
 * a bitmap on insert), this keeps the node/connection graph saved and reopenable.
 * No true pinch-zoom: a large, fixed, two-directionally scrollable canvas instead.
 */
@Composable
fun DiagramScreen(diagramId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var diagramType by remember { mutableStateOf(DiagramDocument.TYPE_PAP) }
    var diagramName by remember { mutableStateOf("") }
    var nodes by remember { mutableStateOf<List<NodeUiState>>(emptyList()) }
    var connections by remember { mutableStateOf<List<DiagramConnection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var pendingShape by remember { mutableStateOf<DiagramShapeKind?>(null) }
    var selectedColor by remember { mutableStateOf(MINDMAP_COLORS[0]) }
    var connectMode by remember { mutableStateOf(false) }
    var connectFromId by remember { mutableStateOf<String?>(null) }
    var editingNode by remember { mutableStateOf<NodeUiState?>(null) }

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()
    val isPap = diagramType == DiagramDocument.TYPE_PAP

    fun persist() {
        val doc = DiagramDocument(
            id = diagramId,
            name = diagramName,
            type = diagramType,
            nodes = nodes.map { it.toNode() }.toMutableList(),
            connections = connections.toMutableList()
        )
        scope.launch(Dispatchers.IO) { DiagramStore.saveDiagram(context, doc) }
    }

    LaunchedEffect(diagramId) {
        val loaded = withContext(Dispatchers.IO) { DiagramStore.loadDiagram(context, diagramId) }
        if (loaded != null) {
            diagramName = loaded.name
            diagramType = loaded.type
            nodes = loaded.nodes.map { NodeUiState(it) }
            connections = loaded.connections
        }
        isLoading = false
    }

    fun addNode(shape: DiagramShapeKind, tapX: Float, tapY: Float) {
        val node = if (isPap) {
            val (col, row) = PapGrid.nearestGrid(tapX, tapY)
            DiagramNode(
                x = PapGrid.centerX(col) - shape.widthPx / 2f,
                y = PapGrid.centerY(row) - shape.heightPx / 2f,
                shape = shape,
                col = col,
                row = row
            )
        } else {
            DiagramNode(
                x = tapX - shape.widthPx / 2f,
                y = tapY - shape.heightPx / 2f,
                shape = shape,
                colorArgb = android.graphics.Color.argb(
                    (selectedColor.alpha * 255).roundToInt(),
                    (selectedColor.red * 255).roundToInt(),
                    (selectedColor.green * 255).roundToInt(),
                    (selectedColor.blue * 255).roundToInt()
                )
            )
        }
        nodes = nodes + NodeUiState(node)
        persist()
    }

    fun deleteNode(id: String) {
        nodes = nodes.filterNot { it.id == id }
        connections = connections.filterNot { it.fromNodeId == id || it.toNodeId == id }
        persist()
    }

    fun inferPort(from: NodeUiState, to: NodeUiState): DiagramPort {
        val fromRow = from.row
        val toRow = to.row
        return if (isPap && fromRow != null && toRow != null && fromRow == toRow) {
            if (to.x >= from.x) DiagramPort.RIGHT else DiagramPort.LEFT
        } else {
            DiagramPort.BOTTOM
        }
    }

    fun defaultLabel(from: NodeUiState): String {
        if (!isPap || from.shape != DiagramShapeKind.DECISION) return ""
        val outgoingCount = connections.count { it.fromNodeId == from.id }
        return if (outgoingCount == 0) "ja" else "nein"
    }

    val palette = if (isPap) PAP_SHAPES else MINDMAP_SHAPES

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { persist(); onBack() }) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Zurück", modifier = Modifier.size(20.dp))
                }
                Text(
                    diagramName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 6.dp)
                )
                IconButton(onClick = {
                    connectMode = !connectMode
                    connectFromId = null
                    if (connectMode) pendingShape = null
                }) {
                    Icon(
                        Icons.Outlined.Timeline,
                        contentDescription = "Verbinden",
                        tint = if (connectMode) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    ) { padding ->
        if (!isLoading) {
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(
                    modifier = Modifier
                        .width(92.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (shape in palette) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(4.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (pendingShape == shape) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    pendingShape = if (pendingShape == shape) null else shape
                                    connectMode = false
                                    connectFromId = null
                                }
                                .padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 52.dp, height = 30.dp)
                                    .clip(composeShapeFor(shape))
                                    .background(if (isPap) Color(0xFF4FC3F7) else selectedColor)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, composeShapeFor(shape))
                            )
                            Text(shape.label, fontSize = 9.sp, maxLines = 1)
                        }
                    }

                    if (!isPap) {
                        Text("Farbe", fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                        for (color in MINDMAP_COLORS) {
                            Box(
                                modifier = Modifier
                                    .padding(3.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        if (selectedColor == color) 2.dp else 1.dp,
                                        if (selectedColor == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        CircleShape
                                    )
                                    .clickable { selectedColor = color }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .horizontalScroll(hScroll)
                        .verticalScroll(vScroll)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = CANVAS_SIZE_DP.dp, height = CANVAS_SIZE_DP.dp)
                            .pointerInputTap(pendingShape) { offset ->
                                val shape = pendingShape
                                if (shape != null) {
                                    addNode(shape, offset.x, offset.y)
                                    pendingShape = null
                                }
                            }
                    ) {
                        if (isPap) {
                            // Column guides, matching the iPad PAP-Designer's labeled grid.
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                for (col in 0..3) {
                                    val x = PapGrid.centerX(col)
                                    drawLine(Color(0x1A000000), Offset(x, 0f), Offset(x, CANVAS_SIZE_DP.dp.toPx()), strokeWidth = 1f)
                                }
                            }
                        }

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (conn in connections) {
                                val from = nodes.find { it.id == conn.fromNodeId } ?: continue
                                val to = nodes.find { it.id == conn.toNodeId } ?: continue
                                if (isPap) {
                                    val points = routePapConnection(from.toNode(), to.toNode(), conn.fromPort)
                                    val path = androidx.compose.ui.graphics.Path()
                                    path.moveTo(points[0].x, points[0].y)
                                    for (p in points.drop(1)) path.lineTo(p.x, p.y)
                                    drawPath(path, Color(0xFF6E6E73), style = Stroke(width = 3.5f))
                                    arrowHeadPath(points)?.let { drawPath(it, Color(0xFF6E6E73)) }
                                } else {
                                    val (start, end) = mindMapEndpoints(from.toNode(), to.toNode())
                                    val path = mindMapCurvePath(start, end)
                                    drawPath(path, Color(from.colorArgb).copy(alpha = 0.85f), style = Stroke(width = 3.5f))
                                }
                            }
                        }

                        // PAP connection labels (ja/nein/…), drawn at the route midpoint.
                        if (isPap) {
                            for (conn in connections) {
                                if (conn.label.isBlank()) continue
                                val from = nodes.find { it.id == conn.fromNodeId } ?: continue
                                val to = nodes.find { it.id == conn.toNodeId } ?: continue
                                val points = routePapConnection(from.toNode(), to.toNode(), conn.fromPort)
                                val mid = points[points.size / 2]
                                Text(
                                    conn.label,
                                    fontSize = 11.sp,
                                    color = Color(0xFF6E6E73),
                                    modifier = Modifier.offset { IntOffset(mid.x.roundToInt() + 4, mid.y.roundToInt() - 20) }
                                )
                            }
                        }

                        for (node in nodes) {
                            DiagramNodeView(
                                node = node,
                                selected = connectMode && connectFromId == node.id,
                                connectMode = connectMode,
                                snapToGrid = isPap,
                                onTap = {
                                    if (connectMode) {
                                        val from = connectFromId
                                        if (from == null) {
                                            connectFromId = node.id
                                        } else if (from != node.id) {
                                            val fromNode = nodes.find { it.id == from }
                                            if (fromNode != null) {
                                                connections = connections + DiagramConnection(
                                                    fromNodeId = from,
                                                    toNodeId = node.id,
                                                    label = defaultLabel(fromNode),
                                                    fromPort = inferPort(fromNode, node)
                                                )
                                            }
                                            connectFromId = null
                                            persist()
                                        }
                                    } else {
                                        editingNode = node
                                    }
                                },
                                onMoved = { persist() }
                            )
                        }
                    }
                }
            }
        }
    }

    editingNode?.let { node ->
        var input by remember(node.id) { mutableStateOf(node.text) }
        var tagInput by remember(node.id) { mutableStateOf(node.tag) }
        AlertDialog(
            onDismissRequest = { editingNode = null },
            title = { Text("Text bearbeiten") },
            text = {
                Column {
                    OutlinedTextField(value = input, onValueChange = { input = it })
                    if (node.shape == DiagramShapeKind.IO) {
                        Row(modifier = Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = { tagInput = if (tagInput == "E") "" else "E" }) {
                                Text(if (tagInput == "E") "✓ Eingabe (E)" else "Eingabe (E)")
                            }
                            TextButton(onClick = { tagInput = if (tagInput == "A") "" else "A" }) {
                                Text(if (tagInput == "A") "✓ Ausgabe (A)" else "Ausgabe (A)")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    node.text = input
                    node.tag = tagInput
                    editingNode = null
                    persist()
                }) { Text("Speichern") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        deleteNode(node.id)
                        editingNode = null
                    }) { Text("Löschen") }
                    TextButton(onClick = { editingNode = null }) { Text("Abbrechen") }
                }
            }
        )
    }
}

@Composable
private fun DiagramNodeView(
    node: NodeUiState,
    selected: Boolean,
    connectMode: Boolean,
    snapToGrid: Boolean,
    onTap: () -> Unit,
    onMoved: () -> Unit
) {
    var totalDrag by remember(node.id) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .offset { IntOffset(node.x.roundToInt(), node.y.roundToInt()) }
            .size(width = node.shape.widthPx.dp, height = node.shape.heightPx.dp)
            .clip(composeShapeFor(node.shape))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color(node.colorArgb))
            .border(1.5.dp, Color.Black.copy(alpha = 0.3f), composeShapeFor(node.shape))
            .pointerInput(node.id, connectMode) {
                detectDragGestures(
                    onDragStart = { totalDrag = Offset.Zero },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDrag += dragAmount
                        if (!connectMode) {
                            node.x += dragAmount.x
                            node.y += dragAmount.y
                        }
                    },
                    onDragEnd = {
                        if (abs(totalDrag.x) < 6f && abs(totalDrag.y) < 6f) {
                            onTap()
                        } else if (!connectMode) {
                            if (snapToGrid) {
                                val cx = node.x + node.shape.widthPx / 2f
                                val cy = node.y + node.shape.heightPx / 2f
                                val (col, row) = PapGrid.nearestGrid(cx, cy)
                                node.col = col
                                node.row = row
                                node.x = PapGrid.centerX(col) - node.shape.widthPx / 2f
                                node.y = PapGrid.centerY(row) - node.shape.heightPx / 2f
                            }
                            onMoved()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (hasSubroutineStripes(node.shape)) {
            Box(Modifier.fillMaxHeight().width(3.dp).offset(x = 8.dp).background(Color.Black.copy(alpha = 0.35f)))
            Box(Modifier.fillMaxHeight().width(3.dp).offset(x = node.shape.widthPx.dp - 11.dp).background(Color.Black.copy(alpha = 0.35f)))
        }
        Text(
            node.text,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = if (selected) Color.White else Color.Black,
            modifier = Modifier.padding(6.dp),
            maxLines = 3
        )
        if (node.tag.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(3.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp)
            ) {
                Text(node.tag, fontSize = 9.sp, color = Color.White)
            }
        }
    }
}

private fun Modifier.pointerInputTap(key: Any?, onTap: (Offset) -> Unit): Modifier =
    this.then(Modifier.pointerInput(key) { detectTapGestures(onTap = onTap) })

private class NodeUiState(node: DiagramNode) {
    val id = node.id
    var x by mutableStateOf(node.x)
    var y by mutableStateOf(node.y)
    var shape by mutableStateOf(node.shape)
    var text by mutableStateOf(node.text)
    var colorArgb by mutableStateOf(node.colorArgb)
    var col by mutableStateOf(node.col)
    var row by mutableStateOf(node.row)
    var tag by mutableStateOf(node.tag)
    fun toNode() = DiagramNode(id = id, x = x, y = y, shape = shape, text = text, colorArgb = colorArgb, col = col, row = row, tag = tag)
}

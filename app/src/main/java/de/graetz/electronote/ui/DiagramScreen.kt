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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.graetz.electronote.diagram.DiagramConnection
import de.graetz.electronote.diagram.DiagramDocument
import de.graetz.electronote.diagram.DiagramNode
import de.graetz.electronote.diagram.DiagramShapeKind
import de.graetz.electronote.diagram.DiagramStore
import de.graetz.electronote.diagram.MINDMAP_SHAPES
import de.graetz.electronote.diagram.PAP_SHAPES
import de.graetz.electronote.diagram.composeShapeFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CANVAS_SIZE_DP = 2400

/**
 * Shared node-and-connection diagram editor for both the "Ablaufplan" (PAP, DIN 66001)
 * and "MindMap" document types — same drag/connect/edit mechanics, just a different shape
 * palette. No true pinch-zoom (matches the Whiteboard's simplification): a large, fixed,
 * two-directionally scrollable canvas instead.
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
    var connectMode by remember { mutableStateOf(false) }
    var connectFromId by remember { mutableStateOf<String?>(null) }
    var editingNode by remember { mutableStateOf<NodeUiState?>(null) }

    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

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

    fun addNode(shape: DiagramShapeKind, x: Float, y: Float) {
        val node = DiagramNode(x = x - DiagramNode.WIDTH / 2, y = y - DiagramNode.HEIGHT / 2, shape = shape)
        nodes = nodes + NodeUiState(node)
        persist()
    }

    fun deleteNode(id: String) {
        nodes = nodes.filterNot { it.id == id }
        connections = connections.filterNot { it.fromNodeId == id || it.toNodeId == id }
        persist()
    }

    val palette = if (diagramType == DiagramDocument.TYPE_PAP) PAP_SHAPES else MINDMAP_SHAPES

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
                                    .background(Color(0xFF4FC3F7))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, composeShapeFor(shape))
                            )
                            Text(shape.label, fontSize = 9.sp, maxLines = 1)
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
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            for (conn in connections) {
                                val from = nodes.find { it.id == conn.fromNodeId } ?: continue
                                val to = nodes.find { it.id == conn.toNodeId } ?: continue
                                val start = Offset(from.x + DiagramNode.WIDTH / 2, from.y + DiagramNode.HEIGHT / 2)
                                val end = Offset(to.x + DiagramNode.WIDTH / 2, to.y + DiagramNode.HEIGHT / 2)
                                drawLine(Color(0xFF8E8E93), start, end, strokeWidth = 4f)
                            }
                        }

                        for (node in nodes) {
                            DiagramNodeView(
                                node = node,
                                selected = connectMode && connectFromId == node.id,
                                connectMode = connectMode,
                                onTap = {
                                    if (connectMode) {
                                        val from = connectFromId
                                        if (from == null) {
                                            connectFromId = node.id
                                        } else if (from != node.id) {
                                            connections = connections + DiagramConnection(fromNodeId = from, toNodeId = node.id)
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
        AlertDialog(
            onDismissRequest = { editingNode = null },
            title = { Text("Text bearbeiten") },
            text = { OutlinedTextField(value = input, onValueChange = { input = it }) },
            confirmButton = {
                TextButton(onClick = {
                    node.text = input
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
    onTap: () -> Unit,
    onMoved: () -> Unit
) {
    var totalDrag by remember(node.id) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .offset { IntOffset(node.x.roundToInt(), node.y.roundToInt()) }
            .size(width = DiagramNode.WIDTH.dp, height = DiagramNode.HEIGHT.dp)
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
                            onMoved()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            node.text,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = if (selected) Color.White else Color.Black,
            modifier = Modifier.padding(6.dp),
            maxLines = 3
        )
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
    fun toNode() = DiagramNode(id = id, x = x, y = y, shape = shape, text = text, colorArgb = colorArgb)
}

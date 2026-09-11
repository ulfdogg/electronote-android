package de.graetz.electronote.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookDocumentSummary
import de.graetz.electronote.data.NotebookStore
import de.graetz.electronote.diagram.DiagramDocument
import de.graetz.electronote.diagram.DiagramStore
import de.graetz.electronote.diagram.DiagramSummary
import de.graetz.electronote.nextcloud.NextcloudDownloadDialog
import de.graetz.electronote.nextcloud.NextcloudLoginDialog
import de.graetz.electronote.pdf.PdfImporter
import de.graetz.electronote.ui.theme.IosColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private sealed class BrowserItem(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val tags: List<String>,
    val isFavorite: Boolean
) {
    class Notebook(val summary: NotebookDocumentSummary) :
        BrowserItem(summary.id, summary.name, summary.updatedAt, summary.tags, summary.isFavorite)
    class Diagram(val summary: DiagramSummary) :
        BrowserItem(summary.id, summary.name, summary.updatedAt, summary.tags, summary.isFavorite)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    onOpenDocument: (String) -> Unit,
    onOpenDiagram: (String) -> Unit,
    onOpenTrash: () -> Unit,
    onOpenSearch: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var documents by remember { mutableStateOf(listOf<NotebookDocumentSummary>()) }
    var diagrams by remember { mutableStateOf(listOf<DiagramSummary>()) }
    var refreshKey by remember { mutableStateOf(0) }
    var showNextcloudLogin by remember { mutableStateOf(false) }
    var showNextcloudDownload by remember { mutableStateOf(false) }
    var showDriveLogin by remember { mutableStateOf(false) }
    var showDriveDownload by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var editingTagsFor by remember { mutableStateOf<BrowserItem?>(null) }
    var isImportingPdf by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        documents = NotebookStore.listDocuments(context)
        diagrams = DiagramStore.listDiagrams(context)
    }

    // "PDF öffnen & markieren": creates a new notebook seeded with the PDF's pages as
    // full-width backgrounds, ready to draw on immediately — reuses the same import path
    // as PDF import inside a notebook, just as its own entry point from the list.
    fun queryPdfDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx)?.removeSuffix(".pdf") else null
        }
    }

    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImportingPdf = true
        scope.launch {
            val name = withContext(Dispatchers.IO) { queryPdfDisplayName(uri) } ?: "PDF-Notizbuch"
            val doc = withContext(Dispatchers.IO) { NotebookStore.createDocument(context, name) }
            val newBackgrounds = withContext(Dispatchers.IO) { PdfImporter.importPdf(context, uri, doc) }
            if (newBackgrounds.isNotEmpty()) {
                doc.backgrounds.addAll(newBackgrounds)
                val bottom = newBackgrounds.maxOf { it.yOffsetPx + it.heightPx } + 200
                doc.canvasHeightPx = maxOf(doc.canvasHeightPx, bottom)
                withContext(Dispatchers.IO) { NotebookStore.saveDocument(context, doc) }
            }
            isImportingPdf = false
            onOpenDocument(doc.id)
        }
    }

    val allTags = (documents.flatMap { it.tags } + diagrams.flatMap { it.tags }).distinct().sorted()
    val browserItems: List<BrowserItem> = remember(documents, diagrams, selectedTag) {
        val nb = documents.map { BrowserItem.Notebook(it) }
        val dg = diagrams.map { BrowserItem.Diagram(it) }
        (nb + dg)
            .filter { selectedTag == null || selectedTag in it.tags }
            .sortedByDescending { it.updatedAt }
    }

    Scaffold(
        topBar = {
            // Compact custom row (not Material3's TopAppBar, which enforces a taller
            // 64dp minimum) with smaller icon glyphs, matching the notebook editor.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(48.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showTypePicker = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Neu", modifier = Modifier.size(20.dp))
                }
                Text(
                    "ElectroNote",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 6.dp)
                )
                IconButton(
                    onClick = { openPdfLauncher.launch(arrayOf("application/pdf")) },
                    enabled = !isImportingPdf
                ) {
                    Icon(Icons.Outlined.PictureAsPdf, contentDescription = "PDF öffnen & markieren", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onOpenSearch) {
                    Icon(Icons.Outlined.Search, contentDescription = "Suchen", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onOpenTrash) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = "Papierkorb", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { showNextcloudDownload = true }) {
                    Icon(Icons.Outlined.CloudDownload, contentDescription = "Von Nextcloud laden", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { showNextcloudLogin = true }) {
                    Icon(Icons.Outlined.CloudQueue, contentDescription = "Nextcloud", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { showDriveDownload = true }) {
                    Icon(Icons.Outlined.CloudSync, contentDescription = "Von Google Drive laden", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { showDriveLogin = true }) {
                    Icon(Icons.Outlined.Cloud, contentDescription = "Google Drive", modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { AppPreferences.toggleDarkMode(context) }) {
                    Icon(
                        if (AppPreferences.isDarkMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                        contentDescription = "Dunkelmodus umschalten",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (allTags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (tag in allTags) {
                        val active = selectedTag == tag
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { selectedTag = if (active) null else tag }
                        ) {
                            Text(
                                tag,
                                fontSize = 12.sp,
                                color = if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            if (browserItems.isEmpty()) {
                Text(
                    text = if (documents.isEmpty() && diagrams.isEmpty()) "Noch nichts hier. Tippe oben links auf + zum Anlegen." else "Nichts mit diesem Tag.",
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(browserItems, key = { it.id }) { item ->
                        when (item) {
                            is BrowserItem.Notebook -> NotebookRow(
                                doc = item.summary,
                                onOpen = { onOpenDocument(item.summary.id) },
                                onToggleFavorite = {
                                    NotebookStore.setFavorite(context, item.summary.id, !item.summary.isFavorite)
                                    refreshKey++
                                },
                                onEditTags = { editingTagsFor = item },
                                onDelete = {
                                    NotebookStore.moveToTrash(context, item.summary.id)
                                    refreshKey++
                                }
                            )
                            is BrowserItem.Diagram -> DiagramRow(
                                summary = item.summary,
                                onOpen = { onOpenDiagram(item.summary.id) },
                                onToggleFavorite = {
                                    DiagramStore.setFavorite(context, item.summary.id, !item.summary.isFavorite)
                                    refreshKey++
                                },
                                onEditTags = { editingTagsFor = item },
                                onDelete = {
                                    DiagramStore.moveToTrash(context, item.summary.id)
                                    refreshKey++
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTypePicker) {
        DocumentTypePickerDialog(
            onDismiss = { showTypePicker = false },
            onPickNotebook = {
                showTypePicker = false
                val count = documents.size + 1
                val doc = NotebookStore.createDocument(context, "Notizbuch $count")
                refreshKey++
                onOpenDocument(doc.id)
            },
            onPickWhiteboard = {
                showTypePicker = false
                val count = documents.count { it.docType == NotebookDocument.DOC_TYPE_WHITEBOARD } + 1
                val doc = NotebookStore.createDocument(context, "Whiteboard $count", NotebookDocument.DOC_TYPE_WHITEBOARD)
                refreshKey++
                onOpenDocument(doc.id)
            },
            onPickPap = {
                showTypePicker = false
                val count = diagrams.count { it.type == DiagramDocument.TYPE_PAP } + 1
                val doc = DiagramStore.createDiagram(context, "Ablaufplan $count", DiagramDocument.TYPE_PAP)
                refreshKey++
                onOpenDiagram(doc.id)
            },
            onPickMindMap = {
                showTypePicker = false
                val count = diagrams.count { it.type == DiagramDocument.TYPE_MINDMAP } + 1
                val doc = DiagramStore.createDiagram(context, "MindMap $count", DiagramDocument.TYPE_MINDMAP)
                refreshKey++
                onOpenDiagram(doc.id)
            }
        )
    }

    editingTagsFor?.let { item ->
        var input by remember(item.id) { mutableStateOf(item.tags.joinToString(", ")) }
        AlertDialog(
            onDismissRequest = { editingTagsFor = null },
            title = { Text("Tags bearbeiten") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("z.B. Klasse10, Wechselstrom") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val tags = input.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    when (item) {
                        is BrowserItem.Notebook -> NotebookStore.setTags(context, item.id, tags)
                        is BrowserItem.Diagram -> DiagramStore.setTags(context, item.id, tags)
                    }
                    editingTagsFor = null
                    refreshKey++
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { editingTagsFor = null }) { Text("Abbrechen") }
            }
        )
    }

    if (showNextcloudLogin) {
        NextcloudLoginDialog(onDismiss = { showNextcloudLogin = false })
    }
    if (showNextcloudDownload) {
        NextcloudDownloadDialog(
            onDismiss = { showNextcloudDownload = false },
            onDownloaded = { refreshKey++ }
        )
    }
    if (showDriveLogin) {
        de.graetz.electronote.drive.GoogleDriveLoginDialog(onDismiss = { showDriveLogin = false })
    }
    if (showDriveDownload) {
        de.graetz.electronote.drive.GoogleDriveDownloadDialog(
            onDismiss = { showDriveDownload = false },
            onDownloaded = { refreshKey++ }
        )
    }
}

@Composable
private fun NotebookRow(
    doc: NotebookDocumentSummary,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditTags: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        if (doc.docType == NotebookDocument.DOC_TYPE_WHITEBOARD) Icons.Outlined.Draw else Icons.Outlined.Description,
                        contentDescription = null,
                        tint = IosColors.Orange
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(doc.name)
                        Text(
                            text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(doc.updatedAt))
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (doc.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorit",
                        tint = if (doc.isFavorite) IosColors.Yellow else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEditTags) {
                    Icon(Icons.Outlined.Label, contentDescription = "Tags bearbeiten")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "In den Papierkorb")
                }
            }
            if (doc.tags.isNotEmpty()) {
                Text(
                    doc.tags.joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DiagramRow(
    summary: DiagramSummary,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEditTags: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        if (summary.type == DiagramDocument.TYPE_PAP) Icons.Outlined.AccountTree else Icons.Outlined.Hub,
                        contentDescription = null,
                        tint = if (summary.type == DiagramDocument.TYPE_PAP) IosColors.Blue else IosColors.Purple
                    )
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(summary.name)
                        Text(
                            text = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(summary.updatedAt))
                        )
                    }
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (summary.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorit",
                        tint = if (summary.isFavorite) IosColors.Yellow else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onEditTags) {
                    Icon(Icons.Outlined.Label, contentDescription = "Tags bearbeiten")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "In den Papierkorb")
                }
            }
            if (summary.tags.isNotEmpty()) {
                Text(
                    summary.tags.joinToString(" · "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 36.dp, top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun DocumentTypePickerDialog(
    onDismiss: () -> Unit,
    onPickNotebook: () -> Unit,
    onPickPap: () -> Unit,
    onPickWhiteboard: () -> Unit,
    onPickMindMap: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dokumenttyp wählen") },
        text = {
            Column {
                TypeOption(Icons.Outlined.Description, IosColors.Blue, "Notizbuch", "Endlos langer Zettel zum Schreiben und Zeichnen", onPickNotebook)
                TypeOption(Icons.Outlined.AccountTree, IosColors.Orange, "Ablaufplan (PAP)", "Programmablaufplan mit Knoten und Verbindungen", onPickPap)
                TypeOption(Icons.Outlined.Draw, IosColors.Green, "Whiteboard", "Freie Zeichenfläche", onPickWhiteboard)
                TypeOption(Icons.Outlined.Hub, IosColors.Purple, "MindMap", "Gedankenkarte mit Ästen und Notizen", onPickMindMap)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun TypeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

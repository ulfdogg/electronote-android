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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
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
import de.graetz.electronote.data.NotebookDocumentSummary
import de.graetz.electronote.data.NotebookStore
import de.graetz.electronote.nextcloud.NextcloudDownloadDialog
import de.graetz.electronote.nextcloud.NextcloudLoginDialog
import de.graetz.electronote.pdf.PdfImporter
import de.graetz.electronote.ui.theme.IosColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(onOpenDocument: (String) -> Unit, onOpenTrash: () -> Unit, onOpenSearch: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var documents by remember { mutableStateOf(listOf<NotebookDocumentSummary>()) }
    var refreshKey by remember { mutableStateOf(0) }
    var showNextcloudLogin by remember { mutableStateOf(false) }
    var showNextcloudDownload by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var editingTagsFor by remember { mutableStateOf<NotebookDocumentSummary?>(null) }
    var isImportingPdf by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        documents = NotebookStore.listDocuments(context)
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

    val allTags = documents.flatMap { it.tags }.distinct().sorted()
    val visibleDocuments = if (selectedTag != null) documents.filter { selectedTag in it.tags } else documents

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
                // iOS puts "new document" as a plain toolbar button, not a floating
                // action button (a Material pattern with no iPad equivalent).
                IconButton(onClick = {
                    val count = documents.size + 1
                    val doc = NotebookStore.createDocument(context, "Notizbuch $count")
                    refreshKey++
                    onOpenDocument(doc.id)
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = "Neues Notizbuch", modifier = Modifier.size(20.dp))
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

            if (visibleDocuments.isEmpty()) {
                Text(
                    text = if (documents.isEmpty()) "Noch keine Notizbücher. Tippe oben links auf + zum Anlegen." else "Keine Notizbücher mit diesem Tag.",
                    modifier = Modifier.padding(24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleDocuments, key = { it.id }) { doc ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDocument(doc.id) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            Icons.Outlined.Description,
                                            contentDescription = null,
                                            tint = IosColors.Orange
                                        )
                                        Column(modifier = Modifier.padding(start = 12.dp)) {
                                            Text(doc.name)
                                            Text(
                                                text = DateFormat.getDateTimeInstance(
                                                    DateFormat.SHORT, DateFormat.SHORT
                                                ).format(Date(doc.updatedAt))
                                            )
                                        }
                                    }
                                    IconButton(onClick = {
                                        NotebookStore.setFavorite(context, doc.id, !doc.isFavorite)
                                        refreshKey++
                                    }) {
                                        Icon(
                                            if (doc.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                                            contentDescription = "Favorit",
                                            tint = if (doc.isFavorite) IosColors.Yellow else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { editingTagsFor = doc }) {
                                        Icon(Icons.Outlined.Label, contentDescription = "Tags bearbeiten")
                                    }
                                    IconButton(onClick = {
                                        NotebookStore.moveToTrash(context, doc.id)
                                        refreshKey++
                                    }) {
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
                }
            }
        }
    }

    editingTagsFor?.let { doc ->
        var input by remember(doc.id) { mutableStateOf(doc.tags.joinToString(", ")) }
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
                    NotebookStore.setTags(context, doc.id, tags)
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
}

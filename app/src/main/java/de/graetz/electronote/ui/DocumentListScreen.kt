package de.graetz.electronote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.graetz.electronote.data.NotebookDocumentSummary
import de.graetz.electronote.data.NotebookStore
import de.graetz.electronote.nextcloud.NextcloudDownloadDialog
import de.graetz.electronote.nextcloud.NextcloudLoginDialog
import de.graetz.electronote.ui.theme.IosColors
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(onOpenDocument: (String) -> Unit, onOpenTrash: () -> Unit, onOpenSearch: () -> Unit) {
    val context = LocalContext.current
    var documents by remember { mutableStateOf(listOf<NotebookDocumentSummary>()) }
    var refreshKey by remember { mutableStateOf(0) }
    var showNextcloudLogin by remember { mutableStateOf(false) }
    var showNextcloudDownload by remember { mutableStateOf(false) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var editingTagsFor by remember { mutableStateOf<NotebookDocumentSummary?>(null) }

    LaunchedEffect(refreshKey) {
        documents = NotebookStore.listDocuments(context)
    }

    val allTags = documents.flatMap { it.tags }.distinct().sorted()
    val visibleDocuments = if (selectedTag != null) documents.filter { selectedTag in it.tags } else documents

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ElectroNote") },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(Icons.Filled.Search, contentDescription = "Suchen")
                    }
                    IconButton(onClick = onOpenTrash) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "Papierkorb")
                    }
                    IconButton(onClick = { showNextcloudDownload = true }) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = "Von Nextcloud laden")
                    }
                    IconButton(onClick = { showNextcloudLogin = true }) {
                        Icon(Icons.Filled.CloudQueue, contentDescription = "Nextcloud")
                    }
                    IconButton(onClick = { AppPreferences.toggleDarkMode(context) }) {
                        Icon(
                            if (AppPreferences.isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                            contentDescription = "Dunkelmodus umschalten"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val count = documents.size + 1
                val doc = NotebookStore.createDocument(context, "Notizbuch $count")
                refreshKey++
                onOpenDocument(doc.id)
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Neues Notizbuch")
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
                    text = if (documents.isEmpty()) "Noch keine Notizbücher. Tippe unten rechts auf + zum Anlegen." else "Keine Notizbücher mit diesem Tag.",
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
                                            Icons.Filled.Description,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
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
                                            if (doc.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            contentDescription = "Favorit",
                                            tint = if (doc.isFavorite) IosColors.Yellow else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { editingTagsFor = doc }) {
                                        Icon(Icons.Filled.Label, contentDescription = "Tags bearbeiten")
                                    }
                                    IconButton(onClick = {
                                        NotebookStore.moveToTrash(context, doc.id)
                                        refreshKey++
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "In den Papierkorb")
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

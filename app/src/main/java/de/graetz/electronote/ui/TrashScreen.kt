package de.graetz.electronote.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.graetz.electronote.data.NotebookStore
import de.graetz.electronote.diagram.DiagramDocument
import de.graetz.electronote.diagram.DiagramStore
import de.graetz.electronote.ui.theme.IosColors
import java.text.DateFormat
import java.util.Date

private sealed class TrashItem(val id: String, val name: String, val deletedAt: Long?, val icon: ImageVector, val tint: androidx.compose.ui.graphics.Color) {
    class Notebook(val id0: String, name: String, deletedAt: Long?) :
        TrashItem(id0, name, deletedAt, Icons.Outlined.Description, IosColors.Orange)
    class Diagram(val id0: String, name: String, deletedAt: Long?, type: String) :
        TrashItem(id0, name, deletedAt, if (type == DiagramDocument.TYPE_PAP) Icons.Outlined.AccountTree else Icons.Outlined.Hub, if (type == DiagramDocument.TYPE_PAP) IosColors.Blue else IosColors.Purple)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var trashed by remember { mutableStateOf(listOf<TrashItem>()) }
    var refreshKey by remember { mutableStateOf(0) }
    var showEmptyConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey) {
        val notebooks = NotebookStore.listTrash(context).map { TrashItem.Notebook(it.id, it.name, it.deletedAt) }
        val diagrams = DiagramStore.listTrash(context).map { TrashItem.Diagram(it.id, it.name, it.deletedAt, it.type) }
        trashed = (notebooks + diagrams).sortedByDescending { it.deletedAt }
    }

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
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Zurück", modifier = Modifier.size(20.dp))
                }
                Text(
                    "Papierkorb",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 6.dp)
                )
                if (trashed.isNotEmpty()) {
                    IconButton(onClick = { showEmptyConfirm = true }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Papierkorb leeren", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        if (trashed.isEmpty()) {
            Text(
                text = "Papierkorb ist leer.",
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(trashed, key = { it.id }) { doc ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(doc.icon, contentDescription = null, tint = doc.tint)
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(doc.name)
                                Text(
                                    text = "Gelöscht: " + (doc.deletedAt?.let {
                                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                                    } ?: ""),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = {
                                when (doc) {
                                    is TrashItem.Notebook -> NotebookStore.restoreFromTrash(context, doc.id)
                                    is TrashItem.Diagram -> DiagramStore.restoreFromTrash(context, doc.id)
                                }
                                refreshKey++
                            }) {
                                Icon(Icons.Outlined.Restore, contentDescription = "Wiederherstellen")
                            }
                            IconButton(onClick = {
                                when (doc) {
                                    is TrashItem.Notebook -> NotebookStore.deleteDocument(context, doc.id)
                                    is TrashItem.Diagram -> DiagramStore.deleteDiagram(context, doc.id)
                                }
                                refreshKey++
                            }) {
                                Icon(Icons.Outlined.DeleteForever, contentDescription = "Endgültig löschen")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEmptyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmptyConfirm = false },
            title = { Text("Papierkorb leeren?") },
            text = { Text("Alles im Papierkorb wird endgültig gelöscht. Das kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                TextButton(onClick = {
                    NotebookStore.emptyTrash(context)
                    DiagramStore.emptyTrash(context)
                    showEmptyConfirm = false
                    refreshKey++
                }) { Text("Endgültig leeren") }
            },
            dismissButton = {
                TextButton(onClick = { showEmptyConfirm = false }) { Text("Abbrechen") }
            }
        )
    }
}

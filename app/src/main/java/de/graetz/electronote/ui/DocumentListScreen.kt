package de.graetz.electronote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.graetz.electronote.data.NotebookDocumentSummary
import de.graetz.electronote.data.NotebookStore
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(onOpenDocument: (String) -> Unit) {
    val context = LocalContext.current
    var documents by remember { mutableStateOf(listOf<NotebookDocumentSummary>()) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(refreshKey) {
        documents = NotebookStore.listDocuments(context)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ElectroNote") }) },
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
        if (documents.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Text(
                    text = "Noch keine Notizbücher. Tippe unten rechts auf + zum Anlegen.",
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(documents, key = { it.id }) { doc ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDocument(doc.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                            ) {
                                Icon(Icons.Filled.Description, contentDescription = null)
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
                                NotebookStore.deleteDocument(context, doc.id)
                                refreshKey++
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                            }
                        }
                    }
                }
            }
        }
    }
}

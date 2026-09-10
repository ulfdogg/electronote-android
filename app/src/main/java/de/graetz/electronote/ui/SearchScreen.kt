package de.graetz.electronote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

/**
 * Cross-notebook full-text search over names, tags, typed text, and OCR'd handwriting
 * (see `NotebookScreen.buildSearchText`, indexed once per editing session on exit).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenDocument: (String) -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var allDocs by remember { mutableStateOf(listOf<NotebookDocumentSummary>()) }

    LaunchedEffect(Unit) { allDocs = NotebookStore.listDocuments(context) }

    val results = remember(query, allDocs) {
        if (query.isBlank()) {
            emptyList()
        } else {
            allDocs.filter { doc ->
                doc.name.contains(query, ignoreCase = true) ||
                    doc.tags.any { it.contains(query, ignoreCase = true) } ||
                    doc.searchText.contains(query, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Notizbücher durchsuchen…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        when {
            query.isBlank() -> Text(
                "Suche nach Notizbuch-Namen, Tags oder Inhalt — auch handgeschriebenem Text.",
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
            )
            results.isEmpty() -> Text(
                "Keine Treffer.",
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.id }) { doc ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDocument(doc.id) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(doc.name, style = MaterialTheme.typography.titleSmall)
                            val snippet = snippetFor(doc.searchText, query)
                            if (snippet.isNotBlank()) {
                                Text(snippet, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun snippetFor(text: String, query: String): String {
    val idx = text.indexOf(query, ignoreCase = true)
    if (idx < 0) return ""
    val start = maxOf(0, idx - 40)
    val end = minOf(text.length, idx + query.length + 40)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < text.length) "…" else ""
    return prefix + text.substring(start, end) + suffix
}

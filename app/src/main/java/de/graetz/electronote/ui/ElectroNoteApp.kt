package de.graetz.electronote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun ElectroNoteApp() {
    MaterialTheme {
        Surface(modifier = Modifier) {
            var openDocumentId by remember { mutableStateOf<String?>(null) }

            val documentId = openDocumentId
            if (documentId == null) {
                DocumentListScreen(onOpenDocument = { openDocumentId = it })
            } else {
                NotebookScreen(documentId = documentId, onBack = { openDocumentId = null })
            }
        }
    }
}

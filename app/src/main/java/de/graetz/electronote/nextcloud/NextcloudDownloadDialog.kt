package de.graetz.electronote.nextcloud

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import de.graetz.electronote.data.NotebookDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun NextcloudDownloadDialog(onDismiss: () -> Unit, onDownloaded: (NotebookDocument) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentials = remember { NextcloudAuthStore.load(context) }
    var entries by remember { mutableStateOf<List<RemoteDocumentEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var downloadingFolder by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (credentials != null) {
            entries = withContext(Dispatchers.IO) { NextcloudWebDav.listDocumentFolders(credentials) }
        }
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .width(340.dp)
            ) {
                Text("Von Nextcloud laden", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                when {
                    credentials == null -> Text("Nicht mit Nextcloud verbunden.")
                    isLoading -> CircularProgressIndicator()
                    entries.isEmpty() -> Text("Keine Notizbücher gefunden.")
                    else -> {
                        for (entry in entries) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = downloadingFolder == null) {
                                        downloadingFolder = entry.folderName
                                        scope.launch {
                                            val doc = withContext(Dispatchers.IO) {
                                                NextcloudSync.download(context, credentials, entry.folderName)
                                            }
                                            downloadingFolder = null
                                            if (doc != null) {
                                                onDownloaded(doc)
                                                onDismiss()
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(entry.folderName, modifier = Modifier.weight(1f))
                                if (downloadingFolder == entry.folderName) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
            }
        }
    }
}

package de.graetz.electronote.drive

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GoogleDriveDownloadDialog(onDismiss: () -> Unit, onDownloaded: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account = remember { GoogleDriveAuth.lastAccount(context) }
    var entries by remember { mutableStateOf<List<DriveNotebookEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var downloadingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (account != null) {
            val token = withContext(Dispatchers.IO) { GoogleDriveAuth.getAccessToken(context, account) }
            if (token != null) {
                entries = withContext(Dispatchers.IO) { GoogleDriveSync.listRemoteNotebooks(context, token) }
            }
        }
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp).width(340.dp)) {
                Text("Von Google Drive laden", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                when {
                    account == null -> Text("Nicht mit Google Drive verbunden.")
                    isLoading -> CircularProgressIndicator()
                    entries.isEmpty() -> Text("Keine Notizbücher gefunden.")
                    else -> {
                        for (entry in entries) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = downloadingId == null) {
                                        downloadingId = entry.folderId
                                        scope.launch {
                                            val token = withContext(Dispatchers.IO) { GoogleDriveAuth.getAccessToken(context, account) }
                                            val doc = token?.let {
                                                withContext(Dispatchers.IO) { GoogleDriveSync.download(context, it, entry) }
                                            }
                                            downloadingId = null
                                            if (doc != null) {
                                                onDownloaded()
                                                onDismiss()
                                            }
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(entry.name, modifier = Modifier.weight(1f))
                                if (downloadingId == entry.folderId) {
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

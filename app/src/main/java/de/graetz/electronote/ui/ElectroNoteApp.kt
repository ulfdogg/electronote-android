package de.graetz.electronote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import de.graetz.electronote.livecast.LiveCastServer
import de.graetz.electronote.ui.theme.ElectroNoteTheme

@Composable
fun ElectroNoteApp() {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    LaunchedEffect(Unit) { AppPreferences.init(context, systemDark) }

    ElectroNoteTheme(darkTheme = AppPreferences.isDarkMode) {
        Surface(modifier = Modifier) {
            var openDocumentId by remember { mutableStateOf<String?>(null) }

            val rootView = LocalView.current
            DisposableEffect(rootView) {
                LiveCastServer.setTargetView(rootView)
                onDispose { LiveCastServer.setTargetView(null) }
            }
            // Without this the screen auto-locks after the normal idle timeout, the app
            // gets backgrounded, and the stream drops — same lesson learned on iOS.
            LaunchedEffect(LiveCastServer.isStreaming) {
                rootView.keepScreenOn = LiveCastServer.isStreaming
            }

            var showTrash by remember { mutableStateOf(false) }
            var showSearch by remember { mutableStateOf(false) }

            val documentId = openDocumentId
            if (documentId != null) {
                NotebookScreen(documentId = documentId, onBack = { openDocumentId = null })
            } else if (showTrash) {
                TrashScreen(onBack = { showTrash = false })
            } else if (showSearch) {
                SearchScreen(
                    onBack = { showSearch = false },
                    onOpenDocument = { showSearch = false; openDocumentId = it }
                )
            } else {
                DocumentListScreen(
                    onOpenDocument = { openDocumentId = it },
                    onOpenTrash = { showTrash = true },
                    onOpenSearch = { showSearch = true }
                )
            }
        }
    }
}

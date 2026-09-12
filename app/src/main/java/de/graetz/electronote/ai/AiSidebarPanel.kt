package de.graetz.electronote.ai

import android.content.ClipData
import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val ELECTRICAL_FOCUS_PREFIX =
    "Du bist ein erfahrener Fachlehrer und Prüfungsmeister für Elektrotechnik (DIN VDE, " +
        "Schutzmaßnahmen, Schaltungsanalyse). Antworte fachlich präzise nach geltenden " +
        "deutschen VDE-Normen."

private data class PresetQuestion(val label: String, val question: String)

private val PRESET_QUESTIONS = listOf(
    PresetQuestion("Seite zusammenfassen", "Fasse den Inhalt dieser Seite kurz und übersichtlich in Stichpunkten zusammen."),
    PresetQuestion("Fachbegriffe & Formeln erklären", "Erkläre die wichtigsten Fachbegriffe, Formeln und Zusammenhänge auf dieser Seite verständlich für Auszubildende."),
    PresetQuestion("Auf Richtigkeit prüfen (DIN VDE)", "Prüfe den Inhalt dieser Seite und die handschriftlichen Notizen auf fachliche Richtigkeit nach DIN VDE."),
    PresetQuestion("3 Prüfungsfragen erstellen", "Erstelle 3 typische Prüfungsfragen inklusive Musterantworten zu diesem Seiteninhalt.")
)

/**
 * In-notebook KI-Assistent panel, matching the iPad app's AISidebarView: provider tabs,
 * bookmarks, "Seite kopieren" (text context) / "Screenshot" (visual context) quick
 * actions, and preset/custom questions — all copied to the clipboard for the user to
 * paste into the chat, the same approach as iOS (neither platform can script text into
 * the provider's own page).
 *
 * Uses an embedded WebView rather than Chrome Custom Tabs (which iOS's SFSafariViewController
 * equivalent would suggest) because a persistent side panel can't be a separate Custom Tabs
 * activity. The known tradeoff: Google blocks "Sign in with Google" inside embedded WebViews
 * ("this browser may not be secure") — the "in Chrome öffnen" button is the escape hatch for
 * that case, opening the same URL via Custom Tabs (which does support Google sign-in) instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSidebarPanel(
    onClose: () -> Unit,
    onCollectContextText: suspend () -> String,
    onCaptureScreenshot: () -> Bitmap?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textClipboard = LocalClipboardManager.current

    var selectedProvider by remember { mutableStateOf(AiProvider.ChatGPT) }
    var activeBookmarkUrl by remember { mutableStateOf<String?>(null) }
    var bookmarks by remember { mutableStateOf(AiBookmarkStore.load(context)) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }
    var showCustomQuestionDialog by remember { mutableStateOf(false) }
    var showQuestionMenu by remember { mutableStateOf(false) }
    var customQuestionText by remember { mutableStateOf("") }
    var isExtracting by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    val currentUrl = activeBookmarkUrl ?: selectedProvider.url

    fun persistBookmarks(updated: List<AiBookmark>) {
        bookmarks = updated
        AiBookmarkStore.save(context, updated)
    }

    fun buildPrompt(contextText: String, customQuestion: String?): String {
        val prefix = "[$ELECTRICAL_FOCUS_PREFIX]\n\n"
        return if (!customQuestion.isNullOrBlank()) {
            """
            ${prefix}Hier ist der Inhalt aus meinen Notizen / Dokumentseiten:
            ---
            $contextText
            ---

            Aufgabe / Frage:
            $customQuestion
            """.trimIndent()
        } else {
            """
            ${prefix}Hier ist der Inhalt aus meinen Notizen / Dokumentseiten:
            ---
            $contextText
            ---

            Bitte analysiere den Inhalt und frage mich, was du dazu erklären oder zusammenfassen sollst.
            """.trimIndent()
        }
    }

    fun transferContext(customQuestion: String?) {
        if (isExtracting) return
        isExtracting = true
        scope.launch {
            val contextText = onCollectContextText()
            isExtracting = false
            if (contextText.isBlank()) {
                Toast.makeText(context, "Kein Text auf dieser Seite gefunden", Toast.LENGTH_SHORT).show()
                return@launch
            }
            textClipboard.setText(AnnotatedString(buildPrompt(contextText, customQuestion)))
            Toast.makeText(context, "In Zwischenablage kopiert – im ${selectedProvider.label}-Feld einfügen", Toast.LENGTH_LONG).show()
        }
    }

    fun copyScreenshot() {
        val bitmap = onCaptureScreenshot()
        if (bitmap == null) {
            Toast.makeText(context, "Konnte keinen Screenshot erstellen", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch(Dispatchers.IO) {
            val dir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(dir, "ai_screenshot_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            withContext(Dispatchers.Main) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(ClipData.newUri(context.contentResolver, "Screenshot", uri))
                Toast.makeText(context, "Screenshot kopiert – im ${selectedProvider.label}-Feld einfügen", Toast.LENGTH_LONG).show()
            }
        }
    }

    Surface(
        modifier = modifier
            .width(400.dp)
            .fillMaxHeight(),
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header: provider tabs + bookmarks + open-externally + close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (provider in AiProvider.entries) {
                        val active = provider == selectedProvider && activeBookmarkUrl == null
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) provider.brandColor else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.clickable {
                                selectedProvider = provider
                                activeBookmarkUrl = null
                            }
                        ) {
                            Text(
                                provider.label,
                                fontSize = 12.sp,
                                color = if (active) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = { showBookmarksDialog = true }) {
                    Icon(
                        if (activeBookmarkUrl != null) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Lesezeichen",
                        modifier = Modifier.width(20.dp)
                    )
                }
                IconButton(onClick = { openUrlInBrowser(context, currentUrl) }) {
                    Icon(Icons.Outlined.OpenInBrowser, contentDescription = "Im Browser öffnen", modifier = Modifier.width(20.dp))
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Outlined.Close, contentDescription = "Schließen", modifier = Modifier.width(20.dp))
                }
            }

            // Quick actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = selectedProvider.brandColor.copy(alpha = 0.12f),
                    modifier = Modifier.clickable(enabled = !isExtracting) { transferContext(null) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isExtracting) {
                            CircularProgressIndicator(modifier = Modifier.width(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = selectedProvider.brandColor, modifier = Modifier.width(16.dp))
                        }
                        Text("Seite kopieren", fontSize = 12.sp, color = selectedProvider.brandColor, modifier = Modifier.padding(start = 4.dp))
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = selectedProvider.brandColor.copy(alpha = 0.12f),
                    modifier = Modifier.clickable { copyScreenshot() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.CropFree, contentDescription = null, tint = selectedProvider.brandColor, modifier = Modifier.width(16.dp))
                        Text("Screenshot", fontSize = 12.sp, color = selectedProvider.brandColor, modifier = Modifier.padding(start = 4.dp))
                    }
                }

                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { showQuestionMenu = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.QuestionAnswer, contentDescription = null, modifier = Modifier.width(16.dp))
                            Text("Frage vorbereiten…", fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    DropdownMenu(expanded = showQuestionMenu, onDismissRequest = { showQuestionMenu = false }) {
                        for (preset in PRESET_QUESTIONS) {
                            DropdownMenuItem(
                                text = { Text(preset.label) },
                                onClick = { showQuestionMenu = false; transferContext(preset.question) }
                            )
                        }
                        androidx.compose.material3.HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Eigene Frage mit Notizinhalt…") },
                            onClick = { showQuestionMenu = false; showCustomQuestionDialog = true }
                        )
                    }
                }
            }

            androidx.compose.material3.HorizontalDivider()

            Box(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            webViewRef = this
                            loadUrl(currentUrl)
                        }
                    },
                    update = { view ->
                        if (view.url != currentUrl) view.loadUrl(currentUrl)
                    }
                )
            }
        }
    }

    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            title = { Text("Lesezeichen") },
            text = {
                Column {
                    if (bookmarks.isEmpty()) {
                        Text("Noch keine Lesezeichen. Öffne den gewünschten Chat im Browser, kopiere den Link und füge ihn hier ein.")
                    } else {
                        for (bookmark in bookmarks) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        activeBookmarkUrl = bookmark.url
                                        showBookmarksDialog = false
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(bookmark.name)
                                    Text(bookmark.url, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                IconButton(onClick = { persistBookmarks(bookmarks.filter { it.id != bookmark.id }) }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Löschen")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddBookmarkDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                    Text(" Hinzufügen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBookmarksDialog = false }) { Text("Fertig") }
            }
        )
    }

    if (showAddBookmarkDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddBookmarkDialog = false },
            title = { Text("Neues Lesezeichen") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, placeholder = { Text("Name") }, singleLine = true)
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(value = url, onValueChange = { url = it }, placeholder = { Text("https://…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { textClipboard.getText()?.text?.let { url = it } }) {
                        Text("Aus Zwischenablage einfügen")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmedUrl = url.trim()
                    if (trimmedUrl.isNotEmpty()) {
                        persistBookmarks(bookmarks + AiBookmark(name = name.trim().ifEmpty { trimmedUrl }, url = trimmedUrl))
                    }
                    showAddBookmarkDialog = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmarkDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showCustomQuestionDialog) {
        AlertDialog(
            onDismissRequest = { showCustomQuestionDialog = false },
            title = { Text("Frage an die KI stellen") },
            text = {
                Column {
                    Text("Der Inhalt der aktuellen Seite wird automatisch als Kontext mitgeschickt.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedTextField(
                            value = customQuestionText,
                            onValueChange = { customQuestionText = it },
                            placeholder = { Text("Deine Frage zum Dokument…") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val q = customQuestionText.trim()
                    customQuestionText = ""
                    showCustomQuestionDialog = false
                    if (q.isNotEmpty()) transferContext(q)
                }) { Text("Übernehmen") }
            },
            dismissButton = {
                TextButton(onClick = { customQuestionText = ""; showCustomQuestionDialog = false }) { Text("Abbrechen") }
            }
        )
    }
}

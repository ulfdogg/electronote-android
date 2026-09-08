package de.graetz.electronote.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import de.graetz.electronote.ai.AiProvider
import de.graetz.electronote.ai.openAiProvider
import de.graetz.electronote.canvas.InkCanvas
import de.graetz.electronote.canvas.InkCanvasController
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookPage
import de.graetz.electronote.data.NotebookStore
import de.graetz.electronote.livecast.LiveCastSheet
import de.graetz.electronote.livecast.LiveCastServer
import de.graetz.electronote.media.DocumentScanImporter
import de.graetz.electronote.media.PhotoImporter
import de.graetz.electronote.ocr.HandwritingRecognizer
import de.graetz.electronote.pdf.PdfExporter
import de.graetz.electronote.pdf.PdfImporter
import de.graetz.electronote.ui.theme.ActionPill
import de.graetz.electronote.ui.theme.IosColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PALETTE = listOf(
    AndroidColor.BLACK,
    AndroidColor.parseColor("#1E88E5"),
    AndroidColor.parseColor("#E53935"),
    AndroidColor.parseColor("#2E7D32"),
    AndroidColor.parseColor("#F57C00"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(documentId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { InkCanvasController() }

    var document by remember { mutableStateOf<NotebookDocument?>(null) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    fun syncCurrentPage() {
        val doc = document ?: return
        if (currentPageIndex in doc.pages.indices) {
            val page = doc.pages[currentPageIndex]
            page.strokes = controller.getStrokes()
            controller.canvasSize()?.let { (w, h) ->
                page.canvasWidthPx = w
                page.canvasHeightPx = h
            }
        }
    }

    suspend fun loadPageIntoCanvas(doc: NotebookDocument, index: Int) {
        val page = doc.pages.getOrNull(index) ?: return
        controller.setStrokes(page.strokes)
        val bgFile = page.backgroundImageFile
        val bitmap = if (bgFile != null) {
            withContext(Dispatchers.IO) { NotebookStore.loadPageBackground(context, doc.id, bgFile) }
        } else {
            null
        }
        controller.setBackgroundPage(bitmap)
    }

    fun saveDocument() {
        val doc = document ?: return
        syncCurrentPage()
        scope.launch(Dispatchers.IO) { NotebookStore.saveDocument(context, doc) }
    }

    LaunchedEffect(documentId) {
        val loaded = withContext(Dispatchers.IO) { NotebookStore.loadDocument(context, documentId) }
        document = loaded
        if (loaded != null) {
            loadPageIntoCanvas(loaded, 0)
        }
        isLoading = false
    }

    LaunchedEffect(AppPreferences.isDarkMode) {
        controller.setDarkPaper(AppPreferences.isDarkMode)
    }

    suspend fun appendPagesAndNavigate(doc: NotebookDocument, newPages: List<NotebookPage>) {
        if (newPages.isEmpty()) return
        doc.pages.addAll(newPages)
        withContext(Dispatchers.IO) { NotebookStore.saveDocument(context, doc) }
        currentPageIndex = doc.pages.size - newPages.size
        loadPageIntoCanvas(doc, currentPageIndex)
    }

    // PDF import: Android's document picker (Storage Access Framework) surfaces Google
    // Drive, Nextcloud etc. as sources automatically if those apps are installed — no
    // separate cloud API integration needed to "import from the cloud".
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val doc = document ?: return@rememberLauncherForActivityResult
        scope.launch {
            syncCurrentPage()
            val newPages = withContext(Dispatchers.IO) { PdfImporter.importPdf(context, uri, doc.id) }
            appendPagesAndNavigate(doc, newPages)
        }
    }

    // PDF export: same idea in reverse — ACTION_CREATE_DOCUMENT lets the user save
    // straight into Drive/Nextcloud/local storage, whatever they pick.
    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val doc = document ?: return@rememberLauncherForActivityResult
        syncCurrentPage()
        scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                PdfExporter.export(context, doc, out)
            }
        }
    }

    // Photo import (gallery) — becomes a new page background, same as PDF pages.
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val doc = document ?: return@rememberLauncherForActivityResult
        scope.launch {
            syncCurrentPage()
            val page = withContext(Dispatchers.IO) { PhotoImporter.importPhoto(context, uri, doc.id) }
            appendPagesAndNavigate(doc, listOfNotNull(page))
        }
    }

    // Document scanner (ML Kit / Google Play Services): camera-based multi-page scan
    // with automatic edge detection, each page becomes a notebook page background.
    val docScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val doc = document ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanResult != null) {
                scope.launch {
                    syncCurrentPage()
                    val newPages = withContext(Dispatchers.IO) {
                        DocumentScanImporter.importResult(context, scanResult, doc.id)
                    }
                    appendPagesAndNavigate(doc, newPages)
                }
            }
        }
    }

    fun startDocumentScan() {
        val activity = context as? Activity ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        val scanner: GmsDocumentScanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                docScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Scanner konnte nicht gestartet werden: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // OCR: circle a region of the page, run on-device ML Kit text recognition on it.
    var ocrResultText by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current

    fun startOcr() {
        controller.startSelection(
            onMade = { rect ->
                val bitmap = controller.captureRegion(rect)
                controller.stopSelection()
                if (bitmap == null) {
                    Toast.makeText(context, "Konnte Bereich nicht erfassen", Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch {
                        val text = try {
                            withContext(Dispatchers.Default) { HandwritingRecognizer.recognize(bitmap) }
                        } catch (e: Exception) {
                            ""
                        } finally {
                            bitmap.recycle()
                        }
                        if (text.isBlank()) {
                            Toast.makeText(context, "Nichts erkannt", Toast.LENGTH_SHORT).show()
                        } else {
                            ocrResultText = text
                        }
                    }
                }
            },
            onCancelled = {
                Toast.makeText(context, "Auswahl zu klein — bitte großzügiger umkreisen", Toast.LENGTH_SHORT).show()
            }
        )
        Toast.makeText(context, "Bereich mit Finger/Stift umkreisen", Toast.LENGTH_SHORT).show()
    }

    var showAiMenu by remember { mutableStateOf(false) }
    var showInsertMenu by remember { mutableStateOf(false) }
    var showLiveCastSheet by remember { mutableStateOf(false) }
    var selectedColorArgb by remember { mutableStateOf(PALETTE[0]) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(document?.name ?: "") },
                    navigationIcon = {
                        IconButton(onClick = {
                            saveDocument()
                            onBack()
                        }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    },
                    actions = {
                        IconButton(onClick = { controller.undo() }, enabled = controller.hasUndo) {
                            Icon(Icons.Filled.Undo, contentDescription = "Rückgängig")
                        }
                        IconButton(onClick = { controller.redo() }, enabled = controller.hasRedo) {
                            Icon(Icons.Filled.Redo, contentDescription = "Wiederholen")
                        }
                        IconButton(onClick = { AppPreferences.toggleDarkMode(context) }) {
                            Icon(
                                if (AppPreferences.isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                                contentDescription = "Dunkelmodus umschalten"
                            )
                        }
                        IconButton(onClick = { saveDocument() }) {
                            Icon(Icons.Filled.Save, contentDescription = "Speichern")
                        }
                        Box {
                            IconButton(onClick = { showAiMenu = true }) {
                                Icon(Icons.Filled.SmartToy, contentDescription = "KI-Assistent")
                            }
                            DropdownMenu(expanded = showAiMenu, onDismissRequest = { showAiMenu = false }) {
                                for (provider in AiProvider.entries) {
                                    DropdownMenuItem(
                                        text = { Text(provider.label) },
                                        onClick = {
                                            showAiMenu = false
                                            openAiProvider(context, provider)
                                        }
                                    )
                                }
                            }
                        }
                    }
                )

                // Row 2: colorful action pills — same pattern as the iPad app's second
                // toolbar row (Formen/Handschrift/Mathe/…), just with Android's own
                // feature set (no eraser/pen-type picker here yet, see README).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        ActionPill(
                            label = "Einfügen",
                            icon = Icons.Filled.Add,
                            color = IosColors.Teal,
                            onClick = { showInsertMenu = true }
                        )
                        DropdownMenu(expanded = showInsertMenu, onDismissRequest = { showInsertMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("PDF importieren") },
                                onClick = {
                                    showInsertMenu = false
                                    pdfPicker.launch(arrayOf("application/pdf"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Foto importieren") },
                                onClick = {
                                    showInsertMenu = false
                                    photoPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Dokument scannen") },
                                onClick = {
                                    showInsertMenu = false
                                    startDocumentScan()
                                }
                            )
                        }
                    }
                    ActionPill(
                        label = "Exportieren",
                        icon = Icons.Filled.IosShare,
                        color = IosColors.Blue,
                        onClick = {
                            val name = (document?.name ?: "Notizbuch") + ".pdf"
                            pdfExportLauncher.launch(name)
                        }
                    )
                    ActionPill(
                        label = "Text erkennen",
                        icon = Icons.Filled.TextFields,
                        color = IosColors.Indigo,
                        onClick = { startOcr() }
                    )
                    ActionPill(
                        label = if (LiveCastServer.isStreaming) "LIVE" else "Übertragen",
                        icon = Icons.Filled.Wifi,
                        color = if (LiveCastServer.isStreaming) IosColors.Red else IosColors.Pink,
                        onClick = { showLiveCastSheet = true }
                    )
                }
            }
        },
        bottomBar = {
            val doc = document
            if (doc != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        for (c in PALETTE) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .clickable {
                                        selectedColorArgb = c
                                        controller.setColor(c)
                                    }
                            ) {
                                if (selectedColorArgb == c) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = if (c == AndroidColor.WHITE) Color.Black else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (currentPageIndex > 0) {
                                    syncCurrentPage()
                                    currentPageIndex--
                                    scope.launch { loadPageIntoCanvas(doc, currentPageIndex) }
                                }
                            },
                            enabled = currentPageIndex > 0
                        ) {
                            Icon(Icons.Filled.ChevronLeft, contentDescription = "Vorherige Seite")
                        }
                        Text("${currentPageIndex + 1} / ${doc.pages.size}")
                        IconButton(onClick = {
                            syncCurrentPage()
                            if (currentPageIndex < doc.pages.size - 1) {
                                currentPageIndex++
                            } else {
                                doc.pages.add(NotebookPage())
                                currentPageIndex = doc.pages.size - 1
                            }
                            scope.launch { loadPageIntoCanvas(doc, currentPageIndex) }
                        }) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Nächste Seite / Neue Seite")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (!isLoading && document != null) {
                InkCanvas(controller = controller, modifier = Modifier.fillMaxSize())
            }
        }
    }

    if (showLiveCastSheet) {
        LiveCastSheet(onDismiss = { showLiveCastSheet = false })
    }

    ocrResultText?.let { text ->
        AlertDialog(
            onDismissRequest = { ocrResultText = null },
            title = { Text("Erkannter Text") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    clipboardManager.setText(AnnotatedString(text))
                    ocrResultText = null
                }) {
                    Text("Kopieren")
                }
            },
            dismissButton = {
                TextButton(onClick = { ocrResultText = null }) {
                    Text("Schließen")
                }
            }
        )
    }
}

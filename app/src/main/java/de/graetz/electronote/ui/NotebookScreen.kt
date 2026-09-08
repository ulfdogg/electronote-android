package de.graetz.electronote.ui

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import de.graetz.electronote.ai.AiProvider
import de.graetz.electronote.ai.openAiProvider
import de.graetz.electronote.canvas.InkCanvas
import de.graetz.electronote.canvas.InkCanvasController
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookPage
import de.graetz.electronote.data.NotebookStore
import de.graetz.electronote.pdf.PdfExporter
import de.graetz.electronote.pdf.PdfImporter
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
            val newPages = withContext(Dispatchers.IO) {
                PdfImporter.importPdf(context, uri, doc.id)
            }
            if (newPages.isNotEmpty()) {
                doc.pages.addAll(newPages)
                withContext(Dispatchers.IO) { NotebookStore.saveDocument(context, doc) }
                currentPageIndex = doc.pages.size - newPages.size
                loadPageIntoCanvas(doc, currentPageIndex)
            }
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

    var showAiMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
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
                    IconButton(onClick = { pdfPicker.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF importieren")
                    }
                    IconButton(onClick = {
                        val name = (document?.name ?: "Notizbuch") + ".pdf"
                        pdfExportLauncher.launch(name)
                    }) {
                        Icon(Icons.Filled.IosShare, contentDescription = "Als PDF exportieren")
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
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(c))
                                    .clickable { controller.setColor(c) }
                            )
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
}

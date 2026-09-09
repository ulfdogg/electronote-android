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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ModeEditOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import de.graetz.electronote.canvas.DrawTool
import de.graetz.electronote.canvas.InkCanvas
import de.graetz.electronote.canvas.InkCanvasController
import de.graetz.electronote.canvas.InkPreset
import de.graetz.electronote.canvas.InkPresetStore
import de.graetz.electronote.canvas.LineSpacing
import de.graetz.electronote.canvas.PaperStyle
import de.graetz.electronote.canvas.StickyNoteElement
import de.graetz.electronote.canvas.TextElement
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookStore
import de.graetz.electronote.livecast.LiveCastServer
import de.graetz.electronote.livecast.LiveCastSheet
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

private val STROKE_WIDTHS = listOf(2.5f, 4.5f, 7f, 11f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(documentId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { InkCanvasController() }
    val scrollState = rememberScrollState()

    var document by remember { mutableStateOf<NotebookDocument?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    suspend fun reloadBackgroundLayers(doc: NotebookDocument) {
        val layers = withContext(Dispatchers.IO) {
            doc.backgrounds.map { bg -> bg to NotebookStore.loadBackgroundImage(context, doc.id, bg.imageFile) }
        }
        controller.setBackgroundLayers(layers)
    }

    fun syncDocumentFromCanvas() {
        val doc = document ?: return
        doc.strokes = controller.getStrokes().toMutableList()
        doc.textElements = controller.getTextElements().toMutableList()
        doc.stickyNotes = controller.getStickyNotes().toMutableList()
        doc.canvasHeightPx = controller.canvasHeightPx
        if (controller.canvasWidthPx > 0) doc.canvasWidthPx = controller.canvasWidthPx
    }

    fun saveDocument() {
        val doc = document ?: return
        syncDocumentFromCanvas()
        scope.launch(Dispatchers.IO) { NotebookStore.saveDocument(context, doc) }
    }

    LaunchedEffect(documentId) {
        val loaded = withContext(Dispatchers.IO) { NotebookStore.loadDocument(context, documentId) }
        document = loaded
        if (loaded != null) {
            controller.canvasHeightPx = loaded.canvasHeightPx
            controller.setStrokes(loaded.strokes)
            controller.setTextElements(loaded.textElements)
            controller.setStickyNotes(loaded.stickyNotes)
            reloadBackgroundLayers(loaded)
        }
        isLoading = false
    }

    // The canvas grows itself as the user writes near the bottom; keep the document's
    // own height field in sync so saves/exports use the current, grown size.
    LaunchedEffect(controller.canvasHeightPx) {
        document?.canvasHeightPx = controller.canvasHeightPx
    }
    LaunchedEffect(controller.canvasWidthPx) {
        if (controller.canvasWidthPx > 0) document?.canvasWidthPx = controller.canvasWidthPx
    }
    LaunchedEffect(AppPreferences.isDarkMode) {
        controller.setDarkPaper(AppPreferences.isDarkMode)
    }

    // PDF import: Android's document picker (Storage Access Framework) surfaces Google
    // Drive, Nextcloud etc. as sources automatically if those apps are installed.
    val pdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val doc = document ?: return@rememberLauncherForActivityResult
        scope.launch {
            syncDocumentFromCanvas()
            val newBackgrounds = withContext(Dispatchers.IO) { PdfImporter.importPdf(context, uri, doc) }
            if (newBackgrounds.isNotEmpty()) {
                doc.backgrounds.addAll(newBackgrounds)
                val bottom = newBackgrounds.maxOf { it.yOffsetPx + it.heightPx } + 200
                doc.canvasHeightPx = maxOf(doc.canvasHeightPx, bottom)
                controller.canvasHeightPx = doc.canvasHeightPx
                withContext(Dispatchers.IO) { NotebookStore.saveDocument(context, doc) }
                reloadBackgroundLayers(doc)
            }
        }
    }

    val pdfExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val doc = document ?: return@rememberLauncherForActivityResult
        syncDocumentFromCanvas()
        scope.launch(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                PdfExporter.export(context, doc, out)
            }
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val doc = document ?: return@rememberLauncherForActivityResult
        scope.launch {
            syncDocumentFromCanvas()
            val bg = withContext(Dispatchers.IO) { PhotoImporter.importPhoto(context, uri, doc) }
            if (bg != null) {
                doc.backgrounds.add(bg)
                doc.canvasHeightPx = maxOf(doc.canvasHeightPx, bg.yOffsetPx + bg.heightPx + 200)
                controller.canvasHeightPx = doc.canvasHeightPx
                withContext(Dispatchers.IO) { NotebookStore.saveDocument(context, doc) }
                reloadBackgroundLayers(doc)
            }
        }
    }

    val docScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val doc = document ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            if (scanResult != null) {
                scope.launch {
                    syncDocumentFromCanvas()
                    val newBackgrounds = withContext(Dispatchers.IO) {
                        DocumentScanImporter.importResult(context, scanResult, doc)
                    }
                    if (newBackgrounds.isNotEmpty()) {
                        doc.backgrounds.addAll(newBackgrounds)
                        val bottom = newBackgrounds.maxOf { it.yOffsetPx + it.heightPx } + 200
                        doc.canvasHeightPx = maxOf(doc.canvasHeightPx, bottom)
                        controller.canvasHeightPx = doc.canvasHeightPx
                        withContext(Dispatchers.IO) { NotebookStore.saveDocument(context, doc) }
                        reloadBackgroundLayers(doc)
                    }
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

    // OCR
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

    // Text / sticky-note placement + editing
    var textPlacementPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var stickyPlacementPos by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var editingTextElement by remember { mutableStateOf<TextElement?>(null) }
    var editingStickyNote by remember { mutableStateOf<StickyNoteElement?>(null) }
    var selectedColorArgb by remember { mutableStateOf(PALETTE[0]) }

    controller.onWantsTextPlacement = { x, y -> textPlacementPos = x to y }
    controller.onWantsStickyPlacement = { x, y -> stickyPlacementPos = x to y }
    controller.onTextTapped = { t -> editingTextElement = t }
    controller.onStickyTapped = { s -> editingStickyNote = s }

    var currentTool by remember { mutableStateOf(DrawTool.PEN) }
    var shapeSnapEnabled by remember { mutableStateOf(false) }
    var selectedWidthPx by remember { mutableStateOf(STROKE_WIDTHS[1]) }
    var paperStyle by remember { mutableStateOf(PaperStyle.LINED) }
    var selectedLineSpacing by remember { mutableStateOf(LineSpacing.MEDIUM) }
    var showAiMenu by remember { mutableStateOf(false) }
    var showInsertMenu by remember { mutableStateOf(false) }
    var showPaperMenu by remember { mutableStateOf(false) }
    var showLiveCastSheet by remember { mutableStateOf(false) }
    var showPresetMenu by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var presets by remember { mutableStateOf(InkPresetStore.load(context)) }

    fun applyPreset(preset: InkPreset) {
        currentTool = preset.tool
        controller.setTool(preset.tool)
        selectedColorArgb = preset.colorArgb
        controller.setColor(preset.colorArgb)
        selectedWidthPx = preset.widthPx
        controller.setWidthPx(preset.widthPx)
    }

    LaunchedEffect(document?.id) {
        document?.let {
            paperStyle = it.paperStyle
            controller.setPaperStyle(it.paperStyle)
            selectedLineSpacing = it.lineSpacing
            controller.setLineSpacing(it.lineSpacing.px)
        }
    }

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

                // Row 2: Werkzeug (Stift/Marker/Bleistift/Radierer), Strichstärke,
                // Formen-Korrektur, Papiervorlage — everything about *how* you're drawing.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolToggle(Icons.Filled.Edit, "Stift", currentTool == DrawTool.PEN) {
                        currentTool = DrawTool.PEN; controller.setTool(DrawTool.PEN)
                    }
                    ToolToggle(Icons.Filled.Brush, "Marker", currentTool == DrawTool.MARKER) {
                        currentTool = DrawTool.MARKER; controller.setTool(DrawTool.MARKER)
                    }
                    ToolToggle(Icons.Filled.ModeEditOutline, "Bleistift", currentTool == DrawTool.PENCIL) {
                        currentTool = DrawTool.PENCIL; controller.setTool(DrawTool.PENCIL)
                    }
                    ToolToggle(Icons.Filled.Backspace, "Radierer", currentTool == DrawTool.ERASER) {
                        currentTool = DrawTool.ERASER; controller.setTool(DrawTool.ERASER)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                        for (w in STROKE_WIDTHS) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(3.dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedWidthPx == w) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        selectedWidthPx = w
                                        controller.setWidthPx(w)
                                    }
                            ) {
                                Box(
                                    Modifier
                                        .size((w / 1.2f).dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface)
                                )
                            }
                        }
                    }

                    ToolToggle(Icons.Filled.AutoFixHigh, "Formen", shapeSnapEnabled) {
                        shapeSnapEnabled = !shapeSnapEnabled
                        controller.setShapeSnapEnabled(shapeSnapEnabled)
                    }

                    Box {
                        IconButton(onClick = { showPaperMenu = true }) {
                            Icon(Icons.Filled.GridOn, contentDescription = "Papiervorlage")
                        }
                        DropdownMenu(expanded = showPaperMenu, onDismissRequest = { showPaperMenu = false }) {
                            for (style in PaperStyle.entries) {
                                DropdownMenuItem(
                                    text = { Text(style.label()) },
                                    trailingIcon = {
                                        if (paperStyle == style) Icon(Icons.Filled.Check, contentDescription = null)
                                    },
                                    onClick = {
                                        showPaperMenu = false
                                        paperStyle = style
                                        controller.setPaperStyle(style)
                                        document?.paperStyle = style
                                    }
                                )
                            }
                            HorizontalDivider()
                            Text(
                                "Zeilenabstand",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                            for (spacing in LineSpacing.entries) {
                                DropdownMenuItem(
                                    text = { Text(spacing.label) },
                                    trailingIcon = {
                                        if (selectedLineSpacing == spacing) Icon(Icons.Filled.Check, contentDescription = null)
                                    },
                                    onClick = {
                                        showPaperMenu = false
                                        selectedLineSpacing = spacing
                                        controller.setLineSpacing(spacing.px)
                                        document?.lineSpacing = spacing
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        IconButton(onClick = { showPresetMenu = true }) {
                            Icon(Icons.Filled.Palette, contentDescription = "Stift-Presets")
                        }
                        DropdownMenu(expanded = showPresetMenu, onDismissRequest = { showPresetMenu = false }) {
                            for (preset in presets) {
                                DropdownMenuItem(
                                    text = { Text(preset.name) },
                                    leadingIcon = {
                                        Box(
                                            Modifier
                                                .size(18.dp)
                                                .clip(CircleShape)
                                                .background(Color(preset.colorArgb))
                                        )
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            val updated = presets.filterNot { it.id == preset.id }
                                            presets = updated
                                            InkPresetStore.save(context, updated)
                                        }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                                        }
                                    },
                                    onClick = {
                                        showPresetMenu = false
                                        applyPreset(preset)
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Aktuellen Stift speichern…") },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                onClick = {
                                    showPresetMenu = false
                                    showSavePresetDialog = true
                                }
                            )
                        }
                    }
                }

                // Row 3: colorful action pills — Einfügen/Exportieren/Text/Haftzettel/
                // Text erkennen/Live-Übertragung.
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
                        label = "Text",
                        icon = Icons.Filled.Title,
                        color = IosColors.Orange,
                        onClick = {
                            controller.startTextPlacement()
                            Toast.makeText(context, "Position zum Einfügen antippen", Toast.LENGTH_SHORT).show()
                        }
                    )
                    ActionPill(
                        label = "Haftzettel",
                        icon = Icons.Filled.StickyNote2,
                        color = IosColors.Yellow,
                        onClick = {
                            controller.startStickyPlacement()
                            Toast.makeText(context, "Position zum Einfügen antippen", Toast.LENGTH_SHORT).show()
                        }
                    )
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
            if (document != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
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
                .verticalScroll(scrollState)
        ) {
            if (!isLoading && document != null) {
                InkCanvas(controller = controller, modifier = Modifier.fillMaxWidth())
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
                }) { Text("Kopieren") }
            },
            dismissButton = {
                TextButton(onClick = { ocrResultText = null }) { Text("Schließen") }
            }
        )
    }

    textPlacementPos?.let { (x, y) ->
        var input by remember(x, y) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { textPlacementPos = null },
            title = { Text("Text einfügen") },
            text = {
                OutlinedTextField(value = input, onValueChange = { input = it }, placeholder = { Text("Text…") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (input.isNotBlank()) {
                        controller.addTextElement(TextElement(x = x, y = y, text = input, colorArgb = selectedColorArgb))
                    }
                    textPlacementPos = null
                }) { Text("Einfügen") }
            },
            dismissButton = {
                TextButton(onClick = { textPlacementPos = null }) { Text("Abbrechen") }
            }
        )
    }

    stickyPlacementPos?.let { (x, y) ->
        var input by remember(x, y) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { stickyPlacementPos = null },
            title = { Text("Haftzettel einfügen") },
            text = {
                OutlinedTextField(value = input, onValueChange = { input = it }, placeholder = { Text("Notiz…") })
            },
            confirmButton = {
                TextButton(onClick = {
                    controller.addStickyNote(
                        StickyNoteElement(x = x, y = y, text = input, colorIndex = controller.getStickyNotes().size)
                    )
                    stickyPlacementPos = null
                }) { Text("Einfügen") }
            },
            dismissButton = {
                TextButton(onClick = { stickyPlacementPos = null }) { Text("Abbrechen") }
            }
        )
    }

    editingTextElement?.let { element ->
        var input by remember(element.id) { mutableStateOf(element.text) }
        AlertDialog(
            onDismissRequest = { editingTextElement = null },
            title = { Text("Text bearbeiten") },
            text = { OutlinedTextField(value = input, onValueChange = { input = it }) },
            confirmButton = {
                TextButton(onClick = {
                    controller.updateOrRemoveTextElement(element.id, input)
                    editingTextElement = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        controller.updateOrRemoveTextElement(element.id, null)
                        editingTextElement = null
                    }) { Text("Löschen") }
                    TextButton(onClick = { editingTextElement = null }) { Text("Abbrechen") }
                }
            }
        )
    }

    editingStickyNote?.let { note ->
        var input by remember(note.id) { mutableStateOf(note.text) }
        AlertDialog(
            onDismissRequest = { editingStickyNote = null },
            title = { Text("Haftzettel bearbeiten") },
            text = { OutlinedTextField(value = input, onValueChange = { input = it }) },
            confirmButton = {
                TextButton(onClick = {
                    controller.updateOrRemoveStickyNote(note.id, input, remove = false)
                    editingStickyNote = null
                }) { Text("Speichern") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        controller.updateOrRemoveStickyNote(note.id, null, remove = true)
                        editingStickyNote = null
                    }) { Text("Löschen") }
                    TextButton(onClick = { editingStickyNote = null }) { Text("Abbrechen") }
                }
            }
        )
    }

    if (showSavePresetDialog) {
        AlertDialog(
            onDismissRequest = { showSavePresetDialog = false; newPresetName = "" },
            title = { Text("Preset speichern") },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    placeholder = { Text("Name…") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPresetName.isNotBlank()) {
                        val preset = InkPreset(
                            name = newPresetName,
                            tool = currentTool,
                            colorArgb = selectedColorArgb,
                            widthPx = selectedWidthPx
                        )
                        val updated = presets + preset
                        presets = updated
                        InkPresetStore.save(context, updated)
                    }
                    newPresetName = ""
                    showSavePresetDialog = false
                }) { Text("Speichern") }
            },
            dismissButton = {
                TextButton(onClick = { showSavePresetDialog = false; newPresetName = "" }) { Text("Abbrechen") }
            }
        )
    }
}

@Composable
private fun ToolToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(2.dp)
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun PaperStyle.label(): String = when (this) {
    PaperStyle.BLANK -> "Blanko"
    PaperStyle.GRID -> "Kariert"
    PaperStyle.LINED -> "Liniert"
    PaperStyle.DOTTED -> "Punktraster"
}

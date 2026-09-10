package de.graetz.electronote.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ElectricBolt
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.ModeEditOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.unit.sp
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.FileProvider
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import de.graetz.electronote.ai.AiProvider
import de.graetz.electronote.ai.openAiProvider
import de.graetz.electronote.canvas.Bookmark
import de.graetz.electronote.canvas.DrawTool
import de.graetz.electronote.canvas.ImageElement
import de.graetz.electronote.canvas.InkCanvas
import de.graetz.electronote.canvas.InkCanvasController
import de.graetz.electronote.canvas.InkPreset
import de.graetz.electronote.canvas.InkPresetStore
import de.graetz.electronote.canvas.LineSpacing
import de.graetz.electronote.canvas.PaperStyle
import de.graetz.electronote.canvas.StickyNoteElement
import de.graetz.electronote.canvas.TextElement
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookDocumentSummary
import de.graetz.electronote.data.NotebookStore
import de.graetz.electronote.electrical.CircuitSymbolPickerDialog
import de.graetz.electronote.electrical.ElektroSimDialog
import de.graetz.electronote.livecast.LiveCastServer
import de.graetz.electronote.livecast.LiveCastSheet
import de.graetz.electronote.math.MathPanelDialog
import de.graetz.electronote.media.DocumentScanImporter
import de.graetz.electronote.media.LocalVideoPlaybackDialog
import de.graetz.electronote.media.PhotoImporter
import de.graetz.electronote.media.YoutubePlaybackDialog
import de.graetz.electronote.media.YoutubeUtil
import de.graetz.electronote.ocr.HandwritingRecognizer
import de.graetz.electronote.pdf.PdfExporter
import de.graetz.electronote.pdf.PdfImporter
import de.graetz.electronote.ui.theme.ActionPill
import de.graetz.electronote.ui.theme.IosColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date

// Matches the iPad app's row-2 color set (white included for writing on dark paper).
private val PALETTE = listOf(
    AndroidColor.BLACK,
    AndroidColor.WHITE,
    AndroidColor.parseColor("#1E88E5"),
    AndroidColor.parseColor("#E53935"),
    AndroidColor.parseColor("#2E7D32"),
    AndroidColor.parseColor("#FFC107"),
    AndroidColor.parseColor("#F57C00"),
    AndroidColor.parseColor("#8E24AA"),
)

private val STROKE_WIDTHS = listOf(2.5f, 4.5f, 7f, 11f)

private data class PendingImageInsert(
    val bitmap: Bitmap,
    val kind: String,
    val videoFilename: String? = null,
    val youtubeUrl: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotebookScreen(documentId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { InkCanvasController() }
    val scrollState = rememberScrollState()

    var document by remember { mutableStateOf<NotebookDocument?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var bookmarks by remember { mutableStateOf<List<Bookmark>>(emptyList()) }
    var showBookmarksMenu by remember { mutableStateOf(false) }
    var showAddBookmarkDialog by remember { mutableStateOf(false) }

    // Master-detail sidebar (matches the iPad app's collapsible "Alle Elemente" panel):
    // switching documents happens in place by changing this instead of leaving the screen.
    var activeDocumentId by remember { mutableStateOf(documentId) }
    var showSidebar by remember { mutableStateOf(false) }
    var sidebarDocuments by remember { mutableStateOf<List<NotebookDocumentSummary>>(emptyList()) }

    fun refreshSidebar() {
        scope.launch { sidebarDocuments = withContext(Dispatchers.IO) { NotebookStore.listDocuments(context) } }
    }

    suspend fun reloadBackgroundLayers(doc: NotebookDocument) {
        val layers = withContext(Dispatchers.IO) {
            doc.backgrounds.map { bg -> bg to NotebookStore.loadBackgroundImage(context, doc.id, bg.imageFile) }
        }
        controller.setBackgroundLayers(layers)
    }

    suspend fun reloadImageElements(doc: NotebookDocument) {
        val elements = withContext(Dispatchers.IO) {
            doc.imageElements.map { img -> img to NotebookStore.loadBackgroundImage(context, doc.id, img.filename) }
        }
        controller.setImageElements(elements)
    }

    fun syncDocumentFromCanvas() {
        val doc = document ?: return
        doc.strokes = controller.getStrokes().toMutableList()
        doc.textElements = controller.getTextElements().toMutableList()
        doc.stickyNotes = controller.getStickyNotes().toMutableList()
        doc.imageElements = controller.getImageElements().toMutableList()
        doc.canvasHeightPx = controller.canvasHeightPx
        if (controller.canvasWidthPx > 0) doc.canvasWidthPx = controller.canvasWidthPx
    }

    fun saveDocument() {
        val doc = document ?: return
        syncDocumentFromCanvas()
        scope.launch(Dispatchers.IO) { NotebookStore.saveDocument(context, doc) }
    }

    fun switchToDocument(id: String) {
        if (id == activeDocumentId) {
            showSidebar = false
            return
        }
        saveDocument()
        activeDocumentId = id
        showSidebar = false
    }

    fun createNewDocumentFromSidebar() {
        val newDoc = NotebookStore.createDocument(context, "Notizbuch ${sidebarDocuments.size + 1}")
        switchToDocument(newDoc.id)
    }

    // Cross-notebook search index: typed text/tags/bookmarks plus an OCR pass over the
    // handwriting, run once when leaving the notebook (not on every keystroke-save) to
    // keep this from slowing down normal editing. Very tall notebooks are only indexed
    // up to their first ~12000px — a generous multi-page span — to bound OCR memory use.
    suspend fun buildSearchText(doc: NotebookDocument): String {
        val parts = mutableListOf<String>()
        parts.add(doc.name)
        parts.addAll(doc.tags)
        parts.addAll(doc.textElements.map { it.text })
        parts.addAll(doc.stickyNotes.map { it.text })
        parts.addAll(doc.bookmarks.map { it.name })

        val ocrHeight = minOf(doc.canvasHeightPx, 12000)
        val widthPx = if (controller.canvasWidthPx > 0) controller.canvasWidthPx.toFloat() else 1600f
        val bitmap = withContext(Dispatchers.Main) {
            controller.captureRegion(RectF(0f, 0f, widthPx, ocrHeight.toFloat()))
        }
        if (bitmap != null) {
            try {
                val ocrText = HandwritingRecognizer.recognize(bitmap)
                if (ocrText.isNotBlank()) parts.add(ocrText)
            } catch (e: Exception) {
                // No recognizable handwriting — the typed/tag text above still gets indexed.
            } finally {
                bitmap.recycle()
            }
        }
        return parts.filter { it.isNotBlank() }.joinToString(" ")
    }

    fun saveAndIndexThenBack() {
        val doc = document
        if (doc == null) {
            onBack()
            return
        }
        syncDocumentFromCanvas()
        scope.launch {
            withContext(Dispatchers.IO) { NotebookStore.saveDocument(context, doc) }
            doc.searchText = buildSearchText(doc)
            withContext(Dispatchers.IO) { NotebookStore.saveDocument(context, doc) }
            onBack()
        }
    }

    // Shared insertion path for math plots, circuit symbols, and video/YouTube
    // thumbnails: saves the bitmap under the document folder and places it as an
    // ImageElement at (x, y), sized to fit within a reasonable on-canvas box while
    // keeping the bitmap's aspect ratio.
    fun insertImageElement(
        bitmap: Bitmap,
        x: Float,
        y: Float,
        kind: String,
        videoFilename: String? = null,
        youtubeUrl: String? = null,
        maxWidthPx: Float = 500f
    ) {
        val doc = document ?: return
        val element = ImageElement(
            x = x,
            y = y,
            widthPx = 0f,
            heightPx = 0f,
            filename = "",
            kind = kind,
            videoFilename = videoFilename,
            youtubeUrl = youtubeUrl
        )
        val scale = if (bitmap.width > maxWidthPx) maxWidthPx / bitmap.width else 1f
        element.widthPx = bitmap.width * scale
        element.heightPx = bitmap.height * scale
        scope.launch {
            val filename = withContext(Dispatchers.IO) {
                NotebookStore.saveBackgroundImage(context, doc.id, element.id, bitmap)
            }
            element.filename = filename
            doc.imageElements.add(element)
            controller.addImageElement(element, bitmap)
            saveDocument()
        }
    }

    LaunchedEffect(activeDocumentId) {
        isLoading = true
        val loaded = withContext(Dispatchers.IO) { NotebookStore.loadDocument(context, activeDocumentId) }
        document = loaded
        if (loaded != null) {
            controller.canvasHeightPx = loaded.canvasHeightPx
            controller.setStrokes(loaded.strokes)
            controller.setTextElements(loaded.textElements)
            controller.setStickyNotes(loaded.stickyNotes)
            bookmarks = loaded.bookmarks
            reloadBackgroundLayers(loaded)
            reloadImageElements(loaded)
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

    // Nextcloud: explicit, manual upload of the current document — no background sync.
    // Requires having connected once from the notebook list screen.
    fun uploadToNextcloud() {
        val doc = document ?: return
        val credentials = de.graetz.electronote.nextcloud.NextcloudAuthStore.load(context)
        if (credentials == null) {
            Toast.makeText(context, "Bitte zuerst in der Notizbuch-Liste mit Nextcloud verbinden", Toast.LENGTH_LONG).show()
            return
        }
        syncDocumentFromCanvas()
        scope.launch {
            val success = withContext(Dispatchers.IO) {
                de.graetz.electronote.nextcloud.NextcloudSync.upload(context, credentials, doc)
            }
            Toast.makeText(
                context,
                if (success) "Auf Nextcloud gespeichert" else "Hochladen fehlgeschlagen",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Teilen: renders the current document to a PDF in the app's cache dir and hands it
    // to the native Android share sheet (Mail/WhatsApp/Nearby Share/…), unlike the
    // "Exportieren" pill which saves to a location the user picks (Drive, Nextcloud, …).
    fun shareAsPdf() {
        val doc = document ?: return
        syncDocumentFromCanvas()
        scope.launch(Dispatchers.IO) {
            val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(sharedDir, "${doc.name}.pdf")
            FileOutputStream(file).use { out -> PdfExporter.export(context, doc, out) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            withContext(Dispatchers.Main) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Notizbuch teilen"))
            }
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

    // Elektro: Bauteil-Bibliothek (Schaltplan-Pill) + eingebetteter Schaltungs-Simulator ("..."-Menü).
    var showCircuitPicker by remember { mutableStateOf(false) }
    var showElektroSim by remember { mutableStateOf(false) }

    // Mathe-Modul: Taschenrechner + Handschrift-Formel-Erkennung + Funktionsplotter.
    var showMathDialog by remember { mutableStateOf(false) }
    var mathExpression by remember { mutableStateOf("") }

    fun recognizeHandwritingForMath() {
        showMathDialog = false
        controller.startSelection(
            onMade = { rect ->
                val bitmap = controller.captureRegion(rect)
                controller.stopSelection()
                if (bitmap == null) {
                    Toast.makeText(context, "Konnte Bereich nicht erfassen", Toast.LENGTH_SHORT).show()
                    showMathDialog = true
                } else {
                    scope.launch {
                        val text = try {
                            withContext(Dispatchers.Default) { HandwritingRecognizer.recognize(bitmap) }
                        } catch (e: Exception) {
                            ""
                        } finally {
                            bitmap.recycle()
                        }
                        if (text.isNotBlank()) mathExpression = text
                        showMathDialog = true
                    }
                }
            },
            onCancelled = {
                Toast.makeText(context, "Auswahl zu klein — bitte großzügiger umkreisen", Toast.LENGTH_SHORT).show()
                showMathDialog = true
            }
        )
        Toast.makeText(context, "Formel mit Finger/Stift umkreisen", Toast.LENGTH_SHORT).show()
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

    var pendingImageInsert by remember { mutableStateOf<PendingImageInsert?>(null) }
    var tappedImageElement by remember { mutableStateOf<ImageElement?>(null) }
    var playingVideoElement by remember { mutableStateOf<ImageElement?>(null) }
    controller.onImageTapped = { img -> tappedImageElement = img }
    controller.onWantsImagePlacement = { x, y ->
        pendingImageInsert?.let { pending ->
            insertImageElement(pending.bitmap, x, y, pending.kind, pending.videoFilename, pending.youtubeUrl)
            pendingImageInsert = null
        }
    }

    fun beginImagePlacement(bitmap: Bitmap, kind: String, videoFilename: String? = null, youtubeUrl: String? = null) {
        pendingImageInsert = PendingImageInsert(bitmap, kind, videoFilename, youtubeUrl)
        controller.startImagePlacement()
        Toast.makeText(context, "Position zum Einfügen antippen", Toast.LENGTH_SHORT).show()
    }

    // Video: Kamera-Aufnahme, Galerie-Import, YouTube-Einbettung — alle enden als
    // ImageElement (Video-Thumbnail) an einer angetippten Stelle im Notizbuch.
    var showYoutubeDialog by remember { mutableStateOf(false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun handleImportedVideo(sourceUri: Uri) {
        val doc = document ?: return
        scope.launch {
            val relFilename = withContext(Dispatchers.IO) { NotebookStore.saveVideoFile(context, doc.id, sourceUri) }
            if (relFilename == null) {
                Toast.makeText(context, "Video konnte nicht gespeichert werden", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val thumb = withContext(Dispatchers.IO) {
                extractVideoThumbnail(NotebookStore.videoFile(context, doc.id, relFilename))
            }
            if (thumb != null) {
                beginImagePlacement(thumb, ImageElement.KIND_VIDEO, videoFilename = relFilename)
            } else {
                Toast.makeText(context, "Konnte Video-Vorschau nicht erzeugen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val videoCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) handleImportedVideo(uri)
    }

    fun startVideoCapture() {
        val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(camDir, "${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri
        videoCaptureLauncher.launch(uri)
    }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) handleImportedVideo(uri)
    }

    fun addYoutubeVideo(url: String) {
        val id = YoutubeUtil.extractVideoId(url)
        if (id == null) {
            Toast.makeText(context, "Ungültiger YouTube-Link", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { YoutubeUtil.fetchThumbnailBytes(id) }
            val bitmap = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
            if (bitmap != null) {
                beginImagePlacement(bitmap, ImageElement.KIND_YOUTUBE, youtubeUrl = "https://www.youtube.com/watch?v=$id")
            } else {
                Toast.makeText(context, "Konnte Vorschaubild nicht laden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Kamera: photographs get appended as a full-width page background, same as a
    // gallery-picked photo (PhotoImporter) — only the source Uri differs.
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val photoCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingPhotoUri
        pendingPhotoUri = null
        val doc = document
        if (success && uri != null && doc != null) {
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
    }

    fun startPhotoCapture() {
        val camDir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(camDir, "${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhotoUri = uri
        photoCaptureLauncher.launch(uri)
    }

    // WebView: opens an arbitrary URL via Chrome Custom Tabs — same Google-login-safe
    // mechanism already used for the AI providers, just for any address the user types.
    var showWebViewDialog by remember { mutableStateOf(false) }
    fun openWebViewUrl(url: String) {
        val normalized = if (!url.startsWith("http://") && !url.startsWith("https://")) "https://$url" else url
        val intent = CustomTabsIntent.Builder().build()
        intent.launchUrl(context, Uri.parse(normalized))
    }

    var currentTool by remember { mutableStateOf(DrawTool.PEN) }
    // Mirrors Apple Pencil's double-tap-to-switch-tool: Android has no single gesture
    // that works across all stylus vendors, so the stylus barrel button is used instead.
    controller.onToolChangeRequested = { tool -> currentTool = tool }
    var shapeSnapEnabled by remember { mutableStateOf(false) }
    var selectedWidthPx by remember { mutableStateOf(STROKE_WIDTHS[1]) }
    var paperStyle by remember { mutableStateOf(PaperStyle.LINED) }
    var selectedLineSpacing by remember { mutableStateOf(LineSpacing.MEDIUM) }
    var showAiMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showInsertMenu by remember { mutableStateOf(false) }
    var showPaperDialog by remember { mutableStateOf(false) }
    var showLiveCastSheet by remember { mutableStateOf(false) }
    var showPresetDialog by remember { mutableStateOf(false) }
    var showSavePresetDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }
    var presets by remember { mutableStateOf<List<InkPreset>>(InkPresetStore.load(context)) }

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
                        Row {
                            IconButton(onClick = {
                                showSidebar = !showSidebar
                                if (showSidebar) refreshSidebar()
                            }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "Notizbücher")
                            }
                            IconButton(onClick = { saveAndIndexThenBack() }) {
                                Icon(Icons.Outlined.ArrowBack, contentDescription = "Zurück")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { controller.undo() }, enabled = controller.hasUndo) {
                            Icon(Icons.Outlined.Undo, contentDescription = "Rückgängig")
                        }
                        IconButton(onClick = { controller.redo() }, enabled = controller.hasRedo) {
                            Icon(Icons.Outlined.Redo, contentDescription = "Wiederholen")
                        }
                        IconButton(onClick = { AppPreferences.toggleDarkMode(context) }) {
                            Icon(
                                if (AppPreferences.isDarkMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                                contentDescription = "Dunkelmodus umschalten"
                            )
                        }
                        IconButton(onClick = { saveDocument() }) {
                            Icon(Icons.Outlined.Save, contentDescription = "Speichern")
                        }
                        IconButton(onClick = { showLiveCastSheet = true }) {
                            Icon(
                                Icons.Outlined.Wifi,
                                contentDescription = "Live-Übertragung",
                                tint = if (LiveCastServer.isStreaming) IosColors.Red else LocalContentColor.current
                            )
                        }
                        Box {
                            IconButton(onClick = { showAiMenu = true }) {
                                Icon(Icons.Outlined.SmartToy, contentDescription = "KI-Assistent")
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
                        Box {
                            ActionPill(
                                label = "Einfügen",
                                icon = Icons.Outlined.Add,
                                color = IosColors.Blue,
                                onClick = { showInsertMenu = true }
                            )
                            DropdownMenu(expanded = showInsertMenu, onDismissRequest = { showInsertMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Text einfügen") },
                                    onClick = {
                                        showInsertMenu = false
                                        controller.startTextPlacement()
                                        Toast.makeText(context, "Position zum Einfügen antippen", Toast.LENGTH_SHORT).show()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Haftzettel einfügen") },
                                    onClick = {
                                        showInsertMenu = false
                                        controller.startStickyPlacement()
                                        Toast.makeText(context, "Position zum Einfügen antippen", Toast.LENGTH_SHORT).show()
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
                            }
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = "Mehr")
                            }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Video aus Galerie") },
                                    onClick = {
                                        showMoreMenu = false
                                        videoPicker.launch(
                                            androidx.activity.result.PickVisualMediaRequest(
                                                ActivityResultContracts.PickVisualMedia.VideoOnly
                                            )
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Video mit Kamera aufnehmen") },
                                    onClick = { showMoreMenu = false; startVideoCapture() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Handschrift erkennen") },
                                    onClick = { showMoreMenu = false; startOcr() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Elektro-Simulator öffnen") },
                                    onClick = { showMoreMenu = false; showElektroSim = true }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Exportieren…") },
                                    onClick = {
                                        showMoreMenu = false
                                        val name = (document?.name ?: "Notizbuch") + ".pdf"
                                        pdfExportLauncher.launch(name)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Teilen…") },
                                    onClick = { showMoreMenu = false; shareAsPdf() }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Papierstil & Zeilenabstand") },
                                    onClick = { showMoreMenu = false; showPaperDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Stift-Presets") },
                                    onClick = { showMoreMenu = false; showPresetDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Lesezeichen") },
                                    onClick = { showMoreMenu = false; showBookmarksMenu = true }
                                )
                            }
                        }
                    }
                )

                // Row 2: Werkzeuge (Stift/Marker/Bleistift/Radierer), Farben, Strichstärke —
                // matches the iPad app's horizontal tool row (no left sidebar).
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolToggle(Icons.Outlined.Edit, "Stift", currentTool == DrawTool.PEN) {
                        currentTool = DrawTool.PEN; controller.setTool(DrawTool.PEN)
                    }
                    ToolToggle(Icons.Outlined.Brush, "Marker", currentTool == DrawTool.MARKER) {
                        currentTool = DrawTool.MARKER; controller.setTool(DrawTool.MARKER)
                    }
                    ToolToggle(Icons.Outlined.ModeEditOutline, "Bleistift", currentTool == DrawTool.PENCIL) {
                        currentTool = DrawTool.PENCIL; controller.setTool(DrawTool.PENCIL)
                    }
                    ToolToggle(Icons.Outlined.Backspace, "Radierer", currentTool == DrawTool.ERASER) {
                        currentTool = DrawTool.ERASER; controller.setTool(DrawTool.ERASER)
                    }

                    VerticalDivider(modifier = Modifier.padding(horizontal = 6.dp).height(28.dp))

                    for (c in PALETTE) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(3.dp)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable {
                                    selectedColorArgb = c
                                    controller.setColor(c)
                                }
                        ) {
                            if (selectedColorArgb == c) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = if (c == AndroidColor.WHITE) Color.Black else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    VerticalDivider(modifier = Modifier.padding(horizontal = 6.dp).height(28.dp))

                    for (w in STROKE_WIDTHS) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(3.dp)
                                .size(30.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selectedWidthPx == w) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
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

                // Row 3: colorful pills, matching the iPad app's set exactly —
                // Formen, Mathe, Schaltplan, Dateien, Nextcloud, Kamera, Scannen, YouTube, WebView.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ActionPill(
                        label = if (shapeSnapEnabled) "Formen ✓" else "Formen",
                        icon = Icons.Outlined.AutoFixHigh,
                        color = Color(0xFFE5E5EA),
                        contentColor = Color.Black,
                        onClick = {
                            shapeSnapEnabled = !shapeSnapEnabled
                            controller.setShapeSnapEnabled(shapeSnapEnabled)
                        }
                    )
                    ActionPill(
                        label = "Mathe",
                        icon = Icons.Outlined.Functions,
                        color = IosColors.Purple,
                        onClick = { showMathDialog = true }
                    )
                    ActionPill(
                        label = "Schaltplan",
                        icon = Icons.Outlined.ElectricBolt,
                        color = IosColors.Yellow,
                        contentColor = Color.Black,
                        onClick = { showCircuitPicker = true }
                    )
                    ActionPill(
                        label = "Dateien",
                        icon = Icons.Outlined.Folder,
                        color = IosColors.Teal,
                        onClick = { pdfPicker.launch(arrayOf("application/pdf")) }
                    )
                    ActionPill(
                        label = "Nextcloud",
                        icon = Icons.Outlined.CloudUpload,
                        color = IosColors.Cyan,
                        onClick = { uploadToNextcloud() }
                    )
                    ActionPill(
                        label = "Kamera",
                        icon = Icons.Outlined.PhotoCamera,
                        color = IosColors.Red,
                        onClick = { startPhotoCapture() }
                    )
                    ActionPill(
                        label = "Scannen",
                        icon = Icons.Outlined.DocumentScanner,
                        color = IosColors.Indigo,
                        onClick = { startDocumentScan() }
                    )
                    ActionPill(
                        label = "YouTube",
                        icon = Icons.Outlined.SmartDisplay,
                        color = IosColors.Red,
                        onClick = { showYoutubeDialog = true }
                    )
                    ActionPill(
                        label = "WebView",
                        icon = Icons.Outlined.Public,
                        color = IosColors.Mint,
                        onClick = { showWebViewDialog = true }
                    )
                }
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showSidebar) {
                Column(
                    modifier = Modifier
                        .width(300.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Alle Elemente", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { createNewDocumentFromSidebar() }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Neues Notizbuch")
                        }
                    }
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(sidebarDocuments, key = { it.id }) { doc ->
                            val isActive = doc.id == activeDocumentId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color.Transparent
                                    )
                                    .clickable { switchToDocument(doc.id) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Description,
                                    contentDescription = null,
                                    tint = IosColors.Orange,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.padding(start = 10.dp)) {
                                    Text(doc.name, maxLines = 1)
                                    Text(
                                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                            .format(Date(doc.updatedAt)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                VerticalDivider()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
            ) {
                if (!isLoading && document != null) {
                    InkCanvas(controller = controller, modifier = Modifier.fillMaxWidth())
                }
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

    if (showAddBookmarkDialog) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddBookmarkDialog = false },
            title = { Text("Lesezeichen setzen") },
            text = {
                OutlinedTextField(value = input, onValueChange = { input = it }, placeholder = { Text("Name…") })
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = input.ifBlank { "Lesezeichen ${bookmarks.size + 1}" }
                    val bm = Bookmark(name = name, yOffsetPx = scrollState.value)
                    val updated = bookmarks + bm
                    bookmarks = updated
                    document?.bookmarks = updated.toMutableList()
                    saveDocument()
                    showAddBookmarkDialog = false
                }) { Text("Setzen") }
            },
            dismissButton = {
                TextButton(onClick = { showAddBookmarkDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    if (showYoutubeDialog) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showYoutubeDialog = false },
            title = { Text("YouTube-Video einfügen") },
            text = {
                OutlinedTextField(value = input, onValueChange = { input = it }, placeholder = { Text("YouTube-Link…") })
            },
            confirmButton = {
                TextButton(onClick = {
                    showYoutubeDialog = false
                    addYoutubeVideo(input)
                }) { Text("Einfügen") }
            },
            dismissButton = {
                TextButton(onClick = { showYoutubeDialog = false }) { Text("Abbrechen") }
            }
        )
    }

    playingVideoElement?.let { img ->
        val doc = document
        val videoFilename = img.videoFilename
        val youtubeUrl = img.youtubeUrl
        if (img.kind == ImageElement.KIND_VIDEO && doc != null && videoFilename != null) {
            LocalVideoPlaybackDialog(
                file = NotebookStore.videoFile(context, doc.id, videoFilename),
                onDismiss = { playingVideoElement = null }
            )
        } else if (img.kind == ImageElement.KIND_YOUTUBE && youtubeUrl != null) {
            val id = YoutubeUtil.extractVideoId(youtubeUrl)
            if (id != null) {
                YoutubePlaybackDialog(videoId = id, onDismiss = { playingVideoElement = null })
            } else {
                playingVideoElement = null
            }
        }
    }

    if (showCircuitPicker) {
        CircuitSymbolPickerDialog(
            onDismiss = { showCircuitPicker = false },
            onPick = { bitmap ->
                beginImagePlacement(bitmap, ImageElement.KIND_CIRCUIT_SYMBOL)
            }
        )
    }

    if (showElektroSim) {
        ElektroSimDialog(
            onDismiss = { showElektroSim = false },
            onInsertImage = { bitmap ->
                beginImagePlacement(bitmap, ImageElement.KIND_IMAGE)
            }
        )
    }

    if (showMathDialog) {
        MathPanelDialog(
            expression = mathExpression,
            onExpressionChange = { mathExpression = it },
            onDismiss = { showMathDialog = false },
            onRecognizeHandwriting = { recognizeHandwritingForMath() },
            onInsertPlot = { bitmap -> beginImagePlacement(bitmap, ImageElement.KIND_MATH_PLOT) }
        )
    }

    tappedImageElement?.let { img ->
        AlertDialog(
            onDismissRequest = { tappedImageElement = null },
            title = {
                Text(
                    when (img.kind) {
                        ImageElement.KIND_VIDEO -> "Video"
                        ImageElement.KIND_YOUTUBE -> "YouTube-Video"
                        ImageElement.KIND_CIRCUIT_SYMBOL -> "Schaltzeichen"
                        ImageElement.KIND_MATH_PLOT -> "Funktionsgraph"
                        else -> "Bild"
                    }
                )
            },
            text = { Text("Was möchtest du tun?") },
            confirmButton = {
                if (img.kind == ImageElement.KIND_VIDEO || img.kind == ImageElement.KIND_YOUTUBE) {
                    TextButton(onClick = {
                        playingVideoElement = img
                        tappedImageElement = null
                    }) { Text("Abspielen") }
                } else {
                    TextButton(onClick = { tappedImageElement = null }) { Text("Schließen") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    document?.imageElements?.removeAll { it.id == img.id }
                    controller.removeImageElement(img.id)
                    saveDocument()
                    tappedImageElement = null
                }) { Text("Löschen") }
            }
        )
    }

    if (showPaperDialog) {
        AlertDialog(
            onDismissRequest = { showPaperDialog = false },
            title = { Text("Papierstil & Zeilenabstand") },
            text = {
                Column {
                    for (style in PaperStyle.entries) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    paperStyle = style
                                    controller.setPaperStyle(style)
                                    document?.paperStyle = style
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(style.label())
                            if (paperStyle == style) Icon(Icons.Outlined.Check, contentDescription = null)
                        }
                    }
                    HorizontalDivider()
                    Text(
                        "Zeilenabstand",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    for (spacing in LineSpacing.entries) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedLineSpacing = spacing
                                    controller.setLineSpacing(spacing.px)
                                    document?.lineSpacing = spacing
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(spacing.label)
                            if (selectedLineSpacing == spacing) Icon(Icons.Outlined.Check, contentDescription = null)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPaperDialog = false }) { Text("Fertig") }
            }
        )
    }

    if (showPresetDialog) {
        AlertDialog(
            onDismissRequest = { showPresetDialog = false },
            title = { Text("Stift-Presets") },
            text = {
                Column {
                    for (preset in presets) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    applyPreset(preset)
                                    showPresetDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(Color(preset.colorArgb)))
                            Text(preset.name, modifier = Modifier.padding(start = 8.dp).weight(1f))
                            IconButton(onClick = {
                                val updated = presets.filterNot { it.id == preset.id }
                                presets = updated
                                InkPresetStore.save(context, updated)
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Löschen")
                            }
                        }
                    }
                    HorizontalDivider()
                    TextButton(onClick = {
                        showPresetDialog = false
                        showSavePresetDialog = true
                    }) { Text("Aktuellen Stift speichern…") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPresetDialog = false }) { Text("Fertig") }
            }
        )
    }

    if (showBookmarksMenu) {
        AlertDialog(
            onDismissRequest = { showBookmarksMenu = false },
            title = { Text("Lesezeichen") },
            text = {
                Column {
                    if (bookmarks.isEmpty()) {
                        Text("Keine Lesezeichen")
                    }
                    for (bm in bookmarks) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showBookmarksMenu = false
                                    scope.launch { scrollState.animateScrollTo(bm.yOffsetPx) }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(bm.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = {
                                val updated = bookmarks.filterNot { it.id == bm.id }
                                bookmarks = updated
                                document?.bookmarks = updated.toMutableList()
                                saveDocument()
                            }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Löschen")
                            }
                        }
                    }
                    HorizontalDivider()
                    TextButton(onClick = {
                        showBookmarksMenu = false
                        showAddBookmarkDialog = true
                    }) { Text("Hier Lesezeichen setzen…") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarksMenu = false }) { Text("Schließen") }
            }
        )
    }

    if (showWebViewDialog) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showWebViewDialog = false },
            title = { Text("Webseite öffnen") },
            text = {
                OutlinedTextField(value = input, onValueChange = { input = it }, placeholder = { Text("URL…") })
            },
            confirmButton = {
                TextButton(onClick = {
                    showWebViewDialog = false
                    if (input.isNotBlank()) openWebViewUrl(input)
                }) { Text("Öffnen") }
            },
            dismissButton = {
                TextButton(onClick = { showWebViewDialog = false }) { Text("Abbrechen") }
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

// A single tool-icon toggle in the horizontal tool row, matching the iPad app's row of
// pen/marker/pencil/eraser icons.
@Composable
private fun ToolToggle(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(2.dp)
            .size(38.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            )
            .clickable(onClick = onClick)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}

// MediaMetadataRetriever only implements Closeable since API 29; minSdk here is 26, so
// release() is called explicitly instead of relying on `use {}`.
private fun extractVideoThumbnail(file: File): Bitmap? {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        retriever.getFrameAtTime(0)
    } catch (e: Exception) {
        null
    } finally {
        retriever.release()
    }
}

private fun PaperStyle.label(): String = when (this) {
    PaperStyle.BLANK -> "Blanko"
    PaperStyle.GRID -> "Kariert"
    PaperStyle.LINED -> "Liniert"
    PaperStyle.DOTTED -> "Punktraster"
    PaperStyle.CORNELL -> "Cornell"
}

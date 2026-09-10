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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.ModeEditOutline
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.unit.sp
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

private val PALETTE = listOf(
    AndroidColor.BLACK,
    AndroidColor.parseColor("#1E88E5"),
    AndroidColor.parseColor("#E53935"),
    AndroidColor.parseColor("#2E7D32"),
    AndroidColor.parseColor("#F57C00"),
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

    LaunchedEffect(documentId) {
        val loaded = withContext(Dispatchers.IO) { NotebookStore.loadDocument(context, documentId) }
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

    // Elektro: Bauteil-Bibliothek + eingebetteter Schaltungs-Simulator.
    var showElektroChooser by remember { mutableStateOf(false) }
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
    var showVideoChooser by remember { mutableStateOf(false) }
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

    var currentTool by remember { mutableStateOf(DrawTool.PEN) }
    // Mirrors Apple Pencil's double-tap-to-switch-tool: Android has no single gesture
    // that works across all stylus vendors, so the stylus barrel button is used instead.
    controller.onToolChangeRequested = { tool -> currentTool = tool }
    var shapeSnapEnabled by remember { mutableStateOf(false) }
    var selectedWidthPx by remember { mutableStateOf(STROKE_WIDTHS[1]) }
    var paperStyle by remember { mutableStateOf(PaperStyle.LINED) }
    var selectedLineSpacing by remember { mutableStateOf(LineSpacing.MEDIUM) }
    var showAiMenu by remember { mutableStateOf(false) }
    var showInsertMenu by remember { mutableStateOf(false) }
    var showPaperMenu by remember { mutableStateOf(false) }
    var showColorMenu by remember { mutableStateOf(false) }
    var showWidthMenu by remember { mutableStateOf(false) }
    var showLiveCastSheet by remember { mutableStateOf(false) }
    var showPresetMenu by remember { mutableStateOf(false) }
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
                        IconButton(onClick = { saveAndIndexThenBack() }) {
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

                // Action pills — Einfügen/Exportieren/Text/Haftzettel/
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
                            DropdownMenuItem(
                                text = { Text("Video / YouTube…") },
                                onClick = {
                                    showInsertMenu = false
                                    showVideoChooser = true
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
                        label = "Teilen",
                        icon = Icons.Filled.Share,
                        color = IosColors.Mint,
                        onClick = { shareAsPdf() }
                    )
                    ActionPill(
                        label = "Text erkennen",
                        icon = Icons.Filled.TextFields,
                        color = IosColors.Indigo,
                        onClick = { startOcr() }
                    )
                    ActionPill(
                        label = "Mathe",
                        icon = Icons.Filled.Functions,
                        color = IosColors.Purple,
                        onClick = { showMathDialog = true }
                    )
                    ActionPill(
                        label = "Elektro",
                        icon = Icons.Filled.ElectricBolt,
                        color = IosColors.Orange,
                        onClick = { showElektroChooser = true }
                    )
                    ActionPill(
                        label = if (LiveCastServer.isStreaming) "LIVE" else "Übertragen",
                        icon = Icons.Filled.Wifi,
                        color = if (LiveCastServer.isStreaming) IosColors.Red else IosColors.Pink,
                        onClick = { showLiveCastSheet = true }
                    )
                    ActionPill(
                        label = "Nextcloud",
                        icon = Icons.Filled.CloudUpload,
                        color = IosColors.Cyan,
                        onClick = { uploadToNextcloud() }
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
            if (!isLoading && document != null) {
                Column(
                    modifier = Modifier
                        .width(76.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    SidebarButton(Icons.Filled.Edit, "Stift", currentTool == DrawTool.PEN) {
                        currentTool = DrawTool.PEN; controller.setTool(DrawTool.PEN)
                    }
                    SidebarButton(Icons.Filled.Brush, "Marker", currentTool == DrawTool.MARKER) {
                        currentTool = DrawTool.MARKER; controller.setTool(DrawTool.MARKER)
                    }
                    SidebarButton(Icons.Filled.ModeEditOutline, "Bleistift", currentTool == DrawTool.PENCIL) {
                        currentTool = DrawTool.PENCIL; controller.setTool(DrawTool.PENCIL)
                    }
                    SidebarButton(Icons.Filled.Backspace, "Radierer", currentTool == DrawTool.ERASER) {
                        currentTool = DrawTool.ERASER; controller.setTool(DrawTool.ERASER)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp, horizontal = 14.dp))

                    Box {
                        SidebarButton(Icons.Filled.Palette, "Farbe", false, tint = Color(selectedColorArgb)) {
                            showColorMenu = true
                        }
                        DropdownMenu(expanded = showColorMenu, onDismissRequest = { showColorMenu = false }) {
                            for (c in PALETTE) {
                                DropdownMenuItem(
                                    text = {
                                        Box(Modifier.size(20.dp).clip(CircleShape).background(Color(c)))
                                    },
                                    trailingIcon = {
                                        if (selectedColorArgb == c) Icon(Icons.Filled.Check, contentDescription = null)
                                    },
                                    onClick = {
                                        showColorMenu = false
                                        selectedColorArgb = c
                                        controller.setColor(c)
                                    }
                                )
                            }
                        }
                    }

                    Box {
                        SidebarButton(Icons.Filled.LineWeight, "Stärke", false) {
                            showWidthMenu = true
                        }
                        DropdownMenu(expanded = showWidthMenu, onDismissRequest = { showWidthMenu = false }) {
                            for (w in STROKE_WIDTHS) {
                                DropdownMenuItem(
                                    text = { Text("${w}pt") },
                                    leadingIcon = {
                                        Box(
                                            Modifier
                                                .size((w / 1.2f).dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onSurface)
                                        )
                                    },
                                    trailingIcon = {
                                        if (selectedWidthPx == w) Icon(Icons.Filled.Check, contentDescription = null)
                                    },
                                    onClick = {
                                        showWidthMenu = false
                                        selectedWidthPx = w
                                        controller.setWidthPx(w)
                                    }
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp, horizontal = 14.dp))

                    SidebarButton(Icons.Filled.AutoFixHigh, "Formen", shapeSnapEnabled) {
                        shapeSnapEnabled = !shapeSnapEnabled
                        controller.setShapeSnapEnabled(shapeSnapEnabled)
                    }

                    Box {
                        SidebarButton(Icons.Filled.GridOn, "Papier", false) { showPaperMenu = true }
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
                        SidebarButton(Icons.Filled.Bookmarks, "Presets", false) { showPresetMenu = true }
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

                    Box {
                        SidebarButton(Icons.Filled.Bookmark, "Marken", false) { showBookmarksMenu = true }
                        DropdownMenu(expanded = showBookmarksMenu, onDismissRequest = { showBookmarksMenu = false }) {
                            if (bookmarks.isEmpty()) {
                                DropdownMenuItem(text = { Text("Keine Lesezeichen") }, onClick = {}, enabled = false)
                            }
                            for (bm in bookmarks) {
                                DropdownMenuItem(
                                    text = { Text(bm.name) },
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            val updated = bookmarks.filterNot { it.id == bm.id }
                                            bookmarks = updated
                                            document?.bookmarks = updated.toMutableList()
                                            saveDocument()
                                        }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                                        }
                                    },
                                    onClick = {
                                        showBookmarksMenu = false
                                        scope.launch { scrollState.animateScrollTo(bm.yOffsetPx) }
                                    }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Hier Lesezeichen setzen…") },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                onClick = {
                                    showBookmarksMenu = false
                                    showAddBookmarkDialog = true
                                }
                            )
                        }
                    }
                }
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

    if (showVideoChooser) {
        AlertDialog(
            onDismissRequest = { showVideoChooser = false },
            title = { Text("Video einfügen") },
            text = { Text("Woher soll das Video kommen?") },
            confirmButton = {
                TextButton(onClick = {
                    showVideoChooser = false
                    startVideoCapture()
                }) { Text("Kamera") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showVideoChooser = false
                        videoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.VideoOnly
                            )
                        )
                    }) { Text("Galerie") }
                    TextButton(onClick = {
                        showVideoChooser = false
                        showYoutubeDialog = true
                    }) { Text("YouTube") }
                }
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

    if (showElektroChooser) {
        AlertDialog(
            onDismissRequest = { showElektroChooser = false },
            title = { Text("Elektro") },
            text = { Text("Bauteil aus der Bibliothek einfügen, oder den Schaltungs-Simulator öffnen?") },
            confirmButton = {
                TextButton(onClick = {
                    showElektroChooser = false
                    showCircuitPicker = true
                }) { Text("Bauteile") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showElektroChooser = false
                    showElektroSim = true
                }) { Text("Simulator") }
            }
        )
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

// A single icon+caption entry in the left tool sidebar — the vertical, tablet-native
// counterpart to the iPad app's horizontal tool row.
@Composable
private fun SidebarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    tint: Color? = null,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(vertical = 3.dp)
            .width(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (active) MaterialTheme.colorScheme.primary else Color.Transparent)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = tint ?: if (active) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            maxLines = 1
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

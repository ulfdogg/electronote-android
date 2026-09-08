package de.graetz.electronote.media

import android.content.Context
import android.graphics.BitmapFactory
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookStore
import de.graetz.electronote.data.PageBackground

/** Converts an ML Kit document-scan result into new background layers, stacked in order. */
object DocumentScanImporter {
    private const val FALLBACK_WIDTH_PX = 1600
    private const val PAGE_GAP_PX = 24

    fun importResult(context: Context, result: GmsDocumentScanningResult, document: NotebookDocument): List<PageBackground> {
        val pages = result.pages ?: return emptyList()
        val targetWidth = if (document.canvasWidthPx > 0) document.canvasWidthPx else FALLBACK_WIDTH_PX
        var yCursor = document.canvasHeightPx
        val newBackgrounds = mutableListOf<PageBackground>()

        for (scannedPage in pages) {
            val bitmap = context.contentResolver.openInputStream(scannedPage.imageUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: continue

            val heightPx = (bitmap.height.toFloat() / bitmap.width * targetWidth).toInt().coerceAtLeast(1)
            val bg = PageBackground(yOffsetPx = yCursor, heightPx = heightPx, imageFile = "")
            val filename = NotebookStore.saveBackgroundImage(context, document.id, bg.id, bitmap)
            bg.imageFile = filename
            newBackgrounds.add(bg)

            yCursor += heightPx + PAGE_GAP_PX
            bitmap.recycle()
        }
        return newBackgrounds
    }
}

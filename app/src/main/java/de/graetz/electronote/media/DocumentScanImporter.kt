package de.graetz.electronote.media

import android.content.Context
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import de.graetz.electronote.data.NotebookPage

/** Converts an ML Kit document-scan result into new notebook page backgrounds. */
object DocumentScanImporter {
    fun importResult(context: Context, result: GmsDocumentScanningResult, documentId: String): List<NotebookPage> {
        val pages = result.pages ?: return emptyList()
        return pages.mapNotNull { scannedPage ->
            PhotoImporter.importPhoto(context, scannedPage.imageUri, documentId)
        }
    }
}

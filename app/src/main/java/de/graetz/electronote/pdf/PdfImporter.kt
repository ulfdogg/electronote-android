package de.graetz.electronote.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import de.graetz.electronote.data.NotebookPage
import de.graetz.electronote.data.NotebookStore

/**
 * Imports a PDF (picked via the system file picker / Storage Access Framework) as a set
 * of notebook pages, each with the rendered PDF page as its background image. Uses the
 * built-in [PdfRenderer] — no external PDF library needed.
 */
object PdfImporter {

    private const val TARGET_WIDTH_PX = 1600

    fun importPdf(context: Context, uri: Uri, documentId: String): List<NotebookPage> {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return emptyList()

        val newPages = mutableListOf<NotebookPage>()
        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = TARGET_WIDTH_PX.toFloat() / page.width.toFloat()
                        val width = TARGET_WIDTH_PX
                        val height = (page.height * scale).toInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val notebookPage = NotebookPage()
                        val filename = NotebookStore.savePageBackground(context, documentId, notebookPage, bitmap)
                        notebookPage.backgroundImageFile = filename
                        newPages.add(notebookPage)
                        bitmap.recycle()
                    }
                }
            }
        }
        return newPages
    }
}

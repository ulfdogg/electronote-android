package de.graetz.electronote.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookStore
import de.graetz.electronote.data.PageBackground

/**
 * Imports a PDF (picked via the system file picker / Storage Access Framework) as a stack
 * of background layers appended to the bottom of the notebook's continuous canvas. Uses
 * the built-in [PdfRenderer] — no external PDF library needed.
 */
object PdfImporter {

    private const val FALLBACK_WIDTH_PX = 1600
    private const val PAGE_GAP_PX = 24

    fun importPdf(context: Context, uri: Uri, document: NotebookDocument): List<PageBackground> {
        val pfd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return emptyList()

        val targetWidth = if (document.canvasWidthPx > 0) document.canvasWidthPx else FALLBACK_WIDTH_PX
        val newBackgrounds = mutableListOf<PageBackground>()
        var yCursor = document.canvasHeightPx

        pfd.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = targetWidth.toFloat() / page.width.toFloat()
                        val height = (page.height * scale).toInt().coerceAtLeast(1)

                        val bitmap = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        val bg = PageBackground(yOffsetPx = yCursor, heightPx = height, imageFile = "")
                        val filename = NotebookStore.saveBackgroundImage(context, document.id, bg.id, bitmap)
                        bg.imageFile = filename
                        newBackgrounds.add(bg)

                        yCursor += height + PAGE_GAP_PX
                        bitmap.recycle()
                    }
                }
            }
        }
        return newBackgrounds
    }
}

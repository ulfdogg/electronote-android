package de.graetz.electronote.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import de.graetz.electronote.canvas.drawStroke
import de.graetz.electronote.canvas.drawStickyNote
import de.graetz.electronote.canvas.drawTextElement
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookStore
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Renders the notebook's whole continuous canvas to a PDF, sliced into A4-proportioned
 * pages — the same idea as the iPad app's infinite-canvas PDF export. The exported file
 * is written wherever the user picks (ACTION_CREATE_DOCUMENT), which includes cloud
 * providers such as Google Drive or Nextcloud if their apps are installed.
 */
object PdfExporter {

    private const val PAGE_WIDTH_PT = 595
    private const val PAGE_HEIGHT_PT = 842
    private const val FALLBACK_WIDTH_PX = 1600

    fun export(context: Context, document: NotebookDocument, out: OutputStream) {
        val canvasWidth = if (document.canvasWidthPx > 0) document.canvasWidthPx else FALLBACK_WIDTH_PX
        val canvasHeight = max(document.canvasHeightPx, 1)

        val bitmapPaint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }

        // Composite the entire canvas once at native resolution, then slice it below.
        val full = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val fullCanvas = Canvas(full)
        fullCanvas.drawColor(Color.WHITE)

        for (bg in document.backgrounds) {
            NotebookStore.loadBackgroundImage(context, document.id, bg.imageFile)?.let { bmp ->
                val dst = RectF(
                    0f, bg.yOffsetPx.toFloat(),
                    canvasWidth.toFloat(), (bg.yOffsetPx + bg.heightPx).toFloat()
                )
                fullCanvas.drawBitmap(bmp, null, dst, bitmapPaint)
                bmp.recycle()
            }
        }
        for (stroke in document.strokes) {
            fullCanvas.drawStroke(stroke)
        }
        for (text in document.textElements) {
            fullCanvas.drawTextElement(text)
        }
        for (sticky in document.stickyNotes) {
            fullCanvas.drawStickyNote(sticky)
        }

        val scale = PAGE_WIDTH_PT.toFloat() / canvasWidth
        val sliceHeightPx = (PAGE_HEIGHT_PT / scale).toInt().coerceAtLeast(1)
        val pageCount = max(1, ceil(canvasHeight.toDouble() / sliceHeightPx).toInt())

        val pdf = PdfDocument()
        for (i in 0 until pageCount) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, i + 1).create()
            val pdfPage = pdf.startPage(pageInfo)

            val srcTop = i * sliceHeightPx
            val srcBottom = min(canvasHeight, srcTop + sliceHeightPx)
            val srcRect = Rect(0, srcTop, canvasWidth, srcBottom)
            val dstHeight = (srcBottom - srcTop) * scale
            val dstRect = RectF(0f, 0f, PAGE_WIDTH_PT.toFloat(), dstHeight)

            pdfPage.canvas.drawBitmap(full, srcRect, dstRect, bitmapPaint)
            pdf.finishPage(pdfPage)
        }
        full.recycle()

        pdf.writeTo(out)
        pdf.close()
    }
}

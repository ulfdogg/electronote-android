package de.graetz.electronote.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import de.graetz.electronote.canvas.drawStroke
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookStore
import java.io.OutputStream

/**
 * Renders a notebook (page background images + ink strokes) to a PDF. This is the
 * "export" side of cloud round-tripping: the caller writes the result wherever the user
 * picks via Android's document picker (ACTION_CREATE_DOCUMENT), which includes cloud
 * providers such as Google Drive or Nextcloud if their apps are installed — no bespoke
 * cloud API integration needed here.
 */
object PdfExporter {

    private const val PAGE_WIDTH_PT = 595
    private const val PAGE_HEIGHT_PT = 842
    private const val FALLBACK_WIDTH_PX = 1240
    private const val FALLBACK_HEIGHT_PX = 1754

    fun export(context: Context, document: NotebookDocument, out: OutputStream) {
        val pdf = PdfDocument()
        val bitmapPaint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
        }

        document.pages.forEachIndexed { index, page ->
            val refWidth = if (page.canvasWidthPx > 0) page.canvasWidthPx else FALLBACK_WIDTH_PX
            val refHeight = if (page.canvasHeightPx > 0) page.canvasHeightPx else FALLBACK_HEIGHT_PX

            // Composite background + ink at the page's own reference resolution first,
            // exactly like InkCanvasView does on screen (background always stretched to fit).
            val composite = Bitmap.createBitmap(refWidth, refHeight, Bitmap.Config.ARGB_8888)
            val compositeCanvas = Canvas(composite)
            compositeCanvas.drawColor(Color.WHITE)

            page.backgroundImageFile?.let { file ->
                NotebookStore.loadPageBackground(context, document.id, file)?.let { bg ->
                    compositeCanvas.drawBitmap(
                        bg, null, RectF(0f, 0f, refWidth.toFloat(), refHeight.toFloat()), bitmapPaint
                    )
                    bg.recycle()
                }
            }
            for (stroke in page.strokes) {
                compositeCanvas.drawStroke(stroke)
            }

            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH_PT, PAGE_HEIGHT_PT, index + 1).create()
            val pdfPage = pdf.startPage(pageInfo)

            val scale = minOf(PAGE_WIDTH_PT.toFloat() / refWidth, PAGE_HEIGHT_PT.toFloat() / refHeight)
            val drawWidth = refWidth * scale
            val drawHeight = refHeight * scale
            val left = (PAGE_WIDTH_PT - drawWidth) / 2f
            val top = (PAGE_HEIGHT_PT - drawHeight) / 2f
            val dst = RectF(left, top, left + drawWidth, top + drawHeight)
            pdfPage.canvas.drawBitmap(composite, null, dst, bitmapPaint)

            pdf.finishPage(pdfPage)
            composite.recycle()
        }

        pdf.writeTo(out)
        pdf.close()
    }
}

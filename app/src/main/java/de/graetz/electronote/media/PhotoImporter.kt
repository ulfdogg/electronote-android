package de.graetz.electronote.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.NotebookStore
import de.graetz.electronote.data.PageBackground

/** Imports a picked gallery photo as a new background layer, same idea as PdfImporter. */
object PhotoImporter {
    private const val FALLBACK_WIDTH_PX = 1600

    fun importPhoto(context: Context, uri: Uri, document: NotebookDocument): PageBackground? {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        val targetWidth = if (document.canvasWidthPx > 0) document.canvasWidthPx else FALLBACK_WIDTH_PX
        val heightPx = (bitmap.height.toFloat() / bitmap.width * targetWidth).toInt().coerceAtLeast(1)

        val bg = PageBackground(yOffsetPx = document.canvasHeightPx, heightPx = heightPx, imageFile = "")
        val filename = NotebookStore.saveBackgroundImage(context, document.id, bg.id, bitmap)
        bg.imageFile = filename
        bitmap.recycle()
        return bg
    }
}

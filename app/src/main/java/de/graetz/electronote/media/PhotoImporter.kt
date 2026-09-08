package de.graetz.electronote.media

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import de.graetz.electronote.data.NotebookPage
import de.graetz.electronote.data.NotebookStore

/** Imports a picked gallery photo as a new notebook page background, same idea as PdfImporter. */
object PhotoImporter {
    fun importPhoto(context: Context, uri: Uri, documentId: String): NotebookPage? {
        val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: return null

        val page = NotebookPage()
        val filename = NotebookStore.savePageBackground(context, documentId, page, bitmap)
        page.backgroundImageFile = filename
        bitmap.recycle()
        return page
    }
}

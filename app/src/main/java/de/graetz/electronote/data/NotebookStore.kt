package de.graetz.electronote.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Saves/loads [NotebookDocument]s to app-internal storage. Each document lives in its
 * own folder: files/notebooks/<id>/document.json, plus any imported page background PNGs.
 */
object NotebookStore {

    private fun rootDir(context: Context): File =
        File(context.filesDir, "notebooks").apply { mkdirs() }

    fun documentDir(context: Context, id: String): File =
        File(rootDir(context), id).apply { mkdirs() }

    private fun readSummary(dir: File): NotebookDocumentSummary? {
        val jsonFile = File(dir, "document.json")
        if (!jsonFile.exists()) return null
        return runCatching {
            val obj = JSONObject(jsonFile.readText())
            val tagsArr = obj.optJSONArray("tags")
            val tags = mutableListOf<String>()
            if (tagsArr != null) for (i in 0 until tagsArr.length()) tags.add(tagsArr.getString(i))
            NotebookDocumentSummary(
                id = obj.optString("id", dir.name),
                name = obj.optString("name", "Notizbuch"),
                updatedAt = obj.optLong("updatedAt", jsonFile.lastModified()),
                isFavorite = obj.optBoolean("isFavorite", false),
                tags = tags,
                deletedAt = if (obj.has("deletedAt") && !obj.isNull("deletedAt")) obj.getLong("deletedAt") else null,
                searchText = obj.optString("searchText", "")
            )
        }.getOrNull()
    }

    fun listDocuments(context: Context): List<NotebookDocumentSummary> {
        val root = rootDir(context)
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { readSummary(it) }
            .filter { it.deletedAt == null }
            .sortedWith(compareByDescending<NotebookDocumentSummary> { it.isFavorite }.thenByDescending { it.updatedAt })
    }

    fun listTrash(context: Context): List<NotebookDocumentSummary> {
        val root = rootDir(context)
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { readSummary(it) }
            .filter { it.deletedAt != null }
            .sortedByDescending { it.deletedAt }
    }

    fun loadDocument(context: Context, id: String): NotebookDocument? {
        val jsonFile = File(documentDir(context, id), "document.json")
        if (!jsonFile.exists()) return null
        return runCatching { NotebookDocument.fromJson(JSONObject(jsonFile.readText())) }.getOrNull()
    }

    fun saveDocument(context: Context, document: NotebookDocument) {
        document.updatedAt = System.currentTimeMillis()
        val jsonFile = File(documentDir(context, document.id), "document.json")
        jsonFile.writeText(document.toJson().toString())
    }

    fun createDocument(context: Context, name: String): NotebookDocument {
        val doc = NotebookDocument(name = name)
        saveDocument(context, doc)
        return doc
    }

    /** Permanently deletes a document — only call this from the trash view. */
    fun deleteDocument(context: Context, id: String) {
        documentDir(context, id).deleteRecursively()
    }

    fun moveToTrash(context: Context, id: String) {
        val doc = loadDocument(context, id) ?: return
        doc.deletedAt = System.currentTimeMillis()
        saveDocument(context, doc)
    }

    fun restoreFromTrash(context: Context, id: String) {
        val doc = loadDocument(context, id) ?: return
        doc.deletedAt = null
        saveDocument(context, doc)
    }

    fun emptyTrash(context: Context) {
        listTrash(context).forEach { deleteDocument(context, it.id) }
    }

    fun setFavorite(context: Context, id: String, favorite: Boolean) {
        val doc = loadDocument(context, id) ?: return
        doc.isFavorite = favorite
        saveDocument(context, doc)
    }

    fun setTags(context: Context, id: String, tags: List<String>) {
        val doc = loadDocument(context, id) ?: return
        doc.tags = tags.toMutableList()
        saveDocument(context, doc)
    }

    /** Saves a background layer bitmap (e.g. a rendered PDF page) and returns its filename. */
    fun saveBackgroundImage(context: Context, documentId: String, backgroundId: String, bitmap: Bitmap): String {
        val filename = "${backgroundId}_bg.png"
        val file = File(documentDir(context, documentId), filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return filename
    }

    fun loadBackgroundImage(context: Context, documentId: String, filename: String): Bitmap? {
        val file = File(documentDir(context, documentId), filename)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    /** Copies a picked/recorded video into the document's own videos/ folder. */
    fun saveVideoFile(context: Context, documentId: String, sourceUri: Uri, extension: String = "mp4"): String? {
        val videosDir = File(documentDir(context, documentId), "videos").apply { mkdirs() }
        val filename = "${UUID.randomUUID()}.$extension"
        val dest = File(videosDir, filename)
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: return null
            "videos/$filename"
        } catch (e: Exception) {
            null
        }
    }

    fun videoFile(context: Context, documentId: String, relativeFilename: String): File =
        File(documentDir(context, documentId), relativeFilename)
}

package de.graetz.electronote.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import de.graetz.electronote.canvas.PaperStyle
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

    /**
     * Documents saved before the tags/favorites/trash/searchText fields were moved out into
     * [DocumentMetadataStore] still have them embedded in their own document.json. The first
     * time such a document is encountered (no entry yet in the metadata store), those legacy
     * values are copied over once so nothing the user already set gets silently lost.
     */
    private fun migrateLegacyMetadataIfNeeded(context: Context, id: String, obj: JSONObject, known: Set<String>) {
        if (id in known) return
        val hasLegacyFields = obj.has("isFavorite") || obj.has("tags") || obj.has("deletedAt") || obj.has("searchText")
        if (!hasLegacyFields) return
        val tagsArr = obj.optJSONArray("tags")
        val tags = mutableListOf<String>()
        if (tagsArr != null) for (i in 0 until tagsArr.length()) tags.add(tagsArr.getString(i))
        DocumentMetadataStore.setFavorite(context, id, obj.optBoolean("isFavorite", false))
        DocumentMetadataStore.setTags(context, id, tags)
        DocumentMetadataStore.setSearchText(context, id, obj.optString("searchText", ""))
        if (obj.has("deletedAt") && !obj.isNull("deletedAt")) {
            DocumentMetadataStore.moveToTrash(context, id)
        }
    }

    private fun readSummary(context: Context, dir: File, knownMetadataIds: Set<String>): NotebookDocumentSummary? {
        val jsonFile = File(dir, "document.json")
        if (!jsonFile.exists()) return null
        return runCatching {
            val obj = JSONObject(jsonFile.readText())
            val id = obj.optString("id", dir.name)
            migrateLegacyMetadataIfNeeded(context, id, obj, knownMetadataIds)
            val meta = DocumentMetadataStore.get(context, id)
            NotebookDocumentSummary(
                id = id,
                name = obj.optString("name", "Notizbuch"),
                updatedAt = obj.optLong("updatedAt", jsonFile.lastModified()),
                isFavorite = meta.isFavorite,
                tags = meta.tags,
                deletedAt = meta.deletedAt,
                docType = obj.optString("docType", NotebookDocument.DOC_TYPE_NOTEBOOK),
                searchText = meta.searchText,
                remoteFolderName = if (obj.has("remoteFolderName") && !obj.isNull("remoteFolderName")) obj.getString("remoteFolderName") else null
            )
        }.getOrNull()
    }

    fun listDocuments(context: Context): List<NotebookDocumentSummary> {
        val root = rootDir(context)
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        val knownMetadataIds = DocumentMetadataStore.getAll(context).keys
        return dirs.mapNotNull { readSummary(context, it, knownMetadataIds) }
            .filter { it.deletedAt == null }
            .sortedWith(compareByDescending<NotebookDocumentSummary> { it.isFavorite }.thenByDescending { it.updatedAt })
    }

    fun listTrash(context: Context): List<NotebookDocumentSummary> {
        val root = rootDir(context)
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        val knownMetadataIds = DocumentMetadataStore.getAll(context).keys
        return dirs.mapNotNull { readSummary(context, it, knownMetadataIds) }
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

    fun createDocument(context: Context, name: String, docType: String = NotebookDocument.DOC_TYPE_NOTEBOOK): NotebookDocument {
        val doc = NotebookDocument(name = name, docType = docType)
        if (docType == NotebookDocument.DOC_TYPE_WHITEBOARD) {
            doc.paperStyle = PaperStyle.BLANK
            doc.canvasHeightPx = 3000
        }
        saveDocument(context, doc)
        return doc
    }

    /** Permanently deletes a document — only call this from the trash view. */
    fun deleteDocument(context: Context, id: String) {
        documentDir(context, id).deleteRecursively()
        DocumentMetadataStore.forget(context, id)
    }

    fun moveToTrash(context: Context, id: String) {
        DocumentMetadataStore.moveToTrash(context, id)
    }

    fun restoreFromTrash(context: Context, id: String) {
        DocumentMetadataStore.restoreFromTrash(context, id)
    }

    fun emptyTrash(context: Context) {
        listTrash(context).forEach { deleteDocument(context, it.id) }
    }

    fun setFavorite(context: Context, id: String, favorite: Boolean) {
        DocumentMetadataStore.setFavorite(context, id, favorite)
    }

    fun setTags(context: Context, id: String, tags: List<String>) {
        DocumentMetadataStore.setTags(context, id, tags)
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

package de.graetz.electronote.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Saves/loads [NotebookDocument]s to app-internal storage. Each document lives in its
 * own folder: files/notebooks/<id>/document.json, plus any imported page background PNGs.
 */
object NotebookStore {

    private fun rootDir(context: Context): File =
        File(context.filesDir, "notebooks").apply { mkdirs() }

    fun documentDir(context: Context, id: String): File =
        File(rootDir(context), id).apply { mkdirs() }

    fun listDocuments(context: Context): List<NotebookDocumentSummary> {
        val root = rootDir(context)
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val jsonFile = File(dir, "document.json")
            if (!jsonFile.exists()) return@mapNotNull null
            runCatching {
                val obj = JSONObject(jsonFile.readText())
                NotebookDocumentSummary(
                    id = obj.optString("id", dir.name),
                    name = obj.optString("name", "Notizbuch"),
                    updatedAt = obj.optLong("updatedAt", jsonFile.lastModified())
                )
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
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

    fun deleteDocument(context: Context, id: String) {
        documentDir(context, id).deleteRecursively()
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
}

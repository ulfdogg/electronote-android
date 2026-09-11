package de.graetz.electronote.diagram

import android.content.Context
import de.graetz.electronote.data.DocumentMetadataStore
import org.json.JSONObject
import java.io.File

object DiagramStore {
    private fun rootDir(context: Context): File =
        File(context.filesDir, "diagrams").apply { mkdirs() }

    fun documentDir(context: Context, id: String): File =
        File(rootDir(context), id).apply { mkdirs() }

    private fun readSummary(context: Context, dir: File): DiagramSummary? {
        val jsonFile = File(dir, "document.json")
        if (!jsonFile.exists()) return null
        return runCatching {
            val obj = JSONObject(jsonFile.readText())
            val id = obj.optString("id", dir.name)
            val meta = DocumentMetadataStore.get(context, id)
            DiagramSummary(
                id = id,
                name = obj.optString("name", "Diagramm"),
                type = obj.optString("type", DiagramDocument.TYPE_PAP),
                updatedAt = obj.optLong("updatedAt", jsonFile.lastModified()),
                isFavorite = meta.isFavorite,
                tags = meta.tags,
                deletedAt = meta.deletedAt
            )
        }.getOrNull()
    }

    fun listDiagrams(context: Context): List<DiagramSummary> {
        val root = rootDir(context)
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { readSummary(context, it) }
            .filter { it.deletedAt == null }
            .sortedWith(compareByDescending<DiagramSummary> { it.isFavorite }.thenByDescending { it.updatedAt })
    }

    fun listTrash(context: Context): List<DiagramSummary> {
        val root = rootDir(context)
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { readSummary(context, it) }
            .filter { it.deletedAt != null }
            .sortedByDescending { it.deletedAt }
    }

    fun loadDiagram(context: Context, id: String): DiagramDocument? {
        val jsonFile = File(documentDir(context, id), "document.json")
        if (!jsonFile.exists()) return null
        return runCatching { DiagramDocument.fromJson(JSONObject(jsonFile.readText())) }.getOrNull()
    }

    fun saveDiagram(context: Context, document: DiagramDocument) {
        document.updatedAt = System.currentTimeMillis()
        val jsonFile = File(documentDir(context, document.id), "document.json")
        jsonFile.writeText(document.toJson().toString())
    }

    fun createDiagram(context: Context, name: String, type: String): DiagramDocument {
        val doc = DiagramDocument(name = name, type = type)
        saveDiagram(context, doc)
        return doc
    }

    /** Permanently deletes a diagram — only call this from the trash view. */
    fun deleteDiagram(context: Context, id: String) {
        documentDir(context, id).deleteRecursively()
        DocumentMetadataStore.forget(context, id)
    }

    fun moveToTrash(context: Context, id: String) = DocumentMetadataStore.moveToTrash(context, id)

    fun restoreFromTrash(context: Context, id: String) = DocumentMetadataStore.restoreFromTrash(context, id)

    fun setFavorite(context: Context, id: String, favorite: Boolean) =
        DocumentMetadataStore.setFavorite(context, id, favorite)

    fun setTags(context: Context, id: String, tags: List<String>) =
        DocumentMetadataStore.setTags(context, id, tags)

    fun emptyTrash(context: Context) {
        listTrash(context).forEach { deleteDiagram(context, it.id) }
    }
}

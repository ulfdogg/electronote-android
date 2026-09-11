package de.graetz.electronote.diagram

import android.content.Context
import org.json.JSONObject
import java.io.File

object DiagramStore {
    private fun rootDir(context: Context): File =
        File(context.filesDir, "diagrams").apply { mkdirs() }

    fun documentDir(context: Context, id: String): File =
        File(rootDir(context), id).apply { mkdirs() }

    fun listDiagrams(context: Context): List<DiagramSummary> {
        val root = rootDir(context)
        val dirs = root.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val jsonFile = File(dir, "document.json")
            if (!jsonFile.exists()) return@mapNotNull null
            runCatching {
                val obj = JSONObject(jsonFile.readText())
                DiagramSummary(
                    id = obj.optString("id", dir.name),
                    name = obj.optString("name", "Diagramm"),
                    type = obj.optString("type", DiagramDocument.TYPE_PAP),
                    updatedAt = obj.optLong("updatedAt", jsonFile.lastModified())
                )
            }.getOrNull()
        }.sortedByDescending { it.updatedAt }
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

    fun deleteDiagram(context: Context, id: String) {
        documentDir(context, id).deleteRecursively()
    }
}

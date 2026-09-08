package de.graetz.electronote.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class NotebookDocument(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var pages: MutableList<NotebookPage> = mutableListOf(NotebookPage())
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("createdAt", createdAt)
        obj.put("updatedAt", updatedAt)
        val pagesArr = JSONArray()
        for (page in pages) {
            pagesArr.put(page.toJson())
        }
        obj.put("pages", pagesArr)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): NotebookDocument {
            val pagesArr = obj.optJSONArray("pages") ?: JSONArray()
            val pages = mutableListOf<NotebookPage>()
            for (i in 0 until pagesArr.length()) {
                pages.add(NotebookPage.fromJson(pagesArr.getJSONObject(i)))
            }
            if (pages.isEmpty()) pages.add(NotebookPage())
            return NotebookDocument(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Notizbuch"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                pages = pages
            )
        }
    }
}

/** Lightweight summary for listing documents without loading all stroke data. */
data class NotebookDocumentSummary(
    val id: String,
    val name: String,
    val updatedAt: Long
)

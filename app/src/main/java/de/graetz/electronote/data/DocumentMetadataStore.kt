package de.graetz.electronote.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class DocumentMetadata(
    val id: String,
    var isFavorite: Boolean = false,
    var tags: MutableList<String> = mutableListOf(),
    var deletedAt: Long? = null,
    var searchText: String = ""
) {
    val isTrashed: Boolean get() = deletedAt != null
}

/**
 * Organizational metadata (favorite/tags/trash/search-index) kept in ONE local index file,
 * deliberately separate from the notebook/diagram document content itself — mirrors how
 * iOS keeps this in its own ItemMetadataStore rather than embedded in the .enote bundle.
 * Keeping it out of the synced document means a future iOS<->Android file sync won't mix
 * per-device organizational state into the portable document content. Shared by both
 * NotebookStore and DiagramStore (keyed by document/diagram id, doesn't care which).
 */
object DocumentMetadataStore {
    private fun file(context: Context): File = File(context.filesDir, "document_metadata.json")

    @Synchronized
    private fun loadAll(context: Context): MutableMap<String, DocumentMetadata> {
        val f = file(context)
        if (!f.exists()) return mutableMapOf()
        return runCatching {
            val obj = JSONObject(f.readText())
            val result = mutableMapOf<String, DocumentMetadata>()
            for (id in obj.keys()) {
                val entry = obj.getJSONObject(id)
                val tagsArr = entry.optJSONArray("tags") ?: JSONArray()
                val tags = mutableListOf<String>()
                for (i in 0 until tagsArr.length()) tags.add(tagsArr.getString(i))
                result[id] = DocumentMetadata(
                    id = id,
                    isFavorite = entry.optBoolean("isFavorite", false),
                    tags = tags,
                    deletedAt = if (entry.has("deletedAt") && !entry.isNull("deletedAt")) entry.getLong("deletedAt") else null,
                    searchText = entry.optString("searchText", "")
                )
            }
            result
        }.getOrElse { mutableMapOf() }
    }

    @Synchronized
    private fun saveAll(context: Context, map: Map<String, DocumentMetadata>) {
        val obj = JSONObject()
        for ((id, meta) in map) {
            obj.put(
                id,
                JSONObject().apply {
                    put("isFavorite", meta.isFavorite)
                    put("tags", JSONArray(meta.tags))
                    put("deletedAt", meta.deletedAt ?: JSONObject.NULL)
                    put("searchText", meta.searchText)
                }
            )
        }
        file(context).writeText(obj.toString())
    }

    fun get(context: Context, id: String): DocumentMetadata =
        loadAll(context)[id] ?: DocumentMetadata(id)

    fun getAll(context: Context): Map<String, DocumentMetadata> = loadAll(context)

    private fun update(context: Context, id: String, mutate: (DocumentMetadata) -> Unit) {
        val all = loadAll(context)
        val meta = all[id] ?: DocumentMetadata(id)
        mutate(meta)
        all[id] = meta
        saveAll(context, all)
    }

    fun setFavorite(context: Context, id: String, favorite: Boolean) =
        update(context, id) { it.isFavorite = favorite }

    fun setTags(context: Context, id: String, tags: List<String>) =
        update(context, id) { it.tags = tags.toMutableList() }

    fun setSearchText(context: Context, id: String, text: String) =
        update(context, id) { it.searchText = text }

    fun moveToTrash(context: Context, id: String) =
        update(context, id) { it.deletedAt = System.currentTimeMillis() }

    fun restoreFromTrash(context: Context, id: String) =
        update(context, id) { it.deletedAt = null }

    /** Call when the underlying document/diagram is permanently deleted. */
    fun forget(context: Context, id: String) {
        val all = loadAll(context)
        if (all.remove(id) != null) saveAll(context, all)
    }
}

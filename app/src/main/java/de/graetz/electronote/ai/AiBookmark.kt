package de.graetz.electronote.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A saved link into one of the AI providers (e.g. a specific ongoing chat/project) —
 * mirrors iOS's AIBookmark. Since the WebView here can't reveal which URL it's currently
 * showing (same limitation as SFSafariViewController on iOS), these can only be added
 * manually by pasting a link copied from the browser's own share/copy-link action. */
data class AiBookmark(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var url: String
)

object AiBookmarkStore {
    private const val PREFS_NAME = "electronote_prefs"
    private const val KEY_BOOKMARKS = "ai_bookmarks"

    fun load(context: Context): List<AiBookmark> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_BOOKMARKS, null)
            ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                AiBookmark(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    name = obj.optString("name", ""),
                    url = obj.optString("url", "")
                )
            }
        }.getOrElse { emptyList() }
    }

    fun save(context: Context, bookmarks: List<AiBookmark>) {
        val arr = JSONArray()
        for (b in bookmarks) {
            arr.put(JSONObject().apply { put("id", b.id); put("name", b.name); put("url", b.url) })
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BOOKMARKS, arr.toString())
            .apply()
    }
}

package de.graetz.electronote.canvas

import org.json.JSONObject
import java.util.UUID

/** A named jump point at a given Y-offset within the long, scrolling notebook canvas. */
data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var yOffsetPx: Int
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("yOffsetPx", yOffsetPx)
    }

    companion object {
        fun fromJson(obj: JSONObject): Bookmark = Bookmark(
            id = obj.optString("id", UUID.randomUUID().toString()),
            name = obj.optString("name", "Lesezeichen"),
            yOffsetPx = obj.optInt("yOffsetPx", 0)
        )
    }
}

package de.graetz.electronote.data

import org.json.JSONObject
import java.util.UUID

/** One imported PDF/photo/scan page, stacked at a Y-offset within the continuous canvas. */
data class PageBackground(
    val id: String = UUID.randomUUID().toString(),
    var yOffsetPx: Int,
    var heightPx: Int,
    var imageFile: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("yOffsetPx", yOffsetPx)
        put("heightPx", heightPx)
        put("imageFile", imageFile)
    }

    companion object {
        fun fromJson(obj: JSONObject): PageBackground = PageBackground(
            id = obj.optString("id", UUID.randomUUID().toString()),
            yOffsetPx = obj.optInt("yOffsetPx", 0),
            heightPx = obj.optInt("heightPx", 0),
            imageFile = obj.optString("imageFile", "")
        )
    }
}

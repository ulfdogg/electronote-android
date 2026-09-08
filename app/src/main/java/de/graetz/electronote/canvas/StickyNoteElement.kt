package de.graetz.electronote.canvas

import org.json.JSONObject
import java.util.UUID

data class StickyNoteElement(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var text: String = "",
    var colorIndex: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("text", text)
        put("colorIndex", colorIndex)
    }

    companion object {
        fun fromJson(obj: JSONObject): StickyNoteElement = StickyNoteElement(
            id = obj.optString("id", UUID.randomUUID().toString()),
            x = obj.optDouble("x", 0.0).toFloat(),
            y = obj.optDouble("y", 0.0).toFloat(),
            text = obj.optString("text", ""),
            colorIndex = obj.optInt("colorIndex", 0)
        )
    }
}

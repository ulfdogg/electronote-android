package de.graetz.electronote.canvas

import org.json.JSONObject
import java.util.UUID

data class TextElement(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var text: String,
    var colorArgb: Int,
    var fontSizePx: Float = 42f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("text", text)
        put("color", colorArgb)
        put("fontSizePx", fontSizePx.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject): TextElement = TextElement(
            id = obj.optString("id", UUID.randomUUID().toString()),
            x = obj.optDouble("x", 0.0).toFloat(),
            y = obj.optDouble("y", 0.0).toFloat(),
            text = obj.optString("text", ""),
            colorArgb = obj.optInt("color"),
            fontSizePx = obj.optDouble("fontSizePx", 42.0).toFloat()
        )
    }
}

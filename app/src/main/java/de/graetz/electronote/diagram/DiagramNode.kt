package de.graetz.electronote.diagram

import android.graphics.Color
import org.json.JSONObject
import java.util.UUID

data class DiagramNode(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var shape: DiagramShapeKind,
    var text: String = "",
    var colorArgb: Int = DEFAULT_COLOR
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("shape", shape.name)
        put("text", text)
        put("color", colorArgb)
    }

    companion object {
        const val WIDTH = 160f
        const val HEIGHT = 72f
        val DEFAULT_COLOR = Color.parseColor("#4FC3F7")

        fun fromJson(obj: JSONObject): DiagramNode {
            val shape = try {
                DiagramShapeKind.valueOf(obj.optString("shape", DiagramShapeKind.RECTANGLE.name))
            } catch (e: IllegalArgumentException) {
                DiagramShapeKind.RECTANGLE
            }
            return DiagramNode(
                id = obj.optString("id", UUID.randomUUID().toString()),
                x = obj.optDouble("x", 0.0).toFloat(),
                y = obj.optDouble("y", 0.0).toFloat(),
                shape = shape,
                text = obj.optString("text", ""),
                colorArgb = obj.optInt("color", DEFAULT_COLOR)
            )
        }
    }
}

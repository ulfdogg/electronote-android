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
    var colorArgb: Int = DEFAULT_COLOR,
    // PAP only: grid column/row (see PapGrid) — x/y are kept in sync as the *derived*
    // pixel position so rendering/hit-testing code doesn't need to care which mode a
    // node is in. Null for MindMap nodes, which use x/y as the source of truth instead.
    var col: Int? = null,
    var row: Int? = null,
    // DIN annotation for IO nodes only ("E" = Eingabe, "A" = Ausgabe).
    var tag: String = ""
) {
    val widthPx: Float get() = shape.widthPx
    val heightPx: Float get() = shape.heightPx

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("shape", shape.name)
        put("text", text)
        put("color", colorArgb)
        put("col", col ?: JSONObject.NULL)
        put("row", row ?: JSONObject.NULL)
        put("tag", tag)
    }

    companion object {
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
                colorArgb = obj.optInt("color", DEFAULT_COLOR),
                col = if (obj.has("col") && !obj.isNull("col")) obj.getInt("col") else null,
                row = if (obj.has("row") && !obj.isNull("row")) obj.getInt("row") else null,
                tag = obj.optString("tag", "")
            )
        }
    }
}

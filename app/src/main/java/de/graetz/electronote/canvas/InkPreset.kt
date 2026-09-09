package de.graetz.electronote.canvas

import org.json.JSONObject
import java.util.UUID

data class InkPreset(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var tool: DrawTool,
    var colorArgb: Int,
    var widthPx: Float
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("tool", tool.name)
        put("color", colorArgb)
        put("widthPx", widthPx.toDouble())
    }

    companion object {
        fun fromJson(obj: JSONObject): InkPreset {
            val tool = try {
                DrawTool.valueOf(obj.optString("tool", DrawTool.PEN.name))
            } catch (e: IllegalArgumentException) {
                DrawTool.PEN
            }
            return InkPreset(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Preset"),
                tool = tool,
                colorArgb = obj.optInt("color"),
                widthPx = obj.optDouble("widthPx", 4.5).toFloat()
            )
        }
    }
}

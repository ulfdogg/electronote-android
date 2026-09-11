package de.graetz.electronote.canvas

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class StickyNoteElement(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var text: String = "",
    var colorIndex: Int = 0,
    // Hand-drawn ink on top of the note, in note-local pixel coordinates (0..STICKY_NOTE_SIZE_PX)
    // — mirrors iOS's embedded PKDrawing on sticky notes so that content doesn't get silently
    // dropped once cross-platform sync exists.
    var inkStrokes: MutableList<Stroke> = mutableListOf()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("text", text)
        put("colorIndex", colorIndex)
        val strokesArr = JSONArray()
        for (stroke in inkStrokes) {
            val strokeObj = JSONObject()
            strokeObj.put("color", stroke.colorArgb)
            strokeObj.put("width", stroke.widthPx.toDouble())
            val pointsArr = JSONArray()
            for (p in stroke.points) {
                val pointArr = JSONArray()
                pointArr.put(p.x.toDouble())
                pointArr.put(p.y.toDouble())
                pointArr.put(p.pressure.toDouble())
                pointsArr.put(pointArr)
            }
            strokeObj.put("points", pointsArr)
            strokesArr.put(strokeObj)
        }
        put("inkStrokes", strokesArr)
    }

    companion object {
        fun fromJson(obj: JSONObject): StickyNoteElement {
            val strokesArr = obj.optJSONArray("inkStrokes") ?: JSONArray()
            val inkStrokes = mutableListOf<Stroke>()
            for (i in 0 until strokesArr.length()) {
                val strokeObj = strokesArr.getJSONObject(i)
                val color = strokeObj.optInt("color")
                val width = strokeObj.optDouble("width", 4.0).toFloat()
                val pointsArr = strokeObj.optJSONArray("points") ?: JSONArray()
                val points = mutableListOf<StrokePoint>()
                for (j in 0 until pointsArr.length()) {
                    val p = pointsArr.getJSONArray(j)
                    points.add(
                        StrokePoint(
                            x = p.getDouble(0).toFloat(),
                            y = p.getDouble(1).toFloat(),
                            pressure = if (p.length() > 2) p.getDouble(2).toFloat() else 1f
                        )
                    )
                }
                inkStrokes.add(Stroke(points = points, colorArgb = color, widthPx = width))
            }
            return StickyNoteElement(
                id = obj.optString("id", UUID.randomUUID().toString()),
                x = obj.optDouble("x", 0.0).toFloat(),
                y = obj.optDouble("y", 0.0).toFloat(),
                text = obj.optString("text", ""),
                colorIndex = obj.optInt("colorIndex", 0),
                inkStrokes = inkStrokes
            )
        }
    }
}

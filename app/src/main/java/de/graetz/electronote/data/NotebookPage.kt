package de.graetz.electronote.data

import de.graetz.electronote.canvas.Stroke
import de.graetz.electronote.canvas.StrokePoint
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * One page of a notebook: freehand strokes, plus an optional background image
 * (e.g. a rendered PDF page imported behind the ink).
 */
data class NotebookPage(
    val id: String = UUID.randomUUID().toString(),
    var backgroundImageFile: String? = null,
    var strokes: List<Stroke> = emptyList(),
    // Pixel size of the InkCanvasView at the time strokes were last captured. The background
    // image is always stretched to fill the view, so this is the one reference frame both
    // strokes and background share — needed to composite them correctly again on export.
    var canvasWidthPx: Int = 0,
    var canvasHeightPx: Int = 0
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("backgroundImageFile", backgroundImageFile ?: JSONObject.NULL)
        obj.put("canvasWidthPx", canvasWidthPx)
        obj.put("canvasHeightPx", canvasHeightPx)
        val strokesArr = JSONArray()
        for (stroke in strokes) {
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
        obj.put("strokes", strokesArr)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): NotebookPage {
            val id = obj.optString("id", UUID.randomUUID().toString())
            val bg = if (obj.isNull("backgroundImageFile")) null else obj.optString("backgroundImageFile", null)
            val strokesArr = obj.optJSONArray("strokes") ?: JSONArray()
            val strokes = mutableListOf<Stroke>()
            for (i in 0 until strokesArr.length()) {
                val strokeObj = strokesArr.getJSONObject(i)
                val color = strokeObj.optInt("color")
                val width = strokeObj.optDouble("width", 6.0).toFloat()
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
                strokes.add(Stroke(points = points, colorArgb = color, widthPx = width))
            }
            return NotebookPage(
                id = id,
                backgroundImageFile = bg,
                strokes = strokes,
                canvasWidthPx = obj.optInt("canvasWidthPx", 0),
                canvasHeightPx = obj.optInt("canvasHeightPx", 0)
            )
        }
    }
}

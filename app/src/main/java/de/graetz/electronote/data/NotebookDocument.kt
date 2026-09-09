package de.graetz.electronote.data

import de.graetz.electronote.canvas.LineSpacing
import de.graetz.electronote.canvas.PaperStyle
import de.graetz.electronote.canvas.Stroke
import de.graetz.electronote.canvas.StrokePoint
import de.graetz.electronote.canvas.StickyNoteElement
import de.graetz.electronote.canvas.TextElement
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * A notebook is one continuous, auto-extending canvas (like the iPad app's infinite
 * notebook) rather than a fixed set of discrete pages. Imported PDFs/photos/scans are
 * stacked as [PageBackground] layers at increasing Y-offsets within that one canvas.
 */
data class NotebookDocument(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var canvasWidthPx: Int = 0,
    var canvasHeightPx: Int = 2200,
    var paperStyle: PaperStyle = PaperStyle.LINED,
    var lineSpacing: LineSpacing = LineSpacing.MEDIUM,
    // Set once this document has been uploaded to/downloaded from Nextcloud, so later
    // uploads always land in the same remote folder instead of creating duplicates.
    var remoteFolderName: String? = null,
    var strokes: MutableList<Stroke> = mutableListOf(),
    var backgrounds: MutableList<PageBackground> = mutableListOf(),
    var textElements: MutableList<TextElement> = mutableListOf(),
    var stickyNotes: MutableList<StickyNoteElement> = mutableListOf()
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("createdAt", createdAt)
        obj.put("updatedAt", updatedAt)
        obj.put("canvasWidthPx", canvasWidthPx)
        obj.put("canvasHeightPx", canvasHeightPx)
        obj.put("paperStyle", paperStyle.name)
        obj.put("lineSpacing", lineSpacing.name)
        obj.put("remoteFolderName", remoteFolderName ?: JSONObject.NULL)

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

        obj.put("backgrounds", JSONArray(backgrounds.map { it.toJson() }))
        obj.put("textElements", JSONArray(textElements.map { it.toJson() }))
        obj.put("stickyNotes", JSONArray(stickyNotes.map { it.toJson() }))
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): NotebookDocument {
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

            val backgroundsArr = obj.optJSONArray("backgrounds") ?: JSONArray()
            val backgrounds = mutableListOf<PageBackground>()
            for (i in 0 until backgroundsArr.length()) {
                backgrounds.add(PageBackground.fromJson(backgroundsArr.getJSONObject(i)))
            }

            val textArr = obj.optJSONArray("textElements") ?: JSONArray()
            val textElements = mutableListOf<TextElement>()
            for (i in 0 until textArr.length()) {
                textElements.add(TextElement.fromJson(textArr.getJSONObject(i)))
            }

            val stickyArr = obj.optJSONArray("stickyNotes") ?: JSONArray()
            val stickyNotes = mutableListOf<StickyNoteElement>()
            for (i in 0 until stickyArr.length()) {
                stickyNotes.add(StickyNoteElement.fromJson(stickyArr.getJSONObject(i)))
            }

            val paperStyle = try {
                PaperStyle.valueOf(obj.optString("paperStyle", PaperStyle.LINED.name))
            } catch (e: IllegalArgumentException) {
                PaperStyle.LINED
            }
            val lineSpacing = try {
                LineSpacing.valueOf(obj.optString("lineSpacing", LineSpacing.MEDIUM.name))
            } catch (e: IllegalArgumentException) {
                LineSpacing.MEDIUM
            }

            return NotebookDocument(
                id = obj.optString("id", UUID.randomUUID().toString()),
                name = obj.optString("name", "Notizbuch"),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                canvasWidthPx = obj.optInt("canvasWidthPx", 0),
                canvasHeightPx = obj.optInt("canvasHeightPx", 2200),
                paperStyle = paperStyle,
                lineSpacing = lineSpacing,
                remoteFolderName = if (obj.has("remoteFolderName") && !obj.isNull("remoteFolderName")) {
                    obj.getString("remoteFolderName")
                } else {
                    null
                },
                strokes = strokes,
                backgrounds = backgrounds,
                textElements = textElements,
                stickyNotes = stickyNotes
            )
        }
    }
}

/** Lightweight summary for listing documents without loading all stroke data. */
data class NotebookDocumentSummary(
    val id: String,
    val name: String,
    val updatedAt: Long
)

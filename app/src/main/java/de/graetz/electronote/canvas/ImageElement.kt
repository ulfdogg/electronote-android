package de.graetz.electronote.canvas

import org.json.JSONObject
import java.util.UUID

/**
 * A raster image placed at an arbitrary position on the canvas. Covers several distinct
 * uses (mirroring how iOS's single InsertedImage type also covers all of these): a
 * rendered math plot, an inserted circuit symbol, or a video/YouTube thumbnail (tapping
 * one of the latter two opens playback instead of just displaying it).
 */
data class ImageElement(
    val id: String = UUID.randomUUID().toString(),
    var x: Float,
    var y: Float,
    var widthPx: Float,
    var heightPx: Float,
    var filename: String,
    var kind: String = KIND_IMAGE,
    // Only set when kind == KIND_VIDEO: filename of the video file under the document dir.
    var videoFilename: String? = null,
    // Only set when kind == KIND_YOUTUBE.
    var youtubeUrl: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("x", x.toDouble())
        put("y", y.toDouble())
        put("widthPx", widthPx.toDouble())
        put("heightPx", heightPx.toDouble())
        put("filename", filename)
        put("kind", kind)
        put("videoFilename", videoFilename ?: JSONObject.NULL)
        put("youtubeUrl", youtubeUrl ?: JSONObject.NULL)
    }

    companion object {
        const val KIND_IMAGE = "image"
        const val KIND_CIRCUIT_SYMBOL = "circuitSymbol"
        const val KIND_MATH_PLOT = "mathPlot"
        const val KIND_VIDEO = "video"
        const val KIND_YOUTUBE = "youtube"

        fun fromJson(obj: JSONObject): ImageElement = ImageElement(
            id = obj.optString("id", UUID.randomUUID().toString()),
            x = obj.optDouble("x", 0.0).toFloat(),
            y = obj.optDouble("y", 0.0).toFloat(),
            widthPx = obj.optDouble("widthPx", 200.0).toFloat(),
            heightPx = obj.optDouble("heightPx", 200.0).toFloat(),
            filename = obj.optString("filename", ""),
            kind = obj.optString("kind", KIND_IMAGE),
            videoFilename = if (obj.has("videoFilename") && !obj.isNull("videoFilename")) obj.getString("videoFilename") else null,
            youtubeUrl = if (obj.has("youtubeUrl") && !obj.isNull("youtubeUrl")) obj.getString("youtubeUrl") else null
        )
    }
}

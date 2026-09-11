package de.graetz.electronote.nextcloud

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import de.graetz.electronote.canvas.ImageElement
import de.graetz.electronote.canvas.LineSpacing
import de.graetz.electronote.canvas.PaperStyle
import de.graetz.electronote.canvas.StickyNoteElement
import de.graetz.electronote.canvas.Stroke
import de.graetz.electronote.canvas.StrokePoint
import de.graetz.electronote.canvas.TextElement
import de.graetz.electronote.canvas.Bookmark as CanvasBookmark
import de.graetz.electronote.data.NotebookDocument
import de.graetz.electronote.data.PageBackground
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Translates between Android's own document.json (NotebookDocument.toJson/fromJson) and
 * iOS's document.json (Features/Canvas/Models/NotebookDocument.swift +
 * NotebookDocumentStore.swift), so the same Nextcloud folder can be opened by either app.
 * Used only at the Nextcloud sync boundary — Android's own local storage format is
 * untouched by this, see [de.graetz.electronote.nextcloud.NextcloudSync].
 *
 * Coordinate systems differ: iOS lays everything out on a fixed 595pt-wide page; Android
 * positions things in a per-document pixel width (NotebookDocument.canvasWidthPx). Both
 * apps already agree on "1 iOS pt = 2 Android px" via LineSpacing (20/28/38pt vs
 * 40/56/76px), so export always normalizes THIS document's own width to 595pt, and import
 * always creates a fixed-width [IOS_COMPAT_CANVAS_WIDTH_PX] document.
 *
 * iOS's actual ink (drawing.pkdrawing / StickyNote.drawingData) is Apple's private
 * PKDrawing binary format and is NOT touched here — see [IosExtras] and
 * [strokesToPortableJson]/[portableJsonToStrokes] for how Android instead writes/reads a
 * portable JSON mirror that a future iOS update can read additively.
 */
object IosNotebookBridge {
    const val IOS_PAGE_WIDTH_PT = 595f
    const val PT_TO_PX = 2f
    const val IOS_COMPAT_CANVAS_WIDTH_PX = (IOS_PAGE_WIDTH_PT * PT_TO_PX).toInt() // 1190

    private const val TEXT_WRAP_WIDTH_PX = 900 // matches ElementRenderer.drawTextElement

    /** iOS-only data Android doesn't model, preserved verbatim across a download → (re-)
     * upload round-trip so opening/re-saving a document on Android never destroys iOS-only
     * content it doesn't yet understand. */
    data class IosExtras(
        var mathEnabled: Boolean = false,
        var darkDrawingMode: Boolean = false,
        var shapeSnapEnabled: Boolean = false,
        // Raw insertedPDFs entries — Android has no "source PDF" concept (only rendered
        // page images), kept only to hand back unchanged.
        var insertedPdfsRaw: JSONArray = JSONArray(),
        // stickyNoteId -> raw base64 PKDrawing bytes, for notes Android hasn't re-drawn.
        var stickyDrawingData: MutableMap<String, String> = mutableMapOf()
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("mathEnabled", mathEnabled)
            put("darkDrawingMode", darkDrawingMode)
            put("shapeSnapEnabled", shapeSnapEnabled)
            put("insertedPdfsRaw", insertedPdfsRaw)
            put("stickyDrawingData", JSONObject().apply { stickyDrawingData.forEach { (k, v) -> put(k, v) } })
        }

        companion object {
            fun fromJson(obj: JSONObject): IosExtras {
                val stickyMap = mutableMapOf<String, String>()
                obj.optJSONObject("stickyDrawingData")?.let { o -> for (key in o.keys()) stickyMap[key] = o.getString(key) }
                return IosExtras(
                    mathEnabled = obj.optBoolean("mathEnabled", false),
                    darkDrawingMode = obj.optBoolean("darkDrawingMode", false),
                    shapeSnapEnabled = obj.optBoolean("shapeSnapEnabled", false),
                    insertedPdfsRaw = obj.optJSONArray("insertedPdfsRaw") ?: JSONArray(),
                    stickyDrawingData = stickyMap
                )
            }
        }
    }

    /** A text element rendered to a standalone PNG, ready to upload as images/<filename>,
     * matching how iOS keeps a live thumbnail image for every text box it can also re-edit. */
    data class RenderedTextPng(val filename: String, val bytes: ByteArray)

    data class ExportResult(val documentJson: JSONObject, val textPngs: List<RenderedTextPng>, val scale: Float)

    // MARK: - Enum mapping (iOS Codable string enums use their German display label as rawValue)

    private fun mapBackgroundStyle(style: PaperStyle): String = when (style) {
        PaperStyle.BLANK -> "Leer"
        PaperStyle.LINED -> "Liniert"
        PaperStyle.GRID -> "Kariert"
        PaperStyle.DOTTED -> "Gepunktet"
        PaperStyle.CORNELL -> "Cornell"
    }

    private fun parseBackgroundStyle(raw: String): PaperStyle = when (raw) {
        "Liniert" -> PaperStyle.LINED
        "Kariert" -> PaperStyle.GRID
        "Gepunktet" -> PaperStyle.DOTTED
        "Cornell" -> PaperStyle.CORNELL
        else -> PaperStyle.BLANK
    }

    private fun mapLineSpacing(spacing: LineSpacing): String = when (spacing) {
        LineSpacing.NARROW -> "Eng"
        LineSpacing.MEDIUM -> "Mittel"
        LineSpacing.WIDE -> "Weit"
    }

    private fun parseLineSpacing(raw: String): LineSpacing = when (raw) {
        "Eng" -> LineSpacing.NARROW
        "Weit" -> LineSpacing.WIDE
        else -> LineSpacing.MEDIUM
    }

    private fun colorToHex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)
    private fun hexToColor(hex: String?): Int = try {
        if (hex != null) Color.parseColor(hex) else Color.BLACK
    } catch (e: IllegalArgumentException) {
        Color.BLACK
    }

    // MARK: - Strokes (portable mirror of PKDrawing, in iOS-pt space)

    fun strokesToPortableJson(strokes: List<Stroke>, scale: Float): JSONArray {
        val arr = JSONArray()
        for (stroke in strokes) {
            val obj = JSONObject()
            obj.put("color", stroke.colorArgb)
            obj.put("width", (stroke.widthPx * scale).toDouble())
            val points = JSONArray()
            for (p in stroke.points) {
                val pt = JSONArray()
                pt.put((p.x * scale).toDouble())
                pt.put((p.y * scale).toDouble())
                pt.put(p.pressure.toDouble())
                points.put(pt)
            }
            obj.put("points", points)
            arr.put(obj)
        }
        return arr
    }

    fun portableJsonToStrokes(arr: JSONArray, scale: Float): MutableList<Stroke> {
        val strokes = mutableListOf<Stroke>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val color = obj.optInt("color")
            val width = (obj.optDouble("width", 3.0) * scale).toFloat()
            val pointsArr = obj.optJSONArray("points") ?: JSONArray()
            val points = mutableListOf<StrokePoint>()
            for (j in 0 until pointsArr.length()) {
                val p = pointsArr.getJSONArray(j)
                points.add(
                    StrokePoint(
                        x = (p.getDouble(0) * scale).toFloat(),
                        y = (p.getDouble(1) * scale).toFloat(),
                        pressure = if (p.length() > 2) p.getDouble(2).toFloat() else 1f
                    )
                )
            }
            strokes.add(Stroke(points = points, colorArgb = color, widthPx = width))
        }
        return strokes
    }

    // MARK: - Export (Android → iOS schema)

    /** [existing] carries over iOS-only fields last seen on download; pass null for a
     * notebook that has never been synced with iOS before (brand new on Android). */
    fun toIosJson(doc: NotebookDocument, existing: IosExtras?): ExportResult {
        val extras = existing ?: IosExtras()
        val scale = IOS_PAGE_WIDTH_PT / (if (doc.canvasWidthPx > 0) doc.canvasWidthPx else IOS_COMPAT_CANVAS_WIDTH_PX)

        val textPngs = mutableListOf<RenderedTextPng>()
        val insertedImages = JSONArray()

        for (t in doc.textElements) {
            val png = renderTextElementPng(t)
            val filename = "${t.id}.png"
            textPngs.add(RenderedTextPng(filename, png))
            insertedImages.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("filename", filename)
                    put("startX", (t.x * scale).toDouble())
                    put("startY", (t.y * scale).toDouble())
                    put("width", (TEXT_WRAP_WIDTH_PX * scale).toDouble())
                    put("height", (measureTextHeight(t) * scale).toDouble())
                    put("textContent", t.text)
                    put("fontSize", (t.fontSizePx * scale).toDouble())
                    put("fontColorHex", colorToHex(t.colorArgb))
                    put("rotation", 0.0)
                }
            )
        }

        for (bg in doc.backgrounds) {
            insertedImages.put(
                JSONObject().apply {
                    put("id", bg.id)
                    put("filename", bg.imageFile)
                    put("startX", 0.0)
                    put("startY", (bg.yOffsetPx * scale).toDouble())
                    put("width", (IOS_PAGE_WIDTH_PT).toDouble())
                    put("height", (bg.heightPx * scale).toDouble())
                    put("isDocumentPage", true)
                }
            )
        }

        for (img in doc.imageElements) {
            insertedImages.put(
                JSONObject().apply {
                    put("id", img.id)
                    put("filename", img.filename)
                    put("startX", (img.x * scale).toDouble())
                    put("startY", (img.y * scale).toDouble())
                    put("width", (img.widthPx * scale).toDouble())
                    put("height", (img.heightPx * scale).toDouble())
                    if (img.kind == ImageElement.KIND_VIDEO || img.kind == ImageElement.KIND_YOUTUBE) {
                        put("mediaType", img.kind)
                        put("mediaURLString", img.videoFilename ?: img.youtubeUrl)
                    }
                }
            )
        }

        val bookmarks = JSONArray()
        for (b in doc.bookmarks) {
            bookmarks.put(JSONObject().apply { put("id", b.id); put("title", b.name); put("y", (b.yOffsetPx * scale).toDouble()) })
        }

        val stickyNotes = JSONArray()
        for (s in doc.stickyNotes) {
            val obj = JSONObject().apply {
                put("id", s.id)
                put("text", s.text)
                put("x", (s.x * scale).toDouble())
                put("y", (s.y * scale).toDouble())
                put("colorIndex", s.colorIndex)
            }
            if (s.inkStrokes.isNotEmpty()) {
                // Android-authored ink wins over whatever opaque PKDrawing bytes iOS last sent.
                obj.put("inkStrokesPortable", strokesToPortableJson(s.inkStrokes, scale))
                obj.put("drawingData", JSONObject.NULL)
            } else {
                extras.stickyDrawingData[s.id]?.let { obj.put("drawingData", it) }
            }
            stickyNotes.put(obj)
        }

        val json = JSONObject().apply {
            put("background", mapBackgroundStyle(doc.paperStyle))
            put("lineSpacing", mapLineSpacing(doc.lineSpacing))
            put("documentHeight", (doc.canvasHeightPx * scale).toDouble())
            put("insertedPDFs", extras.insertedPdfsRaw)
            put("insertedImages", insertedImages)
            put("mathEnabled", extras.mathEnabled)
            put("darkDrawingMode", extras.darkDrawingMode)
            put("shapeSnapEnabled", extras.shapeSnapEnabled)
            put("bookmarks", bookmarks)
            put("stickyNotes", stickyNotes)
        }
        return ExportResult(json, textPngs, scale)
    }

    // MARK: - Import (iOS schema → Android)

    data class ImportResult(val document: NotebookDocument, val extras: IosExtras)

    fun fromIosJson(obj: JSONObject, id: String, name: String): ImportResult {
        val scale = PT_TO_PX // iOS pt -> Android px at the fixed import width, see class doc

        val paperStyle = parseBackgroundStyle(obj.optString("background", "Kariert"))
        val lineSpacing = parseLineSpacing(obj.optString("lineSpacing", "Mittel"))
        val canvasHeightPx = (obj.optDouble("documentHeight", 2200.0) * scale).toInt()

        val backgrounds = mutableListOf<PageBackground>()
        val imageElements = mutableListOf<ImageElement>()
        val textElements = mutableListOf<TextElement>()

        val insertedImages = obj.optJSONArray("insertedImages") ?: JSONArray()
        for (i in 0 until insertedImages.length()) {
            val e = insertedImages.getJSONObject(i)
            val eid = e.optString("id", UUID.randomUUID().toString())
            val filename = e.optString("filename", "")
            val startX = (e.optDouble("startX", 0.0) * scale).toFloat()
            val startY = (e.optDouble("startY", 0.0) * scale).toFloat()
            val width = (e.optDouble("width", 200.0) * scale).toFloat()
            val height = (e.optDouble("height", 200.0) * scale).toFloat()
            when {
                e.has("textContent") && !e.isNull("textContent") -> {
                    textElements.add(
                        TextElement(
                            id = eid,
                            x = startX,
                            y = startY,
                            text = e.optString("textContent", ""),
                            colorArgb = hexToColor(e.optString("fontColorHex", null)),
                            fontSizePx = (e.optDouble("fontSize", 21.0) * scale).toFloat()
                        )
                    )
                }
                e.optBoolean("isDocumentPage", false) -> {
                    backgrounds.add(PageBackground(id = eid, yOffsetPx = startY.toInt(), heightPx = height.toInt(), imageFile = filename))
                }
                else -> {
                    val mediaType = if (e.has("mediaType") && !e.isNull("mediaType")) e.getString("mediaType") else null
                    val mediaUrl = if (e.has("mediaURLString") && !e.isNull("mediaURLString")) e.getString("mediaURLString") else null
                    imageElements.add(
                        ImageElement(
                            id = eid,
                            x = startX,
                            y = startY,
                            widthPx = width,
                            heightPx = height,
                            filename = filename,
                            kind = when (mediaType) {
                                "video" -> ImageElement.KIND_VIDEO
                                "youtube" -> ImageElement.KIND_YOUTUBE
                                else -> ImageElement.KIND_IMAGE
                            },
                            // Android's own convention (NotebookStore.saveVideoFile) always
                            // stores video filenames with a "videos/" prefix baked in.
                            videoFilename = if (mediaType == "video") "videos/${(mediaUrl ?: "").substringAfterLast("/")}" else null,
                            youtubeUrl = if (mediaType == "youtube") mediaUrl else null
                        )
                    )
                }
            }
        }

        val bookmarks = mutableListOf<CanvasBookmark>()
        val bookmarksArr = obj.optJSONArray("bookmarks") ?: JSONArray()
        for (i in 0 until bookmarksArr.length()) {
            val b = bookmarksArr.getJSONObject(i)
            bookmarks.add(CanvasBookmark(id = b.optString("id", UUID.randomUUID().toString()), name = b.optString("title", "Lesezeichen"), yOffsetPx = (b.optDouble("y", 0.0) * scale).toInt()))
        }

        val stickyNotes = mutableListOf<StickyNoteElement>()
        val stickyDrawingData = mutableMapOf<String, String>()
        val stickyArr = obj.optJSONArray("stickyNotes") ?: JSONArray()
        for (i in 0 until stickyArr.length()) {
            val s = stickyArr.getJSONObject(i)
            val sid = s.optString("id", UUID.randomUUID().toString())
            val portable = s.optJSONArray("inkStrokesPortable")
            val inkStrokes = if (portable != null) portableJsonToStrokes(portable, scale) else mutableListOf()
            if (portable == null && s.has("drawingData") && !s.isNull("drawingData")) {
                stickyDrawingData[sid] = s.getString("drawingData")
            }
            stickyNotes.add(
                StickyNoteElement(
                    id = sid,
                    x = (s.optDouble("x", 0.0) * scale).toFloat(),
                    y = (s.optDouble("y", 0.0) * scale).toFloat(),
                    text = s.optString("text", ""),
                    colorIndex = s.optInt("colorIndex", 0),
                    inkStrokes = inkStrokes
                )
            )
        }

        val extras = IosExtras(
            mathEnabled = obj.optBoolean("mathEnabled", false),
            darkDrawingMode = obj.optBoolean("darkDrawingMode", false),
            shapeSnapEnabled = obj.optBoolean("shapeSnapEnabled", false),
            insertedPdfsRaw = obj.optJSONArray("insertedPDFs") ?: JSONArray(),
            stickyDrawingData = stickyDrawingData
        )

        val document = NotebookDocument(
            id = id,
            name = name,
            canvasWidthPx = IOS_COMPAT_CANVAS_WIDTH_PX,
            canvasHeightPx = canvasHeightPx,
            paperStyle = paperStyle,
            lineSpacing = lineSpacing,
            backgrounds = backgrounds,
            textElements = textElements,
            stickyNotes = stickyNotes,
            bookmarks = bookmarks,
            imageElements = imageElements
        )
        return ImportResult(document, extras)
    }

    // MARK: - Text-element PNG rendering (so iOS can display a thumbnail before re-editing)

    private fun textPaintFor(t: TextElement): TextPaint = TextPaint().apply {
        isAntiAlias = true
        color = t.colorArgb
        textSize = t.fontSizePx
    }

    private fun measureTextHeight(t: TextElement): Int {
        val layout = StaticLayout.Builder
            .obtain(t.text, 0, t.text.length, textPaintFor(t), TEXT_WRAP_WIDTH_PX)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        return layout.height.coerceAtLeast(1)
    }

    private fun renderTextElementPng(t: TextElement): ByteArray {
        val paint = textPaintFor(t)
        val layout = StaticLayout.Builder
            .obtain(t.text, 0, t.text.length, paint, TEXT_WRAP_WIDTH_PX)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
        val bitmap = Bitmap.createBitmap(TEXT_WRAP_WIDTH_PX, layout.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        layout.draw(Canvas(bitmap))
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        bitmap.recycle()
        return out.toByteArray()
    }
}

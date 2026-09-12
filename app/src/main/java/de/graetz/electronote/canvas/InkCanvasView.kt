package de.graetz.electronote.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import de.graetz.electronote.data.PageBackground
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class PlacementMode { NONE, TEXT, STICKY, IMAGE }

/**
 * A continuous, tall handwriting canvas — one per notebook, meant to live inside a
 * vertically scrolling container so it behaves like the iPad app's infinite notebook
 * rather than a fixed page. This view does not resize itself; when the user writes near
 * the bottom it asks its caller (via [onWantsMoreHeight]) to grow the Compose-managed
 * height, then just redraws once relaid-out taller — Android has no PencilKit
 * equivalent, so stroke capture/rendering is hand-rolled here.
 */
class InkCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onStrokesChanged: ((hasUndo: Boolean, hasRedo: Boolean) -> Unit)? = null
    var onWantsMoreHeight: ((suggestedHeightPx: Int) -> Unit)? = null
    var onWidthKnown: ((Int) -> Unit)? = null
    var onSelectionMade: ((RectF) -> Unit)? = null
    var onSelectionCancelled: (() -> Unit)? = null
    var onTextPlacementRequested: ((Float, Float) -> Unit)? = null
    var onStickyPlacementRequested: ((Float, Float) -> Unit)? = null
    var onTextElementTapped: ((TextElement) -> Unit)? = null
    var onStickyNoteTapped: ((StickyNoteElement) -> Unit)? = null
    var onToolChangeRequested: ((DrawTool) -> Unit)? = null
    var onImagePlacementRequested: ((Float, Float) -> Unit)? = null
    var onImageElementTapped: ((ImageElement) -> Unit)? = null

    var currentTool: DrawTool = DrawTool.PEN
    var currentColor: Int = Color.BLACK
    var currentWidthPx: Float = 6f
    var shapeSnapEnabled: Boolean = false
    var paperStyle: PaperStyle = PaperStyle.LINED
        set(value) { field = value; invalidate() }
    var lineSpacingPx: Float = LineSpacing.MEDIUM.px
        set(value) { field = value; invalidate() }
    var stylusOnly: Boolean = false
    var selectionModeActive: Boolean = false
        set(value) { field = value; selectionPoints = null; invalidate() }
    var placementMode: PlacementMode = PlacementMode.NONE

    private val strokes = mutableListOf<Stroke>()
    private val redoStack = mutableListOf<Stroke>()
    private var currentPoints: MutableList<StrokePoint>? = null
    private var selectionPoints: MutableList<StrokePoint>? = null
    private var activePointerId: Int = -1

    private val backgroundLayers = mutableListOf<Pair<PageBackground, Bitmap?>>()
    private val textElements = mutableListOf<TextElement>()
    private val stickyNotes = mutableListOf<StickyNoteElement>()
    private val imageElements = mutableListOf<Pair<ImageElement, Bitmap?>>()

    private val backgroundPaint = Paint().apply { isAntiAlias = true; isFilterBitmap = true }
    private val gridPaint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#DADCE0")
        strokeWidth = 1.5f
    }
    private val dotPaint = Paint().apply {
        isAntiAlias = true
        color = Color.parseColor("#C6C9CE")
        style = Paint.Style.FILL
    }
    private val cornellPaint = Paint().apply {
        isAntiAlias = false
        color = Color.parseColor("#AEB2B8")
        strokeWidth = 2.5f
    }
    private val selectionStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#8E24AA")
        pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
    }
    private val selectionFillPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.parseColor("#338E24AA")
    }
    private val eraserPreviewPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#9E9E9E")
    }
    private var lastEraserPoint: StrokePoint? = null
    private var toolBeforeStylusEraser: DrawTool? = null

    private val imagePlaceholderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.parseColor("#E0E0E0")
    }
    private val playBadgeCirclePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.parseColor("#CC000000")
    }
    private val playBadgeTrianglePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    companion object {
        private const val EXTEND_MARGIN_PX = 320f
        private const val EXTEND_STEP_PX = 1000
        private const val ERASER_RADIUS_PX = 26f
        private const val GRID_STEP_PX = 44f
        private const val DOT_STEP_PX = 44f
        // Matches PdfExporter's A4 page slicing (595x842pt) so the Cornell cue-column/
        // summary layout lines up with actual exported page breaks.
        private const val CORNELL_PAGE_ASPECT = 842f / 595f
        private const val CORNELL_CUE_FRACTION = 0.28f
        private const val CORNELL_SUMMARY_FRACTION = 0.15f
    }

    init {
        setBackgroundColor(Color.WHITE)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && w != oldw) onWidthKnown?.invoke(w)
    }

    // MARK: - Content

    fun setBackgroundLayers(layers: List<Pair<PageBackground, Bitmap?>>) {
        backgroundLayers.clear()
        backgroundLayers.addAll(layers)
        invalidate()
    }

    fun setStrokes(newStrokes: List<Stroke>) {
        strokes.clear()
        strokes.addAll(newStrokes)
        redoStack.clear()
        currentPoints = null
        invalidate()
        notifyChanged()
    }

    fun setTextElements(elements: List<TextElement>) {
        textElements.clear()
        textElements.addAll(elements)
        invalidate()
    }

    fun setStickyNotes(notes: List<StickyNoteElement>) {
        stickyNotes.clear()
        stickyNotes.addAll(notes)
        invalidate()
    }

    fun setImageElements(elements: List<Pair<ImageElement, Bitmap?>>) {
        imageElements.clear()
        imageElements.addAll(elements)
        invalidate()
    }

    fun getStrokes(): List<Stroke> = strokes.toList()
    fun getTextElements(): List<TextElement> = textElements.toList()
    fun getStickyNotes(): List<StickyNoteElement> = stickyNotes.toList()
    fun getImageElements(): List<ImageElement> = imageElements.map { it.first }

    fun addTextElement(element: TextElement) {
        textElements.add(element)
        invalidate()
    }

    fun updateOrRemoveTextElement(id: String, newText: String?) {
        if (newText.isNullOrBlank()) {
            textElements.removeAll { it.id == id }
        } else {
            textElements.find { it.id == id }?.text = newText
        }
        invalidate()
    }

    fun addStickyNote(note: StickyNoteElement) {
        stickyNotes.add(note)
        invalidate()
    }

    fun updateOrRemoveStickyNote(id: String, newText: String?, remove: Boolean) {
        if (remove) {
            stickyNotes.removeAll { it.id == id }
        } else if (newText != null) {
            stickyNotes.find { it.id == id }?.text = newText
        }
        invalidate()
    }

    fun updateStickyNoteInk(id: String, strokes: List<Stroke>) {
        stickyNotes.find { it.id == id }?.inkStrokes = strokes.toMutableList()
        invalidate()
    }

    fun addImageElement(element: ImageElement, bitmap: Bitmap?) {
        imageElements.add(element to bitmap)
        invalidate()
    }

    fun removeImageElement(id: String) {
        imageElements.removeAll { it.first.id == id }
        invalidate()
    }

    fun undo() {
        if (strokes.isEmpty()) return
        redoStack.add(strokes.removeAt(strokes.size - 1))
        invalidate()
        notifyChanged()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        strokes.add(redoStack.removeAt(redoStack.size - 1))
        invalidate()
        notifyChanged()
    }

    private fun notifyChanged() {
        onStrokesChanged?.invoke(strokes.isNotEmpty(), redoStack.isNotEmpty())
    }

    private var canvasBackgroundColorInt: Int = Color.WHITE

    /** Dark paper, matching the iPad app's "Dunkles Papier" toggle. */
    fun setDarkPaper(dark: Boolean) {
        canvasBackgroundColorInt = if (dark) Color.parseColor("#1C1C1E") else Color.WHITE
        setBackgroundColor(canvasBackgroundColorInt)
        gridPaint.color = if (dark) Color.parseColor("#3A3A3C") else Color.parseColor("#DADCE0")
        dotPaint.color = if (dark) Color.parseColor("#48484A") else Color.parseColor("#C6C9CE")
        invalidate()
    }

    // MARK: - Touch handling

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Android has no single barrel-button gesture that works identically across all
        // stylus vendors, so the primary button press is used as the equivalent of Apple
        // Pencil's double-tap: switch to the eraser, press again to switch back.
        if (event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS &&
            (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        ) {
            val previous = toolBeforeStylusEraser
            if (previous != null) {
                currentTool = previous
                toolBeforeStylusEraser = null
            } else {
                toolBeforeStylusEraser = currentTool
                currentTool = DrawTool.ERASER
            }
            onToolChangeRequested?.invoke(currentTool)
            return true
        }

        if (selectionModeActive) return handleSelectionTouch(event)

        if (placementMode != PlacementMode.NONE) {
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                val mode = placementMode
                placementMode = PlacementMode.NONE
                when (mode) {
                    PlacementMode.TEXT -> onTextPlacementRequested?.invoke(event.x, event.y)
                    PlacementMode.STICKY -> onStickyPlacementRequested?.invoke(event.x, event.y)
                    PlacementMode.IMAGE -> onImagePlacementRequested?.invoke(event.x, event.y)
                    PlacementMode.NONE -> {}
                }
            }
            return true
        }

        if (currentTool == DrawTool.ERASER) {
            return handleEraserTouch(event)
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val hitText = textElements.lastOrNull { hitTestText(it, event.x, event.y) }
            if (hitText != null) {
                onTextElementTapped?.invoke(hitText)
                return true
            }
            val hitSticky = stickyNotes.lastOrNull { hitTestSticky(it, event.x, event.y) }
            if (hitSticky != null) {
                onStickyNoteTapped?.invoke(hitSticky)
                return true
            }
            val hitImage = imageElements.map { it.first }.lastOrNull { hitTestImage(it, event.x, event.y) }
            if (hitImage != null) {
                onImageElementTapped?.invoke(hitImage)
                return true
            }
        }

        val toolType = event.getToolType(event.actionIndex)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        if (stylusOnly && !isStylus && event.actionMasked == MotionEvent.ACTION_DOWN) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Without this, a Compose ancestor's vertical scroll (the notebook page
                // sits inside Modifier.verticalScroll) can steal an in-progress stroke
                // mid-gesture on real, slightly-diagonal pen/finger movement — synthetic
                // straight-line test swipes never trigger it, which is why this only
                // surfaced as "nothing gets drawn" on real handwriting.
                parent?.requestDisallowInterceptTouchEvent(true)
                activePointerId = event.getPointerId(0)
                currentPoints = mutableListOf(pointFrom(event, 0))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val idx = event.findPointerIndex(activePointerId)
                if (idx < 0) return true
                val pts = currentPoints ?: return true
                for (h in 0 until event.historySize) {
                    pts.add(
                        StrokePoint(
                            x = event.getHistoricalX(idx, h),
                            y = event.getHistoricalY(idx, h),
                            pressure = event.getHistoricalPressure(idx, h)
                        )
                    )
                }
                val p = pointFrom(event, idx)
                pts.add(p)
                maybeExtendCanvas(p.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                currentPoints = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                finishStroke()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                currentPoints = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun pointFrom(event: MotionEvent, index: Int): StrokePoint =
        StrokePoint(event.getX(index), event.getY(index), event.getPressure(index))

    private fun maybeExtendCanvas(y: Float) {
        if (height <= 0) return
        if (y > height - EXTEND_MARGIN_PX) {
            onWantsMoreHeight?.invoke(height + EXTEND_STEP_PX)
        }
    }

    private fun hitTestText(t: TextElement, x: Float, y: Float): Boolean =
        x >= t.x - 12 && x <= t.x + 340 && y >= t.y - 12 && y <= t.y + 110

    private fun hitTestSticky(s: StickyNoteElement, x: Float, y: Float): Boolean =
        x >= s.x && x <= s.x + STICKY_NOTE_SIZE_PX && y >= s.y && y <= s.y + STICKY_NOTE_SIZE_PX

    private fun hitTestImage(img: ImageElement, x: Float, y: Float): Boolean =
        x >= img.x && x <= img.x + img.widthPx && y >= img.y && y <= img.y + img.heightPx

    // MARK: - Eraser

    private fun handleEraserTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastEraserPoint = StrokePoint(event.x, event.y)
                eraseNear(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                lastEraserPoint = null
                invalidate()
                return true
            }
        }
        return true
    }

    private fun eraseNear(x: Float, y: Float) {
        val before = strokes.size
        strokes.removeAll { stroke -> stroke.points.any { hypot((it.x - x).toDouble(), (it.y - y).toDouble()) < ERASER_RADIUS_PX } }
        textElements.removeAll { hitTestText(it, x, y) }
        stickyNotes.removeAll { hitTestSticky(it, x, y) }
        imageElements.removeAll { hitTestImage(it.first, x, y) }
        if (strokes.size != before) {
            redoStack.clear()
            notifyChanged()
        }
    }

    // MARK: - Tool-specific ink

    private fun colorForCurrentTool(): Int {
        val r = Color.red(currentColor)
        val g = Color.green(currentColor)
        val b = Color.blue(currentColor)
        return when (currentTool) {
            DrawTool.MARKER -> Color.argb(90, r, g, b)
            DrawTool.PENCIL -> Color.argb(215, r, g, b)
            else -> currentColor
        }
    }

    private fun widthForCurrentTool(): Float = when (currentTool) {
        DrawTool.PEN -> currentWidthPx
        DrawTool.MARKER -> max(currentWidthPx * 2.5f, 10f)
        DrawTool.PENCIL -> max(currentWidthPx * 0.8f, 2f)
        DrawTool.ERASER -> currentWidthPx
    }

    private fun finishStroke() {
        val pts = currentPoints
        currentPoints = null
        if (pts != null && pts.size > 1) {
            var stroke = Stroke(points = pts, colorArgb = colorForCurrentTool(), widthPx = widthForCurrentTool())
            if (shapeSnapEnabled && (currentTool == DrawTool.PEN || currentTool == DrawTool.PENCIL)) {
                ShapeSnapper.snap(stroke)?.let { (snapped, _) -> stroke = snapped }
            }
            strokes.add(stroke)
            redoStack.clear()
            notifyChanged()
        }
        invalidate()
    }

    // MARK: - Selection (OCR) touch handling

    private fun handleSelectionTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                selectionPoints = mutableListOf(pointFrom(event, 0))
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val pts = selectionPoints ?: return true
                pts.add(pointFrom(event, event.actionIndex))
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val pts = selectionPoints
                selectionPoints = null
                val box = pts?.let { boundingBox(it) }
                if (box != null && box.width() > 24f && box.height() > 24f) {
                    onSelectionMade?.invoke(box)
                } else {
                    onSelectionCancelled?.invoke()
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                selectionPoints = null
                onSelectionCancelled?.invoke()
                invalidate()
                return true
            }
        }
        return true
    }

    private fun boundingBox(points: List<StrokePoint>): RectF {
        var minX = points[0].x
        var maxX = points[0].x
        var minY = points[0].y
        var maxY = points[0].y
        for (p in points) {
            minX = minOf(minX, p.x); maxX = maxOf(maxX, p.x)
            minY = minOf(minY, p.y); maxY = maxOf(maxY, p.y)
        }
        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * Renders the current background + ink to a bitmap, cropped to [rect] (with a small
     * padding margin), for feeding into on-device OCR.
     */
    fun captureRegion(rect: RectF, paddingPx: Float = 30f): Bitmap? {
        if (width <= 0 || height <= 0) return null

        val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val fullCanvas = Canvas(full)
        fullCanvas.drawColor(Color.WHITE)
        drawBackgroundLayers(fullCanvas)
        for (stroke in strokes) fullCanvas.drawStroke(stroke)

        val left = (rect.left - paddingPx).coerceIn(0f, width.toFloat())
        val top = (rect.top - paddingPx).coerceIn(0f, height.toFloat())
        val right = (rect.right + paddingPx).coerceIn(0f, width.toFloat())
        val bottom = (rect.bottom + paddingPx).coerceIn(0f, height.toFloat())

        val x = left.toInt()
        val y = top.toInt()
        val w = (right - left).toInt().coerceAtLeast(1)
        val h = (bottom - top).toInt().coerceAtLeast(1)
        if (x + w > full.width || y + h > full.height) {
            full.recycle()
            return null
        }

        val cropped = Bitmap.createBitmap(full, x, y, w, h)
        full.recycle()
        return cropped
    }

    // MARK: - Drawing

    private fun drawBackgroundLayers(canvas: Canvas) {
        for ((bg, bitmap) in backgroundLayers) {
            bitmap ?: continue
            val dst = RectF(0f, bg.yOffsetPx.toFloat(), width.toFloat(), (bg.yOffsetPx + bg.heightPx).toFloat())
            canvas.drawBitmap(bitmap, null, dst, backgroundPaint)
        }
    }

    private fun drawPaperPattern(canvas: Canvas) {
        if (paperStyle == PaperStyle.BLANK) return
        val clip = Rect()
        val hasClip = canvas.getClipBounds(clip)
        // getClipBounds() has been observed returning a degenerate/empty rect on some
        // GPU drivers for tall, mostly off-screen custom Views (the paper pattern would
        // then silently render nothing at all, on every draw). Fall back to the view's
        // full bounds whenever the reported clip isn't a sane, non-empty range.
        val clipIsUsable = hasClip && clip.top < clip.bottom && clip.bottom > 0
        val top = if (clipIsUsable) max(0, clip.top) else 0
        val bottom = if (clipIsUsable) min(height, clip.bottom) else height
        val right = width.toFloat()

        when (paperStyle) {
            PaperStyle.GRID -> {
                var y = (top / GRID_STEP_PX).toInt() * GRID_STEP_PX
                while (y < bottom) {
                    canvas.drawLine(0f, y, right, y, gridPaint)
                    y += GRID_STEP_PX
                }
                var x = 0f
                while (x < right) {
                    canvas.drawLine(x, top.toFloat(), x, bottom.toFloat(), gridPaint)
                    x += GRID_STEP_PX
                }
            }
            PaperStyle.LINED -> {
                var y = (top / lineSpacingPx).toInt() * lineSpacingPx
                while (y < bottom) {
                    canvas.drawLine(0f, y, right, y, gridPaint)
                    y += lineSpacingPx
                }
            }
            PaperStyle.DOTTED -> {
                var y = (top / DOT_STEP_PX).toInt() * DOT_STEP_PX
                while (y < bottom) {
                    var x = DOT_STEP_PX
                    while (x < right) {
                        canvas.drawCircle(x, y, 2.2f, dotPaint)
                        x += DOT_STEP_PX
                    }
                    y += DOT_STEP_PX
                }
            }
            PaperStyle.CORNELL -> {
                var y = (top / lineSpacingPx).toInt() * lineSpacingPx
                while (y < bottom) {
                    canvas.drawLine(0f, y, right, y, gridPaint)
                    y += lineSpacingPx
                }

                val pageHeightPx = right * CORNELL_PAGE_ASPECT
                val cueX = right * CORNELL_CUE_FRACTION
                val summaryHeightPx = pageHeightPx * CORNELL_SUMMARY_FRACTION
                var pageTop = (top / pageHeightPx).toInt() * pageHeightPx
                while (pageTop < bottom) {
                    val summaryTop = pageTop + pageHeightPx - summaryHeightPx
                    canvas.drawLine(cueX, pageTop, cueX, summaryTop, cornellPaint)
                    canvas.drawLine(0f, summaryTop, right, summaryTop, cornellPaint)
                    pageTop += pageHeightPx
                }
            }
            PaperStyle.BLANK -> {}
        }
    }

    /** Everything that's actually part of the page (paper pattern, backgrounds, ink,
     * images, text, sticky notes) — shared between live rendering and
     * [captureVisibleScreenshot], which needs the same output minus the transient
     * editing overlays (in-progress stroke, eraser cursor, OCR selection marquee). */
    private fun drawPageContent(canvas: Canvas) {
        drawPaperPattern(canvas)
        drawBackgroundLayers(canvas)

        for (stroke in strokes) canvas.drawStroke(stroke)

        for ((img, bmp) in imageElements) {
            val dst = RectF(img.x, img.y, img.x + img.widthPx, img.y + img.heightPx)
            if (bmp != null) {
                canvas.drawBitmap(bmp, null, dst, backgroundPaint)
            } else {
                canvas.drawRect(dst, imagePlaceholderPaint)
            }
            if (img.kind == ImageElement.KIND_VIDEO || img.kind == ImageElement.KIND_YOUTUBE) {
                val cx = dst.centerX()
                val cy = dst.centerY()
                val r = min(dst.width(), dst.height()) * 0.18f
                canvas.drawCircle(cx, cy, r, playBadgeCirclePaint)
                val path = Path()
                path.moveTo(cx - r * 0.35f, cy - r * 0.5f)
                path.lineTo(cx - r * 0.35f, cy + r * 0.5f)
                path.lineTo(cx + r * 0.5f, cy)
                path.close()
                canvas.drawPath(path, playBadgeTrianglePaint)
            }
        }

        for (text in textElements) canvas.drawTextElement(text)
        for (sticky in stickyNotes) canvas.drawStickyNote(sticky)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        drawPageContent(canvas)

        currentPoints?.let {
            canvas.drawStroke(Stroke(it, colorForCurrentTool(), widthForCurrentTool()))
        }

        lastEraserPoint?.let { p ->
            canvas.drawCircle(p.x, p.y, ERASER_RADIUS_PX, eraserPreviewPaint)
        }

        selectionPoints?.let { pts ->
            if (pts.size > 1) {
                val path = Path()
                path.moveTo(pts[0].x, pts[0].y)
                for (p in pts.drop(1)) path.lineTo(p.x, p.y)
                canvas.drawPath(path, selectionFillPaint)
                canvas.drawPath(path, selectionStrokePaint)
            }
        }
    }

    /**
     * Renders the actual page content (paper pattern, images, ink, text, sticky notes —
     * everything [drawPageContent] draws) cropped to [visibleRect], for handing to the AI
     * assistant as a "what's currently on screen" screenshot. Unlike [captureRegion] (used
     * for OCR, which only needs backgrounds+ink), this includes everything a human looking
     * at the screen would see.
     */
    fun captureVisibleScreenshot(visibleRect: RectF, paddingPx: Float = 0f): Bitmap? {
        if (width <= 0 || height <= 0) return null

        val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val fullCanvas = Canvas(full)
        fullCanvas.drawColor(canvasBackgroundColorInt)
        drawPageContent(fullCanvas)

        val left = (visibleRect.left - paddingPx).coerceIn(0f, width.toFloat())
        val top = (visibleRect.top - paddingPx).coerceIn(0f, height.toFloat())
        val right = (visibleRect.right + paddingPx).coerceIn(0f, width.toFloat())
        val bottom = (visibleRect.bottom + paddingPx).coerceIn(0f, height.toFloat())

        val x = left.toInt()
        val y = top.toInt()
        val w = (right - left).toInt().coerceAtLeast(1)
        val h = (bottom - top).toInt().coerceAtLeast(1)
        if (x + w > full.width || y + h > full.height) {
            full.recycle()
            return null
        }

        val cropped = Bitmap.createBitmap(full, x, y, w, h)
        full.recycle()
        return cropped
    }
}

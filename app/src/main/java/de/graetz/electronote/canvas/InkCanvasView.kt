package de.graetz.electronote.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Minimal handwriting canvas: freehand ink strokes from finger or stylus input,
 * with undo/redo and an optional page-background bitmap (e.g. an imported PDF page).
 *
 * This is the Android analogue of PencilKit's PKCanvasView on iOS — there is no
 * built-in equivalent on Android, so stroke capture/rendering is hand-rolled here.
 */
class InkCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** Notified whenever the stroke list changes (draw, undo, redo, clear, load). */
    var onStrokesChanged: ((hasUndo: Boolean, hasRedo: Boolean) -> Unit)? = null

    var currentColor: Int = Color.BLACK
    var currentWidthPx: Float = 6f

    /** If true, only stylus input draws; finger touches are ignored (for palm rejection). */
    var stylusOnly: Boolean = false

    /**
     * While true, touches draw a temporary lasso selection (for OCR) instead of ink.
     * Caller sets this, then reacts to [onSelectionMade] / [onSelectionCancelled].
     */
    var selectionModeActive: Boolean = false
        set(value) {
            field = value
            selectionPoints = null
            invalidate()
        }
    var onSelectionMade: ((RectF) -> Unit)? = null
    var onSelectionCancelled: (() -> Unit)? = null

    private val strokes = mutableListOf<Stroke>()
    private val redoStack = mutableListOf<Stroke>()
    private var currentPoints: MutableList<StrokePoint>? = null
    private var selectionPoints: MutableList<StrokePoint>? = null
    private var activePointerId: Int = -1

    private var backgroundBitmap: Bitmap? = null

    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
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

    init {
        setBackgroundColor(Color.WHITE)
    }

    // MARK: - Public API

    fun setBackgroundPage(bitmap: Bitmap?) {
        backgroundBitmap = bitmap
        invalidate()
    }

    /** Dark paper, matching the iPad app's "Dunkles Papier" toggle. */
    fun setDarkPaper(dark: Boolean) {
        setBackgroundColor(if (dark) Color.parseColor("#1C1C1E") else Color.WHITE)
    }

    fun setStrokes(newStrokes: List<Stroke>) {
        strokes.clear()
        strokes.addAll(newStrokes)
        redoStack.clear()
        currentPoints = null
        invalidate()
        notifyChanged()
    }

    fun getStrokes(): List<Stroke> = strokes.toList()

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

    fun clearAll() {
        strokes.clear()
        redoStack.clear()
        currentPoints = null
        invalidate()
        notifyChanged()
    }

    private fun notifyChanged() {
        onStrokesChanged?.invoke(strokes.isNotEmpty(), redoStack.isNotEmpty())
    }

    // MARK: - Touch handling

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (selectionModeActive) {
            return handleSelectionTouch(event)
        }

        val toolType = event.getToolType(event.actionIndex)
        val isStylus = toolType == MotionEvent.TOOL_TYPE_STYLUS ||
            toolType == MotionEvent.TOOL_TYPE_ERASER
        if (stylusOnly && !isStylus && event.actionMasked == MotionEvent.ACTION_DOWN) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // A second finger touching down while writing cancels the current stroke,
                // so two-finger gestures elsewhere (e.g. scrolling a parent view) don't
                // leave behind a stray mark.
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
                pts.add(pointFrom(event, idx))
                invalidate()
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Extra finger arrived mid-stroke — abort the in-progress stroke.
                currentPoints = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                finishStroke()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                currentPoints = null
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun pointFrom(event: MotionEvent, index: Int): StrokePoint =
        StrokePoint(event.getX(index), event.getY(index), event.getPressure(index))

    // MARK: - Selection (OCR) touch handling

    private fun handleSelectionTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
            minX = minOf(minX, p.x)
            maxX = maxOf(maxX, p.x)
            minY = minOf(minY, p.y)
            maxY = maxOf(maxY, p.y)
        }
        return RectF(minX, minY, maxX, maxY)
    }

    /**
     * Renders the current background + ink to a bitmap, cropped to [rect] (with a small
     * padding margin), for feeding into on-device OCR. Coordinates are in the same
     * view-pixel space as [Stroke] points and the stretched-to-fit background.
     */
    fun captureRegion(rect: RectF, paddingPx: Float = 30f): Bitmap? {
        if (width <= 0 || height <= 0) return null

        val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val fullCanvas = Canvas(full)
        fullCanvas.drawColor(Color.WHITE)
        backgroundBitmap?.let { bmp ->
            fullCanvas.drawBitmap(bmp, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), backgroundPaint)
        }
        for (stroke in strokes) {
            fullCanvas.drawStroke(stroke)
        }

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

    private fun finishStroke() {
        val pts = currentPoints
        currentPoints = null
        if (pts != null && pts.size > 1) {
            strokes.add(Stroke(points = pts, colorArgb = currentColor, widthPx = currentWidthPx))
            redoStack.clear()
            notifyChanged()
        }
        invalidate()
    }

    // MARK: - Drawing

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        backgroundBitmap?.let { bmp ->
            val dst = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(bmp, null, dst, backgroundPaint)
        }

        for (stroke in strokes) {
            canvas.drawStroke(stroke)
        }
        currentPoints?.let { canvas.drawStroke(Stroke(it, currentColor, currentWidthPx)) }

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
}

package de.graetz.electronote.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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

    private val strokes = mutableListOf<Stroke>()
    private val redoStack = mutableListOf<Stroke>()
    private var currentPoints: MutableList<StrokePoint>? = null
    private var activePointerId: Int = -1

    private var backgroundBitmap: Bitmap? = null

    private val strokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
    }

    init {
        setBackgroundColor(Color.WHITE)
    }

    // MARK: - Public API

    fun setBackgroundPage(bitmap: Bitmap?) {
        backgroundBitmap = bitmap
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
            drawStroke(canvas, stroke.points, stroke.colorArgb, stroke.widthPx)
        }
        currentPoints?.let { drawStroke(canvas, it, currentColor, currentWidthPx) }
    }

    private fun drawStroke(canvas: Canvas, points: List<StrokePoint>, color: Int, width: Float) {
        if (points.isEmpty()) return
        if (points.size == 1) {
            // A tap: draw a dot so a single touch is still visible.
            strokePaint.color = color
            strokePaint.strokeWidth = width
            canvas.drawPoint(points[0].x, points[0].y, strokePaint)
            return
        }
        val path = Path()
        path.moveTo(points[0].x, points[0].y)
        for (p in points.drop(1)) {
            path.lineTo(p.x, p.y)
        }
        strokePaint.color = color
        strokePaint.strokeWidth = width
        canvas.drawPath(path, strokePaint)
    }
}

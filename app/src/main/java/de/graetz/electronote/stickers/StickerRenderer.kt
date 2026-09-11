package de.graetz.electronote.stickers

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/** Draws simple, colorful stamp-style stickers as vector paths (no bundled image assets). */
object StickerRenderer {
    private const val S = 120f
    private const val MID = S / 2f

    private fun fillPaint(color: Int) = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
        this.color = color
    }

    private fun strokePaint(color: Int, width: Float = 8f) = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
    }

    fun render(type: StickerType): Bitmap {
        val bitmap = Bitmap.createBitmap(S.toInt(), S.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        when (type) {
            StickerType.ARROW_RIGHT -> arrow(canvas, 0f)
            StickerType.ARROW_LEFT -> arrow(canvas, 180f)
            StickerType.ARROW_UP -> arrow(canvas, -90f)
            StickerType.ARROW_DOWN -> arrow(canvas, 90f)
            StickerType.ARROW_CURVED -> curvedArrow(canvas)
            StickerType.CHECK -> check(canvas)
            StickerType.CROSS -> cross(canvas)
            StickerType.STAR -> star(canvas)
            StickerType.HEART -> heart(canvas)
            StickerType.EXCLAMATION -> exclamation(canvas)
            StickerType.QUESTION -> question(canvas)
            StickerType.SPEECH_BUBBLE -> speechBubble(canvas)
            StickerType.FLAG -> flag(canvas)
            StickerType.LIGHTBULB -> lightbulb(canvas)
        }
        return bitmap
    }

    private fun arrow(canvas: Canvas, degrees: Float) {
        canvas.save()
        canvas.rotate(degrees, MID, MID)
        val paint = fillPaint(Color.parseColor("#007AFF"))
        val path = Path()
        path.moveTo(20f, MID - 12f)
        path.lineTo(65f, MID - 12f)
        path.lineTo(65f, MID - 28f)
        path.lineTo(100f, MID)
        path.lineTo(65f, MID + 28f)
        path.lineTo(65f, MID + 12f)
        path.lineTo(20f, MID + 12f)
        path.close()
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun curvedArrow(canvas: Canvas) {
        val paint = strokePaint(Color.parseColor("#007AFF"), 10f)
        val path = Path()
        path.addArc(RectF(20f, 20f, 100f, 100f), -200f, 250f)
        canvas.drawPath(path, paint)
        val headX = 20f + (MID - 20f) * (1 - cos(Math.toRadians(50.0))).toFloat()
        val headY = MID - (MID - 20f) * sin(Math.toRadians(50.0)).toFloat()
        val head = Path()
        head.moveTo(headX - 14f, headY - 4f)
        head.lineTo(headX + 6f, headY + 10f)
        head.lineTo(headX - 4f, headY + 22f)
        canvas.drawPath(head, strokePaint(Color.parseColor("#007AFF"), 10f))
    }

    private fun check(canvas: Canvas) {
        val paint = strokePaint(Color.parseColor("#34C759"), 12f)
        val path = Path()
        path.moveTo(24f, MID + 4f)
        path.lineTo(48f, MID + 28f)
        path.lineTo(96f, MID - 30f)
        canvas.drawPath(path, paint)
    }

    private fun cross(canvas: Canvas) {
        val paint = strokePaint(Color.parseColor("#FF3B30"), 12f)
        canvas.drawLine(28f, 28f, 92f, 92f, paint)
        canvas.drawLine(92f, 28f, 28f, 92f, paint)
    }

    private fun star(canvas: Canvas) {
        val paint = fillPaint(Color.parseColor("#FFCC00"))
        val path = Path()
        val outerR = 46f
        val innerR = 20f
        for (i in 0 until 10) {
            val angle = Math.toRadians((-90 + i * 36).toDouble())
            val r = if (i % 2 == 0) outerR else innerR
            val x = MID + r * cos(angle).toFloat()
            val y = MID + r * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun heart(canvas: Canvas) {
        val paint = fillPaint(Color.parseColor("#FF2D55"))
        val path = Path()
        path.moveTo(MID, 88f)
        path.cubicTo(10f, 55f, 25f, 20f, MID, 42f)
        path.cubicTo(95f, 20f, 110f, 55f, MID, 88f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun exclamation(canvas: Canvas) {
        val paint = fillPaint(Color.parseColor("#FF9500"))
        val path = Path()
        path.moveTo(MID, 14f)
        path.lineTo(100f, 100f)
        path.lineTo(20f, 100f)
        path.close()
        canvas.drawPath(path, paint)
        val text = strokePaint(Color.WHITE, 8f)
        canvas.drawLine(MID, 45f, MID, 75f, text)
        canvas.drawCircle(MID, 90f, 2f, fillPaint(Color.WHITE))
    }

    private fun question(canvas: Canvas) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#5856D6")
            textSize = 80f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawText("?", MID, MID + 28f, paint)
    }

    private fun speechBubble(canvas: Canvas) {
        val paint = fillPaint(Color.parseColor("#30B0C7"))
        val rect = RectF(16f, 16f, 104f, 80f)
        canvas.drawRoundRect(rect, 18f, 18f, paint)
        val tail = Path()
        tail.moveTo(36f, 78f)
        tail.lineTo(28f, 102f)
        tail.lineTo(56f, 80f)
        tail.close()
        canvas.drawPath(tail, paint)
    }

    private fun flag(canvas: Canvas) {
        val pole = strokePaint(Color.parseColor("#6E6E73"), 6f)
        canvas.drawLine(28f, 16f, 28f, 104f, pole)
        val paint = fillPaint(Color.parseColor("#FF3B30"))
        val path = Path()
        path.moveTo(28f, 20f)
        path.lineTo(96f, 34f)
        path.lineTo(28f, 58f)
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun lightbulb(canvas: Canvas) {
        val paint = fillPaint(Color.parseColor("#FFCC00"))
        canvas.drawCircle(MID, 46f, 30f, paint)
        val base = fillPaint(Color.parseColor("#6E6E73"))
        canvas.drawRect(RectF(MID - 12f, 72f, MID + 12f, 92f), base)
        val stroke = strokePaint(Color.parseColor("#6E6E73"), 4f)
        canvas.drawLine(MID - 12f, 80f, MID + 12f, 80f, stroke)
        canvas.drawLine(MID - 12f, 87f, MID + 12f, 87f, stroke)
    }
}

package de.graetz.electronote.electrical

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Draws simplified (not DIN-exact, but recognizable) schematic symbols as vector paths,
 * matching the iOS app's approach of rendering these itself rather than shipping bundled
 * image assets — mirrors `CircuitSymbolRenderer.swift`, just with a smaller symbol set.
 */
object CircuitSymbolRenderer {
    private const val W = 160f
    private const val H = 100f
    private const val MID_Y = H / 2f
    private const val LEAD = 24f

    private val stroke = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.BLACK
    }
    private val fillWhite = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.WHITE }
    private val fillBlack = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL; color = Color.BLACK }
    private val text = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    fun render(type: CircuitSymbolType): Bitmap {
        val bitmap = Bitmap.createBitmap(W.toInt(), H.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        when (type) {
            CircuitSymbolType.RESISTOR -> resistor(canvas)
            CircuitSymbolType.CAPACITOR -> capacitor(canvas)
            CircuitSymbolType.INDUCTOR -> inductor(canvas)
            CircuitSymbolType.POTENTIOMETER -> potentiometer(canvas)
            CircuitSymbolType.BATTERY -> battery(canvas)
            CircuitSymbolType.VOLTAGE_SOURCE -> circleWithLabel(canvas, "U")
            CircuitSymbolType.AC_SOURCE -> acSource(canvas)
            CircuitSymbolType.GROUND -> ground(canvas)
            CircuitSymbolType.DIODE -> diode(canvas, led = false)
            CircuitSymbolType.LED -> diode(canvas, led = true)
            CircuitSymbolType.NPN_TRANSISTOR -> transistor(canvas)
            CircuitSymbolType.OP_AMP -> opAmp(canvas)
            CircuitSymbolType.SWITCH -> switch(canvas)
            CircuitSymbolType.PUSH_BUTTON -> pushButton(canvas)
            CircuitSymbolType.RELAY -> relay(canvas)
            CircuitSymbolType.FUSE -> fuse(canvas)
            CircuitSymbolType.AMMETER -> circleWithLabel(canvas, "A")
            CircuitSymbolType.VOLTMETER -> circleWithLabel(canvas, "V")
            CircuitSymbolType.LAMP -> lamp(canvas)
            CircuitSymbolType.MOTOR -> circleWithLabel(canvas, "M")
            CircuitSymbolType.AND_GATE -> andGate(canvas)
            CircuitSymbolType.OR_GATE -> orGate(canvas)
            CircuitSymbolType.NOT_GATE -> notGate(canvas)
            CircuitSymbolType.XOR_GATE -> xorGate(canvas)
        }
        return bitmap
    }

    private fun leadLine(canvas: Canvas, fromX: Float, toX: Float) {
        canvas.drawLine(fromX, MID_Y, toX, MID_Y, stroke)
    }

    private fun resistor(canvas: Canvas) {
        leadLine(canvas, 0f, LEAD)
        leadLine(canvas, W - LEAD, W)
        canvas.drawRect(RectF(LEAD, MID_Y - 16f, W - LEAD, MID_Y + 16f), stroke)
    }

    private fun capacitor(canvas: Canvas) {
        leadLine(canvas, 0f, W / 2f - 10f)
        leadLine(canvas, W / 2f + 10f, W)
        canvas.drawLine(W / 2f - 10f, MID_Y - 26f, W / 2f - 10f, MID_Y + 26f, stroke)
        canvas.drawLine(W / 2f + 10f, MID_Y - 26f, W / 2f + 10f, MID_Y + 26f, stroke)
    }

    private fun inductor(canvas: Canvas) {
        leadLine(canvas, 0f, LEAD)
        leadLine(canvas, W - LEAD, W)
        val path = Path()
        val bumpWidth = (W - 2 * LEAD) / 4f
        path.moveTo(LEAD, MID_Y)
        for (i in 0 until 4) {
            val startX = LEAD + i * bumpWidth
            path.arcTo(RectF(startX, MID_Y - bumpWidth / 2f, startX + bumpWidth, MID_Y + bumpWidth / 2f), 180f, -180f, false)
        }
        canvas.drawPath(path, stroke)
    }

    private fun potentiometer(canvas: Canvas) {
        resistor(canvas)
        val path = Path()
        path.moveTo(LEAD, MID_Y + 30f)
        path.lineTo(W - LEAD, MID_Y - 30f)
        canvas.drawPath(path, stroke)
        canvas.drawLine(W - LEAD - 8f, MID_Y - 30f + 10f, W - LEAD, MID_Y - 30f, stroke)
        canvas.drawLine(W - LEAD - 10f, MID_Y - 30f - 6f, W - LEAD, MID_Y - 30f, stroke)
    }

    private fun battery(canvas: Canvas) {
        leadLine(canvas, 0f, W / 2f - 18f)
        leadLine(canvas, W / 2f + 18f, W)
        val cx = W / 2f
        canvas.drawLine(cx - 18f, MID_Y - 28f, cx - 18f, MID_Y + 28f, stroke)
        canvas.drawLine(cx - 6f, MID_Y - 14f, cx - 6f, MID_Y + 14f, stroke)
        canvas.drawLine(cx + 6f, MID_Y - 28f, cx + 6f, MID_Y + 28f, stroke)
        canvas.drawLine(cx + 18f, MID_Y - 14f, cx + 18f, MID_Y + 14f, stroke)
    }

    private fun circleWithLabel(canvas: Canvas, label: String) {
        leadLine(canvas, 0f, W / 2f - 26f)
        leadLine(canvas, W / 2f + 26f, W)
        canvas.drawCircle(W / 2f, MID_Y, 26f, stroke)
        canvas.drawText(label, W / 2f, MID_Y + 10f, text)
    }

    private fun acSource(canvas: Canvas) {
        leadLine(canvas, 0f, W / 2f - 26f)
        leadLine(canvas, W / 2f + 26f, W)
        canvas.drawCircle(W / 2f, MID_Y, 26f, stroke)
        val path = Path()
        path.moveTo(W / 2f - 14f, MID_Y)
        path.cubicTo(W / 2f - 7f, MID_Y - 14f, W / 2f - 2f, MID_Y - 14f, W / 2f, MID_Y)
        path.cubicTo(W / 2f + 5f, MID_Y + 14f, W / 2f + 10f, MID_Y + 14f, W / 2f + 14f, MID_Y)
        canvas.drawPath(path, stroke)
    }

    private fun ground(canvas: Canvas) {
        canvas.drawLine(W / 2f, LEAD, W / 2f, MID_Y, stroke)
        canvas.drawLine(W / 2f - 24f, MID_Y, W / 2f + 24f, MID_Y, stroke)
        canvas.drawLine(W / 2f - 16f, MID_Y + 12f, W / 2f + 16f, MID_Y + 12f, stroke)
        canvas.drawLine(W / 2f - 8f, MID_Y + 24f, W / 2f + 8f, MID_Y + 24f, stroke)
    }

    private fun diode(canvas: Canvas, led: Boolean) {
        leadLine(canvas, 0f, W / 2f - 16f)
        leadLine(canvas, W / 2f + 16f, W)
        val path = Path()
        path.moveTo(W / 2f - 16f, MID_Y - 20f)
        path.lineTo(W / 2f - 16f, MID_Y + 20f)
        path.lineTo(W / 2f + 16f, MID_Y)
        path.close()
        canvas.drawPath(path, stroke)
        canvas.drawLine(W / 2f + 16f, MID_Y - 20f, W / 2f + 16f, MID_Y + 20f, stroke)
        if (led) {
            canvas.drawLine(W / 2f + 4f, MID_Y - 24f, W / 2f + 20f, MID_Y - 40f, stroke)
            canvas.drawLine(W / 2f + 16f, MID_Y - 40f, W / 2f + 20f, MID_Y - 40f, stroke)
            canvas.drawLine(W / 2f + 20f, MID_Y - 40f, W / 2f + 20f, MID_Y - 36f, stroke)
            canvas.drawLine(W / 2f + 12f, MID_Y - 18f, W / 2f + 28f, MID_Y - 34f, stroke)
            canvas.drawLine(W / 2f + 24f, MID_Y - 34f, W / 2f + 28f, MID_Y - 34f, stroke)
            canvas.drawLine(W / 2f + 28f, MID_Y - 34f, W / 2f + 28f, MID_Y - 30f, stroke)
        }
    }

    private fun transistor(canvas: Canvas) {
        canvas.drawLine(0f, MID_Y, W / 2f - 10f, MID_Y, stroke)
        canvas.drawLine(W / 2f - 10f, MID_Y - 26f, W / 2f - 10f, MID_Y + 26f, stroke)
        canvas.drawLine(W / 2f - 10f, MID_Y - 14f, W - LEAD, MID_Y - 34f, stroke)
        canvas.drawLine(W - LEAD, MID_Y - 34f, W - LEAD, LEAD, stroke)
        canvas.drawLine(W / 2f - 10f, MID_Y + 14f, W - LEAD, MID_Y + 34f, stroke)
        canvas.drawLine(W - LEAD, MID_Y + 34f, W - LEAD, H - LEAD, stroke)
        canvas.drawLine(W / 2f - 2f, MID_Y + 10f, W / 2f + 8f, MID_Y + 22f, stroke)
        canvas.drawLine(W / 2f + 0f, MID_Y + 26f, W / 2f + 8f, MID_Y + 22f, stroke)
    }

    private fun opAmp(canvas: Canvas) {
        val path = Path()
        path.moveTo(W / 2f - 30f, MID_Y - 30f)
        path.lineTo(W / 2f - 30f, MID_Y + 30f)
        path.lineTo(W / 2f + 30f, MID_Y)
        path.close()
        canvas.drawPath(path, stroke)
        canvas.drawText("+", W / 2f - 18f, MID_Y - 10f, text)
        canvas.drawText("−", W / 2f - 18f, MID_Y + 20f, text)
        canvas.drawLine(0f, MID_Y - 14f, W / 2f - 30f, MID_Y - 14f, stroke)
        canvas.drawLine(0f, MID_Y + 14f, W / 2f - 30f, MID_Y + 14f, stroke)
        canvas.drawLine(W / 2f + 30f, MID_Y, W, MID_Y, stroke)
    }

    private fun switch(canvas: Canvas) {
        leadLine(canvas, 0f, W / 2f - 22f)
        leadLine(canvas, W / 2f + 22f, W)
        canvas.drawCircle(W / 2f - 22f, MID_Y, 4f, fillBlack)
        canvas.drawCircle(W / 2f + 22f, MID_Y, 4f, fillBlack)
        canvas.drawLine(W / 2f - 20f, MID_Y - 2f, W / 2f + 18f, MID_Y - 22f, stroke)
    }

    private fun pushButton(canvas: Canvas) {
        leadLine(canvas, 0f, W / 2f - 22f)
        leadLine(canvas, W / 2f + 22f, W)
        canvas.drawLine(W / 2f - 22f, MID_Y, W / 2f - 22f, MID_Y - 18f, stroke)
        canvas.drawLine(W / 2f + 22f, MID_Y, W / 2f + 22f, MID_Y - 18f, stroke)
        canvas.drawLine(W / 2f - 26f, MID_Y - 18f, W / 2f + 26f, MID_Y - 18f, stroke)
    }

    private fun relay(canvas: Canvas) {
        leadLine(canvas, 0f, LEAD)
        canvas.drawRect(RectF(LEAD, MID_Y - 18f, W / 2f + 4f, MID_Y + 18f), stroke)
        leadLine(canvas, W / 2f + 4f, W / 2f + 20f)
        canvas.drawCircle(W / 2f + 24f, MID_Y, 4f, fillBlack)
        canvas.drawCircle(W - LEAD, MID_Y, 4f, fillBlack)
        canvas.drawLine(W / 2f + 22f, MID_Y - 2f, W - LEAD - 4f, MID_Y - 20f, stroke)
        leadLine(canvas, W - LEAD, W)
    }

    private fun fuse(canvas: Canvas) {
        leadLine(canvas, 0f, LEAD)
        leadLine(canvas, W - LEAD, W)
        canvas.drawRoundRect(RectF(LEAD, MID_Y - 14f, W - LEAD, MID_Y + 14f), 14f, 14f, stroke)
        canvas.drawLine(LEAD, MID_Y, W - LEAD, MID_Y, stroke)
    }

    private fun lamp(canvas: Canvas) {
        leadLine(canvas, 0f, W / 2f - 26f)
        leadLine(canvas, W / 2f + 26f, W)
        canvas.drawCircle(W / 2f, MID_Y, 26f, stroke)
        canvas.drawLine(W / 2f - 18f, MID_Y - 18f, W / 2f + 18f, MID_Y + 18f, stroke)
        canvas.drawLine(W / 2f - 18f, MID_Y + 18f, W / 2f + 18f, MID_Y - 18f, stroke)
    }

    private fun andGate(canvas: Canvas) {
        val left = W / 2f - 34f
        val right = W / 2f + 20f
        val path = Path()
        path.moveTo(left, MID_Y - 26f)
        path.lineTo(W / 2f, MID_Y - 26f)
        path.arcTo(RectF(W / 2f - 26f, MID_Y - 26f, right, MID_Y + 26f), -90f, 180f, false)
        path.lineTo(left, MID_Y + 26f)
        path.close()
        canvas.drawPath(path, stroke)
        canvas.drawLine(0f, MID_Y - 14f, left, MID_Y - 14f, stroke)
        canvas.drawLine(0f, MID_Y + 14f, left, MID_Y + 14f, stroke)
        canvas.drawLine(right, MID_Y, W, MID_Y, stroke)
    }

    private fun orGate(canvas: Canvas) {
        val left = W / 2f - 34f
        val tip = W / 2f + 26f
        val path = Path()
        path.moveTo(left, MID_Y - 26f)
        path.quadTo(W / 2f, MID_Y - 26f, tip, MID_Y)
        path.quadTo(W / 2f, MID_Y + 26f, left, MID_Y + 26f)
        path.quadTo(W / 2f - 14f, MID_Y, left, MID_Y - 26f)
        canvas.drawPath(path, stroke)
        canvas.drawLine(0f, MID_Y - 14f, left + 6f, MID_Y - 14f, stroke)
        canvas.drawLine(0f, MID_Y + 14f, left + 6f, MID_Y + 14f, stroke)
        canvas.drawLine(tip, MID_Y, W, MID_Y, stroke)
    }

    private fun notGate(canvas: Canvas) {
        val left = W / 2f - 30f
        val tip = W / 2f + 20f
        val path = Path()
        path.moveTo(left, MID_Y - 24f)
        path.lineTo(left, MID_Y + 24f)
        path.lineTo(tip, MID_Y)
        path.close()
        canvas.drawPath(path, stroke)
        canvas.drawCircle(tip + 6f, MID_Y, 6f, stroke)
        canvas.drawLine(0f, MID_Y, left, MID_Y, stroke)
        canvas.drawLine(tip + 12f, MID_Y, W, MID_Y, stroke)
    }

    private fun xorGate(canvas: Canvas) {
        orGate(canvas)
        val left = W / 2f - 40f
        val path = Path()
        path.moveTo(left, MID_Y - 26f)
        path.quadTo(left + 14f, MID_Y, left, MID_Y + 26f)
        canvas.drawPath(path, stroke)
    }
}

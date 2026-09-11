package de.graetz.electronote.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

val STICKY_NOTE_SIZE_PX = 260f
val STICKY_NOTE_COLORS = intArrayOf(
    Color.parseColor("#FFF59D"), // yellow
    Color.parseColor("#A5D6A7"), // green
    Color.parseColor("#90CAF9"), // blue
    Color.parseColor("#F48FB1")  // pink
)

private val textPaint = TextPaint().apply {
    isAntiAlias = true
}

private val stickyBgPaint = Paint().apply {
    isAntiAlias = true
    style = Paint.Style.FILL
}

private val stickyTextPaint = TextPaint().apply {
    isAntiAlias = true
    color = Color.parseColor("#212121")
    textSize = 26f
}

/** Shared between the live canvas view and the PDF exporter, so both render identically. */
fun Canvas.drawTextElement(element: TextElement) {
    textPaint.color = element.colorArgb
    textPaint.textSize = element.fontSizePx
    val staticLayout = StaticLayout.Builder
        .obtain(element.text, 0, element.text.length, textPaint, 900)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .build()
    save()
    translate(element.x, element.y)
    staticLayout.draw(this)
    restore()
}

fun Canvas.drawStickyNote(note: StickyNoteElement) {
    val rect = RectF(note.x, note.y, note.x + STICKY_NOTE_SIZE_PX, note.y + STICKY_NOTE_SIZE_PX)
    stickyBgPaint.color = STICKY_NOTE_COLORS[note.colorIndex.mod(STICKY_NOTE_COLORS.size)]
    drawRoundRect(rect, 20f, 20f, stickyBgPaint)

    val staticLayout = StaticLayout.Builder
        .obtain(note.text, 0, note.text.length, stickyTextPaint, (STICKY_NOTE_SIZE_PX - 32f).toInt())
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .build()
    save()
    translate(note.x + 16f, note.y + 16f)
    staticLayout.draw(this)
    restore()

    if (note.inkStrokes.isNotEmpty()) {
        save()
        translate(note.x, note.y)
        for (stroke in note.inkStrokes) drawStroke(stroke)
        restore()
    }
}

package de.graetz.electronote.canvas

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Bridges [InkCanvasView] (a plain UIKit-style Android View) into Compose, and exposes
 * undo/redo state as Compose state so toolbar buttons can react to it.
 */
class InkCanvasController {
    internal var view: InkCanvasView? = null

    var hasUndo by mutableStateOf(false)
        private set
    var hasRedo by mutableStateOf(false)
        private set

    internal fun bind(view: InkCanvasView) {
        this.view = view
        view.onStrokesChanged = { undo, redo ->
            hasUndo = undo
            hasRedo = redo
        }
    }

    fun undo() = view?.undo()
    fun redo() = view?.redo()
    fun clearAll() = view?.clearAll()
    fun getStrokes(): List<Stroke> = view?.getStrokes() ?: emptyList()

    /** Current on-screen pixel size of the canvas, or null before it's been laid out. */
    fun canvasSize(): Pair<Int, Int>? {
        val v = view ?: return null
        return if (v.width > 0 && v.height > 0) v.width to v.height else null
    }

    fun setStrokes(strokes: List<Stroke>) {
        view?.setStrokes(strokes)
    }

    fun setColor(colorArgb: Int) {
        view?.currentColor = colorArgb
    }

    fun setWidthPx(widthPx: Float) {
        view?.currentWidthPx = widthPx
    }

    fun setBackgroundPage(bitmap: Bitmap?) {
        view?.setBackgroundPage(bitmap)
    }
}

@Composable
fun InkCanvas(
    controller: InkCanvasController,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            InkCanvasView(context).also { controller.bind(it) }
        }
    )
}

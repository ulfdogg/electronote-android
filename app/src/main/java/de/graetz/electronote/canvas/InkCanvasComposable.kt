package de.graetz.electronote.canvas

import android.graphics.Bitmap
import android.graphics.RectF
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import de.graetz.electronote.data.PageBackground

/**
 * Bridges [InkCanvasView] into Compose. The view's height is Compose-managed (not set
 * via manual layoutParams poking): the view merely *requests* more height when the user
 * writes near the bottom, and this controller's [canvasHeightPx] state — read back into
 * an explicit `Modifier.height(...)` on [InkCanvas] — is what actually grows it, so the
 * infinite-notebook auto-extend flows through normal Compose recomposition.
 */
class InkCanvasController {
    internal var view: InkCanvasView? = null

    var hasUndo by mutableStateOf(false)
        private set
    var hasRedo by mutableStateOf(false)
        private set
    var canvasHeightPx by mutableIntStateOf(2200)
    var canvasWidthPx by mutableIntStateOf(0)
        private set

    private var onSelectionMadeCallback: ((RectF) -> Unit)? = null
    private var onSelectionCancelledCallback: (() -> Unit)? = null
    var onWantsTextPlacement: ((Float, Float) -> Unit)? = null
    var onWantsStickyPlacement: ((Float, Float) -> Unit)? = null
    var onTextTapped: ((TextElement) -> Unit)? = null
    var onStickyTapped: ((StickyNoteElement) -> Unit)? = null

    internal fun bind(view: InkCanvasView) {
        this.view = view
        view.onStrokesChanged = { undo, redo -> hasUndo = undo; hasRedo = redo }
        view.onWantsMoreHeight = { suggested -> canvasHeightPx = maxOf(canvasHeightPx, suggested) }
        view.onWidthKnown = { w -> canvasWidthPx = w }
        view.onTextPlacementRequested = { x, y -> onWantsTextPlacement?.invoke(x, y) }
        view.onStickyPlacementRequested = { x, y -> onWantsStickyPlacement?.invoke(x, y) }
        view.onTextElementTapped = { t -> onTextTapped?.invoke(t) }
        view.onStickyNoteTapped = { s -> onStickyTapped?.invoke(s) }
        view.onSelectionMade = { rect -> onSelectionMadeCallback?.invoke(rect) }
        view.onSelectionCancelled = { onSelectionCancelledCallback?.invoke() }
    }

    fun undo() = view?.undo()
    fun redo() = view?.redo()

    fun getStrokes(): List<Stroke> = view?.getStrokes() ?: emptyList()
    fun setStrokes(strokes: List<Stroke>) {
        view?.setStrokes(strokes)
    }

    fun getTextElements(): List<TextElement> = view?.getTextElements() ?: emptyList()
    fun setTextElements(elements: List<TextElement>) {
        view?.setTextElements(elements)
    }

    fun getStickyNotes(): List<StickyNoteElement> = view?.getStickyNotes() ?: emptyList()
    fun setStickyNotes(notes: List<StickyNoteElement>) {
        view?.setStickyNotes(notes)
    }

    fun addTextElement(element: TextElement) = view?.addTextElement(element)
    fun updateOrRemoveTextElement(id: String, newText: String?) = view?.updateOrRemoveTextElement(id, newText)
    fun addStickyNote(note: StickyNoteElement) = view?.addStickyNote(note)
    fun updateOrRemoveStickyNote(id: String, newText: String?, remove: Boolean) =
        view?.updateOrRemoveStickyNote(id, newText, remove)

    fun setColor(colorArgb: Int) { view?.currentColor = colorArgb }
    fun setWidthPx(widthPx: Float) { view?.currentWidthPx = widthPx }
    fun setTool(tool: DrawTool) { view?.currentTool = tool }
    fun setShapeSnapEnabled(enabled: Boolean) { view?.shapeSnapEnabled = enabled }
    fun setPaperStyle(style: PaperStyle) { view?.paperStyle = style }
    fun setDarkPaper(dark: Boolean) { view?.setDarkPaper(dark) }
    fun setBackgroundLayers(layers: List<Pair<PageBackground, Bitmap?>>) {
        view?.setBackgroundLayers(layers)
    }

    fun startTextPlacement() { view?.placementMode = PlacementMode.TEXT }
    fun startStickyPlacement() { view?.placementMode = PlacementMode.STICKY }

    fun startSelection(onMade: (RectF) -> Unit, onCancelled: () -> Unit) {
        onSelectionMadeCallback = onMade
        onSelectionCancelledCallback = onCancelled
        view?.selectionModeActive = true
    }

    fun stopSelection() { view?.selectionModeActive = false }

    fun captureRegion(rect: RectF): Bitmap? = view?.captureRegion(rect)
}

@Composable
fun InkCanvas(controller: InkCanvasController, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val heightDp = with(density) { controller.canvasHeightPx.toDp() }
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp),
        factory = { context ->
            InkCanvasView(context).also { controller.bind(it) }
        }
    )
}

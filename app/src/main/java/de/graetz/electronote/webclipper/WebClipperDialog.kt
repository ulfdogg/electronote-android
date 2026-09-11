package de.graetz.electronote.webclipper

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.abs

/**
 * Mini in-app browser with a "cut out a region" tool — an embedded WebView (not Chrome
 * Custom Tabs) since capturing a region as a bitmap requires drawing the page into our
 * own canvas, which only works for content actually hosted inside this app's view tree.
 * Google-login-gated pages will still fail here (same WebView restriction as ever); that's
 * an accepted tradeoff since this is for clipping ordinary reference/worksheet pages.
 */
@Composable
fun WebClipperDialog(
    onDismiss: () -> Unit,
    onInsertImage: (Bitmap) -> Unit,
    onRecognizeText: (Bitmap) -> Unit
) {
    var urlInput by remember { mutableStateOf("https://") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var clippingMode by remember { mutableStateOf(false) }
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Outlined.Close, contentDescription = "Schließen")
                        }
                        OutlinedTextField(
                            value = urlInput,
                            onValueChange = { urlInput = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("URL…") }
                        )
                        TextButton(onClick = {
                            val normalized = if (!urlInput.startsWith("http")) "https://$urlInput" else urlInput
                            webViewRef?.loadUrl(normalized)
                        }) { Text("Los") }
                        IconButton(onClick = { clippingMode = !clippingMode }) {
                            Icon(
                                Icons.Outlined.CropFree,
                                contentDescription = "Ausschneiden",
                                tint = if (clippingMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { context ->
                                WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = WebViewClient()
                                    webViewRef = this
                                }
                            }
                        )

                        if (clippingMode) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset -> dragStart = offset; dragCurrent = offset },
                                            onDrag = { change, _ -> dragCurrent = change.position },
                                            onDragEnd = {
                                                val start = dragStart
                                                val end = dragCurrent
                                                val view = webViewRef
                                                if (start != null && end != null && view != null) {
                                                    val left = minOf(start.x, end.x).toInt()
                                                    val top = minOf(start.y, end.y).toInt()
                                                    val w = abs(end.x - start.x).toInt()
                                                    val h = abs(end.y - start.y).toInt()
                                                    if (w > 24 && h > 24) {
                                                        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                                                        val canvas = AndroidCanvas(full)
                                                        view.draw(canvas)
                                                        val safeLeft = left.coerceIn(0, view.width - 1)
                                                        val safeTop = top.coerceIn(0, view.height - 1)
                                                        val safeW = w.coerceAtMost(view.width - safeLeft)
                                                        val safeH = h.coerceAtMost(view.height - safeTop)
                                                        if (safeW > 0 && safeH > 0) {
                                                            capturedBitmap = Bitmap.createBitmap(full, safeLeft, safeTop, safeW, safeH)
                                                        }
                                                        full.recycle()
                                                    }
                                                }
                                                dragStart = null
                                                dragCurrent = null
                                                clippingMode = false
                                            }
                                        )
                                    }
                            ) {
                                val start = dragStart
                                val current = dragCurrent
                                if (start != null && current != null) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawRect(
                                            color = Color(0x338E24AA),
                                            topLeft = Offset(minOf(start.x, current.x), minOf(start.y, current.y)),
                                            size = Size(abs(current.x - start.x), abs(current.y - start.y))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                capturedBitmap?.let { bitmap ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        shape = MaterialTheme.shapes.large,
                        shadowElevation = 8.dp
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Ausschnitt bereit", modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                onRecognizeText(bitmap)
                                capturedBitmap = null
                            }) { Text("Text erkennen") }
                            TextButton(onClick = {
                                onInsertImage(bitmap)
                                capturedBitmap = null
                                onDismiss()
                            }) { Text("Als Bild") }
                            TextButton(onClick = { capturedBitmap = null }) { Text("Verwerfen") }
                        }
                    }
                }
            }
        }
    }
}

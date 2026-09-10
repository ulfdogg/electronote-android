package de.graetz.electronote.electrical

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONObject

private const val ELEKTROSIM_URL = "https://elektrosimulator.de/?app=electronote"

// The hosted web tool's JS was built against iOS's WKWebView message-handler bridge
// (window.webkit.messageHandlers.electroNote.postMessage({...})) — this polyfills that
// same shape on top of Android's addJavascriptInterface bridge so the (unmodified,
// externally hosted) page can call it back without needing to know it's on Android. If
// the page ever changes its bridge contract this insertion path degrades silently
// (nothing comes back); the page can still be used as a plain reference/scratch tool.
private const val BRIDGE_POLYFILL = """
(function() {
  if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.electroNote) return;
  window.webkit = window.webkit || {};
  window.webkit.messageHandlers = window.webkit.messageHandlers || {};
  window.webkit.messageHandlers.electroNote = {
    postMessage: function(data) { AndroidElectroNote.onMessage(JSON.stringify(data)); }
  };
})();
"""

@Composable
fun ElektroSimDialog(onDismiss: () -> Unit, onInsertImage: (Bitmap) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val webView = WebView(context)
                    webView.settings.javaScriptEnabled = true
                    webView.settings.domStorageEnabled = true
                    webView.addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onMessage(json: String) {
                                try {
                                    val obj = JSONObject(json)
                                    if (obj.optString("action") == "insertCircuit") {
                                        val raw = obj.optString("imageData")
                                        val base64 = if (raw.contains(",")) raw.substringAfter(",") else raw
                                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (bitmap != null) {
                                            webView.post {
                                                onInsertImage(bitmap)
                                                onDismiss()
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Malformed/unexpected bridge message — ignore.
                                }
                            }
                        },
                        "AndroidElectroNote"
                    )
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            view?.evaluateJavascript(BRIDGE_POLYFILL, null)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.evaluateJavascript(BRIDGE_POLYFILL, null)
                        }
                    }
                    webView.loadUrl(ELEKTROSIM_URL)
                    webView
                }
            )
        }
    }
}

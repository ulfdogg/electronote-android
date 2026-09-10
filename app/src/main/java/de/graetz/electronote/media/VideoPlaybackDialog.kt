package de.graetz.electronote.media

import android.net.Uri
import android.webkit.WebView
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

/** Full-screen local video playback, framework `VideoView` — no ExoPlayer dependency needed. */
@Composable
fun LocalVideoPlaybackDialog(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    VideoView(context).apply {
                        setVideoURI(Uri.fromFile(file))
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnPreparedListener { it.start() }
                    }
                }
            )
        }
    }
}

/** Full-screen YouTube playback via the nocookie iframe embed, mirroring iOS's approach. */
@Composable
fun YoutubePlaybackDialog(videoId: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        val html = """
                            <html><body style="margin:0;background:#000;">
                            <iframe width="100%" height="100%"
                                src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1"
                                frameborder="0" allow="autoplay; encrypted-media" allowfullscreen></iframe>
                            </body></html>
                        """.trimIndent()
                        loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "utf-8", null)
                    }
                }
            )
        }
    }
}

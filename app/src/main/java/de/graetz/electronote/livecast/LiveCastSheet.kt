package de.graetz.electronote.livecast

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun LiveCastSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isStreaming = LiveCastServer.isStreaming
    val viewerCount = LiveCastServer.viewerCount
    val localIp = LiveCastServer.localIp
    val port = LiveCastServer.port
    val error = LiveCastServer.lastError

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(isStreaming, localIp, port) {
        if (isStreaming) {
            LiveCastServer.refreshIp()
            qrBitmap = QrCodeGenerator.generate(LiveCastServer.serverUrl())
        } else {
            qrBitmap = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier
                .padding(20.dp)
                .width(340.dp)) {
                Text("Live-Übertragung (Web Cast)", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(if (isStreaming) "LIVE" else "Aus", fontWeight = FontWeight.Bold)
                        if (isStreaming) {
                            Text("IP: $localIp • Port: $port", style = MaterialTheme.typography.bodySmall)
                        } else {
                            Text(
                                "Übertrage die App live an jeden Browser im WLAN",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Switch(checked = isStreaming, onCheckedChange = { LiveCastServer.toggle() })
                }

                if (isStreaming) {
                    Spacer(Modifier.height(16.dp))
                    qrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "QR-Code",
                            modifier = Modifier
                                .size(180.dp)
                                .align(Alignment.CenterHorizontally)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            LiveCastServer.serverUrl(),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        IconButton(onClick = {
                            val cm = context.getSystemService(ClipboardManager::class.java)
                            cm?.setPrimaryClip(ClipData.newPlainText("LiveCast URL", LiveCastServer.serverUrl()))
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = "Kopieren")
                        }
                    }
                    Text("Zuschauer: $viewerCount", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Wichtig: immer http:// (ohne „s“) eingeben.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Fertig")
                    }
                }
            }
        }
    }
}

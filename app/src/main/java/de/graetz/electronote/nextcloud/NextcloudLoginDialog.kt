package de.graetz.electronote.nextcloud

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class LoginState { IDLE, WAITING, ERROR }

@Composable
fun NextcloudLoginDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var credentials by remember { mutableStateOf(NextcloudAuthStore.load(context)) }
    var serverUrlInput by remember { mutableStateOf("https://") }
    var state by remember { mutableStateOf(LoginState.IDLE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun startLogin() {
        val server = serverUrlInput.trim()
        if (server.isBlank()) return
        state = LoginState.WAITING
        errorMessage = null
        scope.launch {
            val pollInfo = withContext(Dispatchers.IO) { NextcloudLoginFlow.initiate(server) }
            if (pollInfo == null) {
                state = LoginState.ERROR
                errorMessage = "Server nicht erreichbar oder Login Flow nicht unterstützt."
                return@launch
            }
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(pollInfo.loginUrl))

            var result: NextcloudCredentials? = null
            var attempts = 0
            while (result == null && attempts < 150) { // ~5 Minuten bei 2s Intervall
                delay(2000)
                result = withContext(Dispatchers.IO) { NextcloudLoginFlow.poll(pollInfo) }
                attempts++
            }
            if (result != null) {
                NextcloudAuthStore.save(context, result)
                credentials = result
                state = LoginState.IDLE
            } else {
                state = LoginState.ERROR
                errorMessage = "Anmeldung abgebrochen oder Zeit abgelaufen."
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .width(340.dp)
            ) {
                Text("Nextcloud", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                val current = credentials
                if (current != null) {
                    Text("Verbunden als ${current.loginName}", fontWeight = FontWeight.Bold)
                    Text(current.serverUrl, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = {
                        NextcloudAuthStore.clear(context)
                        credentials = null
                    }) { Text("Trennen") }
                } else {
                    when (state) {
                        LoginState.WAITING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Warte auf Anmeldung im Browser…")
                            }
                        }
                        else -> {
                            OutlinedTextField(
                                value = serverUrlInput,
                                onValueChange = { serverUrlInput = it },
                                label = { Text("Server-Adresse") },
                                placeholder = { Text("https://cloud.example.com") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            errorMessage?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { startLogin() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Anmelden")
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Fertig") }
                }
            }
        }
    }
}

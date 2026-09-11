package de.graetz.electronote.drive

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

@Composable
fun GoogleDriveLoginDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var account by remember { mutableStateOf(GoogleDriveAuth.lastAccount(context)) }
    var error by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            account = task.getResult(ApiException::class.java)
            error = null
        } catch (e: ApiException) {
            error = "Anmeldung fehlgeschlagen (Code ${e.statusCode}) — ist die OAuth-Client-ID in der Google Cloud Console registriert?"
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(20.dp).width(340.dp)) {
                Text("Google Drive", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val current = account
                if (current != null) {
                    Text("Verbunden als ${current.email ?: current.displayName ?: "?"}")
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { GoogleDriveAuth.signOut(context) { account = null } }) { Text("Trennen") }
                        TextButton(onClick = onDismiss) { Text("Schließen") }
                    }
                } else {
                    Text("Melde dich mit deinem Google-Konto an, um Notizbücher in Google Drive zu sichern.")
                    error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = onDismiss) { Text("Abbrechen") }
                        TextButton(onClick = { signInLauncher.launch(GoogleDriveAuth.signInClient(context).signInIntent) }) { Text("Anmelden") }
                    }
                }
            }
        }
    }
}

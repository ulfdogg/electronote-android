package de.graetz.electronote.drive

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

/**
 * Google Sign-In with Drive scope. Unlike Nextcloud's Login Flow v2, this requires the
 * app itself to be pre-registered with Google (OAuth client ID tied to the package name +
 * signing certificate) — Google Play Services then handles token storage/refresh on-device,
 * so unlike NextcloudAuthStore this doesn't need to persist credentials itself.
 */
object GoogleDriveAuth {
    private const val DRIVE_SCOPE = "oauth2:https://www.googleapis.com/auth/drive.file"

    fun signInClient(context: Context): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/drive.file"))
            .build()
        return GoogleSignIn.getClient(context, options)
    }

    fun lastAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    /** Blocking — call from a background dispatcher. */
    fun getAccessToken(context: Context, account: GoogleSignInAccount): String? {
        val androidAccount = account.account ?: return null
        return try {
            GoogleAuthUtil.getToken(context, androidAccount, DRIVE_SCOPE)
        } catch (e: Exception) {
            null
        }
    }

    fun signOut(context: Context, onComplete: () -> Unit) {
        signInClient(context).signOut().addOnCompleteListener { onComplete() }
    }
}

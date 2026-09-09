package de.graetz.electronote.nextcloud

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores the Nextcloud app-password credentials encrypted at rest — these are real login secrets. */
object NextcloudAuthStore {
    private const val PREFS_NAME = "electronote_nextcloud_secure"
    private const val KEY_SERVER = "server_url"
    private const val KEY_LOGIN_NAME = "login_name"
    private const val KEY_APP_PASSWORD = "app_password"

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(context: Context, credentials: NextcloudCredentials) {
        prefs(context).edit()
            .putString(KEY_SERVER, credentials.serverUrl)
            .putString(KEY_LOGIN_NAME, credentials.loginName)
            .putString(KEY_APP_PASSWORD, credentials.appPassword)
            .apply()
    }

    fun load(context: Context): NextcloudCredentials? {
        val p = prefs(context)
        val server = p.getString(KEY_SERVER, null) ?: return null
        val loginName = p.getString(KEY_LOGIN_NAME, null) ?: return null
        val appPassword = p.getString(KEY_APP_PASSWORD, null) ?: return null
        return NextcloudCredentials(server, loginName, appPassword)
    }

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

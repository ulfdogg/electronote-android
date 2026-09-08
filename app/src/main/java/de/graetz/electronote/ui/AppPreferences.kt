package de.graetz.electronote.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide, explicitly user-controlled dark mode — same idea as the moon-icon toggle on
 * iOS: independent of the system theme, persisted across launches.
 */
object AppPreferences {
    private const val PREFS_NAME = "electronote_prefs"
    private const val KEY_DARK_MODE = "dark_mode"

    var isDarkMode by mutableStateOf(false)
        private set

    private var initialized = false

    fun init(context: Context, systemDarkDefault: Boolean) {
        if (initialized) return
        initialized = true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDarkMode = if (prefs.contains(KEY_DARK_MODE)) {
            prefs.getBoolean(KEY_DARK_MODE, systemDarkDefault)
        } else {
            systemDarkDefault
        }
    }

    fun toggleDarkMode(context: Context) {
        isDarkMode = !isDarkMode
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_MODE, isDarkMode)
            .apply()
    }
}

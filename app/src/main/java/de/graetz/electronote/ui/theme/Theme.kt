package de.graetz.electronote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = IosColors.Blue,
    secondary = IosColors.Purple,
    background = IosColors.SecondarySystemBackgroundLight,
    surface = IosColors.SystemBackgroundLight,
    surfaceVariant = IosColors.TertiarySystemFillLight,
    error = IosColors.Red
)

private val DarkColors = darkColorScheme(
    primary = IosColors.Blue,
    secondary = IosColors.Purple,
    background = IosColors.SystemBackgroundDark,
    surface = IosColors.SecondarySystemBackgroundDark,
    surfaceVariant = IosColors.TertiarySystemFillDark,
    error = IosColors.Red
)

@Composable
fun ElectroNoteTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}

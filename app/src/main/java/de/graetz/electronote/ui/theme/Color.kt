package de.graetz.electronote.ui.theme

import androidx.compose.ui.graphics.Color

// iOS system-color equivalents, so the Android app reads as the same product as the
// iPad app rather than a generic Material app. Each named color below matches Apple's
// UIColor.system* value.
object IosColors {
    val Blue = Color(0xFF007AFF)
    val Purple = Color(0xFFAF52DE)
    val Orange = Color(0xFFFF9500)
    val Yellow = Color(0xFFFFCC00)
    val Teal = Color(0xFF30B0C7)
    val Cyan = Color(0xFF32ADE6)
    val Pink = Color(0xFFFF2D55)
    val Indigo = Color(0xFF5856D6)
    val Red = Color(0xFFFF3B30)
    val Mint = Color(0xFF00C7BE)
    val Green = Color(0xFF34C759)

    // Light mode system backgrounds
    val SystemBackgroundLight = Color(0xFFFFFFFF)
    val SecondarySystemBackgroundLight = Color(0xFFF2F2F7)
    val TertiarySystemFillLight = Color(0xFFE9E9EE)

    // Dark mode system backgrounds
    val SystemBackgroundDark = Color(0xFF000000)
    val SecondarySystemBackgroundDark = Color(0xFF1C1C1E)
    val TertiarySystemFillDark = Color(0xFF3A3A3C)
}

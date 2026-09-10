package de.graetz.electronote.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A colored icon+label capsule button — the recurring building block of the iPad app's
 * second toolbar row (Formen, Handschrift, Mathe, KI, …). Reused here for every quick
 * action so the Android app reads as the same product, not a generic Material screen.
 */
@Composable
fun ActionPill(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color.White
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.padding(start = 5.dp)) {
            Text(label, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

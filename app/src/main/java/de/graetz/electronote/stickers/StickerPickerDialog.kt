package de.graetz.electronote.stickers

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun StickerPickerDialog(onDismiss: () -> Unit, onPick: (Bitmap) -> Unit) {
    var category by remember { mutableStateOf<StickerCategory?>(null) }
    val filtered = remember(category) {
        StickerType.entries.filter { category == null || it.category == category }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp).width(360.dp).height(420.dp)) {
                Text("Sticker", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryChip("Alle", category == null) { category = null }
                    for (cat in StickerCategory.entries) {
                        CategoryChip(cat.label, category == cat) { category = cat }
                    }
                }
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f)) {
                    items(filtered) { type ->
                        val bitmap = remember(type) { StickerRenderer.render(type) }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .padding(6.dp)
                                .clickable {
                                    onPick(bitmap)
                                    onDismiss()
                                }
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = type.displayName,
                                modifier = Modifier.size(56.dp)
                            )
                            Text(type.displayName, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
